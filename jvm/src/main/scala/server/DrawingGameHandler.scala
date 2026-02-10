package server

import org.slf4j.LoggerFactory
import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import castor.Context.Simple.global as castorGlobal
import shared.DrawingGame.*
import java.util.concurrent.{ConcurrentHashMap, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.jdk.CollectionConverters.*

object DrawingGameHandler:
  private val logger = LoggerFactory.getLogger(getClass)

  given castor.Context = castorGlobal
  given cask.util.Logger = cask.util.Logger.Console.globalLogger

  // Lobby state: lobbyId -> (lobby, apiKey)
  private val lobbies = ConcurrentHashMap[String, (DrawingLobby, String)]()

  // WebSocket connections: lobbyId -> Set[channels]
  private val connections = ConcurrentHashMap[String, java.util.Set[cask.WsChannelActor]]()

  // Player to lobby mapping: playerId -> lobbyId
  private val playerLobbies = ConcurrentHashMap[String, String]()

  // Channel to player mapping: channel -> playerId
  private val channelToPlayer = ConcurrentHashMap[cask.WsChannelActor, String]()

  // Channel to lobby mapping: channel -> lobbyId
  private val channelToLobby = ConcurrentHashMap[cask.WsChannelActor, String]()

  // Timer scheduler
  private val timerScheduler: ScheduledExecutorService =
    java.util.concurrent.Executors.newScheduledThreadPool(2)

  // Active timer futures per lobby - so we can cancel them
  private val activeTimers = ConcurrentHashMap[String, java.util.List[ScheduledFuture[?]]]()

  // Flag to track if round is already completed (prevents double completion)
  private val roundCompleted = ConcurrentHashMap[String, Boolean]()
  
  // Track channels that have been closed (to avoid sending to them)
  private val closedChannels = java.util.Collections.newSetFromMap(
    new java.util.WeakHashMap[cask.WsChannelActor, java.lang.Boolean]()
  )

  def handleWebSocket(lobbyId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      addConnection(lobbyId, channel)
      logger.info(s"Client connected to drawing lobby $lobbyId")

      // Send current lobby state to the newly connected client
      lobbies.get(lobbyId) match
        case null => () // Lobby doesn't exist yet
        case (lobby, _) => sendToClient(channel, ServerMessage.LobbyUpdate(lobby))

      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.info(s"Received WebSocket message in lobby $lobbyId: $msg")
          Try:
            import upickle.default.*
            val clientMsg = read[ClientMessage](msg)
            handleClientMessage(channel, lobbyId, clientMsg)
          .recover:
            case ex =>
              logger.error(s"Error processing message: ${ex.getMessage}", ex)
              sendToClient(channel, ServerMessage.ErrorMessage(s"Invalid message: ${ex.getMessage}"))

        case cask.Ws.Close(_, _) =>
          handleDisconnect(channel, lobbyId)

        case cask.Ws.Error(ex) =>
          logger.error(s"WebSocket error in lobby $lobbyId: ${ex.getMessage}", ex)
          handleDisconnect(channel, lobbyId)

  private def addConnection(lobbyId: String, channel: cask.WsChannelActor): Unit =
    connections.computeIfAbsent(lobbyId, _ => ConcurrentHashMap.newKeySet()).add(channel)

  private def removeConnection(lobbyId: String, channel: cask.WsChannelActor): Unit =
    connections.get(lobbyId) match
      case null => ()
      case channelSet => channelSet.remove(channel)

  // Cancel all active timers for a lobby
  private def cancelTimers(lobbyId: String): Unit =
    Option(activeTimers.remove(lobbyId)).foreach: futures =>
      futures.asScala.foreach(_.cancel(false))

  // Schedule a timer task and track it for later cancellation
  private def scheduleTimer(lobbyId: String, task: Runnable, delaySeconds: Long): Unit =
    val futures = activeTimers.computeIfAbsent(lobbyId, _ => new java.util.ArrayList())
    val future = timerScheduler.schedule(task, delaySeconds, TimeUnit.SECONDS)
    futures.add(future)

  private def handleClientMessage(channel: cask.WsChannelActor, lobbyId: String, msg: ClientMessage): Unit =
    // Get the actual lobby ID from the channel mapping (in case it was moved from "temp")
    val actualLobbyId = Option(channelToLobby.get(channel)).getOrElse(lobbyId)

    msg match
      case ClientMessage.CreateLobby(playerName, apiKey) =>
        handleCreateLobby(channel, playerName, apiKey)

      case ClientMessage.JoinLobby(joinLobbyId, playerName) =>
        handleJoinLobby(channel, joinLobbyId, playerName)

      case ClientMessage.StartGame() =>
        handleStartGame(actualLobbyId)

      case ClientMessage.SubmitDrawing(imageData) =>
        handleSubmitDrawing(channel, actualLobbyId, imageData)

      case ClientMessage.SubmitVote(playerNameVotedFor) =>
        handleSubmitVote(channel, actualLobbyId, playerNameVotedFor)

      case ClientMessage.NextRound() =>
        handleNextRound(actualLobbyId)

  private def handleCreateLobby(channel: cask.WsChannelActor, playerName: String, apiKey: String): Unit =
    val lobbyId = generateLobbyId()
    val playerId = generatePlayerId()
    val lobby = DrawingGame.createLobby(lobbyId, playerId, playerName)

    lobbies.put(lobbyId, (lobby, apiKey))
    playerLobbies.put(playerId, lobbyId)
    channelToPlayer.put(channel, playerId)
    channelToLobby.put(channel, lobbyId)

    // Move connection from "temp" to the actual lobby
    removeConnection("temp", channel)
    addConnection(lobbyId, channel)

    logger.info(s"Created lobby $lobbyId for player $playerName (playerId: $playerId)")

    sendToClient(channel, ServerMessage.LobbyCreated(lobbyId, playerId))
    sendToClient(channel, ServerMessage.LobbyUpdate(lobby))

  private def handleJoinLobby(channel: cask.WsChannelActor, lobbyId: String, playerName: String): Unit =
    lobbies.get(lobbyId) match
      case null =>
        sendToClient(channel, ServerMessage.ErrorMessage("Lobby not found"))
      case (lobby, apiKey) =>
        if lobby.status != LobbyStatus.Waiting then
          sendToClient(channel, ServerMessage.ErrorMessage("Game already in progress"))
        else if lobby.players.size >= DrawingGame.maxPlayersPerLobby then
          sendToClient(channel, ServerMessage.ErrorMessage("Lobby is full"))
        else
          val playerId = generatePlayerId()
          val updatedLobby = DrawingGame.addPlayer(lobby, playerId, playerName)
          lobbies.put(lobbyId, (updatedLobby, apiKey))
          playerLobbies.put(playerId, lobbyId)
          channelToPlayer.put(channel, playerId)
          channelToLobby.put(channel, lobbyId)

          // Add connection to lobby
          addConnection(lobbyId, channel)

          logger.info(s"Player $playerName joined lobby $lobbyId (playerId: $playerId)")

          sendToClient(channel, ServerMessage.LobbyCreated(lobbyId, playerId))
          broadcastLobbyUpdate(lobbyId)

  private def handleStartGame(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null =>
        logger.warn(s"Attempted to start non-existent lobby $lobbyId")
      case (lobby, apiKey) =>
        if lobby.players.isEmpty then
          broadcast(lobbyId, ServerMessage.ErrorMessage("Need at least 1 player to start"))
        else
          val updatedLobby = DrawingGame.startDrawingPhase(lobby)
          lobbies.put(lobbyId, (updatedLobby, apiKey))

          updatedLobby.currentPrompt.foreach: prompt =>
            broadcast(lobbyId, ServerMessage.PromptAnnounced(prompt))

          broadcastLobbyUpdate(lobbyId)
          startDrawingTimer(lobbyId)

  private def handleSubmitDrawing(channel: cask.WsChannelActor, lobbyId: String, imageData: String): Unit =
    getPlayerIdByChannel(channel, lobbyId) match
      case None =>
        sendToClient(channel, ServerMessage.ErrorMessage("Player not found"))
      case Some(playerId) =>
        lobbies.get(lobbyId) match
          case null =>
            sendToClient(channel, ServerMessage.ErrorMessage("Lobby not found"))
          case (lobby, apiKey) =>
            val updatedLobby = DrawingGame.submitDrawing(lobby, playerId, imageData)
            lobbies.put(lobbyId, (updatedLobby, apiKey))

            lobby.players.get(playerId).foreach: player =>
              broadcast(lobbyId, ServerMessage.DrawingSubmitted(player.playerName))

            broadcastLobbyUpdate(lobbyId)

            // If all drawings submitted, start AI captioning
            if DrawingGame.allDrawingsSubmitted(updatedLobby) then
              startCaptioningPhase(lobbyId)

  private def handleSubmitVote(channel: cask.WsChannelActor, lobbyId: String, playerNameVotedFor: String): Unit =
    getPlayerIdByChannel(channel, lobbyId) match
      case None =>
        sendToClient(channel, ServerMessage.ErrorMessage("Player not found"))
      case Some(voterId) =>
        lobbies.get(lobbyId) match
          case null =>
            sendToClient(channel, ServerMessage.ErrorMessage("Lobby not found"))
          case (lobby, apiKey) =>
            val updatedLobby = DrawingGame.submitVote(lobby, voterId, playerNameVotedFor)
            lobbies.put(lobbyId, (updatedLobby, apiKey))

            val voteCounts = DrawingGame.tallyVotes(updatedLobby)
            broadcast(lobbyId, ServerMessage.VoteUpdate(voteCounts))

            // If all votes submitted, complete the round (only if not already completed)
            if DrawingGame.allVotesSubmitted(updatedLobby) && !roundCompleted.getOrDefault(lobbyId, false) then
              roundCompleted.put(lobbyId, true)
              completeRound(lobbyId)

  private def handleNextRound(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null =>
        logger.warn(s"Attempted to advance non-existent lobby $lobbyId")
      case (lobby, apiKey) =>
        if DrawingGame.shouldEndGame(lobby) then
          val finalLobby = lobby.copy(status = LobbyStatus.Waiting, currentRound = 0)
          lobbies.put(lobbyId, (finalLobby, apiKey))
          broadcastLobbyUpdate(lobbyId)
        else
          val updatedLobby = DrawingGame.startDrawingPhase(lobby)
          lobbies.put(lobbyId, (updatedLobby, apiKey))

          updatedLobby.currentPrompt.foreach: prompt =>
            broadcast(lobbyId, ServerMessage.PromptAnnounced(prompt))

          broadcastLobbyUpdate(lobbyId)
          startDrawingTimer(lobbyId)

  private def startDrawingTimer(lobbyId: String): Unit =
    // Cancel any existing timers for this lobby
    cancelTimers(lobbyId)
    roundCompleted.put(lobbyId, false)

    // Schedule timer updates every second
    (1 to DrawingGame.drawingTimeSeconds).foreach: i =>
      val secondsRemaining = DrawingGame.drawingTimeSeconds - i
      val task: Runnable = () =>
        broadcast(lobbyId, ServerMessage.TimerUpdate(secondsRemaining))
        if secondsRemaining <= 0 then
          // Timer expired, move to captioning with submitted drawings
          startCaptioningPhase(lobbyId)
      scheduleTimer(lobbyId, task, i.toLong)

  private def startCaptioningPhase(lobbyId: String): Unit =
    // Cancel drawing timer
    cancelTimers(lobbyId)

    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        val updatedLobby = lobby.copy(status = LobbyStatus.Captioning)
        lobbies.put(lobbyId, (updatedLobby, apiKey))
        broadcastLobbyUpdate(lobbyId)

        // Phase 1: Reveal all drawings (without captions yet)
        val drawingsWithoutCaptions = updatedLobby.drawings.values.map(d => d.copy(caption = None)).toSeq
        broadcast(lobbyId, ServerMessage.DrawingsRevealed(drawingsWithoutCaptions, lobby.currentPrompt.getOrElse("")))

        // Caption all submitted drawings
        val captioningFutures = updatedLobby.drawings.map: (playerId, drawing) =>
          OpenAIClient.captionImage(apiKey, drawing.imageData).map: caption =>
            (playerId, drawing.playerName, caption)

        Future.sequence(captioningFutures).map: captionResults =>
          // Add captions to lobby
          var currentLobby = updatedLobby
          captionResults.foreach: (playerId, _, caption) =>
            currentLobby = DrawingGame.addCaption(currentLobby, playerId, caption)

          lobbies.put(lobbyId, (currentLobby, apiKey))

          // Phase 2: Reveal captions one by one with delays
          var delay = 1L
          captionResults.foreach: (_, playerName, caption) =>
            val captionTask: Runnable = () => broadcast(lobbyId, ServerMessage.CaptionRevealed(playerName, caption))
            scheduleTimer(lobbyId, captionTask, delay)
            delay += 2 // 2 seconds between each caption reveal

          // Phase 3: AI reveals its vote
          val captions = currentLobby.drawings.values.map(d => d.playerName -> d.caption.getOrElse("")).toMap
          currentLobby.currentPrompt.foreach: prompt =>
            val aiVoteDelay = delay + 1
            OpenAIClient.selectWinner(apiKey, prompt, captions).map: aiWinnerName =>
              val aiVoteTask: Runnable = () =>
                broadcast(lobbyId, ServerMessage.AIVoteRevealed(aiWinnerName, s"Best match for '$prompt'"))
                // Phase 4: Start voting phase after AI vote reveal
                val votingTask: Runnable = () => startVotingPhase(lobbyId, aiWinnerName)
                scheduleTimer(lobbyId, votingTask, 2L)
              scheduleTimer(lobbyId, aiVoteTask, aiVoteDelay)
            .recover:
              case ex =>
                logger.error(s"Error selecting AI winner: ${ex.getMessage}", ex)
                // Continue without AI winner
                val fallbackTask: Runnable = () => startVotingPhase(lobbyId, "")
                scheduleTimer(lobbyId, fallbackTask, aiVoteDelay)
        .recover:
          case ex =>
            logger.error(s"Error in captioning phase: ${ex.getMessage}", ex)
            broadcast(lobbyId, ServerMessage.ErrorMessage("AI captioning failed"))

  // Store AI winner for the round
  private val aiWinners = ConcurrentHashMap[String, String]()

  private def startVotingPhase(lobbyId: String, aiWinnerName: String): Unit =
    // Cancel any captioning phase timers
    cancelTimers(lobbyId)

    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        // Store AI winner for later
        if aiWinnerName.nonEmpty then
          aiWinners.put(lobbyId, aiWinnerName)

        val votingLobby = DrawingGame.startVoting(lobby)
        lobbies.put(lobbyId, (votingLobby, apiKey))
        broadcastLobbyUpdate(lobbyId)

        // Start voting timer
        broadcast(lobbyId, ServerMessage.VotingStarted(DrawingGame.votingTimeSeconds))
        startVotingTimer(lobbyId)

  private def startVotingTimer(lobbyId: String): Unit =
    // Schedule timer updates every second
    (1 to DrawingGame.votingTimeSeconds).foreach: i =>
      val secondsRemaining = DrawingGame.votingTimeSeconds - i
      val task: Runnable = () =>
        broadcast(lobbyId, ServerMessage.TimerUpdate(secondsRemaining))
        if secondsRemaining <= 0 then
          // Timer expired, complete the round (only if not already completed)
          if !roundCompleted.getOrDefault(lobbyId, false) then
            roundCompleted.put(lobbyId, true)
            completeRound(lobbyId)
      scheduleTimer(lobbyId, task, i.toLong)

  private def completeRound(lobbyId: String): Unit =
    // Cancel any voting timers
    cancelTimers(lobbyId)

    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        // Check if already in Results status to prevent double completion
        if lobby.status == LobbyStatus.Results then
          return

        val voteCounts = DrawingGame.tallyVotes(lobby)
        val playerWinner = voteCounts.maxByOption(_._2).map(_._1)

        // Get stored AI winner
        val aiWinnerName = Option(aiWinners.remove(lobbyId))

        val result = RoundResult(aiWinnerName, playerWinner, voteCounts)

        // Update scores
        val scoredLobby = DrawingGame.updatePlayerScores(lobby, aiWinnerName, playerWinner)
        val resultsLobby = scoredLobby.copy(status = LobbyStatus.Results)
        lobbies.put(lobbyId, (resultsLobby, apiKey))

        broadcast(lobbyId, ServerMessage.RoundComplete(result))
        broadcastLobbyUpdate(lobbyId)

  private def handleDisconnect(channel: cask.WsChannelActor, lobbyId: String): Unit =
    // Mark channel as closed to prevent future sends
    closedChannels.add(channel)
    
    // Get actual lobby from channel mapping
    val actualLobbyId = Option(channelToLobby.get(channel)).getOrElse(lobbyId)
    
    connections.get(actualLobbyId) match
      case null => ()
      case channelSet =>
        channelSet.remove(channel)
        logger.info(s"Client disconnected from lobby $actualLobbyId")
    
    // Also try to remove from the URL-based lobby
    if actualLobbyId != lobbyId then
      connections.get(lobbyId) match
        case null => ()
        case channelSet => channelSet.remove(channel)
    
    // Clean up mappings
    channelToPlayer.remove(channel)
    channelToLobby.remove(channel)

  private def broadcast(lobbyId: String, message: ServerMessage): Unit =
    import upickle.default.*
    val json = write(message)
    connections.get(lobbyId) match
      case null => ()
      case channelSet =>
        // Filter out closed channels before iterating
        val activeChannels = channelSet.asScala.filterNot(closedChannels.contains)
        activeChannels.foreach: channel =>
          channel.send(cask.Ws.Text(json))

  private def broadcastLobbyUpdate(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, _) =>
        broadcast(lobbyId, ServerMessage.LobbyUpdate(lobby))

  private def sendToClient(channel: cask.WsChannelActor, message: ServerMessage): Unit =
    if closedChannels.contains(channel) then
      return // Channel is closed, skip
    import upickle.default.*
    val json = write(message)
    channel.send(cask.Ws.Text(json))

  private def getPlayerIdByChannel(channel: cask.WsChannelActor, lobbyId: String): Option[String] =
    Option(channelToPlayer.get(channel))

  private def generateLobbyId(): String =
    import scala.util.Random
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    (1 to 6).map(_ => chars(Random.nextInt(chars.length))).mkString

  private def generatePlayerId(): String =
    java.util.UUID.randomUUID().toString

  def cleanupEmptyLobbies(): Unit =
    val emptyLobbies = lobbies.asScala.filter:
      case (lobbyId, (lobby, _)) => lobby.players.isEmpty
    .keys

    emptyLobbies.foreach: lobbyId =>
      lobbies.remove(lobbyId)
      connections.remove(lobbyId)
      logger.info(s"Cleaned up empty lobby $lobbyId")


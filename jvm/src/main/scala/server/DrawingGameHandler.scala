package server

import org.slf4j.{Logger, LoggerFactory}
import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import castor.Context.Simple.global as castorGlobal
import shared.DrawingGame.*
import server.reconnection.ReconnectionSupport
import java.util.concurrent.{ConcurrentHashMap, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.jdk.CollectionConverters.*

object DrawingGameHandler extends ReconnectionSupport[PlayerInfo, DrawingLobby]:
  protected val logger: Logger = LoggerFactory.getLogger(getClass)

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

  // All active timer futures per lobby (countdown ticks AND transition timer)
  private val activeTimers = ConcurrentHashMap[String, java.util.List[ScheduledFuture[?]]]()

  // Track channels that have been closed (to avoid sending to them)
  private val closedChannels = java.util.Collections.newSetFromMap(
    new java.util.WeakHashMap[cask.WsChannelActor, java.lang.Boolean]()
  )
  
  // Pending captions: lobbyId -> list of (playerId, playerName, caption) awaiting reveal
  private val pendingCaptions = ConcurrentHashMap[String, List[(String, String, String)]]()
  
  // Stored AI winner per lobby
  private val aiWinners = ConcurrentHashMap[String, String]()

  // ============================================================================
  // ReconnectionSupport implementation
  // ============================================================================

  protected def getGame(lobbyId: String): Option[DrawingLobby] =
    Option(lobbies.get(lobbyId)).map(_._1)

  protected def getPlayers(lobby: DrawingLobby): Map[String, PlayerInfo] =
    lobby.players

  protected def reconnectPlayer(player: PlayerInfo): PlayerInfo =
    player.copy(connected = true, disconnectedAt = None)

  protected def disconnectPlayer(player: PlayerInfo): PlayerInfo =
    player.copy(connected = false, disconnectedAt = Some(System.currentTimeMillis()))

  protected def withUpdatedPlayer(lobby: DrawingLobby, playerId: String, player: PlayerInfo): DrawingLobby =
    lobby.copy(players = lobby.players + (playerId -> player))

  protected def saveGame(lobbyId: String, lobby: DrawingLobby): Unit =
    // Preserve the apiKey when saving
    Option(lobbies.get(lobbyId)).foreach: (_, apiKey) =>
      lobbies.put(lobbyId, (lobby, apiKey))

  protected def registerPlayerChannel(channel: cask.WsChannelActor, lobbyId: String, playerId: String): Unit =
    channelToPlayer.put(channel, playerId)
    channelToLobby.put(channel, lobbyId)
    addConnection(lobbyId, channel)

  protected def sendRejoinSuccess(channel: cask.WsChannelActor, playerId: String, lobbyId: String): Unit =
    sendToClient(channel, ServerMessage.LobbyCreated(lobbyId, playerId))

  protected def sendRejoinFailure(channel: cask.WsChannelActor, reason: String): Unit =
    sendToClient(channel, ServerMessage.RejoinFailed(reason))

  protected def broadcastGameState(lobbyId: String): Unit =
    broadcastLobbyUpdate(lobbyId)

  // ============================================================================
  // WebSocket Handling
  // ============================================================================

  def handleWebSocket(lobbyId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      addConnection(lobbyId, channel)
      logger.info(s"Client connected to drawing lobby $lobbyId")

      lobbies.get(lobbyId) match
        case null => ()
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

  // ============================================================================
  // Client Message Handling
  // ============================================================================

  private def handleClientMessage(channel: cask.WsChannelActor, lobbyId: String, msg: ClientMessage): Unit =
    val actualLobbyId = Option(channelToLobby.get(channel)).getOrElse(lobbyId)

    msg match
      case ClientMessage.CreateLobby(playerName, apiKey, gameMode, captionStyle) =>
        handleCreateLobby(channel, playerName, apiKey, gameMode, captionStyle)

      case ClientMessage.JoinLobby(joinLobbyId, playerName) =>
        handleJoinLobby(channel, joinLobbyId, playerName)

      case ClientMessage.RejoinLobby(rejoinLobbyId, playerId) =>
        handleRejoinLobby(channel, rejoinLobbyId, playerId)

      case ClientMessage.StartGame() =>
        handleStartGame(actualLobbyId)

      case ClientMessage.SubmitDrawing(imageData) =>
        handleSubmitDrawing(channel, actualLobbyId, imageData)

      case ClientMessage.SubmitVote(playerNameVotedFor) =>
        handleSubmitVote(channel, actualLobbyId, playerNameVotedFor)

      case ClientMessage.NextRound() =>
        handleNextRound(actualLobbyId)

      case ClientMessage.Ping() =>
        // Keepalive ping - no action needed, just keeps the connection alive
        logger.debug(s"Received keepalive ping for lobby $actualLobbyId")

  private def handleCreateLobby(channel: cask.WsChannelActor, playerName: String, apiKey: String, gameMode: GameMode, captionStyle: CaptionStyle): Unit =
    val lobbyId = generateLobbyId()
    val playerId = generatePlayerId()
    val lobby = DrawingGame.createLobby(lobbyId, playerId, playerName, gameMode = gameMode, captionStyle = captionStyle)

    lobbies.put(lobbyId, (lobby, apiKey))
    playerLobbies.put(playerId, lobbyId)
    channelToPlayer.put(channel, playerId)
    channelToLobby.put(channel, lobbyId)

    removeConnection("temp", channel)
    addConnection(lobbyId, channel)

    logger.info(s"Created lobby $lobbyId for player $playerName (playerId: $playerId, gameMode: $gameMode, captionStyle: $captionStyle)")

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
          addConnection(lobbyId, channel)

          logger.info(s"Player $playerName joined lobby $lobbyId (playerId: $playerId)")

          sendToClient(channel, ServerMessage.LobbyCreated(lobbyId, playerId))
          broadcastLobbyUpdate(lobbyId)

  private def handleRejoinLobby(channel: cask.WsChannelActor, lobbyId: String, playerId: String): Unit =
    // Use trait's handleRejoinRequest for the core rejoin logic
    handleRejoinRequest(channel, lobbyId, playerId)
    
    // If rejoin was successful, send additional game-specific state
    // Check if channel is now registered to this lobby
    if Option(channelToLobby.get(channel)).contains(lobbyId) then
      getGame(lobbyId).foreach: lobby =>
        // Send current lobby state
        sendToClient(channel, ServerMessage.LobbyUpdate(lobby))
        
        lobby.status match
          case LobbyStatus.Drawing =>
            // Send current prompt if in drawing phase
            lobby.currentPrompt.foreach: prompt =>
              sendToClient(channel, ServerMessage.PromptAnnounced(prompt))
          
          case LobbyStatus.RevealingDrawings | LobbyStatus.RevealingCaptions | 
               LobbyStatus.RevealingAIWinner | LobbyStatus.Voting | LobbyStatus.Results =>
            // Send drawings data so gallery can be populated
            lobby.currentPrompt.foreach: prompt =>
              val drawings = lobby.drawings.values.toSeq
              sendToClient(channel, ServerMessage.DrawingsRevealed(drawings, prompt))
              
              // Also send any captions that have been revealed
              drawings.foreach: drawing =>
                drawing.caption.foreach: caption =>
                  sendToClient(channel, ServerMessage.CaptionRevealed(drawing.playerName, caption))
              
              // Send AI winner if already revealed
              Option(aiWinners.get(lobbyId)).foreach: winnerName =>
                sendToClient(channel, ServerMessage.AIVoteRevealed(winnerName, "AI's earlier pick"))
              
              // If in voting phase, notify client
              if lobby.status == LobbyStatus.Voting then
                // Calculate remaining time approximately
                val elapsed = lobby.timerStartTime.map(t => (System.currentTimeMillis() - t) / 1000).getOrElse(0L)
                val remaining = Math.max(0, DrawingGame.votingTimeSeconds - elapsed.toInt)
                sendToClient(channel, ServerMessage.VotingStarted(remaining))
                
                // Send current vote counts
                val voteCounts = DrawingGame.tallyVotes(lobby)
                sendToClient(channel, ServerMessage.VoteUpdate(voteCounts))
              
              // If in results phase, send round complete
              if lobby.status == LobbyStatus.Results then
                val voteCounts = DrawingGame.tallyVotes(lobby)
                val playerWinner = voteCounts.maxByOption(_._2).map(_._1)
                val aiWinnerName = Option(aiWinners.get(lobbyId))
                val result = RoundResult(aiWinnerName, playerWinner, voteCounts)
                sendToClient(channel, ServerMessage.RoundComplete(result))
          
          case _ => () // Waiting, CollectingDrawings - no extra state needed

  private def handleStartGame(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null =>
        logger.warn(s"Attempted to start non-existent lobby $lobbyId")
      case (lobby, apiKey) =>
        if lobby.players.isEmpty then
          broadcast(lobbyId, ServerMessage.ErrorMessage("Need at least 1 player to start"))
        else if lobby.status != LobbyStatus.Waiting then
          broadcast(lobbyId, ServerMessage.ErrorMessage("Game already in progress"))
        else
          transitionTo(lobbyId, LobbyStatus.Drawing)

  private def handleSubmitDrawing(channel: cask.WsChannelActor, lobbyId: String, imageData: String): Unit =
    getPlayerIdByChannel(channel, lobbyId) match
      case None =>
        sendToClient(channel, ServerMessage.ErrorMessage("Player not found"))
      case Some(playerId) =>
        lobbies.get(lobbyId) match
          case null =>
            sendToClient(channel, ServerMessage.ErrorMessage("Lobby not found"))
          case (lobby, apiKey) =>
            // Accept drawings in Drawing or CollectingDrawings state
            if lobby.status != LobbyStatus.Drawing && lobby.status != LobbyStatus.CollectingDrawings then
              sendToClient(channel, ServerMessage.ErrorMessage("Not accepting drawings"))
            else
              val updatedLobby = DrawingGame.submitDrawing(lobby, playerId, imageData)
              lobbies.put(lobbyId, (updatedLobby, apiKey))

              lobby.players.get(playerId).foreach: player =>
                broadcast(lobbyId, ServerMessage.DrawingSubmitted(player.playerName))

              broadcastLobbyUpdate(lobbyId)

              // If all drawings submitted during Drawing phase, move to next phase
              if DrawingGame.allDrawingsSubmitted(updatedLobby) && lobby.status == LobbyStatus.Drawing then
                cancelTimer(lobbyId)
                transitionTo(lobbyId, LobbyStatus.RevealingDrawings)

  private def handleSubmitVote(channel: cask.WsChannelActor, lobbyId: String, playerNameVotedFor: String): Unit =
    getPlayerIdByChannel(channel, lobbyId) match
      case None =>
        sendToClient(channel, ServerMessage.ErrorMessage("Player not found"))
      case Some(voterId) =>
        lobbies.get(lobbyId) match
          case null =>
            sendToClient(channel, ServerMessage.ErrorMessage("Lobby not found"))
          case (lobby, apiKey) =>
            if lobby.status != LobbyStatus.Voting then
              sendToClient(channel, ServerMessage.ErrorMessage("Not accepting votes"))
            else
              val updatedLobby = DrawingGame.submitVote(lobby, voterId, playerNameVotedFor)
              lobbies.put(lobbyId, (updatedLobby, apiKey))

              val voteCounts = DrawingGame.tallyVotes(updatedLobby)
              broadcast(lobbyId, ServerMessage.VoteUpdate(voteCounts))

              // If all votes submitted, complete the round
              if DrawingGame.allVotesSubmitted(updatedLobby) then
                cancelTimer(lobbyId)
                transitionTo(lobbyId, LobbyStatus.Results)

  private def handleNextRound(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null =>
        logger.warn(s"Attempted to advance non-existent lobby $lobbyId")
      case (lobby, apiKey) =>
        if lobby.status != LobbyStatus.Results then
          return
        // Cancel any lingering timers before starting new round
        cancelTimer(lobbyId)
        if DrawingGame.shouldEndGame(lobby) then
          val finalLobby = lobby.copy(status = LobbyStatus.Waiting, currentRound = 0)
          lobbies.put(lobbyId, (finalLobby, apiKey))
          broadcastLobbyUpdate(lobbyId)
        else
          transitionTo(lobbyId, LobbyStatus.Drawing)

  // ============================================================================
  // State Machine - All state transitions go through here
  // ============================================================================

  private def transitionTo(lobbyId: String, newStatus: LobbyStatus): Unit =
    lobbies.get(lobbyId) match
      case null => 
        logger.warn(s"Cannot transition non-existent lobby $lobbyId to $newStatus")
      case (lobby, apiKey) =>
        logger.info(s"Lobby $lobbyId: ${lobby.status} -> $newStatus")
        
        newStatus match
          case LobbyStatus.Waiting =>
            val updated = DrawingGame.resetForNextRound(lobby)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)

          case LobbyStatus.GeneratingPrompt =>
            // Update status to show loading state
            val updated = lobby.copy(status = LobbyStatus.GeneratingPrompt)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            broadcast(lobbyId, ServerMessage.GeneratingPrompt())
            // Start the async prompt generation
            startAdvancedDrawingPhase(lobbyId, updated, apiKey)

          case LobbyStatus.Drawing =>
            val needsGeneration = lobby.gameMode != GameMode.SingleWord && lobby.status != LobbyStatus.GeneratingPrompt
            if needsGeneration then
              // Advanced modes: go through GeneratingPrompt first
              transitionTo(lobbyId, LobbyStatus.GeneratingPrompt)
            else
              // SingleWord mode OR coming from GeneratingPrompt: start drawing
              val updated = if lobby.status == LobbyStatus.GeneratingPrompt then
                // Already have prompt set by startAdvancedDrawingPhase
                lobby.copy(
                  status = LobbyStatus.Drawing,
                  currentRound = lobby.currentRound + 1,
                  drawings = Map.empty,
                  votes = Map.empty,
                  timerStartTime = Some(System.currentTimeMillis())
                )
              else
                DrawingGame.startDrawingPhase(lobby)
              lobbies.put(lobbyId, (updated, apiKey))
              updated.currentPrompt.foreach: prompt =>
                broadcast(lobbyId, ServerMessage.PromptAnnounced(prompt))
              broadcastLobbyUpdate(lobbyId)
              startTimer(
                lobbyId,
                DrawingGame.drawingTimeSeconds,
                LobbyStatus.CollectingDrawings,
                seconds => Some(ServerMessage.DrawingTimerUpdate(seconds))
              )

          case LobbyStatus.CollectingDrawings =>
            val updated = lobby.copy(status = LobbyStatus.CollectingDrawings)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            // Brief pause (1 second) to collect any last-moment submissions
            startTimer(lobbyId, 1, LobbyStatus.RevealingDrawings)

          case LobbyStatus.RevealingDrawings =>
            val updated = lobby.copy(status = LobbyStatus.RevealingDrawings)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            // Send drawings (without captions)
            val drawingsWithoutCaptions = updated.drawings.values.map(_.copy(caption = None)).toSeq
            broadcast(lobbyId, ServerMessage.DrawingsRevealed(drawingsWithoutCaptions, lobby.currentPrompt.getOrElse("")))
            // Start AI captioning in background, then transition
            startCaptioning(lobbyId)

          case LobbyStatus.RevealingCaptions =>
            val updated = lobby.copy(status = LobbyStatus.RevealingCaptions)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            // Reveal next caption or move to AI winner
            revealNextCaption(lobbyId)

          case LobbyStatus.RevealingAIWinner =>
            val updated = lobby.copy(status = LobbyStatus.RevealingAIWinner)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            // Get AI winner and announce
            startAIWinnerSelection(lobbyId)

          case LobbyStatus.Voting =>
            val updated = DrawingGame.startVoting(lobby)
            lobbies.put(lobbyId, (updated, apiKey))
            broadcastLobbyUpdate(lobbyId)
            broadcast(lobbyId, ServerMessage.VotingStarted(DrawingGame.votingTimeSeconds))
            startTimer(
              lobbyId, 
              DrawingGame.votingTimeSeconds, 
              LobbyStatus.Results,
              seconds => Some(ServerMessage.VotingTimerUpdate(seconds))
            )

          case LobbyStatus.Results =>
            completeRound(lobbyId)

  // ============================================================================
  // Timer Management (only ONE active transition timer per lobby at any time)
  // ============================================================================

  private def cancelTimer(lobbyId: String): Unit =
    Option(activeTimers.remove(lobbyId)).foreach: futures =>
      futures.forEach(_.cancel(false))

  private def startTimer(
      lobbyId: String, 
      durationSeconds: Int, 
      nextStatus: LobbyStatus,
      timerMessage: Int => Option[ServerMessage] = _ => None
  ): Unit =
    cancelTimer(lobbyId)
    
    val futures = new java.util.ArrayList[ScheduledFuture[?]]()
    
    // Schedule countdown updates (only if timerMessage is provided)
    (1 to durationSeconds).foreach: i =>
      val secondsRemaining = durationSeconds - i
      timerMessage(secondsRemaining).foreach: msg =>
        val future = timerScheduler.schedule(
          (() => broadcast(lobbyId, msg)): Runnable,
          i.toLong,
          TimeUnit.SECONDS
        )
        futures.add(future)
    
    // Schedule the transition to next state
    val transitionTask: Runnable = () => transitionTo(lobbyId, nextStatus)
    val transitionFuture = timerScheduler.schedule(transitionTask, durationSeconds.toLong, TimeUnit.SECONDS)
    futures.add(transitionFuture)
    
    activeTimers.put(lobbyId, futures)

  // ============================================================================
  // Advanced Mode - AI-Generated Prompts
  // ============================================================================

  private def startAdvancedDrawingPhase(lobbyId: String, lobby: DrawingLobby, apiKey: String): Unit =
    logger.info(s"Generating advanced prompt for lobby $lobbyId")

    // Fetch random words and generate prompt
    OpenAIClient.fetchRandomWords(2)
      .flatMap: words =>
        logger.info(s"Got random words for $lobbyId: ${words.mkString(", ")}")
        OpenAIClient.generatePromptFromWords(apiKey, words)
      .map: generatedPrompt =>
        logger.info(s"Generated prompt for $lobbyId: $generatedPrompt")

        // Update lobby with generated prompt, then transition to Drawing
        val updated = lobby.copy(currentPrompt = Some(generatedPrompt))
        lobbies.put(lobbyId, (updated, apiKey))
        transitionTo(lobbyId, LobbyStatus.Drawing)
      .recover:
        case ex =>
          logger.error(s"Failed to generate advanced prompt for $lobbyId: ${ex.getMessage}", ex)
          // Fallback to standard prompt
          val fallbackPrompt = DrawingGame.getRandomPrompt()
          val updated = lobby.copy(currentPrompt = Some(fallbackPrompt))
          lobbies.put(lobbyId, (updated, apiKey))
          transitionTo(lobbyId, LobbyStatus.Drawing)

  // ============================================================================
  // AI Captioning Phase
  // ============================================================================

  private def startCaptioning(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        if lobby.drawings.isEmpty then
          // No drawings to caption, skip to voting
          transitionTo(lobbyId, LobbyStatus.Voting)
        else
          // Caption all drawings in parallel
          val captioningFutures = lobby.drawings.map: (playerId, drawing) =>
            OpenAIClient.captionImage(apiKey, drawing.imageData, lobby.captionStyle).map: caption =>
              (playerId, drawing.playerName, caption)

          Future.sequence(captioningFutures).map: captionResults =>
            // Store captions in lobby
            var currentLobby = lobby
            captionResults.foreach: (playerId, _, caption) =>
              currentLobby = DrawingGame.addCaption(currentLobby, playerId, caption)
            lobbies.put(lobbyId, (currentLobby, apiKey))
            
            // Store pending captions for sequential reveal
            pendingCaptions.put(lobbyId, captionResults.toList)
            
            // Transition to caption reveal phase
            transitionTo(lobbyId, LobbyStatus.RevealingCaptions)
          .recover:
            case ex =>
              logger.error(s"Error captioning images: ${ex.getMessage}", ex)
              broadcast(lobbyId, ServerMessage.ErrorMessage("AI captioning failed"))
              // Skip to voting anyway
              transitionTo(lobbyId, LobbyStatus.Voting)

  private def revealNextCaption(lobbyId: String): Unit =
    Option(pendingCaptions.get(lobbyId)) match
      case Some(caption :: rest) =>
        val (_, playerName, captionText) = caption
        broadcast(lobbyId, ServerMessage.CaptionRevealed(playerName, captionText))
        pendingCaptions.put(lobbyId, rest)
        // Schedule next caption reveal in 2 seconds
        timerScheduler.schedule(
          (() => revealNextCaption(lobbyId)): Runnable,
          2L,
          TimeUnit.SECONDS
        )
      case _ =>
        // All captions revealed, move to AI winner
        pendingCaptions.remove(lobbyId)
        transitionTo(lobbyId, LobbyStatus.RevealingAIWinner)

  // ============================================================================
  // AI Winner Selection
  // ============================================================================

  private def startAIWinnerSelection(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        val captions = lobby.drawings.values.map(d => d.playerName -> d.caption.getOrElse("")).toMap
        lobby.currentPrompt match
          case None =>
            // No prompt, skip to voting
            transitionTo(lobbyId, LobbyStatus.Voting)
          case Some(prompt) =>
            OpenAIClient.selectWinner(apiKey, prompt, captions).map: selection =>
              aiWinners.put(lobbyId, selection.winnerName)
              broadcast(lobbyId, ServerMessage.AIVoteRevealed(selection.winnerName, selection.reasoning))
              // Wait 2 seconds then start voting
              timerScheduler.schedule(
                (() => transitionTo(lobbyId, LobbyStatus.Voting)): Runnable,
                2L,
                TimeUnit.SECONDS
              )
            .recover:
              case ex =>
                logger.error(s"Error selecting AI winner: ${ex.getMessage}", ex)
                // Skip to voting without AI winner
                transitionTo(lobbyId, LobbyStatus.Voting)

  // ============================================================================
  // Round Completion
  // ============================================================================

  private def completeRound(lobbyId: String): Unit =
    cancelTimer(lobbyId)
    
    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, apiKey) =>
        if lobby.status == LobbyStatus.Results then
          return // Already completed

        val voteCounts = DrawingGame.tallyVotes(lobby)
        val playerWinner = voteCounts.maxByOption(_._2).map(_._1)
        val aiWinnerName = Option(aiWinners.remove(lobbyId))

        val result = RoundResult(aiWinnerName, playerWinner, voteCounts)

        val scoredLobby = DrawingGame.updatePlayerScores(lobby, aiWinnerName, playerWinner)
        val resultsLobby = scoredLobby.copy(status = LobbyStatus.Results)
        lobbies.put(lobbyId, (resultsLobby, apiKey))

        broadcast(lobbyId, ServerMessage.RoundComplete(result))
        broadcastLobbyUpdate(lobbyId)

  // ============================================================================
  // Connection Management
  // ============================================================================

  private def addConnection(lobbyId: String, channel: cask.WsChannelActor): Unit =
    connections.computeIfAbsent(lobbyId, _ => ConcurrentHashMap.newKeySet()).add(channel)

  private def removeConnection(lobbyId: String, channel: cask.WsChannelActor): Unit =
    connections.get(lobbyId) match
      case null => ()
      case channelSet => channelSet.remove(channel)

  private def handleDisconnect(channel: cask.WsChannelActor, lobbyId: String): Unit =
    closedChannels.add(channel)
    
    val actualLobbyId = Option(channelToLobby.get(channel)).getOrElse(lobbyId)
    val playerId = Option(channelToPlayer.get(channel))
    
    connections.get(actualLobbyId) match
      case null => ()
      case channelSet =>
        channelSet.remove(channel)
        logger.info(s"Client disconnected from lobby $actualLobbyId")
    
    if actualLobbyId != lobbyId then
      connections.get(lobbyId) match
        case null => ()
        case channelSet => channelSet.remove(channel)
    
    // Use trait's handleDisconnection for the actual disconnect logic
    playerId.foreach: pId =>
      handleDisconnection(actualLobbyId, pId)
    
    channelToPlayer.remove(channel)
    channelToLobby.remove(channel)

  // ============================================================================
  // Message Broadcasting
  // ============================================================================

  private def broadcast(lobbyId: String, message: ServerMessage): Unit =
    import upickle.default.*
    val json = write(message)
    connections.get(lobbyId) match
      case null => ()
      case channelSet =>
        channelSet.asScala.filterNot(closedChannels.contains).foreach: channel =>
          channel.send(cask.Ws.Text(json))

  private def broadcastLobbyUpdate(lobbyId: String): Unit =
    lobbies.get(lobbyId) match
      case null => ()
      case (lobby, _) =>
        broadcast(lobbyId, ServerMessage.LobbyUpdate(lobby))

  private def sendToClient(channel: cask.WsChannelActor, message: ServerMessage): Unit =
    if closedChannels.contains(channel) then return
    import upickle.default.*
    val json = write(message)
    channel.send(cask.Ws.Text(json))

  // ============================================================================
  // Utilities
  // ============================================================================

  private def getPlayerIdByChannel(channel: cask.WsChannelActor, lobbyId: String): Option[String] =
    Option(channelToPlayer.get(channel))

  private def generateLobbyId(): String =
    import scala.util.Random
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    (1 to 6).map(_ => chars(Random.nextInt(chars.length))).mkString

  private def generatePlayerId(): String =
    java.util.UUID.randomUUID().toString

  def cleanupEmptyLobbies(): Unit =
    // First, clean up disconnected players past grace period
    lobbies.asScala.foreach:
      case (lobbyId, (lobby, apiKey)) =>
        val cleanedLobby = DrawingGame.cleanupDisconnectedPlayers(lobby)
        if cleanedLobby.players.size != lobby.players.size then
          lobbies.put(lobbyId, (cleanedLobby, apiKey))
          logger.info(s"Cleaned up ${lobby.players.size - cleanedLobby.players.size} disconnected player(s) from lobby $lobbyId")
    
    // Then clean up empty lobbies
    lobbies.asScala.filter:
      case (_, (lobby, _)) => lobby.players.isEmpty
    .keys.foreach: lobbyId =>
      lobbies.remove(lobbyId)
      connections.remove(lobbyId)
      cancelTimer(lobbyId)
      pendingCaptions.remove(lobbyId)
      aiWinners.remove(lobbyId)
      logger.info(s"Cleaned up empty lobby $lobbyId")


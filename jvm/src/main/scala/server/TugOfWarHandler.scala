package server

import org.slf4j.{Logger, LoggerFactory}
import scala.util.Try
import castor.Context.Simple.global
import server.reconnection.ReconnectionSupport
import shared.TugOfWar.{TugOfWarGame, PlayerState, Team, GameStatus}

object TugOfWarHandler extends ReconnectionSupport[PlayerState, TugOfWarGame]:
  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  // Castor context for WebSocket operations
  given castor.Context = global

  // Cask logger for WebSocket operations
  given cask.util.Logger = cask.util.Logger.Console.globalLogger

  // Game state management - delegates to StateManager
  private val gameManager = TugOfWarStateManager()

  // Throttling for position updates (to avoid flooding clients)
  private val lastBroadcastTime = java.util.concurrent.ConcurrentHashMap[String, Long]()
  private val BroadcastThrottleMs = 50L // Max 20 updates per second

  // Timer management
  private val timerScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1)
  private val activeTimers = java.util.concurrent.ConcurrentHashMap[String, java.util.concurrent.ScheduledFuture[?]]()

  // ============================================================================
  // ReconnectionSupport implementation
  // ============================================================================

  protected def getGame(gameId: String): Option[TugOfWarGame] =
    gameManager.getGame(gameId)

  protected def getPlayers(game: TugOfWarGame): Map[String, PlayerState] =
    game.players

  protected def reconnectPlayer(player: PlayerState): PlayerState =
    player.copy(connected = true, disconnectedAt = None)

  protected def disconnectPlayer(player: PlayerState): PlayerState =
    player.copy(connected = false, disconnectedAt = Some(System.currentTimeMillis()))

  protected def withUpdatedPlayer(game: TugOfWarGame, playerId: String, player: PlayerState): TugOfWarGame =
    game.copy(players = game.players + (playerId -> player))

  protected def saveGame(gameId: String, game: TugOfWarGame): Unit =
    gameManager.updateGame(gameId, game)

  protected def registerPlayerChannel(channel: cask.WsChannelActor, gameId: String, playerId: String): Unit =
    gameManager.registerPlayer(channel, gameId, playerId)

  protected def sendRejoinSuccess(channel: cask.WsChannelActor, playerId: String, gameId: String): Unit =
    sendToChannel(channel, shared.TugOfWar.JoinedMessage(playerId, gameId))

  protected def sendRejoinFailure(channel: cask.WsChannelActor, reason: String): Unit =
    sendToChannel(channel, shared.TugOfWar.RejoinFailedMessage(reason))

  protected def broadcastGameState(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    gameManager.getGame(gameId).foreach: game =>
      val message = shared.TugOfWar.GameUpdateMessage(game)
      val messageJson = write(message)

      Option(gameManager.getConnections(gameId)).foreach: connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast game state: ${ex.getMessage}")

  // ============================================================================
  // WebSocket handling
  // ============================================================================

  def handleWebSocket(gameId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      gameManager.addConnection(gameId, channel)
      logger.info(s"Player connected to TugOfWar game $gameId")

      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.debug(s"Received WebSocket message: $msg")
          Try:
            import upickle.default.*
            val clientMsg = read[shared.TugOfWar.ClientMessage](msg)

            clientMsg match
              case shared.TugOfWar.JoinMessage(playerName) =>
                handleJoin(channel, gameId, playerName)

              case shared.TugOfWar.CreateMessage(playerName, roundsToWin, timeLimitSeconds) =>
                handleCreate(channel, gameId, playerName, roundsToWin, timeLimitSeconds)

              case shared.TugOfWar.RejoinMessage(playerId, rejoinGameId) =>
                handleRejoinRequest(channel, gameId, playerId)

              case shared.TugOfWar.SelectTeamMessage(team) =>
                handleTeamSelect(channel, gameId, team)

              case shared.TugOfWar.ConfigureMessage(roundsToWin, timeLimitSeconds) =>
                handleConfigure(gameId, roundsToWin, timeLimitSeconds)

              case shared.TugOfWar.StartMessage() =>
                handleStart(gameId)

              case shared.TugOfWar.ClickMessage() =>
                handleClick(channel, gameId)

              case shared.TugOfWar.NextRoundMessage() =>
                handleNextRound(gameId)

              case shared.TugOfWar.LeaveMessage() =>
                handleLeave(channel, gameId)

              case shared.TugOfWar.PingMessage() =>
                logger.debug(s"Received keepalive ping for TugOfWar game $gameId")
          .recover:
            case ex =>
              logger.error(s"Error processing TugOfWar message: ${ex.getMessage}", ex)

        case cask.Ws.Close(_, _) =>
          handlePlayerDisconnect(channel, gameId)

        case cask.Ws.Error(ex) =>
          logger.error(s"WebSocket error in TugOfWar game $gameId: ${ex.getMessage}", ex)
          handlePlayerDisconnect(channel, gameId)

  // ============================================================================
  // Message handlers
  // ============================================================================

  private def handleJoin(channel: cask.WsChannelActor, gameId: String, playerName: String): Unit =
    // Only allow joining existing games
    gameManager.getGame(gameId) match
      case Some(game) =>
        if game.status != GameStatus.Waiting then
          sendToChannel(channel, shared.TugOfWar.ErrorMessage("Game already in progress"))
        else
          val playerId = java.util.UUID.randomUUID().toString
          gameManager.registerPlayer(channel, gameId, playerId)

          val updatedGame = shared.TugOfWar.TugOfWar.addPlayer(game, playerId, playerName)
          gameManager.updateGame(gameId, updatedGame)

          logger.info(s"Player $playerName ($playerId) joined TugOfWar game $gameId")

          sendToChannel(channel, shared.TugOfWar.JoinedMessage(playerId, gameId))
          broadcastGameState(gameId)

      case None =>
        sendToChannel(channel, shared.TugOfWar.ErrorMessage("Game not found"))

  private def handleCreate(
      channel: cask.WsChannelActor,
      gameId: String,
      playerName: String,
      roundsToWin: Int,
      timeLimitSeconds: Int
  ): Unit =
    val playerId = java.util.UUID.randomUUID().toString
    gameManager.registerPlayer(channel, gameId, playerId)

    val game = gameManager.createGame(gameId, roundsToWin, timeLimitSeconds)
    val updatedGame = shared.TugOfWar.TugOfWar.addPlayer(game, playerId, playerName)
    gameManager.updateGame(gameId, updatedGame)

    logger.info(
      s"Player $playerName ($playerId) created TugOfWar game $gameId (roundsToWin=$roundsToWin, timeLimit=$timeLimitSeconds)"
    )

    sendToChannel(channel, shared.TugOfWar.JoinedMessage(playerId, gameId))
    broadcastGameState(gameId)

  private def handleTeamSelect(channel: cask.WsChannelActor, gameId: String, team: Team): Unit =
    gameManager.getPlayerInfo(channel) match
      case Some((gId, playerId)) if gId == gameId =>
        gameManager.getGame(gameId).foreach: game =>
          val updatedGame = shared.TugOfWar.TugOfWar.setPlayerTeam(game, playerId, team)
          gameManager.updateGame(gameId, updatedGame)
          logger.info(s"Player $playerId selected team $team in game $gameId")
          broadcastGameState(gameId)
      case _ =>
        logger.warn(s"Team select from unregistered player")

  private def handleLeave(channel: cask.WsChannelActor, gameId: String): Unit =
    gameManager.getPlayerInfo(channel) match
      case Some((gId, playerId)) if gId == gameId =>
        gameManager.getGame(gameId).foreach: game =>
          // Remove player immediately (not just mark as disconnected)
          val updatedGame = shared.TugOfWar.TugOfWar.removePlayer(game, playerId)
          gameManager.updateGame(gameId, updatedGame)
          gameManager.unregisterPlayer(channel)
          logger.info(s"Player $playerId explicitly left game $gameId")
          broadcastGameState(gameId)
      case _ =>
        logger.warn(s"Leave from unregistered player")

  private def handleConfigure(gameId: String, roundsToWin: Int, timeLimitSeconds: Int): Unit =
    gameManager.getGame(gameId).foreach: game =>
      val updatedGame = shared.TugOfWar.TugOfWar.configureGame(game, roundsToWin, timeLimitSeconds)
      gameManager.updateGame(gameId, updatedGame)
      logger.info(s"TugOfWar game $gameId configured: roundsToWin=$roundsToWin, timeLimit=$timeLimitSeconds")
      broadcastGameState(gameId)

  private def handleStart(gameId: String): Unit =
    gameManager.getGame(gameId).foreach: game =>
      if shared.TugOfWar.TugOfWar.canStart(game) then
        val gameWithRound = shared.TugOfWar.TugOfWar.startRound(game)
        gameManager.updateGame(gameId, gameWithRound)
        logger.info(s"TugOfWar game $gameId starting countdown - Round ${gameWithRound.currentRound}")
        broadcastGameState(gameId)
        startCountdown(gameId, gameWithRound.timeLimitSeconds)
      else
        logger.warn(s"Cannot start game $gameId - need at least one player per team")

  private def startCountdown(gameId: String, timeLimitSeconds: Int): Unit =
    // Cancel any existing timer
    stopRoundTimer(gameId)

    var countdownRemaining = shared.TugOfWar.TugOfWar.CountdownSeconds

    // Send initial countdown value
    broadcastCountdownUpdate(gameId, countdownRemaining)

    val countdownTask = new Runnable:
      def run(): Unit =
        countdownRemaining -= 1
        if countdownRemaining > 0 then
          broadcastCountdownUpdate(gameId, countdownRemaining)
        else
          // Countdown finished - start playing
          gameManager.getGame(gameId).foreach: game =>
            if game.status == GameStatus.Countdown then
              val playingGame = shared.TugOfWar.TugOfWar.startPlaying(game)
              gameManager.updateGame(gameId, playingGame)
              logger.info(s"TugOfWar game $gameId countdown finished - Round ${playingGame.currentRound} starting!")
              broadcastCountdownUpdate(gameId, 0) // Signal countdown end
              broadcastGameState(gameId)
              // Now start the round timer
              stopRoundTimer(gameId) // Stop the countdown timer
              startRoundTimer(gameId, timeLimitSeconds)

    val future = timerScheduler.scheduleAtFixedRate(
      countdownTask,
      1,
      1,
      java.util.concurrent.TimeUnit.SECONDS
    )
    activeTimers.put(gameId, future)

  private def startRoundTimer(gameId: String, timeLimitSeconds: Int): Unit =
    // Cancel any existing timer for this game
    stopRoundTimer(gameId)

    if timeLimitSeconds > 0 then
      // Schedule timer ticks every second
      val timerTask = new Runnable:
        def run(): Unit =
          gameManager.getGame(gameId).foreach: game =>
            if game.status == GameStatus.Playing then
              shared.TugOfWar.TugOfWar.getRemainingTime(game) match
                case Some(remaining) if remaining > 0 =>
                  broadcastTimerUpdate(gameId, remaining)
                case Some(0) | None =>
                  // Time's up - determine winner by position
                  handleTimeExpired(gameId, game)
                case _ => ()

      val future = timerScheduler.scheduleAtFixedRate(
        timerTask,
        0,
        1,
        java.util.concurrent.TimeUnit.SECONDS
      )
      activeTimers.put(gameId, future)

  private def stopRoundTimer(gameId: String): Unit =
    Option(activeTimers.remove(gameId)).foreach(_.cancel(false))

  private def handleTimeExpired(gameId: String, game: TugOfWarGame): Unit =
    stopRoundTimer(gameId)

    shared.TugOfWar.TugOfWar.getTimeoutWinner(game) match
      case Some(winner) =>
        val result = shared.TugOfWar.TugOfWar.getRoundResult(game, winner)
        val endedGame = shared.TugOfWar.TugOfWar.endRound(game, winner)
        gameManager.updateGame(gameId, endedGame)
        logger.info(s"Round timed out in game $gameId - $winner wins by position (${game.ropePosition})!")
        broadcastRoundEnd(gameId, winner, result)
        broadcastGameState(gameId)
      case None =>
        // Draw - restart the round
        logger.info(s"Round timed out in game $gameId with tie - restarting round")
        val restartedGame = shared.TugOfWar.TugOfWar.startRound(
          game.copy(currentRound = game.currentRound - 1) // Decrement so startRound brings it back
        )
        gameManager.updateGame(gameId, restartedGame)
        broadcastGameState(gameId)
        startRoundTimer(gameId, restartedGame.timeLimitSeconds)

  private def handleClick(channel: cask.WsChannelActor, gameId: String): Unit =
    gameManager.getPlayerInfo(channel) match
      case Some((gId, playerId)) if gId == gameId =>
        gameManager.getGame(gameId).foreach: game =>
          if game.status == GameStatus.Playing then
            val updatedGame = shared.TugOfWar.TugOfWar.handleClick(game, playerId)
            gameManager.updateGame(gameId, updatedGame)

            // Check if round ended
            shared.TugOfWar.TugOfWar.checkRoundEnd(updatedGame) match
              case Some(winner) =>
                stopRoundTimer(gameId)
                val result = shared.TugOfWar.TugOfWar.getRoundResult(updatedGame, winner)
                val endedGame = shared.TugOfWar.TugOfWar.endRound(updatedGame, winner)
                gameManager.updateGame(gameId, endedGame)
                logger.info(s"Round ended in game $gameId - $winner wins!")
                broadcastRoundEnd(gameId, winner, result)
                broadcastGameState(gameId)
              case None =>
                // Throttle position updates
                throttledPositionBroadcast(gameId, updatedGame)
      case _ =>
        logger.warn(s"Click from unregistered player")

  private def handleNextRound(gameId: String): Unit =
    gameManager.getGame(gameId).foreach: game =>
      if game.status == GameStatus.RoundEnd then
        val nextGame = shared.TugOfWar.TugOfWar.advanceFromRoundEnd(game)
        gameManager.updateGame(gameId, nextGame)
        logger.info(s"TugOfWar game $gameId advancing from roundEnd to ${nextGame.status}")

        if nextGame.status == GameStatus.GameOver then
          stopRoundTimer(gameId)
          shared.TugOfWar.TugOfWar.getGameWinner(nextGame).foreach: winner =>
            broadcastGameEnd(gameId, winner, nextGame.redRoundsWon, nextGame.blueRoundsWon)
          broadcastGameState(gameId)
        else if nextGame.status == GameStatus.Countdown then
          // Start countdown for new round
          broadcastGameState(gameId)
          startCountdown(gameId, nextGame.timeLimitSeconds)

  // ============================================================================
  // Broadcasting helpers
  // ============================================================================

  private def sendToChannel(channel: cask.WsChannelActor, message: shared.TugOfWar.ServerMessage): Unit =
    import upickle.default.*
    Try:
      val messageJson = write(message)
      channel.send(cask.Ws.Text(messageJson))
    .recover:
      case ex => logger.warn(s"Failed to send message to channel: ${ex.getMessage}")

  private def throttledPositionBroadcast(gameId: String, game: TugOfWarGame): Unit =
    val now = System.currentTimeMillis()
    val lastTime = lastBroadcastTime.getOrDefault(gameId, 0L)

    if now - lastTime >= BroadcastThrottleMs then
      lastBroadcastTime.put(gameId, now)
      broadcastPositionUpdate(gameId, game)

  private def broadcastPositionUpdate(gameId: String, game: TugOfWarGame): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val redClicks = shared.TugOfWar.TugOfWar.getTeamClicks(game, Team.Red)
    val blueClicks = shared.TugOfWar.TugOfWar.getTeamClicks(game, Team.Blue)
    val message = shared.TugOfWar.PositionUpdateMessage(game.ropePosition, redClicks, blueClicks)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast position update: ${ex.getMessage}")

  private def broadcastTimerUpdate(gameId: String, secondsRemaining: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.TugOfWar.TimerUpdateMessage(secondsRemaining)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast timer update: ${ex.getMessage}")

  private def broadcastCountdownUpdate(gameId: String, secondsRemaining: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.TugOfWar.CountdownUpdateMessage(secondsRemaining)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast countdown update: ${ex.getMessage}")

  private def broadcastRoundEnd(gameId: String, winner: Team, result: shared.TugOfWar.RoundResult): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.TugOfWar.RoundEndMessage(winner, result)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast round end: ${ex.getMessage}")

  private def broadcastGameEnd(gameId: String, winner: Team, redRoundsWon: Int, blueRoundsWon: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.TugOfWar.GameEndMessage(winner, redRoundsWon, blueRoundsWon)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast game end: ${ex.getMessage}")

  // ============================================================================
  // Disconnect handling
  // ============================================================================

  private def handlePlayerDisconnect(channel: cask.WsChannelActor, gameId: String): Unit =
    gameManager.removeConnection(gameId, channel)

    gameManager.getPlayerInfo(channel) match
      case Some((gId, pId)) if gId == gameId =>
        gameManager.unregisterPlayer(channel)
        handleDisconnection(gameId, pId)

        val connectionCount = gameManager.getGameConnectionCount(gameId)
        if connectionCount == 0 then
          logger.info(s"TugOfWar game $gameId has no active connections - will be cleaned up by periodic task")
      case _ =>

    val remainingConnections = gameManager.getGameConnectionCount(gameId)
    logger.info(s"Connection closed for TugOfWar game $gameId. Remaining connections: $remainingConnections")

  // ============================================================================
  // Cleanup and test helpers
  // ============================================================================

  private[server] def cleanupGame(gameId: String): Unit =
    gameManager.cleanupGame(gameId)

  private[server] def cleanupEmptyGames(): Int =
    gameManager.cleanupEmptyGames()

  private[server] def getGameForTest(gameId: String): Option[TugOfWarGame] =
    gameManager.getGame(gameId)

  private[server] def getGameConnectionCount(gameId: String): Int =
    gameManager.getGameConnectionCount(gameId)

  private[server] def createTestGame(gameId: String, game: TugOfWarGame): Unit =
    gameManager.updateGame(gameId, game)
    gameManager.addConnection(gameId, null)
    gameManager.removeConnection(gameId, null)

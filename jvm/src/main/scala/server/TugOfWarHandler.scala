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

              case shared.TugOfWar.RejoinMessage(playerId, rejoinGameId) =>
                handleRejoinRequest(channel, gameId, playerId)

              case shared.TugOfWar.SelectTeamMessage(team) =>
                handleTeamSelect(channel, gameId, team)

              case shared.TugOfWar.ConfigureMessage(roundsToWin) =>
                handleConfigure(gameId, roundsToWin)

              case shared.TugOfWar.StartMessage() =>
                handleStart(gameId)

              case shared.TugOfWar.ClickMessage() =>
                handleClick(channel, gameId)

              case shared.TugOfWar.NextRoundMessage() =>
                handleNextRound(gameId)

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
    val playerId = java.util.UUID.randomUUID().toString

    gameManager.registerPlayer(channel, gameId, playerId)

    // Create game if it doesn't exist
    val game = gameManager.getGame(gameId).getOrElse(gameManager.createGame(gameId, 3))
    val updatedGame = shared.TugOfWar.TugOfWar.addPlayer(game, playerId, playerName)
    gameManager.updateGame(gameId, updatedGame)

    logger.info(s"Player $playerName ($playerId) joined TugOfWar game $gameId")

    // Send JoinedMessage to the player
    sendToChannel(channel, shared.TugOfWar.JoinedMessage(playerId, gameId))

    // Broadcast game state to all players
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

  private def handleConfigure(gameId: String, roundsToWin: Int): Unit =
    gameManager.getGame(gameId).foreach: game =>
      val updatedGame = shared.TugOfWar.TugOfWar.configureGame(game, roundsToWin)
      gameManager.updateGame(gameId, updatedGame)
      logger.info(s"TugOfWar game $gameId configured: roundsToWin=$roundsToWin")
      broadcastGameState(gameId)

  private def handleStart(gameId: String): Unit =
    gameManager.getGame(gameId).foreach: game =>
      if shared.TugOfWar.TugOfWar.canStart(game) then
        val gameWithRound = shared.TugOfWar.TugOfWar.startRound(game)
        gameManager.updateGame(gameId, gameWithRound)
        logger.info(s"TugOfWar game $gameId started - Round ${gameWithRound.currentRound}")
        broadcastGameState(gameId)
      else
        logger.warn(s"Cannot start game $gameId - need at least one player per team")

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
          shared.TugOfWar.TugOfWar.getGameWinner(nextGame).foreach: winner =>
            broadcastGameEnd(gameId, winner, nextGame.redRoundsWon, nextGame.blueRoundsWon)

        broadcastGameState(gameId)

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


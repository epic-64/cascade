package server

import org.slf4j.LoggerFactory
import scala.util.Try
import castor.Context.Simple.global

object ColorRushHandler:
  private val logger = LoggerFactory.getLogger(getClass)
  
  // Castor context for WebSocket operations
  given castor.Context = global
  
  // Cask logger for WebSocket operations
  given cask.util.Logger = cask.util.Logger.Console.globalLogger

  // Game state management - delegates to GameStateManager
  private val gameManager = ColorRushStateManager()

  def handleWebSocket(gameId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Don't create game yet - wait for JoinMessage with totalRounds
      // Just add the connection tracking
      gameManager.addConnection(gameId, channel)

      logger.info(s"Player connected to game $gameId")

      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.info(s"Received WebSocket message: $msg")
          Try:
            import upickle.default.*
            val clientMsg = read[shared.ColorRush.ClientMessage](msg)

            clientMsg match
              case shared.ColorRush.JoinMessage(playerName, totalRounds) =>
                handleJoin(channel, gameId, playerName, totalRounds)

              case shared.ColorRush.RejoinMessage(playerId, rejoinGameId) =>
                handleRejoin(channel, gameId, playerId)

              case shared.ColorRush.ConfigureMessage(totalRounds) =>
                handleConfigure(gameId, totalRounds)

              case shared.ColorRush.StartMessage() =>
                handleStart(gameId)

              case shared.ColorRush.ClickMessage(color, clickTime) =>
                handleClick(channel, gameId, color, clickTime)

              case shared.ColorRush.NextRoundMessage() =>
                handleNextRound(gameId)
          .recover:
            case ex =>
              logger.error(s"Error processing game message: ${ex.getMessage}", ex)

        case cask.Ws.Close(_, _) =>
          handlePlayerDisconnect(channel, gameId)

        case cask.Ws.Error(ex) =>
          logger.error(s"WebSocket error in game $gameId: ${ex.getMessage}", ex)
          handlePlayerDisconnect(channel, gameId)

  private def handleJoin(channel: cask.WsChannelActor, gameId: String, playerName: String, totalRounds: Int): Unit =
    val playerId = java.util.UUID.randomUUID().toString

    gameManager.registerPlayer(channel, gameId, playerId)

    // Create game if it doesn't exist, using the totalRounds from the first player
    val game = gameManager.getGame(gameId).getOrElse(gameManager.createGame(gameId, totalRounds))
    val updatedGame = shared.ColorRush.ColorRush.addPlayer(game, playerId, playerName)
    gameManager.updateGame(gameId, updatedGame)

    logger.info(s"Player $playerName ($playerId) joined game $gameId with totalRounds=$totalRounds")

    // Send JoinedMessage to the player so they can store their playerId
    sendToChannel(channel, shared.ColorRush.JoinedMessage(playerId, gameId))

    // Broadcast game state to all players
    broadcastGameState(gameId)

  private def handleRejoin(channel: cask.WsChannelActor, gameId: String, playerId: String): Unit =
    gameManager.getGame(gameId) match
      case Some(game) if shared.ColorRush.ColorRush.canRejoin(game, playerId) =>
        // Reconnect the player
        shared.ColorRush.ColorRush.reconnectPlayer(game, playerId) match
          case Some(updatedGame) =>
            gameManager.registerPlayer(channel, gameId, playerId)
            gameManager.updateGame(gameId, updatedGame)
            
            val playerName = updatedGame.players.get(playerId).map(_.name).getOrElse("Unknown")
            logger.info(s"Player $playerName ($playerId) rejoined game $gameId")
            
            // Send JoinedMessage to confirm reconnection
            sendToChannel(channel, shared.ColorRush.JoinedMessage(playerId, gameId))
            
            // Broadcast updated game state to all players
            broadcastGameState(gameId)
            
          case None =>
            logger.warn(s"Failed to reconnect player $playerId to game $gameId")
            sendToChannel(channel, shared.ColorRush.RejoinFailedMessage("Failed to reconnect"))
            
      case Some(_) =>
        logger.info(s"Player $playerId cannot rejoin game $gameId - grace period expired or not found")
        sendToChannel(channel, shared.ColorRush.RejoinFailedMessage("Session expired"))
        
      case None =>
        logger.info(s"Player $playerId cannot rejoin - game $gameId not found")
        sendToChannel(channel, shared.ColorRush.RejoinFailedMessage("Game not found"))

  private def handleConfigure(gameId: String, totalRounds: Int): Unit =
    gameManager.getGame(gameId).foreach: game =>
      val updatedGame = shared.ColorRush.ColorRush.configureGame(game, totalRounds)
      gameManager.updateGame(gameId, updatedGame)
      logger.info(s"Game $gameId configured: totalRounds=$totalRounds")
      broadcastGameState(gameId)

  private def handleStart(gameId: String): Unit =
    gameManager.getGame(gameId).foreach: game =>
      if game.players.nonEmpty then
        val gameWithRound = shared.ColorRush.ColorRush.startNewRound(game)
        gameManager.updateGame(gameId, gameWithRound)
        logger.info(s"Game $gameId started - Round ${gameWithRound.roundNumber}")
        broadcastGameState(gameId)

  private def handleClick(channel: cask.WsChannelActor, gameId: String, color: String, clickTime: Long): Unit =
    gameManager.getPlayerInfo(channel) match
      case Some((gId, pId)) if gId == gameId =>
        gameManager.getGame(gameId).foreach: game =>
          val (updatedGame, winner) = shared.ColorRush.ColorRush.handleColorClick(game, pId, color, clickTime)
          gameManager.updateGame(gameId, updatedGame)

          winner.foreach: (playerId, playerName, points) =>
            logger.info(s"Round winner: $playerName with $points points")
            broadcastRoundWinner(gameId, playerId, playerName, points)

          broadcastGameState(gameId)

      case _ =>
        logger.warn(s"Click from unregistered player")

  private def handleNextRound(gameId: String): Unit =
    // Client confirms they've seen the winner announcement, advance to next round
    gameManager.getGame(gameId).foreach: game =>
      if game.status == shared.ColorRush.GameStatus.RoundEnd then
        val nextGame = shared.ColorRush.ColorRush.advanceFromRoundEnd(game)
        gameManager.updateGame(gameId, nextGame)
        logger.info(s"Game $gameId advancing from roundEnd to ${nextGame.status}")

        if nextGame.status == shared.ColorRush.GameStatus.GameOver then
          val winner = shared.ColorRush.ColorRush.getWinner(nextGame)
          broadcastGameEnd(gameId, winner)

        broadcastGameState(gameId)

  private def sendToChannel(channel: cask.WsChannelActor, message: shared.ColorRush.ServerMessage): Unit =
    import upickle.default.*
    Try:
      val messageJson = write(message)
      channel.send(cask.Ws.Text(messageJson))
    .recover:
      case ex => logger.warn(s"Failed to send message to channel: ${ex.getMessage}")

  private def broadcastGameState(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    gameManager.getGame(gameId).foreach: game =>
      val message     = shared.ColorRush.GameUpdateMessage(game)
      val messageJson = write(message)

      Option(gameManager.getConnections(gameId)).foreach: connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast game state: ${ex.getMessage}")

  private def broadcastRoundWinner(gameId: String, playerId: String, playerName: String, points: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message     = shared.ColorRush.RoundWinnerMessage(playerName, points)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast round winner: ${ex.getMessage}")

  private def broadcastGameEnd(gameId: String, winner: Option[shared.ColorRush.PlayerState]): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message     = shared.ColorRush.GameEndMessage(winner)
    val messageJson = write(message)

    Option(gameManager.getConnections(gameId)).foreach: connections =>
      connections.asScala.foreach: channel =>
        Try(channel.send(cask.Ws.Text(messageJson))).recover:
          case ex => logger.warn(s"Failed to broadcast game end: ${ex.getMessage}")

  private def handlePlayerDisconnect(
      channel: cask.WsChannelActor,
      gameId: String
  ): Unit =
    gameManager.removeConnection(gameId, channel)

    gameManager.getPlayerInfo(channel) match
      case Some((gId, pId)) if gId == gameId =>
        gameManager.unregisterPlayer(channel)
        gameManager.getGame(gameId).foreach: game =>
          // Mark player as disconnected instead of removing (allows rejoin)
          val updatedGame = shared.ColorRush.ColorRush.disconnectPlayer(game, pId)
          gameManager.updateGame(gameId, updatedGame)
          logger.info(s"Player $pId disconnected from game $gameId (can rejoin within grace period)")
          broadcastGameState(gameId)

          // Clean up game if it has no more connections
          val connectionCount = gameManager.getGameConnectionCount(gameId)
          if connectionCount == 0 then
            // Schedule cleanup after grace period instead of immediate cleanup
            logger.info(s"Game $gameId has no active connections - will be cleaned up by periodic task")
      case _ =>

    val remainingConnections = gameManager.getGameConnectionCount(gameId)
    logger.info(s"Connection closed for game $gameId. Remaining connections: $remainingConnections")

  private[server] def cleanupGame(gameId: String): Unit =
    gameManager.cleanupGame(gameId)

  private[server] def cleanupEmptyGames(): Int =
    gameManager.cleanupEmptyGames()

  // Test helpers - package-private for testing
  private[server] def getGame(gameId: String): Option[shared.ColorRush.ColorRushGame] =
    gameManager.getGame(gameId)

  private[server] def getGameConnectionCount(gameId: String): Int =
    gameManager.getGameConnectionCount(gameId)

  private[server] def createTestGame(gameId: String, game: shared.ColorRush.ColorRushGame): Unit =
    gameManager.updateGame(gameId, game)
    gameManager.addConnection(gameId, null) // Ensure connections set exists
    gameManager.removeConnection(gameId, null) // Remove the null connection


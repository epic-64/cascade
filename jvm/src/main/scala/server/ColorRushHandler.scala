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

  // Color Rush game state management
  private val colorRushGames  = java.util.concurrent.ConcurrentHashMap[String, shared.ColorRushGame]()
  private val gameConnections = java.util.concurrent.ConcurrentHashMap[String, java.util.Set[cask.WsChannelActor]]()
  private val playerToGame    =
    java.util.concurrent.ConcurrentHashMap[cask.WsChannelActor, (String, String)]() // (gameId, playerId)

  def handleWebSocket(gameId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Initialize game if it doesn't exist
      colorRushGames.computeIfAbsent(gameId, _ => shared.ColorRush.createGame(gameId))

      // Add channel to game connections
      val connections = gameConnections.computeIfAbsent(
        gameId,
        _ => java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
      )
      connections.add(channel)

      logger.info(s"Player connected to game $gameId. Total players: ${connections.size()}")

      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.info(s"Received WebSocket message: $msg")
          Try:
            import upickle.default.*
            val clientMsg = read[shared.ClientMessage](msg)

            clientMsg match
              case shared.JoinMessage(playerName) =>
                handleJoin(channel, gameId, playerName)

              case shared.StartMessage() =>
                handleStart(gameId)

              case shared.ClickMessage(color, clickTime) =>
                handleClick(channel, gameId, color, clickTime)

              case shared.NextRoundMessage() =>
                handleNextRound(gameId)
          .recover:
            case ex =>
              logger.error(s"Error processing game message: ${ex.getMessage}", ex)

        case cask.Ws.Close(_, _) =>
          handlePlayerDisconnect(channel, gameId, connections)

        case cask.Ws.Error(ex) =>
          logger.error(s"WebSocket error in game $gameId: ${ex.getMessage}", ex)
          handlePlayerDisconnect(channel, gameId, connections)

  private def handleJoin(channel: cask.WsChannelActor, gameId: String, playerName: String): Unit =
    val playerId = java.util.UUID.randomUUID().toString

    playerToGame.put(channel, (gameId, playerId))

    val game        = colorRushGames.get(gameId)
    val updatedGame = shared.ColorRush.addPlayer(game, playerId, playerName)
    colorRushGames.put(gameId, updatedGame)

    logger.info(s"Player $playerName ($playerId) joined game $gameId")

    // Broadcast game state to all players
    broadcastGameState(gameId)

  private def handleStart(gameId: String): Unit =
    val game = colorRushGames.get(gameId)
    if game.players.nonEmpty then
      val gameWithRound = shared.ColorRush.startNewRound(game)
      colorRushGames.put(gameId, gameWithRound)
      logger.info(s"Game $gameId started - Round ${gameWithRound.roundNumber}")
      broadcastGameState(gameId)

  private def handleClick(channel: cask.WsChannelActor, gameId: String, color: String, clickTime: Long): Unit =
    playerToGame.get(channel) match
      case (gId, pId) if gId == gameId =>
        val game                  = colorRushGames.get(gameId)
        val (updatedGame, winner) = shared.ColorRush.handleColorClick(game, pId, color, clickTime)
        colorRushGames.put(gameId, updatedGame)

        winner.foreach: (playerId, playerName, points) =>
          logger.info(s"Round winner: $playerName with $points points")
          broadcastRoundWinner(gameId, playerId, playerName, points)

        broadcastGameState(gameId)

      case _ =>
        logger.warn(s"Click from unregistered player")

  private def handleNextRound(gameId: String): Unit =
    // Client confirms they've seen the winner announcement, advance to next round
    val game = colorRushGames.get(gameId)
    if game != null && game.status == shared.GameStatus.RoundEnd then
      val nextGame = shared.ColorRush.advanceFromRoundEnd(game)
      colorRushGames.put(gameId, nextGame)
      logger.info(s"Game $gameId advancing from roundEnd to ${nextGame.status}")

      if nextGame.status == shared.GameStatus.GameOver then
        val winner = shared.ColorRush.getWinner(nextGame)
        broadcastGameEnd(gameId, winner)

      broadcastGameState(gameId)

  private def broadcastGameState(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val game = colorRushGames.get(gameId)
    if game != null then
      val message     = shared.GameUpdateMessage(game)
      val messageJson = write(message)

      gameConnections.get(gameId) match
        case null        =>
        case connections =>
          connections.asScala.foreach: channel =>
            Try(channel.send(cask.Ws.Text(messageJson))).recover:
              case ex => logger.warn(s"Failed to broadcast game state: ${ex.getMessage}")

  private def broadcastRoundWinner(gameId: String, playerId: String, playerName: String, points: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message     = shared.RoundWinnerMessage(playerName, points)
    val messageJson = write(message)

    gameConnections.get(gameId) match
      case null        =>
      case connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast round winner: ${ex.getMessage}")

  private def broadcastGameEnd(gameId: String, winner: Option[shared.PlayerState]): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message     = shared.GameEndMessage(winner)
    val messageJson = write(message)

    gameConnections.get(gameId) match
      case null        =>
      case connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast game end: ${ex.getMessage}")

  private def handlePlayerDisconnect(
      channel: cask.WsChannelActor,
      gameId: String,
      connections: java.util.Set[cask.WsChannelActor]
  ): Unit =
    connections.remove(channel)

    playerToGame.get(channel) match
      case (gId, pId) if gId == gameId =>
        playerToGame.remove(channel)
        val game = colorRushGames.get(gameId)
        if game != null then
          val updatedGame = shared.ColorRush.removePlayer(game, pId)
          colorRushGames.put(gameId, updatedGame)
          logger.info(s"Player $pId disconnected from game $gameId")
          broadcastGameState(gameId)

          // Clean up game if it's GameOver and has no more connections
          if updatedGame.status == shared.GameStatus.GameOver && connections.isEmpty then cleanupGame(gameId)
      case _                           =>

    logger.info(s"Connection closed for game $gameId. Remaining players: ${connections.size()}")

  private[server] def cleanupGame(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*

    colorRushGames.remove(gameId)
    gameConnections.remove(gameId)

    // Remove all player-to-game mappings for this game
    playerToGame.asScala.foreach:
      case (channel, (gId, _)) if gId == gameId =>
        playerToGame.remove(channel)
      case _                                    =>

    logger.info(s"Cleaned up game $gameId")

  private[server] def cleanupEmptyGames(): Int =
    import scala.jdk.CollectionConverters.*

    val gamesToCleanup = gameConnections.asScala
      .filter:
        case (gameId, connections) => connections.isEmpty
      .keys
      .toList

    gamesToCleanup.foreach: gameId =>
      cleanupGame(gameId)

    if gamesToCleanup.nonEmpty then logger.info(s"Periodic cleanup: removed ${gamesToCleanup.size} empty game(s)")

    gamesToCleanup.size

  // Test helpers - package-private for testing
  private[server] def getGame(gameId: String): Option[shared.ColorRushGame] =
    Option(colorRushGames.get(gameId))

  private[server] def getGameConnectionCount(gameId: String): Int =
    Option(gameConnections.get(gameId)).map(_.size()).getOrElse(0)

  private[server] def createTestGame(gameId: String, game: shared.ColorRushGame): Unit =
    colorRushGames.put(gameId, game)
    gameConnections.computeIfAbsent(
      gameId,
      _ => java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
    )


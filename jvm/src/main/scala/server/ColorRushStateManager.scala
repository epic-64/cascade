package server

import org.slf4j.LoggerFactory

/** Manages game state separately from WebSocket handling - enables testing */
class ColorRushStateManager:
  private val logger = LoggerFactory.getLogger(getClass)

  private val colorRushGames = java.util.concurrent.ConcurrentHashMap[String, shared.ColorRush.ColorRushGame]()
  private val gameConnections = java.util.concurrent.ConcurrentHashMap[String, java.util.Set[cask.WsChannelActor]]()
  private val playerToGame = java.util.concurrent.ConcurrentHashMap[cask.WsChannelActor, (String, String)]()

  def getGame(gameId: String): Option[shared.ColorRush.ColorRushGame] =
    Option(colorRushGames.get(gameId))

  def createGame(gameId: String, totalRounds: Int): shared.ColorRush.ColorRushGame =
    val game = shared.ColorRush.ColorRush.createGame(gameId, totalRounds)
    colorRushGames.put(gameId, game)
    gameConnections.computeIfAbsent(
      gameId,
      _ => java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
    )
    game

  def updateGame(gameId: String, game: shared.ColorRush.ColorRushGame): Unit =
    colorRushGames.put(gameId, game)

  def getOrCreateGame(gameId: String, totalRounds: Int): shared.ColorRush.ColorRushGame =
    colorRushGames.computeIfAbsent(gameId, _ => shared.ColorRush.ColorRush.createGame(gameId, totalRounds))

  def getGameConnectionCount(gameId: String): Int =
    Option(gameConnections.get(gameId)).map(_.size()).getOrElse(0)

  def addConnection(gameId: String, channel: cask.WsChannelActor): Unit =
    val connections = gameConnections.computeIfAbsent(
      gameId,
      _ => java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
    )
    connections.add(channel)

  def removeConnection(gameId: String, channel: cask.WsChannelActor): Unit =
    Option(gameConnections.get(gameId)).foreach(_.remove(channel))

  def getConnections(gameId: String): java.util.Set[cask.WsChannelActor] =
    gameConnections.get(gameId)

  def registerPlayer(channel: cask.WsChannelActor, gameId: String, playerId: String): Unit =
    playerToGame.put(channel, (gameId, playerId))

  def getPlayerInfo(channel: cask.WsChannelActor): Option[(String, String)] =
    Option(playerToGame.get(channel))

  def unregisterPlayer(channel: cask.WsChannelActor): Unit =
    playerToGame.remove(channel)

  def cleanupGame(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*

    colorRushGames.remove(gameId)
    gameConnections.remove(gameId)

    // Remove all player-to-game mappings for this game
    playerToGame.asScala.foreach:
      case (channel, (gId, _)) if gId == gameId =>
        playerToGame.remove(channel)
      case _ =>

    logger.info(s"Cleaned up game $gameId")

  def cleanupEmptyGames(): Int =
    import scala.jdk.CollectionConverters.*

    // First, clean up disconnected players in all games
    colorRushGames.asScala.foreach:
      case (gameId, game) =>
        val cleanedGame = shared.ColorRush.ColorRush.cleanupDisconnectedPlayers(game)
        if cleanedGame.players.size != game.players.size then
          colorRushGames.put(gameId, cleanedGame)
          logger.info(
            s"Cleaned up ${game.players.size - cleanedGame.players.size} disconnected player(s) from game $gameId"
          )

    val gamesToCleanup = gameConnections.asScala
      .filter:
        case (gameId, connections) => connections.isEmpty
      .keys
      .toList

    gamesToCleanup.foreach(cleanupGame)

    if gamesToCleanup.nonEmpty then
      logger.info(s"Periodic cleanup: removed ${gamesToCleanup.size} empty game(s)")

    gamesToCleanup.size

  def getAllGames: Map[String, shared.ColorRush.ColorRushGame] =
    import scala.jdk.CollectionConverters.*
    colorRushGames.asScala.toMap

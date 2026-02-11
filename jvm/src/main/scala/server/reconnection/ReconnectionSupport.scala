package server.reconnection

import org.slf4j.Logger
import shared.session.{PlayerConnection, PlayerConnectionOps}

/**
 * Mixin trait providing standardized reconnection handling for game servers.
 *
 * Games implement the abstract methods to provide game-specific behavior,
 * and get the reconnection logic for free.
 *
 * @tparam Player The player type (must extend PlayerConnection)
 * @tparam Game The game/lobby state type
 */
trait ReconnectionSupport[Player <: PlayerConnection, Game]:

  /** Logger for reconnection events */
  protected def logger: Logger

  /** Get a game by ID */
  protected def getGame(gameId: String): Option[Game]

  /** Get the players map from a game */
  protected def getPlayers(game: Game): Map[String, Player]

  /** Create a reconnected player (set connected=true, disconnectedAt=None) */
  protected def reconnectPlayer(player: Player): Player

  /** Create a disconnected player (set connected=false, disconnectedAt=Some(now)) */
  protected def disconnectPlayer(player: Player): Player

  /** Update a player in the game and return the updated game */
  protected def withUpdatedPlayer(game: Game, playerId: String, player: Player): Game

  /** Persist the updated game state */
  protected def saveGame(gameId: String, game: Game): Unit

  /** Register the channel-to-player mapping */
  protected def registerPlayerChannel(channel: cask.WsChannelActor, gameId: String, playerId: String): Unit

  /** Send rejoin success message to client */
  protected def sendRejoinSuccess(channel: cask.WsChannelActor, playerId: String, gameId: String): Unit

  /** Send rejoin failure message to client */
  protected def sendRejoinFailure(channel: cask.WsChannelActor, reason: String): Unit

  /** Broadcast updated game state to all connected players */
  protected def broadcastGameState(gameId: String): Unit

  /** Grace period for reconnection (default 60 seconds) */
  protected def gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs

  /**
   * Handle a rejoin request from a client.
   *
   * This is the main entry point that games call when receiving a rejoin message.
   *
   * @param channel The WebSocket channel
   * @param gameId The game to rejoin
   * @param playerId The player attempting to rejoin
   */
  final def handleRejoinRequest(
      channel: cask.WsChannelActor,
      gameId: String,
      playerId: String
  ): Unit =
    getGame(gameId) match
      case Some(game) =>
        getPlayers(game).get(playerId) match
          case Some(player) if PlayerConnectionOps.canRejoin(player, gracePeriodMs) =>
            performRejoin(channel, gameId, playerId, player, game)
          case Some(_) =>
            logger.info(s"Player $playerId cannot rejoin game $gameId - grace period expired")
            sendRejoinFailure(channel, "Session expired")
          case None =>
            logger.info(s"Player $playerId not found in game $gameId")
            sendRejoinFailure(channel, "Player not found")
      case None =>
        logger.info(s"Game $gameId not found for rejoin request from $playerId")
        sendRejoinFailure(channel, "Game not found")

  /**
   * Perform the actual rejoin operation.
   */
  private def performRejoin(
      channel: cask.WsChannelActor,
      gameId: String,
      playerId: String,
      player: Player,
      game: Game
  ): Unit =
    val reconnectedPlayer = reconnectPlayer(player)
    val updatedGame = withUpdatedPlayer(game, playerId, reconnectedPlayer)

    registerPlayerChannel(channel, gameId, playerId)
    saveGame(gameId, updatedGame)

    logger.info(s"Player $playerId rejoined game $gameId")

    sendRejoinSuccess(channel, playerId, gameId)
    broadcastGameState(gameId)

  /**
   * Handle a player disconnection.
   *
   * Marks the player as disconnected rather than removing them,
   * allowing them to rejoin within the grace period.
   *
   * @param gameId The game the player disconnected from
   * @param playerId The player who disconnected
   */
  final def handleDisconnection(gameId: String, playerId: String): Unit =
    getGame(gameId).foreach: game =>
      getPlayers(game).get(playerId).foreach: player =>
        val disconnectedPlayer = disconnectPlayer(player)
        val updatedGame = withUpdatedPlayer(game, playerId, disconnectedPlayer)
        saveGame(gameId, updatedGame)
        logger.info(s"Player $playerId disconnected from game $gameId (can rejoin within ${gracePeriodMs}ms)")
        broadcastGameState(gameId)

  /**
   * Clean up players who have exceeded the grace period.
   *
   * @param game The game to clean up
   * @param removePlayer Function to remove a player from the game
   * @return Updated game with expired players removed
   */
  final def cleanupExpiredPlayers(
      game: Game,
      removePlayer: (Game, String) => Game
  ): Game =
    val expiredPlayerIds = getPlayers(game).collect:
      case (playerId, player) if PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs) =>
        playerId

    expiredPlayerIds.foldLeft(game): (g, playerId) =>
      logger.info(s"Removing expired player $playerId")
      removePlayer(g, playerId)


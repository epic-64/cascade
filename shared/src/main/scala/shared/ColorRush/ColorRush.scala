package shared.ColorRush

import upickle.default.ReadWriter
import shared.session.{PlayerConnection, PlayerConnectionOps}

// Color Rush - Fast-paced multiplayer color matching game

enum GameStatus derives ReadWriter:
  case Waiting, Playing, RoundEnd, GameOver

case class ColorRushGame(
    gameId: String,
    players: Map[String, PlayerState],
    currentRound: Option[Round],
    roundNumber: Int,
    totalRounds: Int,
    status: GameStatus
) derives ReadWriter

case class PlayerState(
    playerId: String,
    name: String,
    score: Int,
    roundsWon: Int,
    connected: Boolean = true,
    disconnectedAt: Option[Long] = None
) extends PlayerConnection derives ReadWriter

case class Round(
    targetColor: String, // Hex color code
    colorOptions: Vector[String], // 6 color options in a grid
    startTime: Long
) derives ReadWriter

object ColorRush:
  val colors = Vector(
    "#FF6B6B",
    "#4ECDC4",
    "#45B7D1",
    "#FFA07A",
    "#98D8C8",
    "#F7DC6F",
    "#BB8FCE",
    "#85C1E2",
    "#F8B195",
    "#C06C84",
    "#6C5B7B",
    "#355C7D"
  )

  def createGame(gameId: String, totalRounds: Int): ColorRushGame =
    ColorRushGame(gameId, Map.empty, None, 0, totalRounds, GameStatus.Waiting)

  def addPlayer(game: ColorRushGame, playerId: String, playerName: String): ColorRushGame =
    val player = PlayerState(playerId, playerName, 0, 0, connected = true, disconnectedAt = None)
    game.copy(players = game.players + (playerId -> player))

  def configureGame(game: ColorRushGame, totalRounds: Int): ColorRushGame =
    if game.status == GameStatus.Waiting then
      game.copy(totalRounds = totalRounds)
    else
      game

  def removePlayer(game: ColorRushGame, playerId: String): ColorRushGame =
    game.copy(players = game.players - playerId)

  /** Mark a player as disconnected instead of removing them */
  def disconnectPlayer(game: ColorRushGame, playerId: String): ColorRushGame =
    game.players.get(playerId) match
      case Some(player) =>
        val disconnectedPlayer = player.copy(connected = false, disconnectedAt = Some(System.currentTimeMillis()))
        game.copy(players = game.players + (playerId -> disconnectedPlayer))
      case None => game

  /** Reconnect a previously disconnected player */
  def reconnectPlayer(game: ColorRushGame, playerId: String): Option[ColorRushGame] =
    game.players.get(playerId).map: player =>
      val reconnectedPlayer = player.copy(connected = true, disconnectedAt = None)
      game.copy(players = game.players + (playerId -> reconnectedPlayer))

  /** Check if a player can rejoin (exists and within grace period) */
  def canRejoin(game: ColorRushGame, playerId: String, gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs): Boolean =
    game.players.get(playerId).exists(PlayerConnectionOps.canRejoin(_, gracePeriodMs))

  /** Remove players who have been disconnected longer than the grace period */
  def cleanupDisconnectedPlayers(game: ColorRushGame, gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs): ColorRushGame =
    val activePlayers = game.players.filterNot:
      case (_, player) => PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs)
    game.copy(players = activePlayers)

  def startNewRound(game: ColorRushGame): ColorRushGame =
    import scala.util.Random
    val target = colors(Random.nextInt(colors.length))
    val otherColors = colors.filterNot(_ == target)
    val shuffled = Random.shuffle(otherColors).take(5)
    val allColors = Random.shuffle(shuffled :+ target)

    val round = Round(target, allColors, System.currentTimeMillis())
    game.copy(
      currentRound = Some(round),
      roundNumber = game.roundNumber + 1,
      status = GameStatus.Playing
    )

  def handleColorClick(
      game: ColorRushGame,
      playerId: String,
      color: String,
      clickTime: Long
  ): (ColorRushGame, Option[(String, String, Int)]) = // (playerId, playerName, points)
    // Only process clicks during active gameplay
    if game.status != GameStatus.Playing then
      return (game, None)

    game.currentRound match
      case None => (game, None)
      case Some(round) =>
        if color == round.targetColor then
          val timeElapsed = clickTime - round.startTime
          val speedBonus =
            if timeElapsed < 1000 then 50
            else if timeElapsed < 2000 then 30
            else if timeElapsed < 3000 then 10
            else 0
          val points = 100 + speedBonus

          val updatedPlayer = game.players.get(playerId).map: p =>
            p.copy(score = p.score + points, roundsWon = p.roundsWon + 1)

          val updatedGame = updatedPlayer match
            case Some(p) => game.copy(
                players = game.players + (playerId -> p),
                status = GameStatus.RoundEnd
              )
            case None => game

          (updatedGame, updatedPlayer.map(p => (playerId, p.name, points)))
        else
          (game, None)

  def shouldEndGame(game: ColorRushGame): Boolean =
    game.roundNumber >= game.totalRounds || game.players.isEmpty

  def getWinner(game: ColorRushGame): Option[PlayerState] =
    game.players.values.toSeq.sortBy(-_.score).headOption

  def advanceFromRoundEnd(game: ColorRushGame): ColorRushGame =
    if shouldEndGame(game) then
      game.copy(status = GameStatus.GameOver)
    else
      startNewRound(game)

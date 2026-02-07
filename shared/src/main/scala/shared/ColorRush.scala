package shared

import upickle.default.{ReadWriter, macroRW}

// Color Rush - Fast-paced multiplayer color matching game

enum GameStatus derives ReadWriter:
  case Waiting, Playing, RoundEnd, GameOver

case class ColorRushGame(
  gameId: String,
  players: Map[String, PlayerState],
  currentRound: Option[Round],
  roundNumber: Int,
  status: GameStatus
)

object ColorRushGame:
  implicit val rw: ReadWriter[ColorRushGame] = macroRW

case class PlayerState(
  playerId: String,
  name: String,
  score: Int,
  roundsWon: Int
)

object PlayerState:
  implicit val rw: ReadWriter[PlayerState] = macroRW

case class Round(
  targetColor: String,      // Hex color code
  colorOptions: Vector[String], // 6 color options in a grid
  startTime: Long
)

object Round:
  implicit val rw: ReadWriter[Round] = macroRW

object ColorRush:
  val colors = Vector(
    "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", 
    "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E2",
    "#F8B195", "#C06C84", "#6C5B7B", "#355C7D"
  )
  
  def createGame(gameId: String): ColorRushGame =
    ColorRushGame(gameId, Map.empty, None, 0, GameStatus.Waiting)

  def addPlayer(game: ColorRushGame, playerId: String, playerName: String): ColorRushGame =
    val player = PlayerState(playerId, playerName, 0, 0)
    game.copy(players = game.players + (playerId -> player))

  def removePlayer(game: ColorRushGame, playerId: String): ColorRushGame =
    game.copy(players = game.players - playerId)

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
    game.currentRound match
      case None => (game, None)
      case Some(round) =>
        if color == round.targetColor then
          val timeElapsed = clickTime - round.startTime
          val speedBonus = if timeElapsed < 1000 then 50
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
    game.roundNumber >= 10 || game.players.isEmpty

  def getWinner(game: ColorRushGame): Option[PlayerState] =
    game.players.values.toSeq.sortBy(-_.score).headOption
  
  def advanceFromRoundEnd(game: ColorRushGame): ColorRushGame =
    if shouldEndGame(game) then
      game.copy(status = GameStatus.GameOver)
    else
      startNewRound(game)


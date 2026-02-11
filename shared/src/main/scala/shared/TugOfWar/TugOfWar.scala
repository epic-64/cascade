package shared.TugOfWar

import upickle.default.ReadWriter
import shared.session.{PlayerConnection, PlayerConnectionOps}

enum Team derives ReadWriter:
  case Red, Blue

enum GameStatus derives ReadWriter:
  case Waiting, Playing, RoundEnd, GameOver

case class TugOfWarGame(
    gameId: String,
    hostId: Option[String],
    players: Map[String, PlayerState],
    ropePosition: Int, // -100 to +100, 0 is center
    roundsToWin: Int, // First to X rounds wins
    timeLimitSeconds: Int, // Time limit per round (0 = no limit)
    redRoundsWon: Int,
    blueRoundsWon: Int,
    currentRound: Int,
    status: GameStatus,
    roundStartTime: Option[Long]
) derives ReadWriter

case class PlayerState(
    playerId: String,
    name: String,
    team: Option[Team], // None until team is chosen
    clickCount: Int, // Clicks this round
    totalClicks: Int, // Clicks across all rounds
    connected: Boolean = true,
    disconnectedAt: Option[Long] = None
) extends PlayerConnection derives ReadWriter

case class RoundResult(
    winner: Team,
    redClicks: Int,
    blueClicks: Int,
    finalPosition: Int,
    duration: Long
) derives ReadWriter

object TugOfWar:
  val WinPosition: Int = 100 // Position magnitude to reach to win
  val StartPosition: Int = 0
  val ClickPower: Int = 1 // How much each click moves the rope
  val DefaultTimeLimitSeconds: Int = 20 // Default time limit per round

  def createGame(gameId: String, roundsToWin: Int = 3, timeLimitSeconds: Int = DefaultTimeLimitSeconds): TugOfWarGame =
    TugOfWarGame(
      gameId = gameId,
      hostId = None,
      players = Map.empty,
      ropePosition = StartPosition,
      roundsToWin = roundsToWin,
      timeLimitSeconds = timeLimitSeconds,
      redRoundsWon = 0,
      blueRoundsWon = 0,
      currentRound = 0,
      status = GameStatus.Waiting,
      roundStartTime = None
    )

  def addPlayer(game: TugOfWarGame, playerId: String, playerName: String): TugOfWarGame =
    val player = PlayerState(
      playerId = playerId,
      name = playerName,
      team = None,
      clickCount = 0,
      totalClicks = 0,
      connected = true,
      disconnectedAt = None
    )
    val isFirstPlayer = game.players.isEmpty
    game.copy(
      players = game.players + (playerId -> player),
      hostId = if isFirstPlayer then Some(playerId) else game.hostId
    )

  def setPlayerTeam(game: TugOfWarGame, playerId: String, team: Team): TugOfWarGame =
    game.players.get(playerId) match
      case Some(player) =>
        val updatedPlayer = player.copy(team = Some(team))
        game.copy(players = game.players + (playerId -> updatedPlayer))
      case None => game

  def removePlayer(game: TugOfWarGame, playerId: String): TugOfWarGame =
    val newPlayers = game.players - playerId
    val newHostId =
      if game.hostId.contains(playerId) then newPlayers.keys.headOption
      else game.hostId
    game.copy(players = newPlayers, hostId = newHostId)

  def disconnectPlayer(game: TugOfWarGame, playerId: String): TugOfWarGame =
    game.players.get(playerId) match
      case Some(player) =>
        val disconnectedPlayer = player.copy(
          connected = false,
          disconnectedAt = Some(System.currentTimeMillis())
        )
        game.copy(players = game.players + (playerId -> disconnectedPlayer))
      case None => game

  def reconnectPlayer(game: TugOfWarGame, playerId: String): Option[TugOfWarGame] =
    game.players.get(playerId).map: player =>
      val reconnectedPlayer = player.copy(connected = true, disconnectedAt = None)
      game.copy(players = game.players + (playerId -> reconnectedPlayer))

  def canRejoin(
      game: TugOfWarGame,
      playerId: String,
      gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs
  ): Boolean =
    game.players.get(playerId).exists(PlayerConnectionOps.canRejoin(_, gracePeriodMs))

  def cleanupDisconnectedPlayers(
      game: TugOfWarGame,
      gracePeriodMs: Long = PlayerConnectionOps.DefaultGracePeriodMs
  ): TugOfWarGame =
    val activePlayers = game.players.filterNot:
      case (_, player) => PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs)
    val newHostId =
      if game.hostId.exists(id => !activePlayers.contains(id)) then activePlayers.keys.headOption
      else game.hostId
    game.copy(players = activePlayers, hostId = newHostId)

  def configureGame(game: TugOfWarGame, roundsToWin: Int, timeLimitSeconds: Int): TugOfWarGame =
    if game.status == GameStatus.Waiting then
      game.copy(roundsToWin = roundsToWin, timeLimitSeconds = timeLimitSeconds)
    else
      game

  def canStart(game: TugOfWarGame): Boolean =
    val hasRedPlayer = game.players.values.exists(_.team.contains(Team.Red))
    val hasBluePlayer = game.players.values.exists(_.team.contains(Team.Blue))
    hasRedPlayer && hasBluePlayer && game.status == GameStatus.Waiting

  def startRound(game: TugOfWarGame): TugOfWarGame =
    // Reset click counts for the new round
    val resetPlayers = game.players.map:
      case (id, player) => id -> player.copy(clickCount = 0)

    game.copy(
      players = resetPlayers,
      ropePosition = StartPosition,
      currentRound = game.currentRound + 1,
      status = GameStatus.Playing,
      roundStartTime = Some(System.currentTimeMillis())
    )

  def handleClick(game: TugOfWarGame, playerId: String): TugOfWarGame =
    if game.status != GameStatus.Playing then return game

    game.players.get(playerId) match
      case Some(player) =>
        player.team match
          case Some(Team.Red) =>
            val newPosition = math.max(-WinPosition, game.ropePosition - ClickPower)
            val updatedPlayer = player.copy(
              clickCount = player.clickCount + 1,
              totalClicks = player.totalClicks + 1
            )
            game.copy(
              ropePosition = newPosition,
              players = game.players + (playerId -> updatedPlayer)
            )
          case Some(Team.Blue) =>
            val newPosition = math.min(WinPosition, game.ropePosition + ClickPower)
            val updatedPlayer = player.copy(
              clickCount = player.clickCount + 1,
              totalClicks = player.totalClicks + 1
            )
            game.copy(
              ropePosition = newPosition,
              players = game.players + (playerId -> updatedPlayer)
            )
          case None => game
      case None => game

  def checkRoundEnd(game: TugOfWarGame): Option[Team] =
    if game.ropePosition <= -WinPosition then Some(Team.Red)
    else if game.ropePosition >= WinPosition then Some(Team.Blue)
    else None

  /** Check if time has expired for the round */
  def isTimeExpired(game: TugOfWarGame): Boolean =
    if game.timeLimitSeconds <= 0 then false
    else
      game.roundStartTime match
        case Some(startTime) =>
          val elapsed = System.currentTimeMillis() - startTime
          elapsed >= game.timeLimitSeconds * 1000L
        case None => false

  /** Get remaining time in seconds, or None if no time limit */
  def getRemainingTime(game: TugOfWarGame): Option[Int] =
    if game.timeLimitSeconds <= 0 then None
    else
      game.roundStartTime match
        case Some(startTime) =>
          val elapsed = System.currentTimeMillis() - startTime
          val remaining = game.timeLimitSeconds - (elapsed / 1000).toInt
          Some(math.max(0, remaining))
        case None => Some(game.timeLimitSeconds)

  /** Determine winner when time expires (by position, or draw if at center) */
  def getTimeoutWinner(game: TugOfWarGame): Option[Team] =
    if game.ropePosition < 0 then Some(Team.Red)
    else if game.ropePosition > 0 then Some(Team.Blue)
    else None // Draw - no winner

  def endRound(game: TugOfWarGame, winner: Team): TugOfWarGame =
    val (newRedWins, newBlueWins) = winner match
      case Team.Red  => (game.redRoundsWon + 1, game.blueRoundsWon)
      case Team.Blue => (game.redRoundsWon, game.blueRoundsWon + 1)

    game.copy(
      redRoundsWon = newRedWins,
      blueRoundsWon = newBlueWins,
      status = GameStatus.RoundEnd
    )

  def shouldEndGame(game: TugOfWarGame): Boolean =
    game.redRoundsWon >= game.roundsToWin || game.blueRoundsWon >= game.roundsToWin

  def endGame(game: TugOfWarGame): TugOfWarGame =
    game.copy(status = GameStatus.GameOver)

  def getGameWinner(game: TugOfWarGame): Option[Team] =
    if game.redRoundsWon >= game.roundsToWin then Some(Team.Red)
    else if game.blueRoundsWon >= game.roundsToWin then Some(Team.Blue)
    else None

  def getTeamClicks(game: TugOfWarGame, team: Team): Int =
    game.players.values
      .filter(_.team.contains(team))
      .map(_.clickCount)
      .sum

  def getTeamTotalClicks(game: TugOfWarGame, team: Team): Int =
    game.players.values
      .filter(_.team.contains(team))
      .map(_.totalClicks)
      .sum

  def getTeamPlayers(game: TugOfWarGame, team: Team): Seq[PlayerState] =
    game.players.values.filter(_.team.contains(team)).toSeq

  def getRoundResult(game: TugOfWarGame, winner: Team): RoundResult =
    val duration = game.roundStartTime.map(start => System.currentTimeMillis() - start).getOrElse(0L)
    RoundResult(
      winner = winner,
      redClicks = getTeamClicks(game, Team.Red),
      blueClicks = getTeamClicks(game, Team.Blue),
      finalPosition = game.ropePosition,
      duration = duration
    )

  def advanceFromRoundEnd(game: TugOfWarGame): TugOfWarGame =
    if game.status != GameStatus.RoundEnd then game
    else if shouldEndGame(game) then endGame(game)
    else startRound(game)


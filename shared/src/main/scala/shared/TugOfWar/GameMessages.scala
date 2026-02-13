package shared.TugOfWar

import upickle.default.*

// Client -> Server messages
sealed trait ClientMessage

object ClientMessage:
  given ReadWriter[ClientMessage] = ReadWriter.merge(
    macroRW[JoinMessage],
    macroRW[CreateMessage],
    macroRW[RejoinMessage],
    macroRW[SelectTeamMessage],
    macroRW[ConfigureMessage],
    macroRW[StartMessage],
    macroRW[ClickMessage],
    macroRW[NextRoundMessage],
    macroRW[PingMessage]
  )

case class JoinMessage(playerName: String) extends ClientMessage derives ReadWriter
case class CreateMessage(playerName: String, roundsToWin: Int, timeLimitSeconds: Int) extends ClientMessage
    derives ReadWriter
case class RejoinMessage(playerId: String, gameId: String) extends ClientMessage derives ReadWriter
case class SelectTeamMessage(team: Team) extends ClientMessage derives ReadWriter
case class ConfigureMessage(roundsToWin: Int, timeLimitSeconds: Int) extends ClientMessage derives ReadWriter
case class StartMessage() extends ClientMessage derives ReadWriter
case class ClickMessage() extends ClientMessage derives ReadWriter
case class NextRoundMessage() extends ClientMessage derives ReadWriter
case class PingMessage() extends ClientMessage derives ReadWriter

// Server -> Client messages
sealed trait ServerMessage

object ServerMessage:
  given ReadWriter[ServerMessage] = ReadWriter.merge(
    macroRW[GameUpdateMessage],
    macroRW[JoinedMessage],
    macroRW[RejoinFailedMessage],
    macroRW[PositionUpdateMessage],
    macroRW[TimerUpdateMessage],
    macroRW[CountdownUpdateMessage],
    macroRW[RoundEndMessage],
    macroRW[GameEndMessage],
    macroRW[ErrorMessage]
  )

case class GameUpdateMessage(game: TugOfWarGame) extends ServerMessage derives ReadWriter
case class JoinedMessage(playerId: String, gameId: String) extends ServerMessage derives ReadWriter
case class RejoinFailedMessage(reason: String) extends ServerMessage derives ReadWriter
case class PositionUpdateMessage(position: Int, redClicks: Int, blueClicks: Int) extends ServerMessage
    derives ReadWriter
case class TimerUpdateMessage(secondsRemaining: Int) extends ServerMessage derives ReadWriter
case class CountdownUpdateMessage(secondsRemaining: Int) extends ServerMessage derives ReadWriter
case class RoundEndMessage(winner: Team, result: RoundResult) extends ServerMessage derives ReadWriter
case class GameEndMessage(winner: Team, redRoundsWon: Int, blueRoundsWon: Int) extends ServerMessage derives ReadWriter
case class ErrorMessage(message: String) extends ServerMessage derives ReadWriter

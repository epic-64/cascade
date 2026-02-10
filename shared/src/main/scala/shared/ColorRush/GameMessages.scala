package shared.ColorRush

import upickle.default.*

// WebSocket messages for Color Rush game

// Client -> Server messages
sealed trait ClientMessage

object ClientMessage:
  given ReadWriter[ClientMessage] = ReadWriter.merge(
    macroRW[JoinMessage],
    macroRW[ConfigureMessage],
    macroRW[StartMessage],
    macroRW[ClickMessage],
    macroRW[NextRoundMessage]
  )

case class JoinMessage(playerName: String, totalRounds: Int) extends ClientMessage derives ReadWriter
case class ConfigureMessage(totalRounds: Int) extends ClientMessage derives ReadWriter
case class StartMessage() extends ClientMessage derives ReadWriter
case class ClickMessage(color: String, time: Long) extends ClientMessage derives ReadWriter
case class NextRoundMessage() extends ClientMessage derives ReadWriter

// Server -> Client messages
sealed trait ServerMessage

object ServerMessage:
  given ReadWriter[ServerMessage] = ReadWriter.merge(
    macroRW[GameUpdateMessage],
    macroRW[RoundWinnerMessage],
    macroRW[GameEndMessage]
  )

case class GameUpdateMessage(game: ColorRushGame) extends ServerMessage derives ReadWriter
case class RoundWinnerMessage(playerName: String, points: Int) extends ServerMessage derives ReadWriter
case class GameEndMessage(winner: Option[PlayerState]) extends ServerMessage derives ReadWriter

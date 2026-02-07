package shared

import upickle.default.{ReadWriter, macroRW}

// WebSocket messages for Color Rush game

// Client -> Server messages
sealed trait ClientMessage derives ReadWriter

object ClientMessage:
  implicit val rw: ReadWriter[ClientMessage] = macroRW

case class JoinMessage(playerName: String) extends ClientMessage

object JoinMessage:
  implicit val rw: ReadWriter[JoinMessage] = macroRW

case object StartMessage extends ClientMessage:
  implicit val rw: ReadWriter[StartMessage.type] = macroRW

case class ClickMessage(color: String, time: Long) extends ClientMessage

object ClickMessage:
  implicit val rw: ReadWriter[ClickMessage] = macroRW

case object NextRoundMessage extends ClientMessage:
  implicit val rw: ReadWriter[NextRoundMessage.type] = macroRW

// Server -> Client messages
sealed trait ServerMessage derives ReadWriter

object ServerMessage:
  implicit val rw: ReadWriter[ServerMessage] = macroRW

case class GameUpdateMessage(game: ColorRushGame) extends ServerMessage

object GameUpdateMessage:
  implicit val rw: ReadWriter[GameUpdateMessage] = macroRW

case class RoundWinnerMessage(playerName: String, points: Int) extends ServerMessage

object RoundWinnerMessage:
  implicit val rw: ReadWriter[RoundWinnerMessage] = macroRW

case class GameEndMessage(winner: Option[PlayerState]) extends ServerMessage

object GameEndMessage:
  implicit val rw: ReadWriter[GameEndMessage] = macroRW


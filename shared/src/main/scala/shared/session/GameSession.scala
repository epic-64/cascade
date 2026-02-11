package shared.session

import upickle.default.*

/** Base trait for game session data stored in localStorage */
trait GameSession:
  def playerId: String
  def gameId: String      // Generic identifier (gameId, lobbyId, etc.)
  def playerName: String

/** Simple case class implementation for session storage */
case class BasicGameSession(
    playerId: String,
    gameId: String,
    playerName: String
) extends GameSession derives ReadWriter


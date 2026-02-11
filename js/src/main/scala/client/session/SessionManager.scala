package client.session

import org.scalajs.dom.window
import shared.session.{GameSession, BasicGameSession}

import scala.util.Try

/**
 * Manages game session data in browser localStorage.
 * Provides save/load/clear operations with error handling.
 */
object SessionManager:

  /**
   * Save a session to localStorage.
   * 
   * @param key Unique key prefix for this game type (e.g., "colorRush", "drawing")
   * @param session The session data to save
   */
  def save(key: String, session: GameSession): Unit =
    Try:
      window.localStorage.setItem(s"$key.playerId", session.playerId)
      window.localStorage.setItem(s"$key.gameId", session.gameId)
      window.localStorage.setItem(s"$key.playerName", session.playerName)
      println(s"[Session] Saved session for $key: playerId=${session.playerId}, gameId=${session.gameId}")
    .recover:
      case ex => println(s"[Session] Failed to save session for $key: ${ex.getMessage}")

  /**
   * Load a session from localStorage.
   * 
   * @param key Unique key prefix for this game type
   * @return Some(session) if all required fields exist, None otherwise
   */
  def load(key: String): Option[BasicGameSession] =
    Try:
      val playerId = window.localStorage.getItem(s"$key.playerId")
      val gameId = window.localStorage.getItem(s"$key.gameId")
      val playerName = window.localStorage.getItem(s"$key.playerName")
      
      if playerId != null && gameId != null && playerName != null then
        Some(BasicGameSession(playerId, gameId, playerName))
      else
        None
    .recover:
      case ex =>
        println(s"[Session] Failed to load session for $key: ${ex.getMessage}")
        None
    .getOrElse(None)

  /**
   * Clear session data from localStorage.
   * 
   * @param key Unique key prefix for this game type
   */
  def clear(key: String): Unit =
    Try:
      window.localStorage.removeItem(s"$key.playerId")
      window.localStorage.removeItem(s"$key.gameId")
      window.localStorage.removeItem(s"$key.playerName")
      println(s"[Session] Cleared session for $key")
    .recover:
      case ex => println(s"[Session] Failed to clear session for $key: ${ex.getMessage}")

  /**
   * Check if a session exists in localStorage.
   * 
   * @param key Unique key prefix for this game type
   * @return true if all required session fields exist
   */
  def exists(key: String): Boolean =
    load(key).isDefined


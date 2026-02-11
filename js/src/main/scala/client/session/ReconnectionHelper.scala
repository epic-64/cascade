package client.session

import org.scalajs.dom.*
import shared.session.GameSession

import scala.util.Try

/**
 * Configuration for game-specific reconnection behavior.
 * Each game implements this trait to customize the reconnection flow.
 */
trait ReconnectionConfig:
  /** Key prefix for localStorage (e.g., "colorRush", "drawing") */
  def sessionKey: String
  
  /** Build the WebSocket URL for the given game ID */
  def buildWebSocketUrl(gameId: String): String
  
  /** Build the JSON message to send for rejoining */
  def buildRejoinMessage(session: GameSession): String
  
  /** Called when rejoin succeeds - update game state */
  def onRejoinSuccess(playerId: String, gameId: String): Unit
  
  /** Called when rejoin fails - reset UI state */
  def onRejoinFailure(reason: String): Unit
  
  /** Called before attempting reconnection - hide setup UI */
  def onRejoinAttempt(): Unit
  
  /** Called to set up WebSocket message handler */
  def setupMessageHandler(ws: WebSocket): Unit
  
  /** Store the WebSocket reference */
  def setWebSocket(ws: WebSocket): Unit

/**
 * Helper object for managing session reconnection flow.
 * Provides standardized logic for checking and attempting reconnection.
 */
object ReconnectionHelper:

  /**
   * Check for existing session and attempt reconnection if found.
   * Call this during game initialization.
   * 
   * @param config Game-specific reconnection configuration
   * @return true if reconnection is being attempted, false if no session found
   */
  def checkAndReconnect(config: ReconnectionConfig): Boolean =
    SessionManager.load(config.sessionKey) match
      case Some(session) =>
        println(s"[Reconnect] Found existing session for ${config.sessionKey} - attempting rejoin")
        attemptReconnect(session, config)
        true
      case None =>
        println(s"[Reconnect] No existing session found for ${config.sessionKey}")
        false

  /**
   * Attempt to reconnect to an existing game session.
   * 
   * @param session The session data to reconnect with
   * @param config Game-specific reconnection configuration
   */
  def attemptReconnect(session: GameSession, config: ReconnectionConfig): Unit =
    config.onRejoinAttempt()
    
    val wsUrl = config.buildWebSocketUrl(session.gameId)
    val ws = new WebSocket(wsUrl)
    config.setWebSocket(ws)

    ws.onopen = (_: Event) =>
      println(s"[Reconnect] Connected to ${session.gameId}, sending rejoin message")
      Try:
        ws.send(config.buildRejoinMessage(session))
      .recover:
        case ex => println(s"[Reconnect] Failed to send rejoin message: ${ex.getMessage}")

    config.setupMessageHandler(ws)
    
    ws.onerror = (_: Event) =>
      println(s"[Reconnect] WebSocket error during rejoin to ${session.gameId}")

    ws.onclose = (_: CloseEvent) =>
      println(s"[Reconnect] WebSocket closed for ${session.gameId}")
      // Don't clear session on close - allow manual retry on page refresh

  /**
   * Handle a successful rejoin response from the server.
   * Updates session and notifies config.
   * 
   * @param config Game-specific reconnection configuration
   * @param playerId The player ID confirmed by server
   * @param gameId The game ID confirmed by server
   * @param playerName The player name (from session or server)
   */
  def handleRejoinSuccess(
      config: ReconnectionConfig,
      playerId: String,
      gameId: String,
      playerName: String
  ): Unit =
    // Re-save session with confirmed data
    SessionManager.save(config.sessionKey, shared.session.BasicGameSession(playerId, gameId, playerName))
    config.onRejoinSuccess(playerId, gameId)

  /**
   * Handle a failed rejoin response from the server.
   * Clears session and notifies config.
   * 
   * @param config Game-specific reconnection configuration
   * @param reason The failure reason from server
   */
  def handleRejoinFailure(config: ReconnectionConfig, reason: String): Unit =
    println(s"[Reconnect] Rejoin failed: $reason")
    SessionManager.clear(config.sessionKey)
    config.onRejoinFailure(reason)


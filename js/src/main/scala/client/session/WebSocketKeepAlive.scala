package client.session

import org.scalajs.dom
import org.scalajs.dom.WebSocket

import scala.util.Try

/**
 * Manages WebSocket keepalive pings to prevent idle timeouts.
 *
 * Many proxies and load balancers (including Railway) have idle timeouts
 * that close WebSocket connections after a period of inactivity (typically 60-120 seconds).
 * This module sends periodic ping messages to keep connections alive.
 *
 * Usage:
 * {{{
 *   // Create a keepalive manager for your game
 *   val keepAlive = WebSocketKeepAlive("ColorRush", () => sendPingMessage())
 *
 *   // Start when WebSocket connects
 *   ws.onopen = _ => keepAlive.start()
 *
 *   // Stop when WebSocket closes or on cleanup
 *   ws.onclose = _ => keepAlive.stop()
 * }}}
 */
class WebSocketKeepAlive(
    name: String,
    sendPing: () => Unit,
    intervalMs: Int = WebSocketKeepAlive.DefaultIntervalMs
):
  private var intervalId: Option[Int] = None

  /** Start sending periodic keepalive pings */
  def start(): Unit =
    stop() // Clear any existing interval first
    println(s"[$name] Starting WebSocket keepalive (interval: ${intervalMs}ms)")
    val id = dom.window.setInterval(
      () =>
        Try(sendPing()).recover:
          case ex => println(s"[$name] Keepalive ping failed: ${ex.getMessage}")
      ,
      intervalMs
    )
    intervalId = Some(id)

  /** Stop sending keepalive pings */
  def stop(): Unit =
    intervalId.foreach: id =>
      dom.window.clearInterval(id)
      println(s"[$name] Stopped WebSocket keepalive")
    intervalId = None

  /** Check if keepalive is currently active */
  def isActive: Boolean = intervalId.isDefined

object WebSocketKeepAlive:
  /** Default ping interval: 20 seconds (well under typical 60s proxy timeouts) */
  val DefaultIntervalMs: Int = 20000

  /**
   * Create a keepalive manager that sends a message via WebSocket.
   *
   * @param name Identifier for logging (e.g., game name)
   * @param getWebSocket Function to get the current WebSocket (if connected)
   * @param buildPingMessage Function to build the ping message string
   * @param intervalMs Ping interval in milliseconds (default: 20 seconds)
   */
  def forWebSocket(
      name: String,
      getWebSocket: () => Option[WebSocket],
      buildPingMessage: () => String,
      intervalMs: Int = DefaultIntervalMs
  ): WebSocketKeepAlive =
    WebSocketKeepAlive(
      name,
      () => getWebSocket().foreach: ws =>
        if ws.readyState == WebSocket.OPEN then
          val msg = buildPingMessage()
          println(s"[$name] Sending keepalive ping")
          ws.send(msg)
      ,
      intervalMs
    )


package server

import org.slf4j.LoggerFactory
import scala.util.Try
import castor.Context.Simple.global

object CounterHandler:
  private val logger = LoggerFactory.getLogger(getClass)

  // Castor context for WebSocket operations
  given castor.Context = global

  // Cask logger for WebSocket operations
  given cask.util.Logger = cask.util.Logger.Console.globalLogger

  // Counter state - using AtomicInteger for thread-safe operations
  private val counter = new java.util.concurrent.atomic.AtomicInteger(0)

  // WebSocket connections for broadcasting counter updates
  // Using concurrent collection for thread safety
  private val wsConnections = java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()

  def getCounter(): Int = counter.get()

  def incrementCounter(): Int =
    val newValue = counter.incrementAndGet()
    broadcastCounter()
    newValue

  def decrementCounter(): Int =
    val newValue = counter.decrementAndGet()
    broadcastCounter()
    newValue

  def handleWebSocket(): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Add new connection
      wsConnections.add(channel)
      logger.info(s"WebSocket client connected. Total connections: ${wsConnections.size}")

      // Send current counter value to newly connected client
      channel.send(cask.Ws.Text(counter.get().toString))

      // Handle incoming messages
      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.debug(s"WebSocket received message: $msg")

        case cask.Ws.Close(_, _) =>
          wsConnections.remove(channel)
          logger.info(s"WebSocket client disconnected. Total connections: ${wsConnections.size}")

        case cask.Ws.Error(ex) =>
          wsConnections.remove(channel)
          logger.error(s"WebSocket error: ${ex.getMessage}", ex)

  private def broadcastCounter(): Unit =
    val message = cask.Ws.Text(counter.get().toString)
    import scala.jdk.CollectionConverters.*
    wsConnections.asScala.foreach: channel =>
      Try(channel.send(message)).recover:
        case ex => logger.warn(s"Failed to send WebSocket message to client: ${ex.getMessage}")

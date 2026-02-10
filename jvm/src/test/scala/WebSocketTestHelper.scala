import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.{CompletionStage, ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.chaining.*

/** Reusable WebSocket testing utilities */
trait WebSocketTestHelper:
  this: TestServerHelper =>

  protected val wsClient: HttpClient = HttpClient.newHttpClient()

  /** Thread-safe message buffer for WebSocket tests */
  protected def createMessageBuffer[T](): ConcurrentLinkedQueue[T] = ConcurrentLinkedQueue[T]()

  /** Extension to convert ConcurrentLinkedQueue to Seq for assertions */
  extension [T](queue: ConcurrentLinkedQueue[T])
    def toSeq: Seq[T] = queue.asScala.toSeq

  /** Creates a WebSocket listener that collects raw string messages and signals via latches */
  protected def createStringListener(
    messages: ConcurrentLinkedQueue[String],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        val msg = data.toString
        messages.add(msg)
        latches.lift(messages.size - 1).foreach(_.countDown())
        webSocket.request(1)
        null

  /** Creates a WebSocket listener that parses messages with a custom parser and signals via latches */
  protected def createParsedListener[T](
    messages: ConcurrentLinkedQueue[T],
    latches: Seq[CountDownLatch]
  )(parse: String => T): WebSocket.Listener =
    new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        Try(parse(data.toString)).foreach: msg =>
          messages.add(msg)
          latches.lift(messages.size - 1).foreach(_.countDown())
        webSocket.request(1)
        null

  /** Connects to WebSocket and returns the connection, automatically requesting first message */
  protected def connectWebSocket(wsUrl: String, listener: WebSocket.Listener): WebSocket =
    wsClient.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl), listener)
      .get()
      .tap(_.request(1))

  /** Safely closes a WebSocket connection */
  protected def closeWebSocket(ws: WebSocket): Unit =
    Try(ws.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get())

  /** Executes a block with WebSocket connections, ensuring cleanup */
  protected def withWebSockets[T](connections: WebSocket*)(block: => T): T =
    Try(block)
      .tap(_ => connections.foreach(closeWebSocket))
      .get

  /** Helper to await a latch with a default timeout */
  protected def awaitLatch(latch: CountDownLatch, message: String, timeoutSeconds: Int = 5): Unit =
    assert(latch.await(timeoutSeconds, TimeUnit.SECONDS), message)


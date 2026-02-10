import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.CounterHandler
import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.{CompletionStage, CountDownLatch, TimeUnit}
import scala.util.Try
import scala.util.chaining.*

class CounterEndpointSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:

  private val wsClient = HttpClient.newHttpClient()

  // Reset counter state before each test to ensure test isolation
  override def beforeEach(): Unit =
    super.beforeEach()
    resetCounter()

  private def resetCounter(): Unit =
    (1 to CounterHandler.getCounter().abs).foreach: _ =>
      if CounterHandler.getCounter() > 0 then CounterHandler.decrementCounter()
      else CounterHandler.incrementCounter()

  private def wsUrl: String = s"ws://localhost:$testPort/ws/counter"

  /** Creates a WebSocket listener that collects messages and signals via latches */
  private def createListener(
    messages: scala.collection.mutable.ArrayBuffer[String],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        val msg = data.toString
        messages += msg
        latches.lift(messages.size - 1).foreach(_.countDown())
        webSocket.request(1)
        null

  /** Connects to WebSocket and returns the connection, automatically requesting first message */
  private def connectWebSocket(listener: WebSocket.Listener): WebSocket =
    wsClient.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl), listener)
      .get()
      .tap(_.request(1))

  /** Safely closes a WebSocket connection */
  private def closeWebSocket(ws: WebSocket): Unit =
    Try(ws.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get())

  /** Executes a block with WebSocket connections, ensuring cleanup */
  private def withWebSockets[T](connections: WebSocket*)(block: => T): T =
    Try(block)
      .tap(_ => connections.foreach(closeWebSocket))
      .get

  test("counter starts at 0"):
    val response = requests.get(s"$baseUrl/api/counter")
    assert(response.text().toInt == 0)

  test("incrementCounter increases counter by 1"):
    val before = requests.get(s"$baseUrl/api/counter").text().toInt
    val after = requests.post(s"$baseUrl/api/counter/increment").text().toInt
    assert(after == before + 1)

  test("decrementCounter decreases counter by 1"):
    val before = requests.get(s"$baseUrl/api/counter").text().toInt
    val after = requests.post(s"$baseUrl/api/counter/decrement").text().toInt
    assert(after == before - 1)

  test("WebSocket sends initial counter value on connection"):
    val messages = scala.collection.mutable.ArrayBuffer[String]()
    val initialLatch = CountDownLatch(1)
    val ws = connectWebSocket(createListener(messages, Seq(initialLatch)))

    withWebSockets(ws):
      assert(initialLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for WebSocket message")
      assert(messages.head.toInt == 0, "Initial counter should be 0")

  test("WebSocket broadcasts counter updates to all connected clients"):
    val messages1 = scala.collection.mutable.ArrayBuffer[String]()
    val messages2 = scala.collection.mutable.ArrayBuffer[String]()
    val initialLatch1, broadcastLatch1 = CountDownLatch(1)
    val initialLatch2, broadcastLatch2 = CountDownLatch(1)

    val ws1 = connectWebSocket(createListener(messages1, Seq(initialLatch1, broadcastLatch1)))
    val ws2 = connectWebSocket(createListener(messages2, Seq(initialLatch2, broadcastLatch2)))

    withWebSockets(ws1, ws2):
      // Wait for both clients to receive initial message
      assert(initialLatch1.await(5, TimeUnit.SECONDS), "Client 1 didn't receive initial message")
      assert(initialLatch2.await(5, TimeUnit.SECONDS), "Client 2 didn't receive initial message")

      // Increment counter via REST API
      requests.post(s"$baseUrl/api/counter/increment")

      // Both clients should receive the broadcast
      assert(broadcastLatch1.await(5, TimeUnit.SECONDS), "Client 1 didn't receive broadcast")
      assert(broadcastLatch2.await(5, TimeUnit.SECONDS), "Client 2 didn't receive broadcast")

      // Verify message sequence: initial value (0) and updated value (1)
      assert(messages1.map(_.toInt) == Seq(0, 1), s"Client 1 expected [0, 1], got ${messages1}")
      assert(messages2.map(_.toInt) == Seq(0, 1), s"Client 2 expected [0, 1], got ${messages2}")


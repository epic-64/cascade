import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.CounterHandler
import java.util.concurrent.CountDownLatch

class CounterEndpointSpec extends AnyFunSuite with TestServerHelper with WebSocketTestHelper with BeforeAndAfterEach:

  // Reset counter state before each test to ensure test isolation
  override def beforeEach(): Unit =
    super.beforeEach()
    resetCounter()

  private def resetCounter(): Unit =
    (1 to CounterHandler.getCounter().abs).foreach: _ =>
      if CounterHandler.getCounter() > 0 then CounterHandler.decrementCounter()
      else CounterHandler.incrementCounter()

  private def wsUrl: String = s"ws://localhost:$testPort/ws/counter"

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
    val messages = createMessageBuffer[String]()
    val initialLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl, createStringListener(messages, Seq(initialLatch)))

    withWebSockets(ws):
      awaitLatch(initialLatch, "Timeout waiting for WebSocket message")
      assert(messages.toSeq.head.toInt == 0, "Initial counter should be 0")

  test("WebSocket broadcasts counter updates to all connected clients"):
    val messages1 = createMessageBuffer[String]()
    val messages2 = createMessageBuffer[String]()
    val initialLatch1, broadcastLatch1 = CountDownLatch(1)
    val initialLatch2, broadcastLatch2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl, createStringListener(messages1, Seq(initialLatch1, broadcastLatch1)))
    val ws2 = connectWebSocket(wsUrl, createStringListener(messages2, Seq(initialLatch2, broadcastLatch2)))

    withWebSockets(ws1, ws2):
      // Wait for both clients to receive initial message
      awaitLatch(initialLatch1, "Client 1 didn't receive initial message")
      awaitLatch(initialLatch2, "Client 2 didn't receive initial message")

      // Increment counter via REST API
      requests.post(s"$baseUrl/api/counter/increment")

      // Both clients should receive the broadcast
      awaitLatch(broadcastLatch1, "Client 1 didn't receive broadcast")
      awaitLatch(broadcastLatch2, "Client 2 didn't receive broadcast")

      // Verify message sequence: initial value (0) and updated value (1)
      assert(messages1.toSeq.map(_.toInt) == Seq(0, 1), s"Client 1 expected [0, 1], got ${messages1.toSeq}")
      assert(messages2.toSeq.map(_.toInt) == Seq(0, 1), s"Client 2 expected [0, 1], got ${messages2.toSeq}")


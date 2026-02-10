import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.CounterHandler
import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.{CompletionStage, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

class CounterEndpointSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:

  // Reset counter state before each test to ensure test isolation
  override def beforeEach(): Unit =
    super.beforeEach()
    // Reset counter to 0
    while CounterHandler.getCounter() != 0 do
      if CounterHandler.getCounter() > 0 then
        CounterHandler.decrementCounter()
      else
        CounterHandler.incrementCounter()

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
    val wsUrl = s"ws://localhost:$testPort/ws/counter"
    val latch = CountDownLatch(1)
    val receivedMessage = AtomicReference[String]("")

    val listener = new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        receivedMessage.set(data.toString)
        latch.countDown()
        webSocket.request(1)  // Request next message
        null

    val client = HttpClient.newHttpClient()
    val ws = client.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl), listener)
      .get()

    try
      ws.request(1)  // Request first message
      // Wait for initial message
      assert(latch.await(5, TimeUnit.SECONDS), "Timeout waiting for WebSocket message")
      assert(receivedMessage.get().toInt == 0, "Initial counter should be 0")
    finally
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get()

  test("WebSocket broadcasts counter updates to all connected clients"):
    val wsUrl = s"ws://localhost:$testPort/ws/counter"
    val initialLatch1 = CountDownLatch(1) // For initial message
    val initialLatch2 = CountDownLatch(1)
    val broadcastLatch1 = CountDownLatch(1) // For broadcast message
    val broadcastLatch2 = CountDownLatch(1)
    val messages1 = scala.collection.mutable.ArrayBuffer[String]()
    val messages2 = scala.collection.mutable.ArrayBuffer[String]()

    val listener1 = new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        val msg = data.toString
        messages1 += msg
        if messages1.size == 1 then
          initialLatch1.countDown()
        else
          broadcastLatch1.countDown()
        webSocket.request(1)  // Request next message
        null

    val listener2 = new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        val msg = data.toString
        messages2 += msg
        if messages2.size == 1 then
          initialLatch2.countDown()
        else
          broadcastLatch2.countDown()
        webSocket.request(1)  // Request next message
        null

    val client = HttpClient.newHttpClient()
    val ws1 = client.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl), listener1)
      .get()

    val ws2 = client.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl), listener2)
      .get()

    try
      // Request first message from both connections
      ws1.request(1)
      ws2.request(1)
      
      // Wait for both clients to receive initial message
      assert(initialLatch1.await(5, TimeUnit.SECONDS), "Client 1 didn't receive initial message")
      assert(initialLatch2.await(5, TimeUnit.SECONDS), "Client 2 didn't receive initial message")

      // Now increment counter via REST API
      requests.post(s"$baseUrl/api/counter/increment")

      // Both clients should receive the broadcast
      assert(broadcastLatch1.await(5, TimeUnit.SECONDS), "Client 1 didn't receive broadcast")
      assert(broadcastLatch2.await(5, TimeUnit.SECONDS), "Client 2 didn't receive broadcast")

      // Both should have received: initial value (0) and updated value (1)
      assert(messages1.size == 2, s"Client 1 should receive exactly 2 messages, got ${messages1.size}")
      assert(messages2.size == 2, s"Client 2 should receive exactly 2 messages, got ${messages2.size}")
      assert(messages1.head.toInt == 0, "Client 1 should receive initial counter value of 0")
      assert(messages1.last.toInt == 1, "Client 1 should receive updated counter value of 1")
      assert(messages2.head.toInt == 0, "Client 2 should receive initial counter value of 0")
      assert(messages2.last.toInt == 1, "Client 2 should receive updated counter value of 1")
    finally
      ws1.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get()
      ws2.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get()


import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import server.WebServer

class CounterHandlerSpec extends AnyFunSuite with BeforeAndAfterAll:
  private var server: io.undertow.Undertow = null
  private val testPort = 8081
  private val baseUrl = s"http://localhost:$testPort"

  override def beforeAll(): Unit =
    server = io.undertow.Undertow.builder
      .addHttpListener(testPort, "localhost")
      .setHandler(WebServer.defaultHandler)
      .build
    server.start()
    Thread.sleep(100) // Give server time to start

  override def afterAll(): Unit =
    if server != null then server.stop()

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


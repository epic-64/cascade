import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.CounterHandler

class CounterHandlerSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:

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


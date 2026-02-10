import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.CounterHandler

class CounterHandlerWithStateControlSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:

  // Reset counter state before each test to ensure test isolation
  override def beforeEach(): Unit =
    super.beforeEach()
    // Access the counter directly and reset it
    // This works because we're in the same JVM!
    while CounterHandler.getCounter() != 0 do
      if CounterHandler.getCounter() > 0 then
        CounterHandler.decrementCounter()
      else
        CounterHandler.incrementCounter()

  test("counter starts at 0 (with state reset)"):
    val response = requests.get(s"$baseUrl/api/counter")
    assert(response.text().toInt == 0)

  test("incrementCounter increases from clean state"):
    // This test now always starts from 0, regardless of previous tests
    val after = requests.post(s"$baseUrl/api/counter/increment").text().toInt
    assert(after == 1)

  test("multiple increments work correctly"):
    requests.post(s"$baseUrl/api/counter/increment")
    requests.post(s"$baseUrl/api/counter/increment")
    val result = requests.get(s"$baseUrl/api/counter").text().toInt
    assert(result == 2)


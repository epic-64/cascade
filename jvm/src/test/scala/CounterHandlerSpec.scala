import org.scalatest.funsuite.AnyFunSuite

class CounterHandlerSpec extends AnyFunSuite with TestServerHelper:
  override protected val testPort = 8081

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


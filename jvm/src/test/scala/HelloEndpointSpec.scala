import org.scalatest.funsuite.AnyFunSuite

class HelloEndpointSpec extends AnyFunSuite with TestServerHelper:
  override protected val testPort = 8083

  test("hello route returns Hello, World!"):
    val response = requests.get(s"$baseUrl/hello")
    assert(response.statusCode == 200)
    assert(response.text() == "Hello, World!")

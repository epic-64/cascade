import org.scalatest.funsuite.AnyFunSuite

class HelloEndpointSpec extends AnyFunSuite with TestServerHelper:

  test("hello route returns Hello, World!"):
    val response = requests.get(s"$baseUrl/hello")
    assert(response.statusCode == 200)
    assert(response.text() == "Hello, World!")

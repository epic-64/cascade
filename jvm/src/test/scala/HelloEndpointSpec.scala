import org.scalatest.funsuite.AnyFunSuite
import server.WebServer

class HelloEndpointSpec extends AnyFunSuite:
  test("hello route returns Hello, World!"):
    assert(WebServer.hello() == "Hello, World!")

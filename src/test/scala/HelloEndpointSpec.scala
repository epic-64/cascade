import org.scalatest.funsuite.AnyFunSuite

class HelloEndpointSpec extends AnyFunSuite:
  test("hello endpoint definition renders path"):
    val e = Endpoints.helloWorldEndpoint
    assert(e.show.contains("GET /hello"))

  test("hello route returns Hello, World!"):
    assert(WebServer.hello() == "Hello, World!")

  test("health route returns OK"):
    assert(WebServer.health() == "OK")

  test("default server port is 8080 when PORT env missing"):
    assert(WebServer.port == 8080)

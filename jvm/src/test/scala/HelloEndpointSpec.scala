import org.scalatest.funsuite.AnyFunSuite
import server.WebServer
import shared.{User, SharedGreeter}

class HelloEndpointSpec extends AnyFunSuite:
  // TODO: Create Endpoints object if needed for endpoint metadata
  // test("hello endpoint definition renders path"):
  //   val e = Endpoints.helloWorldEndpoint
  //   assert(e.show.contains("GET /hello"))

  test("hello route returns Hello, World!"):
    assert(WebServer.hello() == "Hello, World!")

  test("health route returns OK"):
    assert(WebServer.health() == "OK")

  test("default server port is 8080 when PORT env missing"):
    assert(WebServer.port == 8080)

  test("SharedGreeter formats greeting with id and name"):
    val user = User(42, "Alice")
    assert(SharedGreeter.greet(user) == "Hello, Alice! (#42)")

  test("greet route uses shared type"):
    assert(WebServer.greet(7, "Bob") == "Hello, Bob! (#7)")

  test("counter starts at 0"):
    assert(WebServer.getCounter() == 0)

  test("incrementCounter increases counter by 1"):
    val before = WebServer.getCounter()
    val after = WebServer.incrementCounter()
    assert(after == before + 1)

  test("decrementCounter decreases counter by 1"):
    val before = WebServer.getCounter()
    val after = WebServer.decrementCounter()
    assert(after == before - 1)


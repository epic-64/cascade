import org.scalatest.funsuite.AnyFunSuite
import server.WebServer
import shared.{User, SharedGreeter}

class HelloEndpointSpec extends AnyFunSuite:
  test("hello route returns Hello, World!"):
    assert(WebServer.hello() == "Hello, World!")

  test("health route returns JSON with status and stats"):
    val healthResponse = WebServer.health()
    assert(healthResponse("status").str == "healthy")
    assert(healthResponse.obj.contains("uptime"))
    assert(healthResponse.obj.contains("counter"))
    assert(healthResponse.obj.contains("memory"))
    assert(healthResponse.obj.contains("system"))
    assert(healthResponse.obj.contains("timestamp"))
    // Check counter stats
    assert(healthResponse("counter")("value").num >= 0)
    assert(healthResponse("counter")("connections").num >= 0)

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


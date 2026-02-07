import org.scalatest.funsuite.AnyFunSuite
import server.{WebServer, CounterHandler}
import shared.{User, SharedGreeter}

class HelloEndpointSpec extends AnyFunSuite:
  test("hello route returns Hello, World!"):
    assert(WebServer.hello() == "Hello, World!")

  test("health route returns JSON with status and stats"):
    val healthResponse = WebServer.health()
    assert(healthResponse("status").str == "healthy")
    assert(healthResponse.obj.contains("uptime"))
    assert(healthResponse.obj.contains("memory"))
    assert(healthResponse.obj.contains("system"))
    assert(healthResponse.obj.contains("timestamp"))

  test("default server port is 8080 when PORT env missing"):
    assert(WebServer.port == 8080)

  test("SharedGreeter formats greeting with id and name"):
    val user = User(42, "Alice")
    assert(SharedGreeter.greet(user) == "Hello, Alice! (#42)")

  test("greet route uses shared type"):
    assert(WebServer.greet(7, "Bob") == "Hello, Bob! (#7)")

  test("counter starts at 0"):
    assert(CounterHandler.getCounter() == 0)

  test("incrementCounter increases counter by 1"):
    val before = CounterHandler.getCounter()
    val after = CounterHandler.incrementCounter()
    assert(after == before + 1)

  test("decrementCounter decreases counter by 1"):
    val before = CounterHandler.getCounter()
    val after = CounterHandler.decrementCounter()
    assert(after == before - 1)


import org.scalatest.funsuite.AnyFunSuite
import server.{WebServer, CounterHandler}

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


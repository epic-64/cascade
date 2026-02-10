import org.scalatest.funsuite.AnyFunSuite
import server.WebServer

class CounterHandlerSpec extends AnyFunSuite:
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


import org.scalatest.funsuite.AnyFunSuite
import server.CounterHandler

class CounterHandlerSpec extends AnyFunSuite:
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


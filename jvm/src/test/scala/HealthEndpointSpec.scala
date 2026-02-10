import org.scalatest.funsuite.AnyFunSuite
import server.WebServer

class HealthEndpointSpec extends AnyFunSuite:
  test("health route returns JSON with status and stats"):
    val healthResponse = WebServer.health()
    assert(healthResponse("status").str == "healthy")
    assert(healthResponse.obj.contains("uptime"))
    assert(healthResponse.obj.contains("memory"))
    assert(healthResponse.obj.contains("system"))
    assert(healthResponse.obj.contains("timestamp"))


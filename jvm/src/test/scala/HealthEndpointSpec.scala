import org.scalatest.funsuite.AnyFunSuite

class HealthEndpointSpec extends AnyFunSuite with TestServerHelper:
  override protected val testPort = 8082

  test("health route returns JSON with status and stats"):
    val response = requests.get(s"$baseUrl/health")
    val healthResponse = ujson.read(response.text())
    assert(healthResponse("status").str == "healthy")
    assert(healthResponse.obj.contains("uptime"))
    assert(healthResponse.obj.contains("memory"))
    assert(healthResponse.obj.contains("system"))
    assert(healthResponse.obj.contains("timestamp"))


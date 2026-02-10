import org.scalatest.funsuite.AnyFunSuite
import server.WebServer

class WebServerConfigSpec extends AnyFunSuite:
  test("default server port is 8080 when PORT env missing"):
    assert(WebServer.port == 8080)


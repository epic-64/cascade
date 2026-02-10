import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import server.WebServer

trait TestServerHelper extends BeforeAndAfterAll:
  this: Suite =>

  private var server: io.undertow.Undertow = null
  protected val testPort: Int
  protected def baseUrl: String = s"http://localhost:$testPort"

  override def beforeAll(): Unit =
    super.beforeAll()
    server = io.undertow.Undertow.builder
      .addHttpListener(testPort, "localhost")
      .setHandler(WebServer.defaultHandler)
      .build
    server.start()
    Thread.sleep(100) // Give server time to start

  override def afterAll(): Unit =
    if server != null then server.stop()
    super.afterAll()


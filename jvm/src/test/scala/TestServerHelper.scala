import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import server.WebServer
import java.util.concurrent.atomic.AtomicInteger

object TestServerHelper:
  // Start from port 8081 and increment for each test suite
  private val portCounter = new AtomicInteger(8081)
  
  def nextPort(): Int = portCounter.getAndIncrement()

trait TestServerHelper extends BeforeAndAfterAll:
  this: Suite =>

  private var server: io.undertow.Undertow = null
  protected val testPort: Int = TestServerHelper.nextPort()
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


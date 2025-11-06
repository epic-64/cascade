import scala.util.Try

object CascadeServer:
  def main(args: Array[String]): Unit =
    // Delegate to Cask's main launcher for the WebServer routes
    WebServer.main(args)


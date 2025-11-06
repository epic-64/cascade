//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
@main
def main(): Unit =
  // Start the Cask web server that implements the Tapir hello world endpoint
  println(s"Starting WebServer on port ${WebServer.port}...")
  WebServer.main(Array.empty)

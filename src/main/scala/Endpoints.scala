import sttp.tapir.*

/** Tapir endpoint definition for Hello World. */
object Endpoints:
  /** GET /hello returning a plain text greeting. */
  val helloWorldEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("hello")
      .out(stringBody.example("Hello, World!"))
      .description("Returns a 'Hello, World!' greeting.")
      .name("helloWorld")

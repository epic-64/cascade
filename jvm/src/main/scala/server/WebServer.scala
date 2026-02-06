package server

import cask.main.MainRoutes
import cask.model.Response
import cask.router.Result
import shared.{SharedGreeter, User}
import scala.collection.mutable
import java.nio.file.{Files, Paths}

// CORS decorator to allow cross-origin requests
class allowCors extends cask.RawDecorator:
  def wrapFunction(ctx: cask.Request, delegate: Delegate): Result[cask.Response.Raw] =
    delegate(ctx, Map.empty).map: response =>
      response.copy(headers = response.headers ++ Seq(
        "Access-Control-Allow-Origin" -> "*",
        "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers" -> "Content-Type"
      ))

object WebServer extends MainRoutes:
  // Counter state
  private var counter: Int = 0

  // WebSocket connections for broadcasting counter updates
  private val wsConnections = mutable.Set[cask.WsChannelActor]()

  @cask.get("/hello")
  def hello(): String = "Hello, World!"

  @cask.get("/health")
  def health(): String = "OK"

  @allowCors()
  @cask.get("/counter")
  def getCounter(): Int = counter

  @allowCors()
  @cask.post("/counter/increment")
  def incrementCounter(): Int =
    counter += 1
    broadcastCounter()
    counter

  @allowCors()
  @cask.post("/counter/decrement")
  def decrementCounter(): Int =
    counter -= 1
    broadcastCounter()
    counter

  @cask.websocket("/ws/counter")
  def counterWebSocket(): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Add new connection
      wsConnections.add(channel)
      println(s"[WebSocket] Client connected. Total connections: ${wsConnections.size}")

      // Send current counter value to newly connected client
      channel.send(cask.Ws.Text(counter.toString))

      // Handle incoming messages (not needed for now, but good to have)
      cask.WsActor:
        case cask.Ws.Text(msg) =>
          println(s"[WebSocket] Received: $msg")

        case cask.Ws.Close(_, _) =>
          wsConnections.remove(channel)
          println(s"[WebSocket] Client disconnected. Total connections: ${wsConnections.size}")

        case cask.Ws.Error(ex) =>
          wsConnections.remove(channel)
          println(s"[WebSocket] Error: ${ex.getMessage}")

  private def broadcastCounter(): Unit =
    val message = cask.Ws.Text(counter.toString)
    wsConnections.foreach: channel =>
      channel.send(message)

  @cask.get("/user/:userName")
  def getUserProfile(userName: String) = s"User $userName"

  // New route demonstrating use of a shared cross-compiled type
  // GET /greet/42/Alice => Hello, Alice! (#42)
  @cask.get("/greet/:id/:name")
  def greet(id: Int, name: String): String =
    val user = User(id, name)
    SharedGreeter.greet(user)

  @cask.get("/user2/:userName") // allow unknown params, e.g. HOST/user2/foo?foo=bar&qux=baz
  def getUserProfileAllowUnknown(userName: String, params: cask.QueryParams): String =
    s"User $userName " + params.value

  @cask.get("/article/:articleId") // Mandatory query param, e.g. HOST/article/foo?param=bar
  def getArticle(articleId: Int, param: String) =
    s"Article $articleId $param"

  @cask.get("/article2/:articleId") // Optional query param
  def getArticleOptional(articleId: Int, param: Option[String] = None) =
    s"Article $articleId $param"

  @cask.get("/article3/:articleId") // Optional query param with default
  def getArticleDefault(articleId: Int, param: String = "DEFAULT VALUE") =
    s"Article $articleId $param"

  @cask.get("/article4/:articleId") // 1-or-more param, e.g. HOST/article/foo?param=bar&param=qux
  def getArticleSeq(articleId: Int, param: Seq[String]) =
    s"Article $articleId $param"

  @cask.get("/article5/:articleId") // 0-or-more query param
  def getArticleOptionalSeq(articleId: Int, param: Seq[String] = Nil) =
    s"Article $articleId $param"

  // Serve the main HTML page
  @cask.get("/")
  def index(): Response[String] =
    val htmlPath = Paths.get("index.html")
    if Files.exists(htmlPath) then
      val content = new String(Files.readAllBytes(htmlPath))
      cask.Response(
        data = content,
        headers = Seq("Content-Type" -> "text/html")
      )
    else
      cask.Response("index.html not found", statusCode = 404)

  // Serve the compiled JavaScript file
  @cask.get("/main.js")
  def mainJs(): Response[Array[Byte]] =
    val jsPath = Paths.get("js/target/scala-3.7.4/cascade-fastopt/main.js")
    if Files.exists(jsPath) then
      val content = Files.readAllBytes(jsPath)
      cask.Response(
        data = content,
        headers = Seq("Content-Type" -> "application/javascript")
      )
    else
      cask.Response(
        data = "main.js not found - run: sbt ~cascadeJS/fastLinkJS".getBytes,
        statusCode = 404
      )

  override def port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

  initialize()

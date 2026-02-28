package server

import cask.main.MainRoutes
import cask.model.Response
import scala.util.Try
import org.slf4j.LoggerFactory
import server.WeatherClient.given

// Configuration object - can be overridden via environment variables
object Config:
  val port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)
  val staticFilesDir: String = sys.env.getOrElse("STATIC_FILES_DIR", ".")
  val cacheDuration: Int = sys.env.get("CACHE_MAX_AGE").flatMap(_.toIntOption).getOrElse(3600)
  val isProd: Boolean = sys.env.get("ENVIRONMENT").contains("production")

object WebServer extends MainRoutes:
  private val logger = LoggerFactory.getLogger(getClass)
  // Server start time for uptime calculation
  private val startTime = System.currentTimeMillis()

  // Scheduled executor for periodic cleanup tasks
  private val cleanupScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1)

  @cask.get("/hello")
  def hello(): String = "Hello, World!"

  @cask.get("/health")
  def health(): ujson.Value =
    HealthEndpoint.health(startTime)

  @cask.get("/api/version")
  def version(): ujson.Value =
    VersionEndpoint.version(startTime)

  @cask.get("/api/weather/:city")
  def weather(city: String): ujson.Value =
    WeatherEndpoint.getWeather(city)

  @cask.get("/api/counter")
  def counter(): Int = CounterHandler.getCounter()

  @cask.post("/api/counter/increment")
  def incrementCounter(): Int = CounterHandler.incrementCounter()

  @cask.post("/api/counter/decrement")
  def decrementCounter(): Int = CounterHandler.decrementCounter()

  @cask.websocket("/ws/counter")
  def counterWebSocket(): cask.WebsocketResult = CounterHandler.handleWebSocket()

  // Color Rush game WebSocket endpoint
  @cask.websocket("/ws/color-rush/:gameId")
  def colorRushWebSocket(gameId: String): cask.WebsocketResult =
    ColorRushHandler.handleWebSocket(gameId)

  // AI Drawing game WebSocket endpoint
  @cask.websocket("/ws/drawing/:lobbyId")
  def drawingGameWebSocket(lobbyId: String): cask.WebsocketResult =
    DrawingGameHandler.handleWebSocket(lobbyId)

  // Tug of War game WebSocket endpoint
  @cask.websocket("/ws/tug-of-war/:gameId")
  def tugOfWarWebSocket(gameId: String): cask.WebsocketResult =
    TugOfWarHandler.handleWebSocket(gameId)

  // AI Chat WebSocket endpoint
  @cask.websocket("/ws/ai-chat")
  def aiChatWebSocket(): cask.WebsocketResult =
    AIChatHandler.handleWebSocket()

  // Start periodic cleanup task
  private def startCleanupTask(): Unit =
    val cleanupTask = new Runnable:
      def run(): Unit =
        Try(ColorRushHandler.cleanupEmptyGames()).recover:
          case ex => logger.error(s"Error during ColorRush cleanup: ${ex.getMessage}", ex)
        Try(DrawingGameHandler.cleanupEmptyLobbies()).recover:
          case ex => logger.error(s"Error during DrawingGame cleanup: ${ex.getMessage}", ex)
        Try(TugOfWarHandler.cleanupEmptyGames()).recover:
          case ex => logger.error(s"Error during TugOfWar cleanup: ${ex.getMessage}", ex)

    // Run cleanup every 5 minutes
    cleanupScheduler.scheduleAtFixedRate(
      cleanupTask,
      5, // initial delay in minutes
      5, // period in minutes
      java.util.concurrent.TimeUnit.MINUTES
    )

    logger.info("Started periodic game cleanup task (runs every 5 minutes)")

  // Serve HTML pages directly at clean URLs
  @cask.get("/")
  def index(): cask.Response[java.io.InputStream] =
    cask.Response(
      data = getClass.getClassLoader.getResourceAsStream("static/index.html"),
      statusCode = 200,
      headers = Seq("Content-Type" -> "text/html")
    )

  @cask.get("/counter")
  def counterPage(): cask.Response[java.io.InputStream] =
    cask.Response(
      data = getClass.getClassLoader.getResourceAsStream("static/counter.html"),
      statusCode = 200,
      headers = Seq("Content-Type" -> "text/html")
    )

  @cask.get("/color-rush")
  def colorRush(): cask.Response[java.io.InputStream] =
    serveGamePage("color-rush.html")

  @cask.get("/color-rush/:lobbyId")
  def colorRushWithLobby(lobbyId: String): cask.Response[java.io.InputStream] =
    serveGamePage("color-rush.html")

  @cask.get("/ai-drawing")
  def aiDrawing(): cask.Response[java.io.InputStream] =
    serveGamePage("ai-drawing.html")

  @cask.get("/ai-drawing/:lobbyId")
  def aiDrawingWithLobby(lobbyId: String): cask.Response[java.io.InputStream] =
    serveGamePage("ai-drawing.html")

  @cask.get("/tug-of-war")
  def tugOfWar(): cask.Response[java.io.InputStream] =
    serveGamePage("tug-of-war.html")

  @cask.get("/tug-of-war/:lobbyId")
  def tugOfWarWithLobby(lobbyId: String): cask.Response[java.io.InputStream] =
    serveGamePage("tug-of-war.html")

  @cask.get("/trader")
  def trader(): cask.Response[java.io.InputStream] =
    serveGamePage("trader.html")

  @cask.get("/tile-kingdom")
  def tileKingdom(): cask.Response[java.io.InputStream] =
    serveGamePage("tile-kingdom.html")

  @cask.get("/ai-chat")
  def aiChat(): cask.Response[java.io.InputStream] =
    serveGamePage("ai-chat.html")

  private def serveGamePage(htmlFile: String): cask.Response[java.io.InputStream] =
    cask.Response(
      data = getClass.getClassLoader.getResourceAsStream(s"static/$htmlFile"),
      statusCode = 200,
      headers = Seq("Content-Type" -> "text/html")
    )

  // Serve static files (HTML, CSS, JS) from src/main/resources
  // Cask automatically handles content types, ETag, Last-Modified, and 304 Not Modified
  @cask.staticResources("/static", headers = Seq("Cache-Control" -> s"public, max-age=${Config.cacheDuration}"))
  def staticResourceRoutes() = "static"

  override def port: Int = Config.port
  override def host: String = "0.0.0.0"

  // Log startup configuration
  logger.info("=" * 60)
  logger.info("Starting Cascade Server")
  logger.info(s"Host: 0.0.0.0")
  logger.info(s"Port: ${Config.port}")
  logger.info(s"Static files directory: ${Config.staticFilesDir}")
  logger.info(s"Cache duration: ${Config.cacheDuration}s")
  logger.info(s"Environment: ${if Config.isProd then "production" else "development"}")
  logger.info(s"JS build: ${JsChanged.timestamp}")
  logger.info("=" * 60)

  startCleanupTask()
  initialize()

package server

import cask.main.MainRoutes
import cask.model.Response
import shared.{SharedGreeter, User}
import java.nio.file.{Files, Paths}
import scala.util.Try
import org.slf4j.LoggerFactory

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

  // Counter state - using AtomicInteger for thread-safe operations
  private val counter = new java.util.concurrent.atomic.AtomicInteger(0)

  // WebSocket connections for broadcasting counter updates
  // Using concurrent collection for thread safety
  private val wsConnections = java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()

  // Color Rush game state management
  private val colorRushGames = java.util.concurrent.ConcurrentHashMap[String, shared.ColorRushGame]()
  private val gameConnections = java.util.concurrent.ConcurrentHashMap[String, java.util.Set[cask.WsChannelActor]]()
  private val playerToGame = java.util.concurrent.ConcurrentHashMap[cask.WsChannelActor, (String, String)]() // (gameId, playerId)

  // Scheduled executor for periodic cleanup tasks
  private val cleanupScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1)

  @cask.get("/hello")
  def hello(): String = "Hello, World!"

  @cask.get("/health")
  def health(): ujson.Value =
    HealthEndpoint.health(startTime, counter, wsConnections)

  @cask.get("/counter")
  def getCounter(): Int = counter.get()

  @cask.post("/counter/increment")
  def incrementCounter(): Int =
    val newValue = counter.incrementAndGet()
    broadcastCounter()
    newValue

  @cask.post("/counter/decrement")
  def decrementCounter(): Int =
    val newValue = counter.decrementAndGet()
    broadcastCounter()
    newValue

  @cask.websocket("/ws/counter")
  def counterWebSocket(): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Add new connection
      wsConnections.add(channel)
      logger.info(s"WebSocket client connected. Total connections: ${wsConnections.size}")

      // Send current counter value to newly connected client
      channel.send(cask.Ws.Text(counter.get().toString))

      // Handle incoming messages
      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.debug(s"WebSocket received message: $msg")

        case cask.Ws.Close(_, _) =>
          wsConnections.remove(channel)
          logger.info(s"WebSocket client disconnected. Total connections: ${wsConnections.size}")

        case cask.Ws.Error(ex) =>
          wsConnections.remove(channel)
          logger.error(s"WebSocket error: ${ex.getMessage}", ex)

  private def broadcastCounter(): Unit =
    val message = cask.Ws.Text(counter.get().toString)
    import scala.jdk.CollectionConverters.*
    wsConnections.asScala.foreach: channel =>
      Try(channel.send(message)).recover:
        case ex => logger.warn(s"Failed to send WebSocket message to client: ${ex.getMessage}")

  // Color Rush game WebSocket endpoint
  @cask.websocket("/ws/game/:gameId")
  def colorRushWebSocket(gameId: String): cask.WebsocketResult =
    cask.WsHandler: channel =>
      // Initialize game if it doesn't exist
      colorRushGames.computeIfAbsent(gameId, _ => shared.ColorRush.createGame(gameId))

      // Add channel to game connections
      val connections = gameConnections.computeIfAbsent(gameId, _ =>
        java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
      )
      connections.add(channel)

      logger.info(s"Player connected to game $gameId. Total players: ${connections.size()}")

      cask.WsActor:
        case cask.Ws.Text(msg) =>
          logger.info(s"Received WebSocket message: $msg")
          Try:
            import upickle.default.*
            val clientMsg = read[shared.ClientMessage](msg)

            clientMsg match
              case shared.JoinMessage(playerName) =>
                val playerId = java.util.UUID.randomUUID().toString

                playerToGame.put(channel, (gameId, playerId))

                val game = colorRushGames.get(gameId)
                val updatedGame = shared.ColorRush.addPlayer(game, playerId, playerName)
                colorRushGames.put(gameId, updatedGame)

                logger.info(s"Player $playerName ($playerId) joined game $gameId")

                // Broadcast game state to all players
                broadcastGameState(gameId)

              case shared.StartMessage() =>
                val game = colorRushGames.get(gameId)
                if game.players.nonEmpty then
                  val gameWithRound = shared.ColorRush.startNewRound(game)
                  colorRushGames.put(gameId, gameWithRound)
                  logger.info(s"Game $gameId started - Round ${gameWithRound.roundNumber}")
                  broadcastGameState(gameId)

              case shared.ClickMessage(color, clickTime) =>
                playerToGame.get(channel) match
                  case (gId, pId) if gId == gameId =>
                    val game = colorRushGames.get(gameId)
                    val (updatedGame, winner) = shared.ColorRush.handleColorClick(game, pId, color, clickTime)
                    colorRushGames.put(gameId, updatedGame)

                    winner.foreach: (playerId, playerName, points) =>
                      logger.info(s"Round winner: $playerName with $points points")
                      broadcastRoundWinner(gameId, playerId, playerName, points)

                    broadcastGameState(gameId)

                  case _ =>
                    logger.warn(s"Click from unregistered player")

              case shared.NextRoundMessage() =>
                // Client confirms they've seen the winner announcement, advance to next round
                val game = colorRushGames.get(gameId)
                if game != null && game.status == shared.GameStatus.RoundEnd then
                  val nextGame = shared.ColorRush.advanceFromRoundEnd(game)
                  colorRushGames.put(gameId, nextGame)
                  logger.info(s"Game $gameId advancing from roundEnd to ${nextGame.status}")

                  if nextGame.status == shared.GameStatus.GameOver then
                    val winner = shared.ColorRush.getWinner(nextGame)
                    broadcastGameEnd(gameId, winner)

                  broadcastGameState(gameId)
          .recover:
            case ex =>
              logger.error(s"Error processing game message: ${ex.getMessage}", ex)

        case cask.Ws.Close(_, _) =>
          handlePlayerDisconnect(channel, gameId, connections)

        case cask.Ws.Error(ex) =>
          logger.error(s"WebSocket error in game $gameId: ${ex.getMessage}", ex)
          handlePlayerDisconnect(channel, gameId, connections)

  private def broadcastGameState(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val game = colorRushGames.get(gameId)
    if game != null then
      val message = shared.GameUpdateMessage(game)
      val messageJson = write(message)

      gameConnections.get(gameId) match
        case null =>
        case connections =>
          connections.asScala.foreach: channel =>
            Try(channel.send(cask.Ws.Text(messageJson))).recover:
              case ex => logger.warn(s"Failed to broadcast game state: ${ex.getMessage}")

  private def broadcastRoundWinner(gameId: String, playerId: String, playerName: String, points: Int): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.RoundWinnerMessage(playerName, points)
    val messageJson = write(message)

    gameConnections.get(gameId) match
      case null =>
      case connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast round winner: ${ex.getMessage}")

  private def broadcastGameEnd(gameId: String, winner: Option[shared.PlayerState]): Unit =
    import scala.jdk.CollectionConverters.*
    import upickle.default.*

    val message = shared.GameEndMessage(winner)
    val messageJson = write(message)

    gameConnections.get(gameId) match
      case null =>
      case connections =>
        connections.asScala.foreach: channel =>
          Try(channel.send(cask.Ws.Text(messageJson))).recover:
            case ex => logger.warn(s"Failed to broadcast game end: ${ex.getMessage}")

  private def handlePlayerDisconnect(
    channel: cask.WsChannelActor,
    gameId: String,
    connections: java.util.Set[cask.WsChannelActor]
  ): Unit =
    connections.remove(channel)

    playerToGame.get(channel) match
      case (gId, pId) if gId == gameId =>
        playerToGame.remove(channel)
        val game = colorRushGames.get(gameId)
        if game != null then
          val updatedGame = shared.ColorRush.removePlayer(game, pId)
          colorRushGames.put(gameId, updatedGame)
          logger.info(s"Player $pId disconnected from game $gameId")
          broadcastGameState(gameId)

          // Clean up game if it's GameOver and has no more connections
          if updatedGame.status == shared.GameStatus.GameOver && connections.isEmpty then
            cleanupGame(gameId)
      case _ =>

    logger.info(s"Connection closed for game $gameId. Remaining players: ${connections.size()}")

  private[server] def cleanupGame(gameId: String): Unit =
    import scala.jdk.CollectionConverters.*

    colorRushGames.remove(gameId)
    gameConnections.remove(gameId)

    // Remove all player-to-game mappings for this game
    playerToGame.asScala.foreach:
      case (channel, (gId, _)) if gId == gameId =>
        playerToGame.remove(channel)
      case _ =>

    logger.info(s"Cleaned up game $gameId")

  private[server] def cleanupEmptyGames(): Int =
    import scala.jdk.CollectionConverters.*

    val gamesToCleanup = gameConnections.asScala.filter:
      case (gameId, connections) => connections.isEmpty
    .keys.toList

    gamesToCleanup.foreach: gameId =>
      cleanupGame(gameId)

    if gamesToCleanup.nonEmpty then
      logger.info(s"Periodic cleanup: removed ${gamesToCleanup.size} empty game(s)")

    gamesToCleanup.size

  // Test helpers - package-private for testing
  private[server] def getGame(gameId: String): Option[shared.ColorRushGame] =
    Option(colorRushGames.get(gameId))

  private[server] def getGameConnectionCount(gameId: String): Int =
    Option(gameConnections.get(gameId)).map(_.size()).getOrElse(0)

  private[server] def createTestGame(gameId: String, game: shared.ColorRushGame): Unit =
    colorRushGames.put(gameId, game)
    gameConnections.computeIfAbsent(gameId, _ =>
      java.util.concurrent.ConcurrentHashMap.newKeySet[cask.WsChannelActor]()
    )

  // Start periodic cleanup task
  private def startCleanupTask(): Unit =
    val cleanupTask = new Runnable:
      def run(): Unit =
        Try(cleanupEmptyGames()).recover:
          case ex => logger.error(s"Error during periodic cleanup: ${ex.getMessage}", ex)

    // Run cleanup every 5 minutes
    cleanupScheduler.scheduleAtFixedRate(
      cleanupTask,
      5, // initial delay in minutes
      5, // period in minutes
      java.util.concurrent.TimeUnit.MINUTES
    )

    logger.info("Started periodic game cleanup task (runs every 5 minutes)")

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
    val htmlPath = Paths.get(Config.staticFilesDir, "static", "index.html")
    if Files.exists(htmlPath) then
      val content = new String(Files.readAllBytes(htmlPath))
      cask.Response(
        data = content,
        headers = Seq("Content-Type" -> "text/html")
      )
    else
      val msg = s"index.html not found at ${htmlPath.toAbsolutePath}"
      logger.error(msg)
      cask.Response(msg, statusCode = 404)

  // Serve the compiled JavaScript file
  @cask.get("/main.js")
  def mainJs(request: cask.Request): Response[Array[Byte]] =
    serveStaticFile(request, "main.js", "application/javascript")

  // Serve CSS files - primary stylesheet
  @cask.get("/styles.css")
  def serveStylesCss(request: cask.Request): Response[Array[Byte]] =
    serveStaticFile(request, "styles.css", "text/css")

  // Serve game.html
  @cask.get("/game.html")
  def serveGameHtml(request: cask.Request): Response[Array[Byte]] =
    serveStaticFile(request, "game.html", "text/html")

  // Serve additional CSS files if needed
  @cask.get("/css/:filename")
  def serveCssFile(filename: String, request: cask.Request): Response[Array[Byte]] =
    if filename.endsWith(".css") then
      serveStaticFile(request, s"css/$filename", "text/css")
    else
      cask.Response(
        data = s"Invalid CSS filename: $filename".getBytes,
        statusCode = 400
      )

  // Generic static file serving with caching
  private def serveStaticFile(request: cask.Request, filename: String, contentType: String): Response[Array[Byte]] =
    // Try multiple locations in order of preference
    val possiblePaths = Seq(
      // 1. Static directory (primary location)
      Paths.get(Config.staticFilesDir, "static", filename),
      // 2. Production/deployment location
      Paths.get(Config.staticFilesDir, filename),
      // 3. Development - fastopt build
      Paths.get(Config.staticFilesDir, "js", "target", "scala-3.7.4", "cascade-fastopt", filename),
      // 4. Development - fullopt build
      Paths.get(Config.staticFilesDir, "js", "target", "scala-3.7.4", "cascade-opt", filename)
    )

    val filePath = possiblePaths.find(Files.exists(_))

    filePath match
      case Some(path) =>
        val lastModified = Files.getLastModifiedTime(path).toMillis
        val fileSize = Files.size(path)
        val etag = s""""${lastModified}-${fileSize}""""

        // Check if client has cached version (ETag validation)
        val clientETag = request.headers.get("if-none-match")
        if clientETag.contains(etag) then
          // File hasn't changed - return 304 Not Modified
          cask.Response(
            data = Array.empty[Byte],
            statusCode = 304,
            headers = Seq(
              "ETag" -> etag,
              "Cache-Control" -> s"public, max-age=${Config.cacheDuration}"
            )
          )
        else
          // File changed or first request - send full content
          val content = Files.readAllBytes(path)
          cask.Response(
            data = content,
            headers = Seq(
              "Content-Type" -> contentType,
              "Cache-Control" -> s"public, max-age=${Config.cacheDuration}",
              "ETag" -> etag,
              "Last-Modified" -> formatHttpDate(lastModified)
            )
          )
      case None =>
        val msg = s"$filename not found. Searched in:\n${possiblePaths.map(p => s"  - ${p.toAbsolutePath}").mkString("\n")}"
        logger.error(msg)
        cask.Response(
          data = msg.getBytes,
          statusCode = 404
        )

  // Format timestamp as HTTP date (RFC 7231)
  private def formatHttpDate(millis: Long): String =
    val instant = java.time.Instant.ofEpochMilli(millis)
    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
      .withZone(java.time.ZoneId.of("GMT"))
      .format(instant)

  override def port: Int = Config.port

  // Log startup configuration
  logger.info("=" * 60)
  logger.info("Starting Cascade Server")
  logger.info(s"Port: ${Config.port}")
  logger.info(s"Static files directory: ${Config.staticFilesDir}")
  logger.info(s"Cache duration: ${Config.cacheDuration}s")
  logger.info(s"Environment: ${if Config.isProd then "production" else "development"}")
  logger.info("=" * 60)

  startCleanupTask()
  initialize()

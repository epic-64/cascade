import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.{CompletionStage, CountDownLatch, TimeUnit}
import scala.util.Try
import scala.util.chaining.*
import upickle.default.*
import shared.ColorRush.*

class ColorRushEndpointSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:

  private val wsClient = HttpClient.newHttpClient()
  private var gameCounter = 0

  override def beforeEach(): Unit =
    super.beforeEach()
    gameCounter += 1

  private def uniqueGameId: String = s"test-game-$testPort-$gameCounter"

  private def wsUrl(gameId: String): String = s"ws://localhost:$testPort/ws/color-rush/$gameId"

  /** Creates a WebSocket listener that collects parsed server messages and signals via latches */
  private def createListener(
    messages: scala.collection.mutable.ArrayBuffer[ServerMessage],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    new WebSocket.Listener:
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
        Try(read[ServerMessage](data.toString)).foreach: msg =>
          messages += msg
          latches.lift(messages.size - 1).foreach(_.countDown())
        webSocket.request(1)
        null

  /** Connects to WebSocket and returns the connection */
  private def connectWebSocket(gameId: String, listener: WebSocket.Listener): WebSocket =
    wsClient.newWebSocketBuilder()
      .buildAsync(URI.create(wsUrl(gameId)), listener)
      .get()
      .tap(_.request(1))

  /** Sends a client message as JSON */
  private def sendMessage(ws: WebSocket, msg: ClientMessage): Unit =
    ws.sendText(write(msg), true).get()

  /** Safely closes a WebSocket connection */
  private def closeWebSocket(ws: WebSocket): Unit =
    Try(ws.sendClose(WebSocket.NORMAL_CLOSURE, "Test completed").get())

  /** Executes a block with WebSocket connections, ensuring cleanup */
  private def withWebSockets[T](connections: WebSocket*)(block: => T): T =
    Try(block)
      .tap(_ => connections.foreach(closeWebSocket))
      .get

  test("player can join a game and receive initial game state"):
    val gameId = uniqueGameId
    val messages = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch = CountDownLatch(1)
    val ws = connectWebSocket(gameId, createListener(messages, Seq(joinLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 5))

      assert(joinLatch.await(5, TimeUnit.SECONDS), "Timeout waiting for game state")

      messages.head match
        case GameUpdateMessage(game) =>
          assert(game.gameId == gameId)
          assert(game.players.size == 1)
          assert(game.players.values.exists(_.name == "Alice"))
          assert(game.status == GameStatus.Waiting)
          assert(game.totalRounds == 5)
        case other =>
          fail(s"Expected GameUpdateMessage, got $other")

  test("multiple players can join the same game"):
    val gameId = uniqueGameId
    val messages1 = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val messages2 = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch1, updateLatch1 = CountDownLatch(1)
    val joinLatch2 = CountDownLatch(1)

    val ws1 = connectWebSocket(gameId, createListener(messages1, Seq(joinLatch1, updateLatch1)))
    val ws2 = connectWebSocket(gameId, createListener(messages2, Seq(joinLatch2)))

    withWebSockets(ws1, ws2):
      // Player 1 joins
      sendMessage(ws1, JoinMessage("Alice", 5))
      assert(joinLatch1.await(5, TimeUnit.SECONDS), "Player 1 didn't receive join confirmation")

      // Player 2 joins
      sendMessage(ws2, JoinMessage("Bob", 5))
      assert(joinLatch2.await(5, TimeUnit.SECONDS), "Player 2 didn't receive join confirmation")

      // Player 1 should receive broadcast of player 2 joining
      assert(updateLatch1.await(5, TimeUnit.SECONDS), "Player 1 didn't receive player 2 join broadcast")

      // Player 2's game state should show both players
      messages2.last match
        case GameUpdateMessage(game) =>
          assert(game.players.size == 2)
          assert(game.players.values.map(_.name).toSet == Set("Alice", "Bob"))
        case other =>
          fail(s"Expected GameUpdateMessage, got $other")

  test("game can be started and enters Playing state"):
    val gameId = uniqueGameId
    val messages = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch, startLatch = CountDownLatch(1)
    val ws = connectWebSocket(gameId, createListener(messages, Seq(joinLatch, startLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      assert(joinLatch.await(5, TimeUnit.SECONDS), "Join failed")

      sendMessage(ws, StartMessage())
      assert(startLatch.await(5, TimeUnit.SECONDS), "Start failed")

      messages.last match
        case GameUpdateMessage(game) =>
          assert(game.status == GameStatus.Playing)
          assert(game.roundNumber == 1)
          assert(game.currentRound.isDefined)
          game.currentRound.foreach: round =>
            assert(round.colorOptions.size == 6)
            assert(round.colorOptions.contains(round.targetColor))
        case other =>
          fail(s"Expected GameUpdateMessage with Playing status, got $other")

  test("clicking correct color wins the round"):
    val gameId = uniqueGameId
    val messages = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch, startLatch, winnerLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(gameId, createListener(messages, Seq(joinLatch, startLatch, winnerLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      assert(joinLatch.await(5, TimeUnit.SECONDS), "Join failed")

      sendMessage(ws, StartMessage())
      assert(startLatch.await(5, TimeUnit.SECONDS), "Start failed")

      // Get the target color from the round
      val targetColor = messages.collectFirst:
        case GameUpdateMessage(game) if game.currentRound.isDefined =>
          game.currentRound.get.targetColor
      .getOrElse(fail("No round started"))

      // Click the correct color
      sendMessage(ws, ClickMessage(targetColor, System.currentTimeMillis()))
      assert(winnerLatch.await(5, TimeUnit.SECONDS), "Winner announcement failed")

      // Should receive RoundWinnerMessage
      val hasWinnerMessage = messages.exists:
        case RoundWinnerMessage(name, points) => name == "Alice" && points > 0
        case _ => false

      assert(hasWinnerMessage, s"Expected RoundWinnerMessage for Alice, got: ${messages}")

  test("game configuration can be changed before starting"):
    val gameId = uniqueGameId
    val messages = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch, configLatch = CountDownLatch(1)
    val ws = connectWebSocket(gameId, createListener(messages, Seq(joinLatch, configLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 5))
      assert(joinLatch.await(5, TimeUnit.SECONDS), "Join failed")

      // Change configuration
      sendMessage(ws, ConfigureMessage(10))
      assert(configLatch.await(5, TimeUnit.SECONDS), "Configure failed")

      messages.last match
        case GameUpdateMessage(game) =>
          assert(game.totalRounds == 10)
        case other =>
          fail(s"Expected GameUpdateMessage with updated config, got $other")

  test("player disconnect updates game state for remaining players"):
    val gameId = uniqueGameId
    val messages1 = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val messages2 = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    val joinLatch1, updateLatch1, disconnectLatch1 = CountDownLatch(1)
    val joinLatch2 = CountDownLatch(1)

    val ws1 = connectWebSocket(gameId, createListener(messages1, Seq(joinLatch1, updateLatch1, disconnectLatch1)))
    val ws2 = connectWebSocket(gameId, createListener(messages2, Seq(joinLatch2)))

    // Don't use withWebSockets here since we need to close ws2 mid-test
    Try:
      sendMessage(ws1, JoinMessage("Alice", 5))
      assert(joinLatch1.await(5, TimeUnit.SECONDS), "Player 1 join failed")

      sendMessage(ws2, JoinMessage("Bob", 5))
      assert(joinLatch2.await(5, TimeUnit.SECONDS), "Player 2 join failed")
      assert(updateLatch1.await(5, TimeUnit.SECONDS), "Player 1 didn't receive player 2 join")

      // Verify both players are in the game
      val gameWithBothPlayers = messages1.collect { case GameUpdateMessage(g) => g }.last
      assert(gameWithBothPlayers.players.size == 2)

      // Player 2 disconnects
      closeWebSocket(ws2)

      // Player 1 should receive updated state
      assert(disconnectLatch1.await(5, TimeUnit.SECONDS), "Player 1 didn't receive disconnect update")

      val finalGame = messages1.collect { case GameUpdateMessage(g) => g }.last
      assert(finalGame.players.size == 1)
      assert(finalGame.players.values.exists(_.name == "Alice"))
    .tap(_ => closeWebSocket(ws1))
    .get

  test("NextRoundMessage advances game after round end"):
    val gameId = uniqueGameId
    val messages = scala.collection.mutable.ArrayBuffer[ServerMessage]()
    // join, start, winner, roundEnd update, nextRound update
    val latches = (1 to 5).map(_ => CountDownLatch(1))
    val ws = connectWebSocket(gameId, createListener(messages, latches))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      assert(latches(0).await(5, TimeUnit.SECONDS), "Join failed")

      sendMessage(ws, StartMessage())
      assert(latches(1).await(5, TimeUnit.SECONDS), "Start failed")

      // Get target color and win the round
      val targetColor = messages.collectFirst:
        case GameUpdateMessage(game) if game.currentRound.isDefined =>
          game.currentRound.get.targetColor
      .getOrElse(fail("No round started"))

      sendMessage(ws, ClickMessage(targetColor, System.currentTimeMillis()))
      assert(latches(2).await(5, TimeUnit.SECONDS), "Winner message failed")
      assert(latches(3).await(5, TimeUnit.SECONDS), "Round end update failed")

      // Request next round
      sendMessage(ws, NextRoundMessage())
      assert(latches(4).await(5, TimeUnit.SECONDS), "Next round failed")

      val lastGame = messages.collect { case GameUpdateMessage(g) => g }.last
      assert(lastGame.roundNumber == 2 || lastGame.status == GameStatus.Playing,
        s"Expected round 2 or Playing status, got round ${lastGame.roundNumber} with status ${lastGame.status}")


import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import java.net.http.WebSocket
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.util.Try
import scala.util.chaining.*
import upickle.default.*
import shared.ColorRush.*

class ColorRushEndpointSpec extends AnyFunSuite with TestServerHelper with WebSocketTestHelper with BeforeAndAfterEach:

  private var gameCounter = 0

  override def beforeEach(): Unit =
    super.beforeEach()
    gameCounter += 1

  private def uniqueGameId: String = s"test-game-$testPort-$gameCounter"

  private def wsUrl(gameId: String): String = s"ws://localhost:$testPort/ws/color-rush/$gameId"

  /** Creates a listener that parses ColorRush ServerMessages */
  private def createGameListener(
    messages: ConcurrentLinkedQueue[ServerMessage],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    createParsedListener(messages, latches)(read[ServerMessage](_))

  /** Sends a client message as JSON */
  private def sendMessage(ws: WebSocket, msg: ClientMessage): Unit =
    ws.sendText(write(msg), true).get()


  test("player can join a game and receive initial game state"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    val joinedLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, Seq(joinedLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 5))
      awaitLatch(joinedLatch, "Timeout waiting for JoinedMessage")
      awaitLatch(updateLatch, "Timeout waiting for game state")

      // First message should be JoinedMessage
      messages.toSeq.head match
        case JoinedMessage(playerId, gId) =>
          assert(gId == gameId)
          assert(playerId.nonEmpty)
        case other =>
          fail(s"Expected JoinedMessage, got $other")

      // Second message should be GameUpdateMessage
      messages.toSeq.collect { case GameUpdateMessage(g) => g }.headOption match
        case Some(game) =>
          assert(game.gameId == gameId)
          assert(game.players.size == 1)
          assert(game.players.values.exists(_.name == "Alice"))
          assert(game.status == GameStatus.Waiting)
          assert(game.totalRounds == 5)
        case None =>
          fail(s"Expected GameUpdateMessage, got ${messages.toSeq}")

  test("multiple players can join the same game"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, then GameUpdate when player 2 joins
    val joined1, update1a, update1b = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate
    val joined2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b)))
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2)))

    withWebSockets(ws1, ws2):
      // Player 1 joins
      sendMessage(ws1, JoinMessage("Alice", 5))
      awaitLatch(joined1, "Player 1 didn't receive JoinedMessage")
      awaitLatch(update1a, "Player 1 didn't receive initial game state")

      // Player 2 joins - this triggers broadcast to both players
      sendMessage(ws2, JoinMessage("Bob", 5))
      
      // Wait for both players to receive the updated game state with 2 players
      awaitLatch(update1b, "Player 1 didn't receive player 2 join broadcast")
      awaitLatch(joined2, "Player 2 didn't receive JoinedMessage")
      awaitLatch(update2, "Player 2 didn't receive game state")

      // Both players should now see both players in their last message
      val game1 = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      val game2 = messages2.toSeq.collect { case GameUpdateMessage(g) => g }.last
      
      assert(game1.players.size == 2, s"Player 1 should see 2 players, got ${game1.players.size}")
      assert(game2.players.size == 2, s"Player 2 should see 2 players, got ${game2.players.size}")
      assert(game1.players.values.map(_.name).toSet == Set("Alice", "Bob"))
      assert(game2.players.values.map(_.name).toSet == Set("Alice", "Bob"))

  test("game can be started and enters Playing state"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    val joinedLatch, joinUpdateLatch, startLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, Seq(joinedLatch, joinUpdateLatch, startLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      awaitLatch(joinedLatch, "JoinedMessage failed")
      awaitLatch(joinUpdateLatch, "Join update failed")

      sendMessage(ws, StartMessage())
      awaitLatch(startLatch, "Start failed")

      messages.toSeq.collect { case GameUpdateMessage(g) => g }.last match
        case game =>
          assert(game.status == GameStatus.Playing)
          assert(game.roundNumber == 1)
          assert(game.currentRound.isDefined)
          game.currentRound.foreach: round =>
            assert(round.colorOptions.size == 6)
            assert(round.colorOptions.contains(round.targetColor))

  test("clicking correct color wins the round"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    val joinedLatch, joinUpdateLatch, startLatch, winnerLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, Seq(joinedLatch, joinUpdateLatch, startLatch, winnerLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      awaitLatch(joinedLatch, "JoinedMessage failed")
      awaitLatch(joinUpdateLatch, "Join update failed")

      sendMessage(ws, StartMessage())
      awaitLatch(startLatch, "Start failed")

      // Get the target color from the round
      val targetColor = messages.toSeq.collectFirst:
        case GameUpdateMessage(game) if game.currentRound.isDefined =>
          game.currentRound.get.targetColor
      .getOrElse(fail("No round started"))

      // Click the correct color
      sendMessage(ws, ClickMessage(targetColor, System.currentTimeMillis()))
      awaitLatch(winnerLatch, "Winner announcement failed")

      // Should receive RoundWinnerMessage
      val hasWinnerMessage = messages.toSeq.exists:
        case RoundWinnerMessage(name, points) => name == "Alice" && points > 0
        case _ => false

      assert(hasWinnerMessage, s"Expected RoundWinnerMessage for Alice, got: ${messages.toSeq}")

  test("player disconnect updates game state for remaining players"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, GameUpdate (player 2 joins), GameUpdate (player 2 disconnects)
    val joined1, update1a, update1b, disconnectLatch1 = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate
    val joined2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b, disconnectLatch1)))
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2)))

    // Don't use withWebSockets here since we need to close ws2 mid-test
    Try:
      sendMessage(ws1, JoinMessage("Alice", 5))
      awaitLatch(joined1, "Player 1 JoinedMessage failed")
      awaitLatch(update1a, "Player 1 join update failed")

      sendMessage(ws2, JoinMessage("Bob", 5))
      awaitLatch(joined2, "Player 2 JoinedMessage failed")
      awaitLatch(update2, "Player 2 join update failed")
      awaitLatch(update1b, "Player 1 didn't receive player 2 join")

      // Verify both players are in the game
      val gameWithBothPlayers = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(gameWithBothPlayers.players.size == 2)

      // Player 2 disconnects
      closeWebSocket(ws2)

      // Player 1 should receive updated state
      awaitLatch(disconnectLatch1, "Player 1 didn't receive disconnect update")

      val finalGame = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      // Player is marked as disconnected but still in the game (grace period)
      assert(finalGame.players.size == 2, "Both players should still be in game during grace period")
      assert(finalGame.players.values.exists(p => p.name == "Alice" && p.connected))
      assert(finalGame.players.values.exists(p => p.name == "Bob" && !p.connected))
    .tap(_ => closeWebSocket(ws1))
    .get

  test("NextRoundMessage advances game after round end"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    // joined, join update, start, winner, roundEnd update, nextRound update
    val latches = (1 to 6).map(_ => CountDownLatch(1))
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, latches))

    withWebSockets(ws):
      sendMessage(ws, JoinMessage("Alice", 3))
      awaitLatch(latches(0), "JoinedMessage failed")
      awaitLatch(latches(1), "Join update failed")

      sendMessage(ws, StartMessage())
      awaitLatch(latches(2), "Start failed")

      // Get target color and win the round
      val targetColor = messages.toSeq.collectFirst:
        case GameUpdateMessage(game) if game.currentRound.isDefined =>
          game.currentRound.get.targetColor
      .getOrElse(fail("No round started"))

      sendMessage(ws, ClickMessage(targetColor, System.currentTimeMillis()))
      awaitLatch(latches(3), "Winner message failed")
      awaitLatch(latches(4), "Round end update failed")

      // Request next round
      sendMessage(ws, NextRoundMessage())
      awaitLatch(latches(5), "Next round failed")

      val lastGame = messages.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(lastGame.roundNumber == 2 || lastGame.status == GameStatus.Playing,
        s"Expected round 2 or Playing status, got round ${lastGame.roundNumber} with status ${lastGame.status}")


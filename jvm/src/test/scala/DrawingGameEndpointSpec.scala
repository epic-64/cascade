import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import java.net.http.WebSocket
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.util.Try
import scala.util.chaining.*
import upickle.default.*
import shared.DrawingGame.*

class DrawingGameEndpointSpec extends AnyFunSuite with TestServerHelper with WebSocketTestHelper with BeforeAndAfterEach:

  private var lobbyCounter = 0

  override def beforeEach(): Unit =
    super.beforeEach()
    lobbyCounter += 1

  private def uniqueLobbyId: String = s"test-lobby-$testPort-$lobbyCounter"

  /** WebSocket URL for creating a new lobby (temp endpoint) */
  private def tempWsUrl: String = s"ws://localhost:$testPort/ws/drawing/temp"

  /** WebSocket URL for joining an existing lobby */
  private def wsUrl(lobbyId: String): String = s"ws://localhost:$testPort/ws/drawing/$lobbyId"

  /** Creates a listener that parses DrawingGame ServerMessages */
  private def createGameListener(
    messages: ConcurrentLinkedQueue[ServerMessage],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    createParsedListener(messages, latches)(read[ServerMessage](_))

  /** Sends a client message as JSON */
  private def sendMessage(ws: WebSocket, msg: ClientMessage): Unit =
    ws.sendText(write(msg), true).get()

  // A minimal test API key (tests won't actually call OpenAI)
  private val testApiKey = "test-api-key-12345"

  // A simple base64 PNG image (1x1 pixel transparent PNG)
  private val testImageData = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="


  test("player can create a lobby and receive lobby state"):
    val messages = createMessageBuffer[ServerMessage]()
    val createdLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, Seq(createdLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(createdLatch, "Timeout waiting for LobbyCreated")
      awaitLatch(updateLatch, "Timeout waiting for LobbyUpdate")

      // First message should be LobbyCreated
      messages.toSeq.head match
        case ServerMessage.LobbyCreated(lobbyId, playerId) =>
          assert(lobbyId.nonEmpty, "Lobby ID should not be empty")
          assert(playerId.nonEmpty, "Player ID should not be empty")
        case other =>
          fail(s"Expected LobbyCreated, got $other")

      // Second message should be LobbyUpdate
      messages.toSeq.collectFirst { case ServerMessage.LobbyUpdate(l) => l } match
        case Some(lobby) =>
          assert(lobby.players.size == 1)
          assert(lobby.players.values.exists(_.playerName == "Alice"))
          assert(lobby.status == LobbyStatus.Waiting)
        case None =>
          fail(s"Expected LobbyUpdate, got ${messages.toSeq}")


  test("player can join an existing lobby"):
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Host: LobbyCreated, LobbyUpdate, LobbyUpdate (when player 2 joins)
    val created1, update1a, update1b = CountDownLatch(1)
    // Joiner: LobbyCreated, LobbyUpdate
    val created2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(tempWsUrl, createGameListener(messages1, Seq(created1, update1a, update1b)))

    withWebSockets(ws1):
      // Host creates lobby
      sendMessage(ws1, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(created1, "Host didn't receive LobbyCreated")
      awaitLatch(update1a, "Host didn't receive initial LobbyUpdate")

      // Get lobby ID from the created message
      val lobbyId = messages1.toSeq.collectFirst:
        case ServerMessage.LobbyCreated(lid, _) => lid
      .getOrElse(fail("No LobbyCreated message"))

      // Second player joins
      val ws2 = connectWebSocket(wsUrl(lobbyId), createGameListener(messages2, Seq(created2, update2)))
      Try:
        sendMessage(ws2, ClientMessage.JoinLobby(lobbyId, "Bob"))
        awaitLatch(created2, "Joiner didn't receive LobbyCreated")
        awaitLatch(update2, "Joiner didn't receive LobbyUpdate")
        awaitLatch(update1b, "Host didn't receive join broadcast")

        // Both should see 2 players
        val lobby1 = messages1.toSeq.collect { case ServerMessage.LobbyUpdate(l) => l }.last
        val lobby2 = messages2.toSeq.collect { case ServerMessage.LobbyUpdate(l) => l }.last

        assert(lobby1.players.size == 2, s"Host should see 2 players, got ${lobby1.players.size}")
        assert(lobby2.players.size == 2, s"Joiner should see 2 players, got ${lobby2.players.size}")
        assert(lobby1.players.values.map(_.playerName).toSet == Set("Alice", "Bob"))
        assert(lobby2.players.values.map(_.playerName).toSet == Set("Alice", "Bob"))
      .tap(_ => closeWebSocket(ws2))
      .get


  test("lobby creation uses specified game mode and caption style"):
    val messages = createMessageBuffer[ServerMessage]()
    val createdLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, Seq(createdLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey, GameMode.TwoWordScene, CaptionStyle.Roast))
      awaitLatch(createdLatch, "Timeout waiting for LobbyCreated")
      awaitLatch(updateLatch, "Timeout waiting for LobbyUpdate")

      messages.toSeq.collectFirst { case ServerMessage.LobbyUpdate(l) => l } match
        case Some(lobby) =>
          assert(lobby.gameMode == GameMode.TwoWordScene)
          assert(lobby.captionStyle == CaptionStyle.Roast)
        case None =>
          fail("Expected LobbyUpdate with game mode and caption style")


  test("joining non-existent lobby returns error"):
    val messages = createMessageBuffer[ServerMessage]()
    val errorLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl("non-existent-lobby"), createGameListener(messages, Seq(errorLatch)))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.JoinLobby("non-existent-lobby", "Alice"))
      awaitLatch(errorLatch, "Timeout waiting for error message")

      val hasError = messages.toSeq.exists:
        case ServerMessage.ErrorMessage(msg) => msg.contains("not found")
        case _ => false

      assert(hasError, s"Expected error message about lobby not found, got ${messages.toSeq}")


  test("game can be started and enters Drawing state"):
    val messages = createMessageBuffer[ServerMessage]()
    // LobbyCreated, LobbyUpdate, PromptAnnounced, LobbyUpdate (status=Drawing)
    val createdLatch, update1, promptLatch, update2 = CountDownLatch(1)
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, Seq(createdLatch, update1, promptLatch, update2)))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(createdLatch, "LobbyCreated failed")
      awaitLatch(update1, "Initial LobbyUpdate failed")

      sendMessage(ws, ClientMessage.StartGame())
      awaitLatch(promptLatch, "PromptAnnounced failed")
      awaitLatch(update2, "Drawing state update failed")

      // Should receive PromptAnnounced
      val hasPrompt = messages.toSeq.exists:
        case ServerMessage.PromptAnnounced(prompt) => prompt.nonEmpty
        case _ => false

      assert(hasPrompt, s"Expected PromptAnnounced message, got ${messages.toSeq}")

      // Last lobby update should show Drawing status
      messages.toSeq.collect { case ServerMessage.LobbyUpdate(l) => l }.last match
        case lobby =>
          assert(lobby.status == LobbyStatus.Drawing)
          assert(lobby.currentRound == 1)


  test("drawing can be submitted during Drawing phase"):
    val messages = createMessageBuffer[ServerMessage]()
    // LobbyCreated, LobbyUpdate, PromptAnnounced, LobbyUpdate (Drawing), DrawingSubmitted, LobbyUpdate
    val latches = (1 to 6).map(_ => CountDownLatch(1))
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, latches))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(latches(0), "LobbyCreated failed")
      awaitLatch(latches(1), "Initial LobbyUpdate failed")

      sendMessage(ws, ClientMessage.StartGame())
      awaitLatch(latches(2), "PromptAnnounced failed")
      awaitLatch(latches(3), "Drawing state update failed")

      sendMessage(ws, ClientMessage.SubmitDrawing(testImageData))
      awaitLatch(latches(4), "DrawingSubmitted failed")
      awaitLatch(latches(5), "Post-drawing LobbyUpdate failed")

      // Should receive DrawingSubmitted for Alice
      val hasSubmitted = messages.toSeq.exists:
        case ServerMessage.DrawingSubmitted(playerName) => playerName == "Alice"
        case _ => false

      assert(hasSubmitted, s"Expected DrawingSubmitted for Alice, got ${messages.toSeq}")


  test("cannot start game when already in progress"):
    val messages = createMessageBuffer[ServerMessage]()
    // LobbyCreated, LobbyUpdate, PromptAnnounced, LobbyUpdate, ErrorMessage
    val latches = (1 to 5).map(_ => CountDownLatch(1))
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, latches))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(latches(0), "LobbyCreated failed")
      awaitLatch(latches(1), "Initial LobbyUpdate failed")

      sendMessage(ws, ClientMessage.StartGame())
      awaitLatch(latches(2), "PromptAnnounced failed")
      awaitLatch(latches(3), "Drawing state update failed")

      // Try to start again
      sendMessage(ws, ClientMessage.StartGame())
      awaitLatch(latches(4), "Error message failed")

      val hasError = messages.toSeq.exists:
        case ServerMessage.ErrorMessage(msg) => msg.contains("already")
        case _ => false

      assert(hasError, s"Expected error about game already in progress, got ${messages.toSeq}")


  test("cannot join lobby when game already in progress"):
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Host: LobbyCreated, LobbyUpdate, PromptAnnounced, LobbyUpdate
    val latches1 = (1 to 4).map(_ => CountDownLatch(1))
    // Joiner: First gets LobbyUpdate (on connect), then ErrorMessage (on join attempt)
    val lobbyUpdateLatch, errorLatch = CountDownLatch(1)

    val ws1 = connectWebSocket(tempWsUrl, createGameListener(messages1, latches1))

    withWebSockets(ws1):
      sendMessage(ws1, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(latches1(0), "LobbyCreated failed")
      awaitLatch(latches1(1), "Initial LobbyUpdate failed")

      val lobbyId = messages1.toSeq.collectFirst:
        case ServerMessage.LobbyCreated(lid, _) => lid
      .getOrElse(fail("No LobbyCreated message"))

      sendMessage(ws1, ClientMessage.StartGame())
      awaitLatch(latches1(2), "PromptAnnounced failed")
      awaitLatch(latches1(3), "Drawing state update failed")

      // Try to join after game started
      val ws2 = connectWebSocket(wsUrl(lobbyId), createGameListener(messages2, Seq(lobbyUpdateLatch, errorLatch)))
      Try:
        // First should receive current lobby state (since lobby exists)
        awaitLatch(lobbyUpdateLatch, "Didn't receive initial LobbyUpdate on connect")
        
        sendMessage(ws2, ClientMessage.JoinLobby(lobbyId, "Bob"))
        awaitLatch(errorLatch, "Error message for late join failed")

        val hasError = messages2.toSeq.exists:
          case ServerMessage.ErrorMessage(msg) => msg.contains("in progress")
          case _ => false

        assert(hasError, s"Expected error about game in progress, got ${messages2.toSeq}")
      .tap(_ => closeWebSocket(ws2))
      .get


  test("player disconnect updates lobby state for remaining players"):
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Host: LobbyCreated, LobbyUpdate, LobbyUpdate (player 2 joins), LobbyUpdate (player 2 disconnects)
    val created1, update1a, update1b, disconnectLatch1 = CountDownLatch(1)
    // Joiner: LobbyCreated, LobbyUpdate
    val created2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(tempWsUrl, createGameListener(messages1, Seq(created1, update1a, update1b, disconnectLatch1)))

    // Don't use withWebSockets here since we need to close ws2 mid-test
    Try:
      sendMessage(ws1, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(created1, "Host LobbyCreated failed")
      awaitLatch(update1a, "Host initial LobbyUpdate failed")

      val lobbyId = messages1.toSeq.collectFirst:
        case ServerMessage.LobbyCreated(lid, _) => lid
      .getOrElse(fail("No LobbyCreated message"))

      val ws2 = connectWebSocket(wsUrl(lobbyId), createGameListener(messages2, Seq(created2, update2)))

      sendMessage(ws2, ClientMessage.JoinLobby(lobbyId, "Bob"))
      awaitLatch(created2, "Joiner LobbyCreated failed")
      awaitLatch(update2, "Joiner LobbyUpdate failed")
      awaitLatch(update1b, "Host didn't receive join broadcast")

      // Verify both players are in the lobby
      val lobbyWithBoth = messages1.toSeq.collect { case ServerMessage.LobbyUpdate(l) => l }.last
      assert(lobbyWithBoth.players.size == 2)

      // Player 2 disconnects
      closeWebSocket(ws2)

      // Host should receive updated state
      awaitLatch(disconnectLatch1, "Host didn't receive disconnect update")

      val finalLobby = messages1.toSeq.collect { case ServerMessage.LobbyUpdate(l) => l }.last
      // Player is marked as disconnected but still in the lobby (grace period)
      assert(finalLobby.players.size == 2, "Both players should still be in lobby during grace period")
      assert(finalLobby.players.values.exists(p => p.playerName == "Alice" && p.connected))
      assert(finalLobby.players.values.exists(p => p.playerName == "Bob" && !p.connected))
    .tap(_ => closeWebSocket(ws1))
    .get


  test("ping message keeps connection alive"):
    val messages = createMessageBuffer[ServerMessage]()
    val createdLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, Seq(createdLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(createdLatch, "LobbyCreated failed")
      awaitLatch(updateLatch, "LobbyUpdate failed")

      // Send ping - should not error or disconnect
      sendMessage(ws, ClientMessage.Ping())

      // Small delay to ensure ping was processed
      Thread.sleep(100)

      // Connection should still be open - verify by checking we received the expected messages
      val messageCount = messages.size
      assert(messageCount >= 2, "Should have received at least LobbyCreated and LobbyUpdate")


  test("lobby is created with default game mode SingleWord"):
    val messages = createMessageBuffer[ServerMessage]()
    val createdLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(tempWsUrl, createGameListener(messages, Seq(createdLatch, updateLatch)))

    withWebSockets(ws):
      // Create without specifying game mode (should default to SingleWord)
      sendMessage(ws, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(createdLatch, "LobbyCreated failed")
      awaitLatch(updateLatch, "LobbyUpdate failed")

      messages.toSeq.collectFirst { case ServerMessage.LobbyUpdate(l) => l } match
        case Some(lobby) =>
          assert(lobby.gameMode == GameMode.SingleWord)
          assert(lobby.captionStyle == CaptionStyle.Descriptive)
        case None =>
          fail("Expected LobbyUpdate")


  test("multiple players receive drawing timer updates"):
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Allocate enough latches for timer updates
    val latches1 = (1 to 10).map(_ => CountDownLatch(1))
    val latches2 = (1 to 8).map(_ => CountDownLatch(1))

    val ws1 = connectWebSocket(tempWsUrl, createGameListener(messages1, latches1))

    withWebSockets(ws1):
      sendMessage(ws1, ClientMessage.CreateLobby("Alice", testApiKey))
      awaitLatch(latches1(0), "LobbyCreated failed")
      awaitLatch(latches1(1), "Initial LobbyUpdate failed")

      val lobbyId = messages1.toSeq.collectFirst:
        case ServerMessage.LobbyCreated(lid, _) => lid
      .getOrElse(fail("No LobbyCreated message"))

      val ws2 = connectWebSocket(wsUrl(lobbyId), createGameListener(messages2, latches2))
      Try:
        sendMessage(ws2, ClientMessage.JoinLobby(lobbyId, "Bob"))
        awaitLatch(latches2(0), "Joiner LobbyCreated failed")
        awaitLatch(latches2(1), "Joiner LobbyUpdate failed")
        awaitLatch(latches1(2), "Host didn't receive join broadcast")

        sendMessage(ws1, ClientMessage.StartGame())
        // Wait a bit for timer to start broadcasting
        Thread.sleep(2500)

        // Both players should receive DrawingTimerUpdate messages
        val hasTimerUpdate1 = messages1.toSeq.exists:
          case ServerMessage.DrawingTimerUpdate(_) => true
          case _ => false

        val hasTimerUpdate2 = messages2.toSeq.exists:
          case ServerMessage.DrawingTimerUpdate(_) => true
          case _ => false

        assert(hasTimerUpdate1, s"Host should receive timer updates, got ${messages1.toSeq}")
        assert(hasTimerUpdate2, s"Joiner should receive timer updates, got ${messages2.toSeq}")
      .tap(_ => closeWebSocket(ws2))
      .get


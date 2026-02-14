import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import java.net.http.WebSocket
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.util.Try
import scala.util.chaining.*
import upickle.default.*
import shared.TugOfWar.*

class TugOfWarEndpointSpec extends AnyFunSuite with TestServerHelper with WebSocketTestHelper with BeforeAndAfterEach:

  private var gameCounter = 0

  override def beforeEach(): Unit =
    super.beforeEach()
    gameCounter += 1

  private def uniqueGameId: String = s"tow-test-$testPort-$gameCounter"

  private def wsUrl(gameId: String): String = s"ws://localhost:$testPort/ws/tug-of-war/$gameId"

  /** Creates a listener that parses TugOfWar ServerMessages */
  private def createGameListener(
    messages: ConcurrentLinkedQueue[ServerMessage],
    latches: Seq[CountDownLatch]
  ): WebSocket.Listener =
    createParsedListener(messages, latches)(read[ServerMessage](_))

  /** Sends a client message as JSON */
  private def sendMessage(ws: WebSocket, msg: ClientMessage): Unit =
    ws.sendText(write(msg), true).get()


  test("player can create a game and receive initial game state"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    val joinedLatch, updateLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, Seq(joinedLatch, updateLatch)))

    withWebSockets(ws):
      sendMessage(ws, CreateMessage("Alice", 3, 20))
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
      messages.toSeq.collectFirst { case GameUpdateMessage(g) => g } match
        case Some(game) =>
          assert(game.gameId == gameId)
          assert(game.players.size == 1)
          assert(game.players.values.exists(_.name == "Alice"))
          assert(game.status == GameStatus.Waiting)
          assert(game.roundsToWin == 3)
          assert(game.timeLimitSeconds == 20)
        case None =>
          fail(s"Expected GameUpdateMessage, got ${messages.toSeq}")


  test("player can join an existing game"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, then GameUpdate when player 2 joins
    val joined1, update1a, update1b = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate
    val joined2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b)))

    // Player 1 creates game
    sendMessage(ws1, CreateMessage("Alice", 3, 20))
    awaitLatch(joined1, "Player 1 didn't receive JoinedMessage")
    awaitLatch(update1a, "Player 1 didn't receive initial game state")

    // Player 2 joins
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2)))

    withWebSockets(ws1, ws2):
      sendMessage(ws2, JoinMessage("Bob"))

      awaitLatch(update1b, "Player 1 didn't receive player 2 join broadcast")
      awaitLatch(joined2, "Player 2 didn't receive JoinedMessage")
      awaitLatch(update2, "Player 2 didn't receive game state")

      val game1 = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      val game2 = messages2.toSeq.collect { case GameUpdateMessage(g) => g }.last

      assert(game1.players.size == 2, s"Player 1 should see 2 players, got ${game1.players.size}")
      assert(game2.players.size == 2, s"Player 2 should see 2 players, got ${game2.players.size}")
      assert(game1.players.values.map(_.name).toSet == Set("Alice", "Bob"))


  test("player can select a team"):
    val gameId = uniqueGameId
    val messages = createMessageBuffer[ServerMessage]()
    val joinedLatch, updateLatch, teamSelectLatch = CountDownLatch(1)
    val ws = connectWebSocket(wsUrl(gameId), createGameListener(messages, Seq(joinedLatch, updateLatch, teamSelectLatch)))

    withWebSockets(ws):
      sendMessage(ws, CreateMessage("Alice", 3, 20))
      awaitLatch(joinedLatch, "JoinedMessage failed")
      awaitLatch(updateLatch, "Game update failed")

      sendMessage(ws, SelectTeamMessage(Team.Red))
      awaitLatch(teamSelectLatch, "Team select failed")

      val lastGame = messages.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(lastGame.players.values.exists(p => p.name == "Alice" && p.team.contains(Team.Red)))


  test("LeaveMessage removes player immediately from player list"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, GameUpdate (player 2 joins), GameUpdate (player 2 leaves)
    val joined1, update1a, update1b, leaveLatch1 = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate
    val joined2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b, leaveLatch1)))

    // Player 1 creates game
    sendMessage(ws1, CreateMessage("Alice", 3, 20))
    awaitLatch(joined1, "Player 1 JoinedMessage failed")
    awaitLatch(update1a, "Player 1 join update failed")

    // Player 2 joins
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2)))

    Try:
      sendMessage(ws2, JoinMessage("Bob"))
      awaitLatch(joined2, "Player 2 JoinedMessage failed")
      awaitLatch(update2, "Player 2 join update failed")
      awaitLatch(update1b, "Player 1 didn't receive player 2 join")

      // Verify both players are in the game
      val gameWithBothPlayers = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(gameWithBothPlayers.players.size == 2)
      assert(gameWithBothPlayers.players.values.map(_.name).toSet == Set("Alice", "Bob"))

      // Player 2 explicitly leaves via LeaveMessage
      sendMessage(ws2, LeaveMessage())

      // Player 1 should receive updated state with player 2 removed
      awaitLatch(leaveLatch1, "Player 1 didn't receive leave update")

      val finalGame = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last

      // Player 2 should be completely removed (not just disconnected)
      assert(finalGame.players.size == 1, s"Expected 1 player after leave, got ${finalGame.players.size}")
      assert(finalGame.players.values.exists(_.name == "Alice"), "Alice should still be in game")
      assert(!finalGame.players.values.exists(_.name == "Bob"), "Bob should be removed after LeaveMessage")
    .tap(_ => closeWebSocket(ws1))
    .tap(_ => closeWebSocket(ws2))
    .get


  test("LeaveMessage removes player with team assignment"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, GameUpdate (team select), GameUpdate (p2 joins), GameUpdate (p2 team), GameUpdate (p2 leaves)
    val joined1, update1a, team1, update1b, team2update, leaveLatch1 = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate, GameUpdate (team select)
    val joined2, update2, team2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, team1, update1b, team2update, leaveLatch1)))

    // Player 1 creates game and selects team
    sendMessage(ws1, CreateMessage("Alice", 3, 20))
    awaitLatch(joined1, "Player 1 JoinedMessage failed")
    awaitLatch(update1a, "Player 1 join update failed")

    sendMessage(ws1, SelectTeamMessage(Team.Red))
    awaitLatch(team1, "Player 1 team select failed")

    // Player 2 joins and selects team
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2, team2)))

    Try:
      sendMessage(ws2, JoinMessage("Bob"))
      awaitLatch(joined2, "Player 2 JoinedMessage failed")
      awaitLatch(update2, "Player 2 join update failed")
      awaitLatch(update1b, "Player 1 didn't receive player 2 join")

      sendMessage(ws2, SelectTeamMessage(Team.Blue))
      awaitLatch(team2, "Player 2 team select failed")
      awaitLatch(team2update, "Player 1 didn't receive player 2 team select")

      // Verify both players are in the game with teams
      val gameWithTeams = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(gameWithTeams.players.size == 2)
      assert(gameWithTeams.players.values.exists(p => p.name == "Alice" && p.team.contains(Team.Red)))
      assert(gameWithTeams.players.values.exists(p => p.name == "Bob" && p.team.contains(Team.Blue)))

      // Player 2 explicitly leaves
      sendMessage(ws2, LeaveMessage())
      awaitLatch(leaveLatch1, "Player 1 didn't receive leave update")

      val finalGame = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last

      // Player 2 should be completely removed even though they had a team
      assert(finalGame.players.size == 1, s"Expected 1 player after leave, got ${finalGame.players.size}")
      assert(!finalGame.players.values.exists(_.name == "Bob"), "Bob should be removed after LeaveMessage")
      assert(finalGame.players.values.exists(p => p.name == "Alice" && p.team.contains(Team.Red)))
    .tap(_ => closeWebSocket(ws1))
    .tap(_ => closeWebSocket(ws2))
    .get


  test("LeaveMessage differs from disconnect - disconnect marks player as disconnected"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1: JoinedMessage, GameUpdate, GameUpdate (player 2 joins), GameUpdate (player 2 disconnects)
    val joined1, update1a, update1b, disconnectLatch1 = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate
    val joined2, update2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b, disconnectLatch1)))

    // Player 1 creates game
    sendMessage(ws1, CreateMessage("Alice", 3, 20))
    awaitLatch(joined1, "Player 1 JoinedMessage failed")
    awaitLatch(update1a, "Player 1 join update failed")

    // Player 2 joins
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2)))

    Try:
      sendMessage(ws2, JoinMessage("Bob"))
      awaitLatch(joined2, "Player 2 JoinedMessage failed")
      awaitLatch(update2, "Player 2 join update failed")
      awaitLatch(update1b, "Player 1 didn't receive player 2 join")

      // Verify both players are in the game
      val gameWithBothPlayers = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last
      assert(gameWithBothPlayers.players.size == 2)

      // Player 2 disconnects (closes WebSocket without LeaveMessage)
      closeWebSocket(ws2)

      // Player 1 should receive updated state
      awaitLatch(disconnectLatch1, "Player 1 didn't receive disconnect update")

      val finalGame = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.last

      // Player is marked as disconnected but still in the game (grace period)
      assert(finalGame.players.size == 2, "Both players should still be in game during grace period")
      assert(finalGame.players.values.exists(p => p.name == "Alice" && p.connected))
      assert(finalGame.players.values.exists(p => p.name == "Bob" && !p.connected),
        "Bob should be marked as disconnected, not removed")
    .tap(_ => closeWebSocket(ws1))
    .get


  test("host leaving reassigns host to another player"):
    val gameId = uniqueGameId
    val messages1 = createMessageBuffer[ServerMessage]()
    val messages2 = createMessageBuffer[ServerMessage]()
    // Player 1 (host): JoinedMessage, GameUpdate, GameUpdate (player 2 joins)
    val joined1, update1a, update1b = CountDownLatch(1)
    // Player 2: JoinedMessage, GameUpdate, GameUpdate (player 1 leaves)
    val joined2, update2, leaveLatch2 = CountDownLatch(1)

    val ws1 = connectWebSocket(wsUrl(gameId), createGameListener(messages1, Seq(joined1, update1a, update1b)))

    // Player 1 creates game (becomes host)
    sendMessage(ws1, CreateMessage("Alice", 3, 20))
    awaitLatch(joined1, "Player 1 JoinedMessage failed")
    awaitLatch(update1a, "Player 1 join update failed")

    // Verify Alice is host
    val initialGame = messages1.toSeq.collect { case GameUpdateMessage(g) => g }.head
    val aliceId = initialGame.players.keys.head
    assert(initialGame.hostId.contains(aliceId), "Alice should be host")

    // Player 2 joins
    val ws2 = connectWebSocket(wsUrl(gameId), createGameListener(messages2, Seq(joined2, update2, leaveLatch2)))

    Try:
      sendMessage(ws2, JoinMessage("Bob"))
      awaitLatch(joined2, "Player 2 JoinedMessage failed")
      awaitLatch(update2, "Player 2 join update failed")
      awaitLatch(update1b, "Player 1 didn't receive player 2 join")

      // Player 1 (host) leaves
      sendMessage(ws1, LeaveMessage())

      // Player 2 should receive updated state
      awaitLatch(leaveLatch2, "Player 2 didn't receive leave update")

      val finalGame = messages2.toSeq.collect { case GameUpdateMessage(g) => g }.last

      // Only Bob should remain, and should become new host
      assert(finalGame.players.size == 1)
      assert(finalGame.players.values.exists(_.name == "Bob"))
      assert(finalGame.hostId.isDefined, "There should still be a host")
      val bobId = finalGame.players.keys.head
      assert(finalGame.hostId.contains(bobId), "Bob should now be host")
    .tap(_ => closeWebSocket(ws1))
    .tap(_ => closeWebSocket(ws2))
    .get


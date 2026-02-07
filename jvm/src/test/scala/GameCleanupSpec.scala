import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import shared.*
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.compiletime.uninitialized

class GameCleanupSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach:
  
  // Test fixtures
  var colorRushGames: ConcurrentHashMap[String, ColorRushGame] = uninitialized
  var gameConnections: ConcurrentHashMap[String, java.util.Set[MockWsChannel]] = uninitialized

  override def beforeEach(): Unit =
    colorRushGames = new ConcurrentHashMap[String, ColorRushGame]()
    gameConnections = new ConcurrentHashMap[String, java.util.Set[MockWsChannel]]()

  // Mock WebSocket channel for testing
  class MockWsChannel(val id: String)

  // Helper to simulate game state
  def createTestGame(gameId: String, status: GameStatus, roundNumber: Int = 1): ColorRushGame =
    ColorRushGame(
      gameId = gameId,
      players = Map(
        "player1" -> PlayerState("player1", "Alice", 100, 5),
        "player2" -> PlayerState("player2", "Bob", 80, 3)
      ),
      currentRound = None,
      roundNumber = roundNumber,
      status = status
    )

  def addGameConnection(gameId: String, channel: MockWsChannel): Unit =
    val connections = gameConnections.computeIfAbsent(gameId, _ =>
      ConcurrentHashMap.newKeySet[MockWsChannel]()
    )
    connections.add(channel)

  def removeGameConnection(gameId: String, channel: MockWsChannel): Unit =
    Option(gameConnections.get(gameId)).foreach(_.remove(channel))

  def cleanupGame(gameId: String): Unit =
    colorRushGames.remove(gameId)
    gameConnections.remove(gameId)

  def cleanupEmptyGames(): Int =
    val gamesToCleanup = gameConnections.asScala.filter:
      case (gameId, connections) => connections.isEmpty
    .keys.toList

    gamesToCleanup.foreach(cleanupGame)
    gamesToCleanup.size

  describe("Game Cleanup System"):

    describe("Feature: Immediate cleanup for GameOver games"):

      it("should remove a GameOver game when the last player disconnects"):
        // Given a game that has finished (GameOver status)
        val gameId = "game123"
        val game = createTestGame(gameId, GameStatus.GameOver, roundNumber = 10)
        colorRushGames.put(gameId, game)

        val channel1 = MockWsChannel("channel1")
        val channel2 = MockWsChannel("channel2")
        addGameConnection(gameId, channel1)
        addGameConnection(gameId, channel2)

        // And there are still connections
        gameConnections.get(gameId).size() shouldBe 2

        // When the first player disconnects
        removeGameConnection(gameId, channel1)
        gameConnections.get(gameId).size() shouldBe 1

        // Then the game should still exist
        colorRushGames.containsKey(gameId) shouldBe true

        // When the last player disconnects
        removeGameConnection(gameId, channel2)
        val connections = gameConnections.get(gameId)
        connections.isEmpty shouldBe true

        // And cleanup is triggered
        if game.status == GameStatus.GameOver && connections.isEmpty then
          cleanupGame(gameId)

        // Then the game should be removed
        colorRushGames.containsKey(gameId) shouldBe false
        gameConnections.containsKey(gameId) shouldBe false

      it("should NOT remove a GameOver game if connections still exist"):
        // Given a game that has finished but still has active connections
        val gameId = "game456"
        val game = createTestGame(gameId, GameStatus.GameOver, roundNumber = 10)
        colorRushGames.put(gameId, game)

        val channel = MockWsChannel("channel1")
        addGameConnection(gameId, channel)

        // When checking for cleanup
        val connections = gameConnections.get(gameId)

        // Then cleanup should NOT be triggered
        val shouldCleanup = game.status == GameStatus.GameOver && connections.isEmpty
        shouldCleanup shouldBe false

        // And the game should still exist
        colorRushGames.containsKey(gameId) shouldBe true

      it("should NOT remove a non-GameOver game even if connections are empty"):
        // Given a game in Playing status with no connections
        val gameId = "game789"
        val game = createTestGame(gameId, GameStatus.Playing, roundNumber = 5)
        colorRushGames.put(gameId, game)
        gameConnections.put(gameId, ConcurrentHashMap.newKeySet[MockWsChannel]())

        val connections = gameConnections.get(gameId)
        connections.isEmpty shouldBe true

        // When checking if immediate cleanup should trigger
        val shouldCleanup = game.status == GameStatus.GameOver && connections.isEmpty
        shouldCleanup shouldBe false

        // Then immediate cleanup should not remove it
        // (It will be removed by periodic cleanup instead)
        colorRushGames.containsKey(gameId) shouldBe true

    describe("Feature: Periodic cleanup for empty games"):

      it("should remove all games without connections regardless of status"):
        // Given multiple games with different statuses but no connections
        val waitingGame = createTestGame("waiting-game", GameStatus.Waiting)
        val playingGame = createTestGame("playing-game", GameStatus.Playing)
        val gameOverGame = createTestGame("gameover-game", GameStatus.GameOver)

        colorRushGames.put("waiting-game", waitingGame)
        colorRushGames.put("playing-game", playingGame)
        colorRushGames.put("gameover-game", gameOverGame)

        gameConnections.put("waiting-game", ConcurrentHashMap.newKeySet[MockWsChannel]())
        gameConnections.put("playing-game", ConcurrentHashMap.newKeySet[MockWsChannel]())
        gameConnections.put("gameover-game", ConcurrentHashMap.newKeySet[MockWsChannel]())

        // All should be empty
        gameConnections.get("waiting-game").isEmpty shouldBe true
        gameConnections.get("playing-game").isEmpty shouldBe true
        gameConnections.get("gameover-game").isEmpty shouldBe true

        // When periodic cleanup runs
        val cleanedCount = cleanupEmptyGames()

        // Then all games should be removed
        cleanedCount shouldBe 3
        colorRushGames.isEmpty shouldBe true
        gameConnections.isEmpty shouldBe true

      it("should NOT remove games that have active connections"):
        // Given a mix of games with and without connections
        val emptyGame = createTestGame("empty-game", GameStatus.Waiting)
        val activeGame = createTestGame("active-game", GameStatus.Playing)

        colorRushGames.put("empty-game", emptyGame)
        colorRushGames.put("active-game", activeGame)

        gameConnections.put("empty-game", ConcurrentHashMap.newKeySet[MockWsChannel]())
        addGameConnection("active-game", MockWsChannel("player1"))

        // When periodic cleanup runs
        val cleanedCount = cleanupEmptyGames()

        // Then only the empty game should be removed
        cleanedCount shouldBe 1
        colorRushGames.containsKey("empty-game") shouldBe false
        colorRushGames.containsKey("active-game") shouldBe true
        gameConnections.containsKey("active-game") shouldBe true

      it("should handle cleanup of zero games gracefully"):
        // Given all games have active connections
        val game1 = createTestGame("game1", GameStatus.Playing)
        val game2 = createTestGame("game2", GameStatus.Waiting)

        colorRushGames.put("game1", game1)
        colorRushGames.put("game2", game2)

        addGameConnection("game1", MockWsChannel("player1"))
        addGameConnection("game2", MockWsChannel("player2"))

        // When periodic cleanup runs
        val cleanedCount = cleanupEmptyGames()

        // Then nothing should be removed
        cleanedCount shouldBe 0
        colorRushGames.size() shouldBe 2

    describe("Feature: Prevent rejoining finished games"):

      it("should allow rejoining with same game ID but start fresh after cleanup"):
        // Given a game that finished at round 10
        val gameId = "completed-game"
        val finishedGame = createTestGame(gameId, GameStatus.GameOver, roundNumber = 10)
        colorRushGames.put(gameId, finishedGame)
        
        val channel = MockWsChannel("player1")
        addGameConnection(gameId, channel)
        
        // The game should exist initially at round 10
        colorRushGames.containsKey(gameId) shouldBe true
        colorRushGames.get(gameId).roundNumber shouldBe 10
        colorRushGames.get(gameId).status shouldBe GameStatus.GameOver
        
        // When the last player disconnects
        removeGameConnection(gameId, channel)
        val connections = gameConnections.get(gameId)
        connections.isEmpty shouldBe true
        
        // And cleanup is triggered for GameOver games
        if finishedGame.status == GameStatus.GameOver && connections.isEmpty then
          cleanupGame(gameId)
        
        // Then the game should no longer exist
        colorRushGames.containsKey(gameId) shouldBe false
        
        // When a player tries to rejoin with the same game ID
        // (simulating computeIfAbsent in actual implementation)
        val newGame = colorRushGames.computeIfAbsent(gameId, _ => 
          ColorRush.createGame(gameId)
        )
        
        // Then a fresh game should be created, not the old one at round 10
        newGame.roundNumber shouldBe 0
        newGame.status shouldBe GameStatus.Waiting
        newGame.players shouldBe empty
        // This prevents the 11/10 bug - the game starts fresh instead of continuing

    describe("Scenario: Complete game lifecycle with cleanup"):

      it("should handle a full game from start to finish with proper cleanup"):
        // Given a new game is created
        val gameId = "lifecycle-game"
        val newGame = createTestGame(gameId, GameStatus.Waiting, roundNumber = 0)
        colorRushGames.put(gameId, newGame)

        // When players join
        val player1 = MockWsChannel("player1")
        val player2 = MockWsChannel("player2")
        addGameConnection(gameId, player1)
        addGameConnection(gameId, player2)

        gameConnections.get(gameId).size() shouldBe 2

        // And the game progresses to Playing
        val playingGame = newGame.copy(status = GameStatus.Playing, roundNumber = 5)
        colorRushGames.put(gameId, playingGame)

        // And eventually reaches GameOver
        val finishedGame = playingGame.copy(status = GameStatus.GameOver, roundNumber = 10)
        colorRushGames.put(gameId, finishedGame)

        // When all players disconnect
        removeGameConnection(gameId, player1)
        removeGameConnection(gameId, player2)

        val connections = gameConnections.get(gameId)
        connections.isEmpty shouldBe true

        // And immediate cleanup is triggered
        if finishedGame.status == GameStatus.GameOver && connections.isEmpty then
          cleanupGame(gameId)

        // Then the game should be completely removed
        colorRushGames.containsKey(gameId) shouldBe false
        gameConnections.containsKey(gameId) shouldBe false

        // And the server is ready for a new game with the same ID
        val freshGame = createTestGame(gameId, GameStatus.Waiting, roundNumber = 0)
        colorRushGames.put(gameId, freshGame)

        colorRushGames.get(gameId).roundNumber shouldBe 0
        colorRushGames.get(gameId).status shouldBe GameStatus.Waiting



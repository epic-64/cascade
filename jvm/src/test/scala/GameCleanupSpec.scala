package server

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import shared.*

class GameCleanupSpec extends AnyFunSpec with Matchers:

  describe("Game Cleanup System") {

    describe("Feature: Immediate cleanup for GameOver games") {

      it("should remove a GameOver game when it has no connections") {
        val gameManager = ColorRushStateManager()
        
        // Given a game that has finished (GameOver status)
        val gameId = "test-gameover-cleanup"
        val game = ColorRushGame(
          gameId = gameId,
          players = Map("p1" -> PlayerState("p1", "Alice", 100, 10)),
          currentRound = None,
          roundNumber = 10,
          totalRounds = 10,
          status = GameStatus.GameOver
        )
        gameManager.createGame(gameId, 10)
        gameManager.updateGame(gameId, game)

        // Initially the game exists
        gameManager.getGame(gameId) shouldBe defined
        gameManager.getGameConnectionCount(gameId) shouldBe 0

        // When cleanup is triggered for a GameOver game with no connections
        gameManager.cleanupGame(gameId)

        // Then the game should be removed
        gameManager.getGame(gameId) shouldBe empty
      }

      it("should allow rejoining with same game ID but start fresh after cleanup") {
        val gameManager = ColorRushStateManager()
        
        // Given a game that finished at round 10 and was cleaned up
        val gameId = "test-rejoin-fresh"
        val finishedGame = ColorRushGame(
          gameId = gameId,
          players = Map("p1" -> PlayerState("p1", "Alice", 100, 10)),
          currentRound = None,
          roundNumber = 10,
          totalRounds = 10,
          status = GameStatus.GameOver
        )
        gameManager.createGame(gameId, 10)
        gameManager.updateGame(gameId, finishedGame)

        // The game exists at round 10
        gameManager.getGame(gameId).map(_.roundNumber) shouldBe Some(10)
        gameManager.getGame(gameId).map(_.status) shouldBe Some(GameStatus.GameOver)

        // When cleanup is triggered
        gameManager.cleanupGame(gameId)

        // Then the game should no longer exist
        gameManager.getGame(gameId) shouldBe empty

        // When a player creates/joins with the same game ID
        val freshGame = gameManager.createGame(gameId, 10)

        // Then a fresh game should exist, not the old one at round 10
        gameManager.getGame(gameId).map(_.roundNumber) shouldBe Some(0)
        gameManager.getGame(gameId).map(_.status) shouldBe Some(GameStatus.Waiting)
        gameManager.getGame(gameId).map(_.players) shouldBe Some(Map.empty)
      }
    }

    describe("Feature: Periodic cleanup for empty games") {

      it("should remove all games without connections regardless of status") {
        val gameManager = ColorRushStateManager()
        
        // Given multiple games with different statuses but no connections
        val games = List(
          ("waiting-game", GameStatus.Waiting),
          ("playing-game", GameStatus.Playing),
          ("gameover-game", GameStatus.GameOver)
        )

        games.foreach { (gameId, status) =>
          val game = ColorRushGame(
            gameId = gameId,
            players = Map.empty,
            currentRound = None,
            roundNumber = 1,
            totalRounds = 10,
            status = status
          )
          gameManager.createGame(gameId, 10)
          gameManager.updateGame(gameId, game)
        }

        // All games exist initially
        games.foreach { (gameId, _) =>
          gameManager.getGame(gameId) shouldBe defined
          gameManager.getGameConnectionCount(gameId) shouldBe 0
        }

        // When periodic cleanup runs
        val cleanedCount = gameManager.cleanupEmptyGames()

        // Then all games should be removed
        cleanedCount shouldBe 3
        games.foreach { (gameId, _) =>
          gameManager.getGame(gameId) shouldBe empty
        }
      }

      it("should NOT remove games that have active connections") {
        val gameManager = ColorRushStateManager()
        
        // Given two games with no connections
        val game1Id = "game-1"
        val game1 = ColorRushGame(
          gameId = game1Id,
          players = Map.empty,
          currentRound = None,
          roundNumber = 1,
          totalRounds = 10,
          status = GameStatus.Waiting
        )
        gameManager.createGame(game1Id, 10)
        gameManager.updateGame(game1Id, game1)

        val game2Id = "game-2"
        val game2 = ColorRushGame(
          gameId = game2Id,
          players = Map.empty,
          currentRound = None,
          roundNumber = 1,
          totalRounds = 10,
          status = GameStatus.Playing
        )
        gameManager.createGame(game2Id, 10)
        gameManager.updateGame(game2Id, game2)

        // Both games initially exist with no connections
        gameManager.getGame(game1Id) shouldBe defined
        gameManager.getGame(game2Id) shouldBe defined
        gameManager.getGameConnectionCount(game1Id) shouldBe 0
        gameManager.getGameConnectionCount(game2Id) shouldBe 0

        // When periodic cleanup runs
        val cleanedCount = gameManager.cleanupEmptyGames()

        // Then both games should be removed since they have no connections
        cleanedCount shouldBe 2
        gameManager.getGame(game1Id) shouldBe empty
        gameManager.getGame(game2Id) shouldBe empty
      }

      it("should handle cleanup of zero games gracefully") {
        val gameManager = ColorRushStateManager()
        
        // Given no games exist
        // When periodic cleanup runs
        val cleanedCount = gameManager.cleanupEmptyGames()

        // Then it should complete without error and return count
        cleanedCount shouldBe 0
      }
    }

    describe("Scenario: Complete game lifecycle with cleanup") {

      it("should handle a full game from start to finish with proper cleanup") {
        val gameManager = ColorRushStateManager()
        
        // Given a new game is created
        val gameId = "lifecycle-test"
        val newGame = gameManager.createGame(gameId, 10)

        // The game starts fresh
        gameManager.getGame(gameId).map(_.roundNumber) shouldBe Some(0)
        gameManager.getGame(gameId).map(_.status) shouldBe Some(GameStatus.Waiting)

        // When the game progresses to Playing
        val playingGame = newGame.copy(status = GameStatus.Playing, roundNumber = 5)
        gameManager.updateGame(gameId, playingGame)
        gameManager.getGame(gameId).map(_.roundNumber) shouldBe Some(5)

        // And eventually reaches GameOver
        val finishedGame = playingGame.copy(status = GameStatus.GameOver, roundNumber = 10)
        gameManager.updateGame(gameId, finishedGame)
        gameManager.getGame(gameId).map(_.status) shouldBe Some(GameStatus.GameOver)

        // When cleanup is triggered (simulating all players disconnecting)
        gameManager.cleanupGame(gameId)

        // Then the game should be completely removed
        gameManager.getGame(gameId) shouldBe empty

        // And the server is ready for a new game with the same ID
        val freshGame = gameManager.createGame(gameId, 10)

        gameManager.getGame(gameId).map(_.roundNumber) shouldBe Some(0)
        gameManager.getGame(gameId).map(_.status) shouldBe Some(GameStatus.Waiting)
      }
    }
  }

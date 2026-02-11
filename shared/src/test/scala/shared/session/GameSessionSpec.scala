package shared.session

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import upickle.default.*

class GameSessionSpec extends AnyFunSuite with Matchers:

  test("BasicGameSession contains all required fields"):
    val session = BasicGameSession(
      playerId = "player-123",
      gameId = "game-456",
      playerName = "Alice"
    )
    
    session.playerId shouldBe "player-123"
    session.gameId shouldBe "game-456"
    session.playerName shouldBe "Alice"

  test("BasicGameSession implements GameSession trait"):
    val session: GameSession = BasicGameSession("p1", "g1", "Test")
    
    session.playerId shouldBe "p1"
    session.gameId shouldBe "g1"
    session.playerName shouldBe "Test"

  test("BasicGameSession can be serialized to JSON"):
    val session = BasicGameSession("player-1", "game-2", "Bob")
    
    val json = write(session)
    
    json should include("player-1")
    json should include("game-2")
    json should include("Bob")

  test("BasicGameSession can be deserialized from JSON"):
    val json = """{"playerId":"p-abc","gameId":"g-xyz","playerName":"Carol"}"""
    
    val session = read[BasicGameSession](json)
    
    session.playerId shouldBe "p-abc"
    session.gameId shouldBe "g-xyz"
    session.playerName shouldBe "Carol"

  test("BasicGameSession roundtrip serialization"):
    val original = BasicGameSession("id1", "id2", "Player Name With Spaces")
    
    val json = write(original)
    val restored = read[BasicGameSession](json)
    
    restored shouldBe original


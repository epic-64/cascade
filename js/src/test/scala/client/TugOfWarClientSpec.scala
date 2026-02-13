package client

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalajs.dom
import org.scalajs.dom.*
import upickle.default.*
import shared.TugOfWar.*

class TugOfWarClientSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach:

  override def beforeEach(): Unit =
    document.body.innerHTML = ""
    super.beforeEach()

  describe("TugOfWar Client Message Serialization"):

    describe("Feature: LeaveMessage can be serialized and deserialized"):

      it("should serialize LeaveMessage to JSON"):
        // Given: A LeaveMessage
        val message: ClientMessage = LeaveMessage()

        // When: It is serialized to JSON
        val json = write(message)

        // Then: The JSON should contain the message type
        json should include("LeaveMessage")

      it("should deserialize LeaveMessage from JSON"):
        // Given: JSON representing a LeaveMessage
        val json = """{"$type":"shared.TugOfWar.LeaveMessage"}"""

        // When: It is deserialized
        val message = read[ClientMessage](json)

        // Then: It should be a LeaveMessage
        message shouldBe a[LeaveMessage]

      it("should roundtrip serialize LeaveMessage"):
        // Given: A LeaveMessage
        val original: ClientMessage = LeaveMessage()

        // When: It is serialized and then deserialized
        val json = write(original)
        val restored = read[ClientMessage](json)

        // Then: The restored message should equal the original
        restored shouldBe original


    describe("Feature: LeaveMessage is distinct from other client messages"):

      it("should serialize differently from other messages"):
        // Given: Various client messages
        val leaveMsg: ClientMessage = LeaveMessage()
        val joinMsg: ClientMessage = JoinMessage("Player")
        val teamMsg: ClientMessage = SelectTeamMessage(Team.Red)

        // When: They are serialized
        val leaveJson = write(leaveMsg)
        val joinJson = write(joinMsg)
        val teamJson = write(teamMsg)

        // Then: They should produce different JSON
        leaveJson should not equal joinJson
        leaveJson should not equal teamJson

      it("should be identifiable via pattern matching after deserialization"):
        // Given: Various serialized messages
        val messages = Seq(
          write[ClientMessage](LeaveMessage()),
          write[ClientMessage](JoinMessage("Test")),
          write[ClientMessage](PingMessage())
        )

        // When: They are deserialized and pattern matched
        val results = messages.map: json =>
          read[ClientMessage](json) match
            case LeaveMessage() => "leave"
            case JoinMessage(_) => "join"
            case PingMessage() => "ping"
            case _ => "other"

        // Then: Each should match correctly
        results shouldBe Seq("leave", "join", "ping")


  describe("TugOfWar Client UI Initialization"):

    describe("Feature: Tug of War UI loads correctly"):

      it("should render lobby UI when initialized"):
        // Given: A pathname to the Tug of War app
        val pathname = "/tug-of-war"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Tug of War UI should be rendered
        val lobby = document.getElementById("towLobby")
        lobby should not be null

        val joinForm = document.getElementById("towJoinForm")
        joinForm should not be null

        val createForm = document.getElementById("towCreateForm")
        createForm should not be null

      it("should have leave button in waiting area"):
        // Given: A pathname to the Tug of War app
        val pathname = "/tug-of-war"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: The waiting area should have a leave button
        val leaveButton = document.getElementById("towLeaveButton")
        leaveButton should not be null
        leaveButton.textContent should include("Leave")


  describe("TugOfWar Player State Model"):

    describe("Feature: removePlayer removes player entirely"):

      it("should remove player from players map"):
        // Given: A game with two players
        val game = TugOfWar.createGame("test-game")
        val gameWithAlice = TugOfWar.addPlayer(game, "alice-id", "Alice")
        val gameWithBoth = TugOfWar.addPlayer(gameWithAlice, "bob-id", "Bob")

        gameWithBoth.players.size shouldBe 2

        // When: Bob is removed
        val gameAfterLeave = TugOfWar.removePlayer(gameWithBoth, "bob-id")

        // Then: Only Alice should remain
        gameAfterLeave.players.size shouldBe 1
        gameAfterLeave.players.keys.toSeq shouldBe Seq("alice-id")
        gameAfterLeave.players.values.map(_.name).toSeq shouldBe Seq("Alice")

      it("should differ from disconnectPlayer which keeps player in list"):
        // Given: A game with two players
        val game = TugOfWar.createGame("test-game")
        val gameWithAlice = TugOfWar.addPlayer(game, "alice-id", "Alice")
        val gameWithBoth = TugOfWar.addPlayer(gameWithAlice, "bob-id", "Bob")

        // When: Bob disconnects (not leaves)
        val gameAfterDisconnect = TugOfWar.disconnectPlayer(gameWithBoth, "bob-id")

        // Then: Both players should still be in the list, but Bob is disconnected
        gameAfterDisconnect.players.size shouldBe 2
        gameAfterDisconnect.players("bob-id").connected shouldBe false
        gameAfterDisconnect.players("bob-id").disconnectedAt shouldBe defined

      it("should reassign host when host leaves"):
        // Given: A game where Alice is host and Bob is a player
        val game = TugOfWar.createGame("test-game")
        val gameWithAlice = TugOfWar.addPlayer(game, "alice-id", "Alice")
        val gameWithBoth = TugOfWar.addPlayer(gameWithAlice, "bob-id", "Bob")

        gameWithBoth.hostId shouldBe Some("alice-id")

        // When: Alice (host) leaves
        val gameAfterHostLeave = TugOfWar.removePlayer(gameWithBoth, "alice-id")

        // Then: Bob should become the new host
        gameAfterHostLeave.players.size shouldBe 1
        gameAfterHostLeave.hostId shouldBe Some("bob-id")

      it("should remove player's team selection when they leave"):
        // Given: A game where Bob has selected a team
        val game = TugOfWar.createGame("test-game")
        val gameWithAlice = TugOfWar.addPlayer(game, "alice-id", "Alice")
        val gameWithBoth = TugOfWar.addPlayer(gameWithAlice, "bob-id", "Bob")
        val gameWithTeams = TugOfWar.setPlayerTeam(gameWithBoth, "bob-id", Team.Blue)

        gameWithTeams.players("bob-id").team shouldBe Some(Team.Blue)

        // When: Bob leaves
        val gameAfterLeave = TugOfWar.removePlayer(gameWithTeams, "bob-id")

        // Then: Bob's team selection should be gone (player removed entirely)
        gameAfterLeave.players.contains("bob-id") shouldBe false


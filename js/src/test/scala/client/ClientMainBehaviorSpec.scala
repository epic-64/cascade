package client

import org.scalajs.dom
import org.scalajs.dom.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.scalajs.js

class ClientMainBehaviorSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach:

  override def beforeEach(): Unit =
    // Clear the document body before each test
    document.body.innerHTML = ""
    super.beforeEach()

  describe("Client Main Routing Behavior"):

    describe("Feature: Route to Counter app when pathname is /counter"):

      it("should load Counter UI with counter display and increment/decrement buttons"):
        // Given: Set the window location to /counter
        setWindowLocation("/counter")

        // When: Client main initializes
        clientMain()

        // Then: Counter UI should be rendered
        // Check for counter-specific elements that prove Counter app loaded
        val counterDisplay = document.getElementById("counter-display")
        counterDisplay should not be null

        val incrementButton = document.getElementById("btn-increment")
        incrementButton should not be null
        incrementButton.textContent shouldBe "+"

        val decrementButton = document.getElementById("btn-decrement")
        decrementButton should not be null
        decrementButton.textContent shouldBe "-"

        // Check for navigation bar with "Counter" title
        val navLinks = document.querySelectorAll("nav a")
        navLinks.length should be > 0

    describe("Feature: Route to Color Rush app when pathname is /color-rush"):

      it("should load Color Rush UI with game lobby and join form"):
        // Given: Set the window location to /color-rush
        setWindowLocation("/color-rush")

        // When: Client main initializes
        clientMain()

        // Then: Color Rush UI should be rendered
        // Check for Color Rush-specific elements
        val lobby = document.getElementById("lobby")
        lobby should not be null

        val joinForm = document.getElementById("joinForm")
        joinForm should not be null

        val gameIdInput = document.getElementById("gameId")
        gameIdInput should not be null
        gameIdInput.asInstanceOf[dom.HTMLInputElement].placeholder should include("Game ID")

        val playerNameInput = document.getElementById("playerName")
        playerNameInput should not be null
        playerNameInput.asInstanceOf[dom.HTMLInputElement].placeholder should include("Name")

        val gameArea = document.getElementById("gameArea")
        gameArea should not be null

    describe("Feature: Route to Landing page for unknown paths"):

      it("should not initialize any app when pathname is /"):
        // Given: Set the window location to root
        setWindowLocation("/")

        // When: Client main initializes
        clientMain()

        // Then: No app-specific UI should be rendered (body stays mostly empty)
        val counterDisplay = document.getElementById("counter-display")
        counterDisplay shouldBe null

        val lobby = document.getElementById("lobby")
        lobby shouldBe null

      it("should not initialize any app when pathname is unknown"):
        // Given: Set the window location to an unknown path
        setWindowLocation("/about")

        // When: Client main initializes
        clientMain()

        // Then: No app-specific UI should be rendered
        val counterDisplay = document.getElementById("counter-display")
        counterDisplay shouldBe null

        val lobby = document.getElementById("lobby")
        lobby shouldBe null

  // Helper to set window.location.pathname for testing
  // Note: In jsdom we can manipulate the location
  private def setWindowLocation(pathname: String): Unit =
    // Use jsdom's location manipulation
    val location = js.Dynamic.literal(
      pathname = pathname,
      href = s"http://localhost:8080$pathname",
      host = "localhost:8080",
      hostname = "localhost",
      port = "8080",
      protocol = "http:",
      search = "",
      hash = ""
    )
    // Override window.location
    js.Dynamic.global.window.updateDynamic("location")(location)



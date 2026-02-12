package client

import org.scalajs.dom
import org.scalajs.dom.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class ClientMainBehaviorSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach:

  override def beforeEach(): Unit =
    // Clear the document body before each test
    document.body.innerHTML = ""
    super.beforeEach()

  describe("Client Main Routing Behavior"):

    describe("Feature: Route to Counter app when pathname is /counter"):

      it("should load Counter UI with counter display and increment/decrement buttons"):
        // Given: A pathname to the counter app
        val pathname = "/counter"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

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
        // Given: A pathname to the Color Rush app
        val pathname = "/color-rush"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Color Rush UI should be rendered
        // Check for Color Rush-specific elements
        val lobby = document.getElementById("lobby")
        lobby should not be null

        val joinForm = document.getElementById("joinForm")
        joinForm should not be null

        val gameIdInput = document.getElementById("joinGameId")
        gameIdInput should not be null
        // The floating input uses a label element instead of placeholder
        val gameIdLabel = joinForm.querySelector("label[for='joinGameId']")
        gameIdLabel should not be null
        gameIdLabel.textContent should include("Game")

        val playerNameInput = document.getElementById("joinPlayerName")
        playerNameInput should not be null
        val playerNameLabel = joinForm.querySelector("label[for='joinPlayerName']")
        playerNameLabel should not be null
        playerNameLabel.textContent should include("Name")

        val gameArea = document.getElementById("gameArea")
        gameArea should not be null

    describe("Feature: Route to Landing page for unknown paths"):

      it("should not initialize any app when pathname is /"):
        // Given: A pathname to the root
        val pathname = "/"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: No app-specific UI should be rendered (body stays mostly empty)
        val counterDisplay = document.getElementById("counter-display")
        counterDisplay shouldBe null

        val lobby = document.getElementById("lobby")
        lobby shouldBe null

      it("should not initialize any app when pathname is unknown"):
        // Given: A pathname to an unknown page
        val pathname = "/about"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: No app-specific UI should be rendered
        val counterDisplay = document.getElementById("counter-display")
        counterDisplay shouldBe null

        val lobby = document.getElementById("lobby")
        lobby shouldBe null




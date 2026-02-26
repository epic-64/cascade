package client

import org.scalajs.dom
import org.scalajs.dom.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class TileKingdomClientBehaviorSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach:

  override def beforeEach(): Unit =
    // Clear the document body before each test
    document.body.innerHTML = ""
    super.beforeEach()

  describe("TileKingdom Client Routing Behavior"):

    describe("Feature: Route to TileKingdom app when pathname is /tile-kingdom"):

      it("should load TileKingdom UI with game container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: TileKingdom UI should be rendered
        val container = document.getElementById("tile-kingdom-container")
        container should not be null

      it("should render the game header with title"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Header should be rendered with game title
        val header = document.querySelector(".tile-kingdom-header")
        header should not be null
        header.textContent should include("Tile Kingdom")

      it("should render the help button"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Help button should be present
        val helpButton = document.querySelector(".help-button")
        helpButton should not be null
        helpButton.textContent should include("?")

      it("should render resource panel container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Resource panel container should be rendered
        val resourcePanel = document.getElementById("laminar-resource-panel")
        resourcePanel should not be null

      it("should render action bar container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Action bar container should be rendered
        val actionBar = document.getElementById("laminar-action-bar")
        actionBar should not be null

      it("should render tile grid container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Tile grid container should be rendered
        val tileGrid = document.getElementById("laminar-tile-grid")
        tileGrid should not be null

      it("should render notification container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Notification container should be rendered
        val notification = document.getElementById("laminar-notification")
        notification should not be null

      it("should render skill tree modal container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Skill tree modal container should be rendered
        val skillTree = document.getElementById("laminar-skill-tree-modal")
        skillTree should not be null

      it("should render help popup container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Help popup container should be rendered
        val helpPopup = document.getElementById("laminar-help-popup")
        helpPopup should not be null

      it("should render dev tools popup container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Dev tools popup container should be rendered
        val devTools = document.getElementById("laminar-dev-tools-popup")
        devTools should not be null

      it("should render politician roster panel container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Politician roster panel container should be rendered
        val rosterPanel = document.getElementById("laminar-politician-roster-panel")
        rosterPanel should not be null

      it("should render welcome back modal container"):
        // Given: A pathname to the TileKingdom app
        val pathname = "/tile-kingdom"

        // When: Client main initializes with that pathname
        clientMain(Some(pathname))

        // Then: Welcome back modal container should be rendered
        val welcomeModal = document.getElementById("laminar-welcome-modal")
        welcomeModal should not be null


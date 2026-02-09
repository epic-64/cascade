package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.*
import client.{el, form, input, button, *}

import scala.scalajs.js
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

def initializeColorRush(): Unit =
  println("[ColorRush] Starting Color Rush client...")
  buildGameUI()
  setupEnterKeyHandler()

var gameWebSocket: Option[WebSocket] = None
var currentGameId: Option[String]    = None
var currentRoundId: Option[String]   = None

def buildGameUI(): Unit =
  // Clear existing content
  document.body.innerHTML = ""

  // Add navigation bar
  document.body.appendChild(NavigationBar.render("Color Rush"))

  // Create main container
  val container = el("div", classes = "container")

  // Create lobby
  container.appendChild(createLobby())

  // Create game area
  container.appendChild(createGameArea())

  // Create players sidebar (outside game area)
  container.appendChild(createPlayersSidebar())

  // Create winner announcement
  container.appendChild(createWinnerAnnouncement())

  // Append container to body
  document.body.appendChild(container)

def createLobby(): HTMLElement =
  val joinGameListener = (e: Event) =>
    e.preventDefault()
    joinGame()

  el("div", id = "lobby")(
    el("div", id = "joinFormContainer", classes = "join-form-container")(
      el("h2", content = "Join Game"),
      form(id = "joinForm").tap { formEl =>
        formEl.addEventListener("submit", joinGameListener)
      }(
        input("text", id = "gameId").tap: el =>
          el.placeholder = "Game ID (e.g., game123)"
          el.value = "game1"
          el.setAttribute("autocomplete", "off")
          el.required = true
        ,
        input("text", id = "playerName").tap: el =>
          el.placeholder = "Your Name"
          el.setAttribute("autocomplete", "off")
          el.required = true
        ,
        button("submit").tap: btn =>
          btn.textContent = "Join Game"
      )
    ),
    // Waiting area container
    el("div").with_id("waitingArea").with_classes("waiting-area-container hidden")(
      el("h3").with_content("Players in Lobby:"),
      el("div").with_id("playersList").with_classes("players"),
      button().tap: btn =>
        btn.id = "startButton"
        btn.className = "start-button"
        btn.textContent = "Start Game"
        btn.addEventListener("click", (e: Event) => startGame())
    )
  )

def createGameArea(): HTMLElement =
  el("div").with_id("gameArea").with_classes("game-area hidden")(
    // Round info
    el("div").with_classes("round-info")(
      el("div").with_classes("round-number")(
        el("span").with_content("Round "),
        el("span").with_id("roundNumber").with_content("1"),
        el("span").with_content(" of 10")
      ),
      el("div").with_classes("target-color-label").with_content("Click this color:"),
      el("div").with_id("targetColor").with_classes("target-color")
    ),
    // Color grid
    el("div").with_id("colorGrid").with_classes("color-grid")
  )

def createPlayersSidebar(): HTMLElement =
  el("div").with_id("gamePlayers").with_classes("players hidden")

def createWinnerAnnouncement(): HTMLElement =
  el("div").with_id("winnerAnnouncement").with_classes("winner-announcement hidden")(
    el("h2").with_id("winnerName"),
    el("p").with_id("winnerPoints").with_classes("points")
  )

def setupEnterKeyHandler(): Unit =
  document.addEventListener(
    "keydown",
    (event: KeyboardEvent) =>
      if event.key == "Enter" then
        val waitingArea = getElementById("waitingArea")
        val lobby       = getElementById("lobby")

        if !waitingArea.exists(_.classList.contains("hidden")) &&
          !lobby.exists(_.classList.contains("hidden"))
        then
          event.preventDefault()
          startGame()
  )

def joinGame(): Unit =
  val gameIdOpt     = getInputValue("gameId")
  val playerNameOpt = getInputValue("playerName")

  (gameIdOpt, playerNameOpt) match
    case (Some(gameId), Some(playerName)) =>
      currentGameId = Some(gameId)
      connectToGame(gameId, playerName)
    case _                                =>
      // HTML5 form validation should prevent reaching here
      println("[ColorRush] Missing game ID or player name")

def connectToGame(gameId: String, playerName: String): Unit =
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl    = s"$protocol//${window.location.host}/ws/color-rush/$gameId"

  val ws = new WebSocket(wsUrl)
  gameWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[ColorRush] Connected to game $gameId")
    sendMessage(ws, JoinMessage(playerName))
    updateLobbyUI()

  ws.onmessage = (event: MessageEvent) => handleWebSocketMessage(event.data.toString)
  ws.onerror = (event: Event) => println(s"[ColorRush] WebSocket error")
  ws.onclose = (e: CloseEvent) => println(s"[ColorRush] Disconnected from game")

def sendMessage(ws: WebSocket, msg: ClientMessage): Unit =
  Try:
    val json = upickle.default.write(msg)
    println(s"[ColorRush] Sending message: $json")
    ws.send(json)
  .recover:
    case ex => println(s"[ColorRush] Failed to send message: ${ex.getMessage}")

def handleWebSocketMessage(data: String): Unit =
  Try:
    val serverMsg = upickle.default.read[ServerMessage](data)

    serverMsg match
      case GameUpdateMessage(game)                => parseGameUpdate(game)
      case RoundWinnerMessage(playerName, points) => showRoundWinner(playerName, points)
      case GameEndMessage(winner)                 => showGameWinner(winner)
  .recover:
    case ex => println(s"[ColorRush] Error handling message: ${ex.getMessage}")

def parseGameUpdate(game: ColorRushGame): Unit =
  Try:
    // Update players list for all game statuses
    updatePlayersList(game.players)

    // Show game area if playing or round end
    game.status match
      case GameStatus.Playing | GameStatus.RoundEnd =>
        showGameArea()

        game.currentRound.foreach: currentRound =>
          val roundId = s"${game.gameId}-${game.roundNumber}"

          // Only update round display if this is a new round
          if !currentRoundId.contains(roundId) then
            currentRoundId = Some(roundId)
            val isRoundEnd = game.status == GameStatus.RoundEnd
            updateRoundDisplay(game.roundNumber, currentRound, isRoundEnd)

      case _ => // Waiting or GameOver - keep lobby visible
  .recover:
    case ex => println(s"[ColorRush] Error parsing game update: ${ex.getMessage}")

def updatePlayersList(players: Map[String, PlayerState]): Unit =
  val playersListElem = getElementById("playersList")
  val gamePlayersElem = getElementById("gamePlayers")

  Try:
    val playersArray = players.values.toSeq.sortBy(p => -p.score)

    val playersHTML = playersArray
      .map: player =>
        s"""
        <div class="player-card">
          <span class="player-name">${player.name}</span>
          <span class="player-score">${player.score} pts (${player.roundsWon} wins)</span>
        </div>
      """
      .mkString("")

    playersListElem.foreach(_.innerHTML = playersHTML)
    gamePlayersElem.foreach(_.innerHTML = playersHTML)
  .recover:
    case ex => println(s"[ColorRush] Error updating players list: ${ex.getMessage}")

def updateRoundDisplay(roundNumber: Int, round: Round, isRoundEnd: Boolean): Unit =
  getElementById("roundNumber").foreach: elem =>
    elem.textContent = roundNumber.toString

  getElementById("targetColor").foreach: elem =>
    elem.style.backgroundColor = round.targetColor

  // Update color grid
  getElementById("colorGrid").foreach: grid =>
    // Clear existing buttons
    grid.innerHTML = ""

    // Create buttons with proper event listeners
    round.colorOptions.foreach: color =>
      val button = document.createElement("button").asInstanceOf[dom.HTMLButtonElement]
      button.className = "color-button"
      button.style.backgroundColor = color
      button.disabled = isRoundEnd

      if !isRoundEnd then button.addEventListener("click", (e: Event) => clickColor(color))

      grid.appendChild(button)

def startGame(): Unit =
  gameWebSocket.foreach: ws =>
    sendMessage(ws, StartMessage())

def clickColor(color: String): Unit =
  gameWebSocket.foreach: ws =>
    sendMessage(ws, ClickMessage(color, System.currentTimeMillis()))

def showRoundWinner(playerName: String, points: Int): Unit =
  getElementById("winnerAnnouncement").foreach: announcement =>
    announcement.classList.remove("hidden")

    getElementById("winnerName").foreach: elem =>
      elem.textContent = s"$playerName wins!"

    getElementById("winnerPoints").foreach: elem =>
      elem.textContent = s"+$points points"

    // Hide after 2 seconds and request next round
    js.timers.setTimeout(2000):
      announcement.classList.add("hidden")
      gameWebSocket.foreach: ws =>
        if ws.readyState == WebSocket.OPEN then sendMessage(ws, NextRoundMessage())

def showGameWinner(winnerOpt: Option[PlayerState]): Unit =
  val message = winnerOpt match
    case Some(winner) =>
      s"🎉 GAME OVER!\\n\\nWinner: ${winner.name}\\nScore: ${winner.score} points\\nRounds Won: ${winner.roundsWon}"
    case None         =>
      "Game Over!"

  showAlert(message)

  // Reload page after 3 seconds
  js.timers.setTimeout(3000):
    window.location.reload()

def updateLobbyUI(): Unit =
  // Hide the join form container
  getElementById("joinFormContainer").foreach: container =>
    container.classList.add("hidden")

  // Show the waiting area container
  getElementById("waitingArea").foreach: area =>
    area.classList.remove("hidden")

def showGameArea(): Unit =
  getElementById("lobby").foreach(_.classList.add("hidden"))
  getElementById("gameArea").foreach(_.classList.remove("hidden"))
  getElementById("gamePlayers").foreach(_.classList.remove("hidden"))

// Helper functions for DOM manipulation
def getElementById(id: String): Option[HTMLElement] =
  Option(document.getElementById(id).asInstanceOf[HTMLElement])

def getElement(id: String): Option[dom.Element] =
  Option(document.getElementById(id))

def getInputElement(id: String): Option[HTMLInputElement] =
  Option(document.getElementById(id).asInstanceOf[HTMLInputElement])

def getInputValue(id: String): Option[String] =
  getInputElement(id).map(_.value)

def showAlert(message: String): Unit =
  window.alert(message)

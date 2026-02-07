package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.*

import scala.scalajs.js
import scala.util.Try

def initializeGame(): Unit =
  println("[ColorRush] Starting Color Rush client...")
  buildGameUI()
  setupJoinForm()
  setupEnterKeyHandler()
  setupStartButton()

var gameWebSocket: Option[WebSocket] = None
var currentGameId: Option[String]    = None
var currentRoundId: Option[String]   = None


def buildGameUI(): Unit =
  // Clear existing content
  document.body.innerHTML = ""
  
  // Create main container
  val container = document.createElement("div").asInstanceOf[HTMLElement]
  container.className = "container"
  
  // Create title
  val title = document.createElement("h1").asInstanceOf[HTMLElement]
  title.textContent = "Color Rush"
  container.appendChild(title)
  
  // Create subtitle
  val subtitle = document.createElement("p").asInstanceOf[HTMLElement]
  subtitle.className = "subtitle"
  subtitle.textContent = "Be the fastest to click the matching color"
  container.appendChild(subtitle)
  
  // Create lobby
  container.appendChild(createLobby())
  
  // Create game area
  container.appendChild(createGameArea())
  
  // Create winner announcement
  container.appendChild(createWinnerAnnouncement())
  
  document.body.appendChild(container)

def createLobby(): HTMLElement =
  val lobby = document.createElement("div").asInstanceOf[HTMLElement]
  lobby.id = "lobby"
  lobby.className = "lobby"
  
  val heading = document.createElement("h2").asInstanceOf[HTMLElement]
  heading.textContent = "Join Game"
  lobby.appendChild(heading)
  
  // Create join form
  val form = document.createElement("form").asInstanceOf[HTMLFormElement]
  form.id = "joinForm"
  
  val gameIdInput = document.createElement("input").asInstanceOf[HTMLInputElement]
  gameIdInput.`type` = "text"
  gameIdInput.id = "gameId"
  gameIdInput.placeholder = "Game ID (e.g., game123)"
  gameIdInput.value = "game1"
  form.appendChild(gameIdInput)
  
  val playerNameInput = document.createElement("input").asInstanceOf[HTMLInputElement]
  playerNameInput.`type` = "text"
  playerNameInput.id = "playerName"
  playerNameInput.placeholder = "Your Name"
  form.appendChild(playerNameInput)
  
  val submitButton = document.createElement("button").asInstanceOf[HTMLButtonElement]
  submitButton.`type` = "submit"
  submitButton.textContent = "Join Game"
  form.appendChild(submitButton)
  
  lobby.appendChild(form)
  
  // Create waiting area
  val waitingArea = document.createElement("div").asInstanceOf[HTMLElement]
  waitingArea.id = "waitingArea"
  waitingArea.className = "hidden"
  
  val waitingHeading = document.createElement("h3").asInstanceOf[HTMLElement]
  waitingHeading.textContent = "Players in Lobby:"
  waitingArea.appendChild(waitingHeading)
  
  val playersList = document.createElement("div").asInstanceOf[HTMLElement]
  playersList.id = "playersList"
  playersList.className = "players"
  waitingArea.appendChild(playersList)
  
  val startButton = document.createElement("button").asInstanceOf[HTMLButtonElement]
  startButton.id = "startButton"
  startButton.className = "start-button"
  startButton.textContent = "Start Game"
  waitingArea.appendChild(startButton)
  
  lobby.appendChild(waitingArea)
  
  lobby

def createGameArea(): HTMLElement =
  val gameArea = document.createElement("div").asInstanceOf[HTMLElement]
  gameArea.id = "gameArea"
  gameArea.className = "game-area hidden"
  
  // Round info
  val roundInfo = document.createElement("div").asInstanceOf[HTMLElement]
  roundInfo.className = "round-info"
  
  val roundNumber = document.createElement("div").asInstanceOf[HTMLElement]
  roundNumber.className = "round-number"
  roundNumber.innerHTML = "Round <span id=\"roundNumber\">1</span> of 10"
  roundInfo.appendChild(roundNumber)
  
  val targetColorLabel = document.createElement("div").asInstanceOf[HTMLElement]
  targetColorLabel.className = "target-color-label"
  targetColorLabel.textContent = "Click this color:"
  roundInfo.appendChild(targetColorLabel)
  
  val targetColor = document.createElement("div").asInstanceOf[HTMLElement]
  targetColor.id = "targetColor"
  targetColor.className = "target-color"
  roundInfo.appendChild(targetColor)
  
  gameArea.appendChild(roundInfo)
  
  // Color grid
  val colorGrid = document.createElement("div").asInstanceOf[HTMLElement]
  colorGrid.id = "colorGrid"
  colorGrid.className = "color-grid"
  gameArea.appendChild(colorGrid)
  
  // Players list in game
  val gamePlayers = document.createElement("div").asInstanceOf[HTMLElement]
  gamePlayers.id = "gamePlayers"
  gamePlayers.className = "players"
  gameArea.appendChild(gamePlayers)
  
  gameArea

def createWinnerAnnouncement(): HTMLElement =
  val announcement = document.createElement("div").asInstanceOf[HTMLElement]
  announcement.id = "winnerAnnouncement"
  announcement.className = "winner-announcement hidden"
  
  val winnerName = document.createElement("h2").asInstanceOf[HTMLElement]
  winnerName.id = "winnerName"
  announcement.appendChild(winnerName)
  
  val winnerPoints = document.createElement("p").asInstanceOf[HTMLElement]
  winnerPoints.id = "winnerPoints"
  winnerPoints.className = "points"
  announcement.appendChild(winnerPoints)
  
  announcement


def setupJoinForm(): Unit =
  getElement("joinForm").foreach: form =>
    form.addEventListener(
      "submit",
      (e: Event) =>
        e.preventDefault()
        joinGame()
    )

def setupStartButton(): Unit =
  getElement("startButton").foreach: button =>
    button.addEventListener("click", (e: Event) => startGame())

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
    case (Some(gameId), Some(playerName)) if gameId.nonEmpty && playerName.nonEmpty =>
      currentGameId = Some(gameId)
      connectToGame(gameId, playerName)
    case _                                                                          =>
      showAlert("Please enter both Game ID and your name")

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
    import upickle.default.*
    val json = write(msg)
    println(s"[ColorRush] Sending message: $json")
    ws.send(json)
  .recover:
    case ex => println(s"[ColorRush] Failed to send message: ${ex.getMessage}")

def handleWebSocketMessage(data: String): Unit =
  Try:
    import upickle.default.*
    val serverMsg = read[ServerMessage](data)

    serverMsg match
      case GameUpdateMessage(game) =>
        parseGameUpdate(game)

      case RoundWinnerMessage(playerName, points) =>
        showRoundWinner(playerName, points)

      case GameEndMessage(winner) =>
        showGameWinner(winner)
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
    val playersArray = players.values.toSeq
      .sortBy(p => -p.score)

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
  getElementById("lobby").foreach: lobby =>
    lobby.querySelector("h2").asInstanceOf[HTMLElement].textContent = "Waiting for players..."

  getInputElement("gameId").foreach(_.disabled = true)
  getInputElement("playerName").foreach(_.disabled = true)

  getElementById("joinForm").foreach: form =>
    form.style.display = "none"

  getElementById("waitingArea").foreach: area =>
    area.classList.remove("hidden")

def showGameArea(): Unit =
  getElementById("lobby").foreach(_.classList.add("hidden"))
  getElementById("gameArea").foreach(_.classList.remove("hidden"))

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

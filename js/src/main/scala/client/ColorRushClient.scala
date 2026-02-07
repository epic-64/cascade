package client

import shared.*
import org.scalajs.dom
import org.scalajs.dom.{document, window, HTMLElement, HTMLInputElement, WebSocket, MessageEvent, Event, CloseEvent, KeyboardEvent}
import scala.scalajs.js
import scala.util.Try

def initializeGame(): Unit =
  println("[ColorRush] Starting Color Rush client...")
  setupJoinForm()
  setupEnterKeyHandler()
  setupStartButton()

var gameWebSocket: Option[WebSocket] = None
var currentGameId: Option[String] = None
var currentRoundId: Option[String] = None

def setupJoinForm(): Unit =
  getElement("joinForm").foreach: form =>
    form.addEventListener("submit", (e: Event) =>
      e.preventDefault()
      joinGame()
    )

def setupStartButton(): Unit =
  getElement("startButton").foreach: button =>
    button.addEventListener("click", (_: Event) => startGame())

def setupEnterKeyHandler(): Unit =
  document.addEventListener("keydown", (event: KeyboardEvent) =>
    if event.key == "Enter" then
      val waitingArea = getElementById("waitingArea")
      val lobby = getElementById("lobby")

      if !waitingArea.exists(_.classList.contains("hidden")) &&
         !lobby.exists(_.classList.contains("hidden")) then
        event.preventDefault()
        startGame()
  )

def joinGame(): Unit =
  val gameIdOpt = getInputValue("gameId")
  val playerNameOpt = getInputValue("playerName")

  (gameIdOpt, playerNameOpt) match
    case (Some(gameId), Some(playerName)) if gameId.nonEmpty && playerName.nonEmpty =>
      currentGameId = Some(gameId)
      connectToGame(gameId, playerName)
    case _ =>
      showAlert("Please enter both Game ID and your name")

def connectToGame(gameId: String, playerName: String): Unit =
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/game/$gameId"

  val ws = new WebSocket(wsUrl)
  gameWebSocket = Some(ws)

  ws.onopen = (_: Event) =>
    println(s"[ColorRush] Connected to game $gameId")
    sendMessage(ws, JoinMessage(playerName))
    updateLobbyUI()

  ws.onmessage = (event: MessageEvent) =>
    handleWebSocketMessage(event.data.toString)

  ws.onerror = (event: Event) =>
    println(s"[ColorRush] WebSocket error")

  ws.onclose = (_: CloseEvent) =>
    println(s"[ColorRush] Disconnected from game")

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
    val serverMsg = read[shared.ServerMessage](data)
    
    serverMsg match
      case shared.GameUpdateMessage(game) =>
        parseGameUpdate(game)
        
      case shared.RoundWinnerMessage(playerName, points) =>
        showRoundWinner(playerName, points)
        
      case shared.GameEndMessage(winner) =>
        showGameWinner(winner)
  .recover:
    case ex => println(s"[ColorRush] Error handling message: ${ex.getMessage}")

def parseGameUpdate(game: shared.ColorRushGame): Unit =
  Try:
    // Update players list
    updatePlayersList(game.players)

    // Show game area if playing or round end
    game.status match
      case shared.GameStatus.Playing | shared.GameStatus.RoundEnd =>
        showGameArea()

        game.currentRound.foreach: currentRound =>
          val roundId = s"${game.gameId}-${game.roundNumber}"

          // Only update round display if this is a new round
          if !currentRoundId.contains(roundId) then
            currentRoundId = Some(roundId)
            val isRoundEnd = game.status == shared.GameStatus.RoundEnd
            updateRoundDisplay(game.roundNumber, currentRound, isRoundEnd)
      
      case _ => // Waiting or GameOver - keep lobby visible
  .recover:
    case ex => println(s"[ColorRush] Error parsing game update: ${ex.getMessage}")

def updatePlayersList(players: Map[String, shared.PlayerState]): Unit =
  val playersListElem = getElementById("playersList")
  val gamePlayersElem = getElementById("gamePlayers")
  
  Try:
    val playersArray = players.values.toSeq
      .sortBy(-_.score)
    
    val playersHTML = playersArray.map: player =>
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

def updateRoundDisplay(roundNumber: Int, round: shared.Round, isRoundEnd: Boolean): Unit =
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
      
      if !isRoundEnd then
        button.addEventListener("click", (_: Event) => clickColor(color))
      
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
        if ws.readyState == WebSocket.OPEN then
          sendMessage(ws, NextRoundMessage())

def showGameWinner(winnerOpt: Option[shared.PlayerState]): Unit =
  val message = winnerOpt match
    case Some(winner) =>
      s"🎉 GAME OVER!\\n\\nWinner: ${winner.name}\\nScore: ${winner.score} points\\nRounds Won: ${winner.roundsWon}"
    case None =>
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




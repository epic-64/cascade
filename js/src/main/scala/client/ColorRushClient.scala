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
    val json = msg match
      case JoinMessage(name) =>
        js.Dynamic.literal("type" -> "join", "playerName" -> name)
      case StartMessage =>
        js.Dynamic.literal("type" -> "start")
      case ClickMessage(color, time) =>
        js.Dynamic.literal("type" -> "click", "color" -> color, "time" -> time)
      case NextRoundMessage =>
        js.Dynamic.literal("type" -> "nextRound")

    ws.send(js.JSON.stringify(json))
  .recover:
    case ex => println(s"[ColorRush] Failed to send message: ${ex.getMessage}")

enum ServerMessageType:
  case GameUpdate, RoundWinner, GameEnd, Unknown

object ServerMessageType:
  def fromString(s: String): ServerMessageType = s match
    case "gameUpdate" => GameUpdate
    case "roundWinner" => RoundWinner
    case "gameEnd" => GameEnd
    case _ => Unknown

enum GameStatusType:
  case Waiting, Playing, RoundEnd, GameOver, Unknown

object GameStatusType:
  def fromString(s: String): GameStatusType = s match
    case "Waiting" => Waiting
    case "Playing" => Playing
    case "RoundEnd" => RoundEnd
    case "GameOver" => GameOver
    case _ => Unknown

def handleWebSocketMessage(data: String): Unit =
  Try:
    val json = js.JSON.parse(data)
    val msgType = ServerMessageType.fromString(json.`type`.toString)
    
    msgType match
      case ServerMessageType.GameUpdate =>
        parseGameUpdate(json.game)
        
      case ServerMessageType.RoundWinner =>
        (getStringField(json, "playerName"), getIntField(json, "points")) match
          case (Some(name), Some(pts)) => showRoundWinner(name, pts)
          case _ => println("[ColorRush] Invalid round winner message")
        
      case ServerMessageType.GameEnd =>
        val winner = Option(json.winner).filter(isDefined)
        showGameWinner(winner)
        
      case ServerMessageType.Unknown =>
        println(s"[ColorRush] Unknown message type: ${json.`type`}")
  .recover:
    case ex => println(s"[ColorRush] Error handling message: ${ex.getMessage}")

def parseGameUpdate(gameJson: js.Dynamic): Unit =
  Try:
    val gameId = getStringField(gameJson, "gameId").getOrElse("")
    val status = GameStatusType.fromString(gameJson.status.toString)
    val roundNumber = getIntField(gameJson, "roundNumber").getOrElse(0)

    // Update players list
    updatePlayersList(gameJson.players)

    // Show game area if playing or round end
    status match
      case GameStatusType.Playing | GameStatusType.RoundEnd =>
        showGameArea()

        Option(gameJson.currentRound).filter(isDefined).foreach: currentRound =>
          val roundId = s"$gameId-$roundNumber"

          // Only update round display if this is a new round
          if !currentRoundId.contains(roundId) then
            currentRoundId = Some(roundId)
            val isRoundEnd = status == GameStatusType.RoundEnd
            updateRoundDisplay(roundNumber, currentRound, isRoundEnd)
      
      case _ => // Waiting or GameOver or Unknown - keep lobby visible
  .recover:
    case ex => println(s"[ColorRush] Error parsing game update: ${ex.getMessage}")

def updatePlayersList(playersObj: js.Dynamic): Unit =
  val playersListElem = getElementById("playersList")
  val gamePlayersElem = getElementById("gamePlayers")
  
  Try:
    // Convert players object to dictionary and get values
    val playersDict = playersObj.asInstanceOf[js.Dictionary[js.Dynamic]]
    val playersArray = playersDict.values.toSeq
      .sortBy(p => -getIntField(p, "score").getOrElse(0))
    
    val playersHTML = playersArray.map: player =>
      val name = getStringField(player, "name").getOrElse("Unknown")
      val score = getIntField(player, "score").getOrElse(0)
      val roundsWon = getIntField(player, "roundsWon").getOrElse(0)
      s"""
        <div class="player-card">
          <span class="player-name">$name</span>
          <span class="player-score">$score pts ($roundsWon wins)</span>
        </div>
      """
    .mkString("")
    
    playersListElem.foreach(_.innerHTML = playersHTML)
    gamePlayersElem.foreach(_.innerHTML = playersHTML)
  .recover:
    case ex => println(s"[ColorRush] Error updating players list: ${ex.getMessage}")

def updateRoundDisplay(roundNumber: Int, round: js.Dynamic, isRoundEnd: Boolean): Unit =
  getElementById("roundNumber").foreach: elem =>
    elem.textContent = roundNumber.toString
  
  getStringField(round, "targetColor").foreach: targetColor =>
    getElementById("targetColor").foreach: elem =>
      elem.style.backgroundColor = targetColor
  
  // Update color grid
  getElementById("colorGrid").foreach: grid =>
    val colorOptions = round.colorOptions.asInstanceOf[js.Array[String]]
    
    // Clear existing buttons
    grid.innerHTML = ""
    
    // Create buttons with proper event listeners
    colorOptions.foreach: color =>
      val button = document.createElement("button").asInstanceOf[dom.HTMLButtonElement]
      button.className = "color-button"
      button.style.backgroundColor = color
      button.disabled = isRoundEnd
      
      if !isRoundEnd then
        button.addEventListener("click", (_: Event) => clickColor(color))
      
      grid.appendChild(button)

def startGame(): Unit =
  gameWebSocket.foreach: ws =>
    sendMessage(ws, StartMessage)

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
          sendMessage(ws, NextRoundMessage)

def showGameWinner(winnerOpt: Option[js.Dynamic]): Unit =
  val message = winnerOpt match
    case Some(winner) =>
      val name = getStringField(winner, "name").getOrElse("Unknown")
      val score = getIntField(winner, "score").getOrElse(0)
      val roundsWon = getIntField(winner, "roundsWon").getOrElse(0)
      s"🎉 GAME OVER!\\n\\nWinner: $name\\nScore: $score points\\nRounds Won: $roundsWon"
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

// Helper functions for safer JS value extraction
def getIntField(obj: js.Dynamic, field: String): Option[Int] =
  Try(obj.selectDynamic(field).toString.toInt).toOption

def getStringField(obj: js.Dynamic, field: String): Option[String] =
  Try(obj.selectDynamic(field).toString).toOption

def isDefined(value: js.Dynamic): Boolean =
  !js.isUndefined(value) && value != null



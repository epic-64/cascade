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

  document.body(
    NavigationBar.render("Color Rush"),
    el("div", cls = "container")(
      createLobby(),
      createGameArea(),
      createPlayersSidebar(),
      createRoundWinnerAnnouncement(),
      createGameWinnerAnnouncement()
    )
  )

def createLobby(): HTMLElement =
  el("div", id = "lobby")(
    createJoinForm(),
    createWaitingArea()
  )

def createJoinForm(): HTMLElement =
  val joinGameListener = (e: Event) =>
    e.preventDefault()
    joinGame()

  el("div", id = "joinFormContainer", cls = "join-form-container")(
    el("h2", content = "Join Game"),
    form(id = "joinForm").tap(_.addEventListener("submit", joinGameListener))(
      input("text", id = "gameId").tap: el =>
        el.placeholder = "Game ID (e.g., game123)"
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
  )

def createWaitingArea(): HTMLElement =
  // Waiting area container
  el("div", id = "waitingArea", cls = "waiting-area-container hidden")(
    el("h3", content = "Players in Lobby:"),
    el("div", id = "playersList", cls = "players"),
    el("div", cls = "game-settings")(
      el("label")(
        el("span", content = "Number of Rounds: "),
        el("select", id = "roundsSelector").tap: select =>
          select.addEventListener("change", (e: Event) => updateGameSettings())
          Vector(1, 3, 5, 10, 15).foreach: rounds =>
            val option = el("option").asInstanceOf[dom.HTMLOptionElement].tap: o =>
              o.value = rounds.toString
              o.textContent = rounds.toString
              if rounds == 5 then o.selected = true
              select.appendChild(o)
      )
    ),
    button(id = "startButton", cls = "start-button").tap: btn =>
      btn.textContent = "Start Game"
      btn.addEventListener("click", (e: Event) => startGame())
  )

def createGameArea(): HTMLElement =
  el("div", id = "gameArea", cls = "game-area hidden")(
    // Game controls at the top
    el("div").with_classes("game-controls")(
      button().with_classes("secondary-button").tap: btn =>
        btn.textContent = "Return to Lobby"
        btn.addEventListener("click", (e: Event) => returnToLobby())
      ,
      button(id = "showWinnerButton").with_classes("secondary-button hidden").tap: btn =>
        btn.textContent = "Show Results"
        btn.addEventListener("click", (e: Event) => reshowGameWinner())
    ),
    // Round info
    el("div", cls = "round-info")(
      el("div", cls = "round-number")(
        el("span", content = "Round "),
        el("span", id = "roundNumber", content = "1"),
        el("span", content = " of "),
        el("span", id = "totalRounds", content = "10")
      ),
      el("div", cls = "target-color-label", content = "Click this color:"),
      el("div", id = "targetColor", cls = "target-color")
    ),
    // Color grid
    el("div", id = "colorGrid", cls = "color-grid")
  )

def createPlayersSidebar(): HTMLElement =
  el("div").with_id("gamePlayers").with_classes("players hidden")

def createRoundWinnerAnnouncement(): HTMLElement =
  el("div").with_id("winnerAnnouncement").with_classes("winner-announcement hidden")(
    el("h2").with_id("winnerName"),
    el("p").with_id("winnerPoints").with_classes("points")
  )

def createGameWinnerAnnouncement(): HTMLElement =
  val announcement = el("div").with_id("gameWinnerAnnouncement").with_classes("game-winner-announcement hidden")(
    el("div").with_classes("game-winner-content")(
      el("h1").with_id("gameWinnerTitle"),
      el("div").with_classes("game-winner-details")(
        el("p").with_id("gameWinnerName").with_classes("winner-name"),
        el("p").with_id("gameWinnerScore").with_classes("winner-score"),
        el("p").with_id("gameWinnerRounds").with_classes("winner-rounds")
      ),
      el("button").with_classes("close-winner-button").tap: btn =>
        btn.textContent = "Close"
        btn.addEventListener("click", (e: Event) => hideGameWinner())
    )
  )

  // Click outside to close
  announcement.addEventListener("click", (e: Event) =>
    if e.target == announcement then hideGameWinner()
  )

  announcement

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

    // Get totalRounds from UI and send with join message
    val totalRounds = getInputValue("roundsSelector")
      .flatMap(s => Try(s.toInt).toOption)
      .getOrElse(5) // Fallback to 5 if something goes wrong

    sendMessage(ws, JoinMessage(playerName, totalRounds))
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
    updatePlayersList(game.players, game.status)

    // Show game area if playing or round end
    game.status match
      case GameStatus.Playing | GameStatus.RoundEnd =>
        showGameArea()
        game.currentRound.foreach: round =>
          val roundId = s"${game.gameId}-${game.roundNumber}"

          // Only update round display if this is a new round
          if !currentRoundId.contains(roundId) then
            currentRoundId = Some(roundId)
            val isRoundEnd = game.status == GameStatus.RoundEnd
            updateRoundDisplay(game.roundNumber, game.totalRounds, round, isRoundEnd)

      case _ => () // Waiting or GameOver - keep lobby visible
  .recover:
    case ex => println(s"[ColorRush] Error parsing game update: ${ex.getMessage}")

def updatePlayersList(players: Map[String, PlayerState], gameStatus: GameStatus): Unit =
  val playersListElem = getElementById("playersList")
  val gamePlayersElem = getElementById("gamePlayers")

  Try:
    val playersArray = players.values.toSeq.sortBy(p => (-p.score, -p.roundsWon))

    // Determine the winner (highest score, then most rounds won)
    val winner = playersArray.headOption

    // Only show crown if game is over
    val showCrown = gameStatus == GameStatus.GameOver

    val playersHTML = playersArray
      .map: player =>
        val crown = if showCrown && winner.contains(player) then "👑 " else ""
        s"""
        <div class="player-card">
          <span class="player-name">$crown${player.name}</span>
          <span class="player-score">${player.score} pts (${player.roundsWon} wins)</span>
        </div>
      """
      .mkString("")

    playersListElem.foreach(_.innerHTML = playersHTML)
    gamePlayersElem.foreach(_.innerHTML = playersHTML)
  .recover:
    case ex => println(s"[ColorRush] Error updating players list: ${ex.getMessage}")

def updateRoundDisplay(roundNumber: Int, totalRounds: Int, round: Round, isRoundEnd: Boolean): Unit =
  getElementById("roundNumber").foreach: elem =>
    elem.textContent = roundNumber.toString

  getElementById("totalRounds").foreach: elem =>
    elem.textContent = totalRounds.toString

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

def updateGameSettings(): Unit =
  getInputValue("roundsSelector").foreach: roundsStr =>
    Try(roundsStr.toInt).toOption.foreach: totalRounds =>
      gameWebSocket.foreach: ws =>
        sendMessage(ws, ConfigureMessage(totalRounds))

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
  getElementById("gameWinnerAnnouncement").foreach: announcement =>
    announcement.classList.remove("hidden")

    winnerOpt match
      case Some(winner) =>
        getElementById("gameWinnerTitle").foreach: elem =>
          elem.textContent = "🎉 GAME OVER!"

        getElementById("gameWinnerName").foreach: elem =>
          elem.textContent = s"Winner: ${winner.name}"

        getElementById("gameWinnerScore").foreach: elem =>
          elem.textContent = s"Score: ${winner.score} points"

        getElementById("gameWinnerRounds").foreach: elem =>
          elem.textContent = s"Rounds Won: ${winner.roundsWon}"

      case None =>
        getElementById("gameWinnerTitle").foreach: elem =>
          elem.textContent = "Game Over!"

        getElementById("gameWinnerName").foreach(_.textContent = "")
        getElementById("gameWinnerScore").foreach(_.textContent = "")
        getElementById("gameWinnerRounds").foreach(_.textContent = "")

    // Show the "Show Results" button
    getElementById("showWinnerButton").foreach(_.classList.remove("hidden"))

def hideGameWinner(): Unit =
  getElementById("gameWinnerAnnouncement").foreach: announcement =>
    announcement.classList.add("hidden")

def reshowGameWinner(): Unit =
  getElementById("gameWinnerAnnouncement").foreach: announcement =>
    announcement.classList.remove("hidden")

def returnToLobby(): Unit =
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

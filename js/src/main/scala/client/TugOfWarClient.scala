package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.TugOfWar.*
import shared.session.BasicGameSession
import client.{el, form, input, button, *}
import client.session.{SessionManager, WebSocketKeepAlive}
import client.components.ShareableLink

import scala.scalajs.js
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

// Session key for TugOfWar
private val TugOfWarSessionKey = "tugOfWar"

def initializeTugOfWar(lobbyIdFromUrl: Option[String] = None): Unit =
  println("[TugOfWar] Starting Tug of War client...")
  buildTugOfWarUI()
  setupTugOfWarEnterKeyHandler()

  // Session takes priority over URL (for page refresh during game)
  // URL is only used for initial join via shared link
  loadTugOfWarSession() match
    case Some(session) =>
      println(s"[TugOfWar] Found existing session - attempting rejoin: gameId=${session.gameId}")
      towIsRejoining = true
      towGameId = Some(session.gameId)
      towPlayerId = Some(session.playerId)
      attemptTugOfWarRejoin(session.gameId, session.playerId, session.playerName)
    case None =>
      lobbyIdFromUrl match
        case Some(lobbyId) =>
          // Pre-fill join form and switch to join tab
          println(s"[TugOfWar] Pre-filling lobby ID from URL: $lobbyId")
          getElementById("towJoinGameId").foreach(_.asInstanceOf[HTMLInputElement].value = lobbyId)
          switchTugOfWarTab("join")
          getElementById("towJoinPlayerName").foreach(_.focus())
        case None =>
          println("[TugOfWar] No existing session found")

var towWebSocket: Option[WebSocket] = None
var towGameId: Option[String] = None
var towPlayerId: Option[String] = None
var towPlayerName: Option[String] = None
var towPlayerTeam: Option[Team] = None
var towIsRejoining: Boolean = false
var towCurrentGame: Option[TugOfWarGame] = None

// WebSocket keepalive
val towKeepAlive: WebSocketKeepAlive = WebSocketKeepAlive.forWebSocket(
  "TugOfWar",
  () => towWebSocket,
  () => upickle.default.write(PingMessage())
)

// Session management
def saveTugOfWarSession(playerId: String, gameId: String, playerName: String): Unit =
  SessionManager.save(TugOfWarSessionKey, BasicGameSession(playerId, gameId, playerName))

def loadTugOfWarSession(): Option[BasicGameSession] =
  SessionManager.load(TugOfWarSessionKey)

def clearTugOfWarSession(): Unit =
  SessionManager.clear(TugOfWarSessionKey)

def checkForExistingTugOfWarSession(): Unit =
  loadTugOfWarSession() match
    case Some(session) =>
      println(s"[TugOfWar] Found existing session - attempting rejoin: gameId=${session.gameId}")
      towIsRejoining = true
      towGameId = Some(session.gameId)
      towPlayerId = Some(session.playerId)
      attemptTugOfWarRejoin(session.gameId, session.playerId, session.playerName)
    case None =>
      println("[TugOfWar] No existing session found")

def attemptTugOfWarRejoin(gameId: String, playerId: String, playerName: String): Unit =
  towPlayerName = Some(playerName)
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/tug-of-war/$gameId"

  val ws = new WebSocket(wsUrl)
  towWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[TugOfWar] Connected, attempting rejoin to game $gameId")
    sendTugOfWarMessage(ws, RejoinMessage(playerId, gameId))
    towKeepAlive.start()

  ws.onmessage = (event: MessageEvent) => handleTugOfWarWebSocketMessage(event.data.toString)
  ws.onerror = (event: Event) => println(s"[TugOfWar] WebSocket error during rejoin")
  ws.onclose = (e: CloseEvent) =>
    println(s"[TugOfWar] Disconnected from game")
    towKeepAlive.stop()
    scheduleTugOfWarReconnect()

// ============================================================================
// UI Building
// ============================================================================

def buildTugOfWarUI(): Unit =
  document.body.innerHTML = ""

  document.body(
    NavigationBar.render("Tug of War"),
    div(cls = "container")(
      createTugOfWarLobby(),
      createTugOfWarGameArea(),
      createTugOfWarRoundWinner(),
      createTugOfWarGameWinner()
    )
  )

def createTugOfWarLobby(): HTMLElement =
  div(id = "towLobby")(
    createTugOfWarLobbySetup(),
    createTugOfWarWaitingArea()
  )

def switchTugOfWarTab(tab: String): Unit =
  tab match
    case "join" =>
      getElementById("towJoinTab").foreach(_.classList.add("active"))
      getElementById("towCreateTab").foreach(_.classList.remove("active"))
      getElementById("towJoinTabContent").foreach(_.classList.add("active"))
      getElementById("towCreateTabContent").foreach(_.classList.remove("active"))
    case "create" =>
      getElementById("towCreateTab").foreach(_.classList.add("active"))
      getElementById("towJoinTab").foreach(_.classList.remove("active"))
      getElementById("towCreateTabContent").foreach(_.classList.add("active"))
      getElementById("towJoinTabContent").foreach(_.classList.remove("active"))
    case _ => ()

def createTugOfWarLobbySetup(): HTMLElement =
  div(id = "towLobbySetup", cls = "lobby-setup")(
    h2(content = "🪢 Tug of War"),
    p(cls = "subtitle", content = "Pull the rope to your side to win!"),
    div(cls = "tabs")(
      button(id = "towJoinTab", cls = "tab-btn active", content = "Join Game").tap: btn =>
        btn.addEventListener("click", (e: Event) => switchTugOfWarTab("join")),
      button(id = "towCreateTab", cls = "tab-btn", content = "Create Game").tap: btn =>
        btn.addEventListener("click", (e: Event) => switchTugOfWarTab("create"))
    ),
    div(cls = "tab-content")(
      div(id = "towJoinTabContent", cls = "tab-pane active")(
        form(id = "towJoinForm").tap(_.addEventListener(
          "submit",
          (e: Event) =>
            e.preventDefault()
            joinTugOfWarGame()
        ))(
          floatingInput("text", id = "towJoinGameId", label = "Game Code").tap: field =>
            val inp = field.querySelector("input").asInstanceOf[HTMLInputElement]
            inp.required = true
            inp.autocomplete = "off"
          ,
          floatingInput("text", id = "towJoinPlayerName", label = "Your Name").tap: field =>
            val inp = field.querySelector("input").asInstanceOf[HTMLInputElement]
            inp.required = true
            inp.autocomplete = "off"
          ,
          button("submit", content = "Join Game")
        )
      ),
      div(id = "towCreateTabContent", cls = "tab-pane")(
        form(id = "towCreateForm").tap(_.addEventListener(
          "submit",
          (e: Event) =>
            e.preventDefault()
            createTugOfWarGame()
        ))(
          floatingInput("text", id = "towCreatePlayerName", label = "Your Name").tap: field =>
            val inp = field.querySelector("input").asInstanceOf[HTMLInputElement]
            inp.required = true
            inp.autocomplete = "off"
          ,
          div(cls = "select-row")(
            el("label").tap: lbl =>
              lbl.setAttribute("for", "towCreateRounds")
              lbl.textContent = "Rounds to Win:"
            ,
            el("select", id = "towCreateRounds").tap: select =>
              Vector(1, 2, 3, 5, 7).foreach: rounds =>
                val option = el("option").asInstanceOf[dom.HTMLOptionElement].tap: o =>
                  o.value = rounds.toString
                  o.textContent = rounds.toString
                  if rounds == 3 then o.selected = true
                  select.appendChild(o)
          ),
          div(cls = "select-row")(
            el("label").tap: lbl =>
              lbl.setAttribute("for", "towCreateTimeLimit")
              lbl.textContent = "Time per Round:"
            ,
            el("select", id = "towCreateTimeLimit").tap: select =>
              Vector((0, "No Limit"), (10, "10 seconds"), (20, "20 seconds"), (30, "30 seconds"), (60, "60 seconds")).foreach: (secs, label) =>
                val option = el("option").asInstanceOf[dom.HTMLOptionElement].tap: o =>
                  o.value = secs.toString
                  o.textContent = label
                  if secs == 20 then o.selected = true
                  select.appendChild(o)
          ),
          button("submit", content = "Create Game")
        )
      )
    )
  )

def createTugOfWarWaitingArea(): HTMLElement =
  div(id = "towWaitingArea", cls = "waiting-area hidden")(
    h4(content = "Tug of War Lobby"),
    div(id = "towLobbyCode"),

    // Game settings (readonly display)
    div(id = "towLobbySettings", cls = "lobby-settings"),

    // Team selection
    h4(content = "Choose Your Team"),
    div(cls = "tow-team-selector")(
      button(id = "towSelectRed", cls = "tow-team-btn team-red", content = "🔴 RED TEAM").tap: btn =>
        btn.addEventListener("click", (e: Event) => selectTeam(Team.Red)),
      button(id = "towSelectBlue", cls = "tow-team-btn team-blue", content = "🔵 BLUE TEAM").tap: btn =>
        btn.addEventListener("click", (e: Event) => selectTeam(Team.Blue))
    ),

    // Team lists
    div(cls = "tow-teams-container")(
      div(id = "towRedTeamList", cls = "tow-team-list red")(
        div(id = "towRedHeader", cls = "tow-team-header red", content = "🔴 Red Team (0)"),
        div(id = "towRedPlayers", cls = "players-container")
      ),
      div(id = "towBlueTeamList", cls = "tow-team-list blue")(
        div(id = "towBlueHeader", cls = "tow-team-header blue", content = "🔵 Blue Team (0)"),
        div(id = "towBluePlayers", cls = "players-container")
      )
    ),


    // Start button and leave button
    div(cls = "lobby-buttons")(
      button(cls = "btn btn-secondary").tap: btn =>
        btn.textContent = "Leave Lobby"
        btn.addEventListener("click", (e: Event) => leaveTugOfWarLobby())
      ,
      button(id = "towStartButton", cls = "btn btn-success").tap: btn =>
        btn.textContent = "Start Game"
        btn.addEventListener("click", (e: Event) => startTugOfWarGame())
    ),
    div(id = "towStartHint", cls = "tow-waiting-message", content = "Need at least one player on each team to start")
  )

def createTugOfWarGameArea(): HTMLElement =
  div(id = "towGameArea", cls = "game-area hidden")(
    // Game controls
    div(cls = "game-controls")(
      button(cls = "btn btn-secondary").tap: btn =>
        btn.textContent = "Return to Lobby"
        btn.addEventListener("click", (e: Event) => returnToTugOfWarLobby())
    ),

    // Scoreboard
    div(id = "towScoreboard", cls = "tow-scoreboard")(
      div(cls = "tow-team-score red")(
        div(cls = "team-name", content = "RED"),
        div(id = "towRedRounds", cls = "rounds-won", content = "0")
      ),
      div(cls = "tow-round-info")(
        div(cls = "round-label", content = "Round"),
        div(id = "towCurrentRound", cls = "round-number", content = "1"),
        span(content = " of "),
        span(id = "towTotalRounds", content = "3"),
        div(id = "towTimer", cls = "timer hidden")
      ),
      div(cls = "tow-team-score blue")(
        div(cls = "team-name", content = "BLUE"),
        div(id = "towBlueRounds", cls = "rounds-won", content = "0")
      )
    ),

    // Rope visualization
    div(id = "towRopeContainer", cls = "tow-rope-container")(
      div(cls = "tow-goal tow-goal-red", content = "🏁"),
      div(cls = "tow-rope-track")(
        div(cls = "tow-rope-texture")
      ),
      div(id = "towMarker", cls = "tow-marker"),
      div(cls = "tow-goal tow-goal-blue", content = "🏁")
    ),

    // Position indicator
    div(id = "towPositionIndicator", cls = "tow-position-indicator")(
      span(content = "Position: "),
      span(id = "towPositionValue", cls = "position-value neutral", content = "0")
    ),

    // Click area
    div(cls = "tow-click-area")(
      button(id = "towClickButton", cls = "tow-click-button").tap: btn =>
        btn.addEventListener("click", (e: Event) => handleTugOfWarClick())
        btn.addEventListener("mousedown", (e: Event) => e.preventDefault()) // Prevent text selection
      ,
      div(id = "towClickStats", cls = "tow-click-stats")(
        div(cls = "tow-stat red")(
          div(cls = "stat-label", content = "Red Clicks"),
          div(id = "towRedClicks", cls = "stat-value", content = "0")
        ),
        div(cls = "tow-stat")(
          div(cls = "stat-label", content = "Your Clicks"),
          div(id = "towYourClicks", cls = "stat-value", content = "0")
        ),
        div(cls = "tow-stat blue")(
          div(cls = "stat-label", content = "Blue Clicks"),
          div(id = "towBlueClicks", cls = "stat-value", content = "0")
        )
      )
    )
  )

def createTugOfWarRoundWinner(): HTMLElement =
  div(id = "towRoundWinner", cls = "tow-round-winner hidden")(
    div(id = "towRoundWinnerContent", cls = "tow-round-winner-content")(
      div(cls = "tow-round-winner-title", content = "Round Winner!"),
      div(id = "towRoundWinnerTeam", cls = "tow-round-winner-team"),
      div(id = "towRoundStats", cls = "tow-round-stats")(
        div(cls = "tow-stat red")(
          div(cls = "stat-label", content = "Red Clicks"),
          div(id = "towRoundRedClicks", cls = "stat-value", content = "0")
        ),
        div(cls = "tow-stat blue")(
          div(cls = "stat-label", content = "Blue Clicks"),
          div(id = "towRoundBlueClicks", cls = "stat-value", content = "0")
        )
      ),
      button(cls = "btn", content = "Next Round").tap: btn =>
        btn.addEventListener("click", (e: Event) => requestNextRound())
    )
  )

def createTugOfWarGameWinner(): HTMLElement =
  val announcement = div(id = "towGameWinner", cls = "tow-game-winner hidden")(
    div(id = "towGameWinnerContent", cls = "tow-game-winner-content")(
      div(cls = "tow-game-winner-title", content = "🏆 Game Over! 🏆"),
      div(id = "towGameWinnerTeam", cls = "tow-game-winner-team"),
      div(id = "towFinalScore", cls = "tow-final-score"),
      button(cls = "btn", content = "Close").tap: btn =>
        btn.addEventListener("click", (e: Event) => hideTugOfWarGameWinner())
    )
  )

  announcement.addEventListener(
    "click",
    (e: Event) =>
      if e.target == announcement then hideTugOfWarGameWinner()
  )

  announcement

def setupTugOfWarEnterKeyHandler(): Unit =
  document.addEventListener(
    "keydown",
    (event: KeyboardEvent) =>
      if event.key == "Enter" then
        val waitingArea = getElementById("towWaitingArea")
        val lobby = getElementById("towLobby")

        if !waitingArea.exists(_.classList.contains("hidden")) &&
          !lobby.exists(_.classList.contains("hidden"))
        then
          event.preventDefault()
          startTugOfWarGame()
  )

// ============================================================================
// Game Actions
// ============================================================================

def joinTugOfWarGame(): Unit =
  val gameIdOpt = getInputValue("towJoinGameId")
  val playerNameOpt = getInputValue("towJoinPlayerName")

  (gameIdOpt, playerNameOpt) match
    case (Some(gameId), Some(playerName)) if gameId.nonEmpty && playerName.nonEmpty =>
      towGameId = Some(gameId.toUpperCase)
      connectToTugOfWarGame(gameId.toUpperCase, playerName)
    case _ =>
      println("[TugOfWar] Missing game ID or player name")

def createTugOfWarGame(): Unit =
  val playerNameOpt = getInputValue("towCreatePlayerName")
  val roundsToWin = getInputValue("towCreateRounds").flatMap(s => Try(s.toInt).toOption).getOrElse(3)
  val timeLimitSeconds = getInputValue("towCreateTimeLimit").flatMap(s => Try(s.toInt).toOption).getOrElse(20)

  playerNameOpt match
    case Some(playerName) if playerName.nonEmpty =>
      val gameId = generateTugOfWarGameCode()
      towGameId = Some(gameId)
      createTugOfWarGameWithConfig(gameId, playerName, roundsToWin, timeLimitSeconds)
    case _ =>
      println("[TugOfWar] Missing player name")

def generateTugOfWarGameCode(): String =
  val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  (1 to 6).map(_ => chars(scala.util.Random.nextInt(chars.length))).mkString

def connectToTugOfWarGame(gameId: String, playerName: String): Unit =
  towPlayerName = Some(playerName)
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/tug-of-war/$gameId"

  val ws = new WebSocket(wsUrl)
  towWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[TugOfWar] Connected to game $gameId")
    sendTugOfWarMessage(ws, JoinMessage(playerName))
    towKeepAlive.start()

  ws.onmessage = (event: MessageEvent) => handleTugOfWarWebSocketMessage(event.data.toString)
  ws.onerror = (event: Event) => println(s"[TugOfWar] WebSocket error")
  ws.onclose = (e: CloseEvent) =>
    println(s"[TugOfWar] Disconnected from game")
    towKeepAlive.stop()
    scheduleTugOfWarReconnect()

def createTugOfWarGameWithConfig(gameId: String, playerName: String, roundsToWin: Int, timeLimitSeconds: Int): Unit =
  towPlayerName = Some(playerName)
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/tug-of-war/$gameId"

  val ws = new WebSocket(wsUrl)
  towWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[TugOfWar] Connected to game $gameId (creating with config)")
    sendTugOfWarMessage(ws, CreateMessage(playerName, roundsToWin, timeLimitSeconds))
    towKeepAlive.start()

  ws.onmessage = (event: MessageEvent) => handleTugOfWarWebSocketMessage(event.data.toString)
  ws.onerror = (event: Event) => println(s"[TugOfWar] WebSocket error")
  ws.onclose = (e: CloseEvent) =>
    println(s"[TugOfWar] Disconnected from game")
    towKeepAlive.stop()
    scheduleTugOfWarReconnect()

def sendTugOfWarMessage(ws: WebSocket, msg: ClientMessage): Unit =
  if ws.readyState == WebSocket.OPEN then
    Try:
      val json = upickle.default.write(msg)
      ws.send(json)
    .recover:
      case ex => println(s"[TugOfWar] Failed to send message: ${ex.getMessage}")
  else
    println(s"[TugOfWar] Cannot send message - WebSocket not open")
    scheduleTugOfWarReconnect()

def sendTugOfWarMessageSafe(msg: ClientMessage): Unit =
  towWebSocket match
    case Some(ws) => sendTugOfWarMessage(ws, msg)
    case None =>
      println("[TugOfWar] No WebSocket connection")
      scheduleTugOfWarReconnect()

def scheduleTugOfWarReconnect(): Unit =
  loadTugOfWarSession() match
    case Some(session) if !towIsRejoining =>
      println(s"[TugOfWar] Scheduling reconnection attempt in 2 seconds...")
      towIsRejoining = true
      js.timers.setTimeout(2000):
        println(s"[TugOfWar] Attempting automatic reconnection...")
        attemptTugOfWarRejoin(session.gameId, session.playerId, session.playerName)
    case Some(_) =>
      println("[TugOfWar] Reconnection already in progress")
    case None =>
      println("[TugOfWar] No session to reconnect")

def selectTeam(team: Team): Unit =
  towWebSocket.foreach: ws =>
    sendTugOfWarMessage(ws, SelectTeamMessage(team))


def startTugOfWarGame(): Unit =
  towWebSocket.foreach: ws =>
    sendTugOfWarMessage(ws, StartMessage())

def handleTugOfWarClick(): Unit =
  towWebSocket.foreach: ws =>
    sendTugOfWarMessage(ws, ClickMessage())

def requestNextRound(): Unit =
  hideTugOfWarRoundWinner()
  towWebSocket.foreach: ws =>
    sendTugOfWarMessage(ws, NextRoundMessage())

def returnToTugOfWarLobby(): Unit =
  towKeepAlive.stop()
  clearTugOfWarSession()
  window.location.reload()

def leaveTugOfWarLobby(): Unit =
  towKeepAlive.stop()
  clearTugOfWarSession()
  towWebSocket.foreach(_.close())
  towWebSocket = None
  window.location.assign("/")

// ============================================================================
// WebSocket Message Handling
// ============================================================================

def handleTugOfWarWebSocketMessage(data: String): Unit =
  Try:
    val serverMsg = upickle.default.read[ServerMessage](data)

    serverMsg match
      case JoinedMessage(playerId, gameId) =>
        println(s"[TugOfWar] Joined - playerId=$playerId, gameId=$gameId")
        towPlayerId = Some(playerId)
        towGameId = Some(gameId)

        val playerName = getInputValue("towJoinPlayerName")
          .orElse(getInputValue("towCreatePlayerName"))
          .orElse(loadTugOfWarSession().map(_.playerName))
          .getOrElse("Player")
        saveTugOfWarSession(playerId, gameId, playerName)

        towIsRejoining = false
        updateTugOfWarLobbyUI()

      case RejoinFailedMessage(reason) =>
        println(s"[TugOfWar] Rejoin failed: $reason")
        towKeepAlive.stop()
        clearTugOfWarSession()
        towIsRejoining = false
        towWebSocket.foreach(_.close())
        towWebSocket = None
        towGameId = None
        towPlayerId = None
        towPlayerName = None
        towPlayerTeam = None

      case GameUpdateMessage(game) =>
        towCurrentGame = Some(game)
        handleTugOfWarGameUpdate(game)

      case PositionUpdateMessage(position, redClicks, blueClicks) =>
        updateRopePosition(position)
        updateClickCounts(redClicks, blueClicks)

      case TimerUpdateMessage(secondsRemaining) =>
        updateTugOfWarTimer(secondsRemaining)

      case RoundEndMessage(winner, result) =>
        showTugOfWarRoundWinner(winner, result)

      case GameEndMessage(winner, redRoundsWon, blueRoundsWon) =>
        showTugOfWarGameWinner(winner, redRoundsWon, blueRoundsWon)

      case ErrorMessage(message) =>
        println(s"[TugOfWar] Error: $message")
        dom.window.alert(s"Error: $message")
  .recover:
    case ex => println(s"[TugOfWar] Error handling message: ${ex.getMessage}")

def handleTugOfWarGameUpdate(game: TugOfWarGame): Unit =
  // Update player's team from game state
  towPlayerId.flatMap(game.players.get).foreach: player =>
    towPlayerTeam = player.team

  game.status match
    case GameStatus.Waiting =>
      updateTugOfWarTeamLists(game)
      updateTugOfWarLobbySettings(game)
      updateTugOfWarStartButton(game)

    case GameStatus.Playing =>
      showTugOfWarGameArea()
      updateTugOfWarScoreboard(game)
      updateRopePosition(game.ropePosition)
      updateClickCounts(
        TugOfWar.getTeamClicks(game, Team.Red),
        TugOfWar.getTeamClicks(game, Team.Blue)
      )
      updateTugOfWarClickButton()
      updateYourClicks(game)
      // Show timer if there's a time limit
      if game.timeLimitSeconds > 0 then
        getElementById("towTimer").foreach(_.classList.remove("hidden"))
      else
        getElementById("towTimer").foreach(_.classList.add("hidden"))
      updateYourClicks(game)

    case GameStatus.RoundEnd =>
      updateTugOfWarScoreboard(game)

    case GameStatus.GameOver =>
      updateTugOfWarScoreboard(game)

// ============================================================================
// UI Updates
// ============================================================================

def updateTugOfWarLobbyUI(): Unit =
  getElementById("towLobbySetup").foreach(_.classList.add("hidden"))

  towGameId.foreach: gameId =>
    getElementById("towLobbyCode").foreach: elem =>
      elem.innerHTML = ""
      elem.appendChild(ShareableLink.render("tug-of-war", gameId))

  getElementById("towWaitingArea").foreach(_.classList.remove("hidden"))

def updateTugOfWarTeamLists(game: TugOfWarGame): Unit =
  val redPlayers = TugOfWar.getTeamPlayers(game, Team.Red)
  val bluePlayers = TugOfWar.getTeamPlayers(game, Team.Blue)
  val unassigned = game.players.values.filter(_.team.isEmpty).toSeq

  // Update team selection buttons
  towPlayerTeam match
    case Some(Team.Red) =>
      getElementById("towSelectRed").foreach(_.classList.add("selected"))
      getElementById("towSelectBlue").foreach(_.classList.remove("selected"))
    case Some(Team.Blue) =>
      getElementById("towSelectBlue").foreach(_.classList.add("selected"))
      getElementById("towSelectRed").foreach(_.classList.remove("selected"))
    case None =>
      getElementById("towSelectRed").foreach(_.classList.remove("selected"))
      getElementById("towSelectBlue").foreach(_.classList.remove("selected"))

  // Update red team list
  getElementById("towRedPlayers").foreach: elem =>
    elem.innerHTML = redPlayers.map: player =>
      val hostBadge = if game.hostId.contains(player.playerId) then "⭐ " else ""
      s"""<span class="player-bean">$hostBadge${player.name}</span>"""
    .mkString

  // Update red team header with count
  getElementById("towRedHeader").foreach(_.textContent = s"🔴 Red Team (${redPlayers.size})")

  // Update blue team list
  getElementById("towBluePlayers").foreach: elem =>
    elem.innerHTML = bluePlayers.map: player =>
      val hostBadge = if game.hostId.contains(player.playerId) then "⭐ " else ""
      s"""<span class="player-bean">$hostBadge${player.name}</span>"""
    .mkString

  // Update blue team header with count
  getElementById("towBlueHeader").foreach(_.textContent = s"🔵 Blue Team (${bluePlayers.size})")

def updateTugOfWarLobbySettings(game: TugOfWarGame): Unit =
  val timeLimitText = if game.timeLimitSeconds > 0 then s"${game.timeLimitSeconds}s" else "No limit"
  getElementById("towLobbySettings").foreach: elem =>
    elem.innerHTML = s"<strong>Settings:</strong> First to ${game.roundsToWin} rounds • Time limit: $timeLimitText"

def updateTugOfWarTimer(secondsRemaining: Int): Unit =
  getElementById("towTimer").foreach: elem =>
    elem.textContent = secondsRemaining.toString
    elem.classList.remove("hidden")

def updateTugOfWarStartButton(game: TugOfWarGame): Unit =
  val canStart = TugOfWar.canStart(game)
  val isHost = game.hostId == towPlayerId

  getElementById("towStartButton").foreach: btn =>
    val button = btn.asInstanceOf[dom.HTMLButtonElement]
    button.disabled = !canStart || !isHost
    button.textContent = if isHost then "Start Game" else "Waiting for host..."

  getElementById("towStartHint").foreach: hint =>
    hint.textContent =
      if canStart then "Ready to start!"
      else "Need at least one player on each team to start"

def showTugOfWarGameArea(): Unit =
  getElementById("towLobby").foreach(_.classList.add("hidden"))
  getElementById("towGameArea").foreach(_.classList.remove("hidden"))

def updateTugOfWarScoreboard(game: TugOfWarGame): Unit =
  getElementById("towRedRounds").foreach(_.textContent = game.redRoundsWon.toString)
  getElementById("towBlueRounds").foreach(_.textContent = game.blueRoundsWon.toString)
  getElementById("towCurrentRound").foreach(_.textContent = game.currentRound.toString)
  getElementById("towTotalRounds").foreach(_.textContent = game.roundsToWin.toString)

def updateRopePosition(position: Int): Unit =
  // Calculate marker position (50% is center, range 5% to 95%)
  val percentage = 50 + (position.toDouble / TugOfWar.WinPosition * 45)
  val clampedPercentage = math.max(5, math.min(95, percentage))

  getElementById("towMarker").foreach: marker =>
    marker.style.left = s"${clampedPercentage}%"

  // Update position indicator
  getElementById("towPositionValue").foreach: elem =>
    elem.textContent = position.toString
    elem.classList.remove("red")
    elem.classList.remove("blue")
    elem.classList.remove("neutral")
    elem.classList.add(
      if position < 0 then "red"
      else if position > 0 then "blue"
      else "neutral"
    )

def updateClickCounts(redClicks: Int, blueClicks: Int): Unit =
  getElementById("towRedClicks").foreach(_.textContent = redClicks.toString)
  getElementById("towBlueClicks").foreach(_.textContent = blueClicks.toString)

def updateYourClicks(game: TugOfWarGame): Unit =
  towPlayerId.flatMap(game.players.get).foreach: player =>
    getElementById("towYourClicks").foreach(_.textContent = player.clickCount.toString)

def updateTugOfWarClickButton(): Unit =
  getElementById("towClickButton").foreach: btn =>
    val button = btn.asInstanceOf[dom.HTMLButtonElement]
    towPlayerTeam match
      case Some(Team.Red) =>
        button.className = "tow-click-button team-red"
        button.innerHTML = "🔴 PULL LEFT! 🔴<span class='pull-direction'>← ← ←</span>"
        button.disabled = false
      case Some(Team.Blue) =>
        button.className = "tow-click-button team-blue"
        button.innerHTML = "🔵 PULL RIGHT! 🔵<span class='pull-direction'>→ → →</span>"
        button.disabled = false
      case None =>
        button.className = "tow-click-button"
        button.innerHTML = "Select a team first!"
        button.disabled = true

def showTugOfWarRoundWinner(winner: Team, result: RoundResult): Unit =
  getElementById("towRoundWinner").foreach(_.classList.remove("hidden"))

  getElementById("towRoundWinnerContent").foreach: content =>
    content.classList.remove("red")
    content.classList.remove("blue")
    content.classList.add(if winner == Team.Red then "red" else "blue")

  getElementById("towRoundWinnerTeam").foreach: elem =>
    elem.classList.remove("red")
    elem.classList.remove("blue")
    elem.classList.add(if winner == Team.Red then "red" else "blue")
    elem.textContent = if winner == Team.Red then "🔴 RED TEAM WINS! 🔴" else "🔵 BLUE TEAM WINS! 🔵"

  getElementById("towRoundRedClicks").foreach(_.textContent = result.redClicks.toString)
  getElementById("towRoundBlueClicks").foreach(_.textContent = result.blueClicks.toString)

def hideTugOfWarRoundWinner(): Unit =
  getElementById("towRoundWinner").foreach(_.classList.add("hidden"))

def showTugOfWarGameWinner(winner: Team, redRoundsWon: Int, blueRoundsWon: Int): Unit =
  getElementById("towGameWinner").foreach(_.classList.remove("hidden"))

  getElementById("towGameWinnerContent").foreach: content =>
    content.classList.remove("red")
    content.classList.remove("blue")
    content.classList.add(if winner == Team.Red then "red" else "blue")

  getElementById("towGameWinnerTeam").foreach: elem =>
    elem.classList.remove("red")
    elem.classList.remove("blue")
    elem.classList.add(if winner == Team.Red then "red" else "blue")
    elem.textContent = if winner == Team.Red then "🔴 RED TEAM! 🔴" else "🔵 BLUE TEAM! 🔵"

  getElementById("towFinalScore").foreach: elem =>
    elem.innerHTML = s"<span class='score-red'>$redRoundsWon</span> - <span class='score-blue'>$blueRoundsWon</span>"

def hideTugOfWarGameWinner(): Unit =
  getElementById("towGameWinner").foreach(_.classList.add("hidden"))


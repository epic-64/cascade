package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.DrawingGame.*
import shared.session.BasicGameSession
import client.{el, form, input, button, *}
import client.session.SessionManager

import scala.scalajs.js
import scala.util.{Try, Success, Failure}
import scala.util.chaining.*

// Session key for DrawingGame
private val DrawingSessionKey = "drawing"

def initializeDrawing(): Unit =
  println("[Drawing] Starting AI Drawing game client...")
  buildDrawingUI()
  
  // Check for existing session and attempt reconnect
  checkForExistingDrawingSession()

var drawingWebSocket: Option[WebSocket] = None
var currentPlayerId: Option[String] = None
var currentPlayerName: Option[String] = None
var currentLobbyId: Option[String] = None
var drawingCanvas: Option[HTMLCanvasElement] = None
var drawingContext: Option[CanvasRenderingContext2D] = None
var isDrawing = false
var hasSubmittedDrawing = false // Track if current drawing has been submitted
var isRejoiningDrawing = false // Track if we're attempting to rejoin

// Session management functions - delegating to shared SessionManager
def saveDrawingSession(playerId: String, lobbyId: String, playerName: String): Unit =
  SessionManager.save(DrawingSessionKey, BasicGameSession(playerId, lobbyId, playerName))

def loadDrawingSession(): Option[BasicGameSession] =
  SessionManager.load(DrawingSessionKey)

def clearDrawingSession(): Unit =
  SessionManager.clear(DrawingSessionKey)

def checkForExistingDrawingSession(): Unit =
  loadDrawingSession() match
    case Some(session) =>
      println(s"[Drawing] Found existing session - attempting rejoin: lobbyId=${session.gameId}, playerId=${session.playerId}")
      isRejoiningDrawing = true
      currentLobbyId = Some(session.gameId)
      currentPlayerId = Some(session.playerId)
      currentPlayerName = Some(session.playerName)
      // Hide lobby setup while attempting to rejoin
      hideElement("lobbySetup")
      attemptDrawingRejoin(session.gameId, session.playerId)
    case None =>
      println("[Drawing] No existing session found")

def attemptDrawingRejoin(lobbyId: String, playerId: String): Unit =
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/drawing/$lobbyId"

  val ws = new WebSocket(wsUrl)
  drawingWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[Drawing] Connected, attempting rejoin to lobby $lobbyId")
    sendDrawingMessage(ClientMessage.RejoinLobby(lobbyId, playerId))

  ws.onmessage = (event: MessageEvent) => handleServerMessage(event.data.toString)
  ws.onerror = (event: Event) => println(s"[Drawing] WebSocket error during rejoin")
  ws.onclose = (e: CloseEvent) =>
    println(s"[Drawing] Disconnected from lobby")
    // Don't clear session on close - allow reconnection attempts

def buildDrawingUI(): Unit =
  document.body.innerHTML = ""

  document.body(
    NavigationBar.render("AI Drawing"),
    div(cls = "container")(
      createLobbySetup(),
      createWaitingRoom(),
      createDrawingArea(),
      createGalleryArea(),
      createResultsArea()
    )
  )

def switchTab(tab: String): Unit =
  tab match
    case "join" =>
      getElementById("joinTab").foreach(_.classList.add("active"))
      getElementById("createTab").foreach(_.classList.remove("active"))
      getElementById("joinTabContent").foreach(_.classList.add("active"))
      getElementById("createTabContent").foreach(_.classList.remove("active"))
    case "create" =>
      getElementById("createTab").foreach(_.classList.add("active"))
      getElementById("joinTab").foreach(_.classList.remove("active"))
      getElementById("createTabContent").foreach(_.classList.add("active"))
      getElementById("joinTabContent").foreach(_.classList.remove("active"))
    case _ => ()

def createLobbySetup(): HTMLElement =
  div(id = "lobbySetup", cls = "lobby-setup")(
    h2(content = "AI Drawing Challenge"),
    p(cls = "subtitle", content = "Draw a prompt, let AI caption it, and compete for the best match!"),

    div(cls = "tabs")(
      button(id = "joinTab", cls = "tab-btn active", content = "Join Lobby").tap: btn =>
        btn.addEventListener("click", (e: Event) => switchTab("join"))
      ,
      button(id = "createTab", cls = "tab-btn", content = "Create Lobby").tap: btn =>
        btn.addEventListener("click", (e: Event) => switchTab("create"))
    ),

    div(cls = "tab-content")(
      div(id = "joinTabContent", cls = "tab-pane active")(
        form(id = "joinForm").tap(_.addEventListener("submit", (e: Event) =>
          e.preventDefault()
          joinDrawingLobby()
        ))(
          input("text", id = "joinLobbyId").tap: el =>
            el.placeholder = "Lobby Code (e.g., ABC123)"
            el.required = true
            el.maxLength = 6
            el.autocomplete = "off"
          ,
          input("text", id = "joinPlayerName").tap: el =>
            el.placeholder = "Your name"
            el.required = true
            el.autocomplete = "off"
          ,
          button("submit", content = "Join Lobby")
        )
      ),

      div(id = "createTabContent", cls = "tab-pane")(
        form(id = "createForm").tap(_.addEventListener("submit", (e: Event) =>
          e.preventDefault()
          createDrawingLobby()
        ))(
          input("password", id = "apiKey").tap: el =>
            el.placeholder = "OpenAI API Key"
            el.required = true
            el.autocomplete = "off"
          ,
          input("text", id = "createPlayerName").tap: el =>
            el.placeholder = "Your name"
            el.required = true
            el.autocomplete = "off"
          ,
          div(cls = "warning", content = "⚠️ You'll pay for OpenAI API usage (~$0.02-0.04 per round)"),
          button("submit", content = "Create Lobby")
        )
      )
    )
  )

def createWaitingRoom(): HTMLElement =
  div(id = "waitingRoom", cls = "waiting-area hidden")(
    h3(content = "Lobby:"),
    div(id = "lobbyCode", cls = "lobby-code"),
    div(id = "playersList", cls = "players"),
    button(id = "startGameBtn", cls = "btn btn-success btn-block").tap: btn =>
      btn.textContent = "Start Game"
      btn.addEventListener("click", (e: Event) => startDrawingGame())
  )

def createDrawingArea(): HTMLElement =
  div(id = "drawingArea", cls = "drawing-area hidden")(
    div(cls = "game-controls")(
      button(cls = "btn btn-secondary").tap: btn =>
        btn.textContent = "Return to Lobby"
        btn.addEventListener("click", (e: Event) => returnToDrawingLobby())
    ),
    div(cls = "drawing-header")(
      h3(id = "drawingPrompt", content = "Draw: ???"),
      div(id = "drawingTimer", cls = "timer", content = "60")
    ),
    div(cls = "canvas-container")(
      el("canvas", id = "drawingCanvas").asInstanceOf[HTMLCanvasElement].tap: canvas =>
        canvas.width = 512
        canvas.height = 512
        drawingCanvas = Some(canvas)
        setupCanvas(canvas)
    ),
    div(cls = "drawing-controls")(
      div(cls = "color-picker")(
        button(cls = "color-btn active").tap: btn =>
          btn.style.background = "#000000"
          btn.addEventListener("click", (e: Event) => selectColor("#000000", btn))
        ,
        button(cls = "color-btn").tap: btn =>
          btn.style.background = "#FF0000"
          btn.addEventListener("click", (e: Event) => selectColor("#FF0000", btn))
        ,
        button(cls = "color-btn").tap: btn =>
          btn.style.background = "#0000FF"
          btn.addEventListener("click", (e: Event) => selectColor("#0000FF", btn))
        ,
        button(cls = "color-btn").tap: btn =>
          btn.style.background = "#00FF00"
          btn.addEventListener("click", (e: Event) => selectColor("#00FF00", btn))
      ),
      button(id = "clearBtn", cls = "btn btn-danger", content = "Clear").tap: btn =>
        btn.addEventListener("click", (e: Event) => clearCanvas())
      ,
      button(id = "submitDrawingBtn", cls = "btn btn-success", content = "Submit Drawing").tap: btn =>
        btn.addEventListener("click", (e: Event) => submitDrawing())
    )
  )

def createGalleryArea(): HTMLElement =
  div(id = "galleryArea", cls = "gallery-area hidden")(
    div(cls = "game-controls")(
      button(cls = "btn btn-secondary").tap: btn =>
        btn.textContent = "Return to Lobby"
        btn.addEventListener("click", (e: Event) => returnToDrawingLobby())
      ,
      button(id = "nextRoundBtn", cls = "btn btn-primary hidden", content = "Next Round").tap: btn =>
        btn.addEventListener("click", (e: Event) => nextRound())
    ),
    // Header with prompt and status
    div(id = "galleryHeader", cls = "gallery-header")(
      h2(id = "galleryPrompt", content = ""),
      div(id = "galleryStatus", cls = "gallery-status"),
      div(id = "galleryTimer", cls = "gallery-timer hidden")
    ),
    // Grid of all drawings
    div(id = "drawingsGallery", cls = "drawings-gallery")
  )

def createResultsArea(): HTMLElement =
  // Keep this for compatibility but it won't be used - everything stays in gallery
  div(id = "resultsArea", cls = "results-area hidden")

def setupCanvas(canvas: HTMLCanvasElement): Unit =
  val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
  drawingContext = Some(ctx)

  // Set white background
  ctx.fillStyle = "#FFFFFF"
  ctx.fillRect(0, 0, canvas.width, canvas.height)

  ctx.strokeStyle = "#000000"
  ctx.lineWidth = 3
  ctx.lineCap = "round"
  ctx.lineJoin = "round"

  var lastX = 0.0
  var lastY = 0.0

  canvas.addEventListener("mousedown", (e: MouseEvent) =>
    isDrawing = true
    val rect = canvas.getBoundingClientRect()
    val scaleX = canvas.width.toDouble / rect.width
    val scaleY = canvas.height.toDouble / rect.height
    lastX = (e.clientX - rect.left) * scaleX
    lastY = (e.clientY - rect.top) * scaleY
  )

  canvas.addEventListener("mousemove", (e: MouseEvent) =>
    if isDrawing then
      val rect = canvas.getBoundingClientRect()
      val scaleX = canvas.width.toDouble / rect.width
      val scaleY = canvas.height.toDouble / rect.height
      val x = (e.clientX - rect.left) * scaleX
      val y = (e.clientY - rect.top) * scaleY

      ctx.beginPath()
      ctx.moveTo(lastX, lastY)
      ctx.lineTo(x, y)
      ctx.stroke()

      lastX = x
      lastY = y
  )

  canvas.addEventListener("mouseup", (e: MouseEvent) =>
    isDrawing = false
  )

  canvas.addEventListener("mouseleave", (e: MouseEvent) =>
    isDrawing = false
  )

  // Touch support
  canvas.addEventListener("touchstart", (e: TouchEvent) =>
    e.preventDefault()
    isDrawing = true
    val rect = canvas.getBoundingClientRect()
    val scaleX = canvas.width.toDouble / rect.width
    val scaleY = canvas.height.toDouble / rect.height
    val touch = e.touches(0)
    lastX = (touch.clientX - rect.left) * scaleX
    lastY = (touch.clientY - rect.top) * scaleY
  )

  canvas.addEventListener("touchmove", (e: TouchEvent) =>
    e.preventDefault()
    if isDrawing then
      val rect = canvas.getBoundingClientRect()
      val scaleX = canvas.width.toDouble / rect.width
      val scaleY = canvas.height.toDouble / rect.height
      val touch = e.touches(0)
      val x = (touch.clientX - rect.left) * scaleX
      val y = (touch.clientY - rect.top) * scaleY

      drawingContext.foreach: ctx =>
        ctx.beginPath()
        ctx.moveTo(lastX, lastY)
        ctx.lineTo(x, y)
        ctx.stroke()

      lastX = x
      lastY = y
  )

  canvas.addEventListener("touchend", (e: TouchEvent) =>
    e.preventDefault()
    isDrawing = false
  )

var currentColor = "#000000"

def selectColor(color: String, btn: HTMLElement): Unit =
  currentColor = color
  drawingContext.foreach(_.strokeStyle = color)

  // Update active state
  document.querySelectorAll(".color-btn").foreach:
    case elem: HTMLElement => elem.classList.remove("active")
    case _ => ()

  btn.classList.add("active")

def clearCanvas(): Unit =
  drawingCanvas.foreach: canvas =>
    drawingContext.foreach: ctx =>
      ctx.fillStyle = "#FFFFFF"
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.fillStyle = currentColor

def createDrawingLobby(): Unit =
  val playerName = getInputValue("createPlayerName").getOrElse("")
  val apiKey = getInputValue("apiKey").getOrElse("")

  if playerName.nonEmpty && apiKey.nonEmpty then
    // First connect to temporary WebSocket
    val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
    val wsUrl = s"$protocol//${window.location.host}/ws/drawing/temp"

    val ws = new WebSocket(wsUrl)
    drawingWebSocket = Some(ws)

    ws.onopen = (e: Event) =>
      println("[Drawing] WebSocket connected, creating lobby...")
      sendDrawingMessage(ClientMessage.CreateLobby(playerName, apiKey))

    ws.onmessage = (event: MessageEvent) =>
      handleServerMessage(event.data.toString)

    ws.onerror = (event: Event) =>
      println("[Drawing] WebSocket error")

    ws.onclose = (event: CloseEvent) =>
      println("[Drawing] WebSocket disconnected")

def joinDrawingLobby(): Unit =
  val lobbyId = getInputValue("joinLobbyId").getOrElse("").toUpperCase
  val playerName = getInputValue("joinPlayerName").getOrElse("")

  if lobbyId.nonEmpty && playerName.nonEmpty then
    currentLobbyId = Some(lobbyId)
    connectDrawingWebSocket(lobbyId)
    drawingWebSocket.foreach: ws =>
      ws.onopen = (e: Event) =>
        sendDrawingMessage(ClientMessage.JoinLobby(lobbyId, playerName))

def connectDrawingWebSocket(lobbyId: String): Unit =
  val protocol = if window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl = s"$protocol//${window.location.host}/ws/drawing/$lobbyId"

  val ws = new WebSocket(wsUrl)
  drawingWebSocket = Some(ws)

  ws.onopen = (e: Event) =>
    println(s"[Drawing] WebSocket connected to lobby $lobbyId")

  ws.onmessage = (event: MessageEvent) =>
    handleServerMessage(event.data.toString)

  ws.onerror = (event: Event) =>
    println("[Drawing] WebSocket error")

  ws.onclose = (event: CloseEvent) =>
    println("[Drawing] WebSocket disconnected")

def handleServerMessage(data: String): Unit =
  Try:
    import upickle.default.*
    read[ServerMessage](data)
  match
    case Success(msg) => processServerMessage(msg)
    case Failure(ex) =>
      println(s"[Drawing] Error parsing message: ${ex.getMessage}")

def processServerMessage(msg: ServerMessage): Unit =
  msg match
    case ServerMessage.LobbyCreated(lobbyId, playerId) =>
      currentLobbyId = Some(lobbyId)
      currentPlayerId = Some(playerId)
      println(s"[Drawing] Lobby created/joined: $lobbyId, player: $playerId")
      
      // Save session for reconnection - get player name from form or existing session
      val playerName = getInputValue("createPlayerName")
        .orElse(getInputValue("joinPlayerName"))
        .orElse(currentPlayerName)
        .getOrElse("Player")
      currentPlayerName = Some(playerName)
      saveDrawingSession(playerId, lobbyId, playerName)
      isRejoiningDrawing = false
      // Server will send LobbyUpdate next, no need to reconnect

    case ServerMessage.RejoinFailed(reason) =>
      println(s"[Drawing] Rejoin failed: $reason")
      clearDrawingSession()
      isRejoiningDrawing = false
      // Close the WebSocket and reset state
      drawingWebSocket.foreach(_.close())
      drawingWebSocket = None
      currentLobbyId = None
      currentPlayerId = None
      currentPlayerName = None
      // Show lobby setup again since rejoin failed
      showElement("lobbySetup")

    case ServerMessage.LobbyUpdate(lobby) =>
      updateDrawingLobbyUI(lobby)

    case ServerMessage.PromptAnnounced(prompt) =>
      showPrompt(prompt)

    case ServerMessage.DrawingTimerUpdate(secondsRemaining) =>
      updateDrawingTimer(secondsRemaining)

    case ServerMessage.VotingTimerUpdate(secondsRemaining) =>
      updateVotingTimer(secondsRemaining)

    case ServerMessage.DrawingSubmitted(playerName) =>
      println(s"[Drawing] $playerName submitted their drawing")

    case ServerMessage.DrawingsRevealed(drawings, prompt) =>
      showGalleryWithDrawings(drawings, prompt)

    case ServerMessage.CaptionRevealed(playerName, caption) =>
      revealCaption(playerName, caption)

    case ServerMessage.AIVoteRevealed(winnerName, reasoning) =>
      revealAIVote(winnerName, reasoning)

    case ServerMessage.VotingStarted(secondsRemaining) =>
      startVotingUI(secondsRemaining)

    case ServerMessage.VoteUpdate(votes) =>
      updateVoteDisplay(votes)

    case ServerMessage.RoundComplete(result) =>
      showRoundComplete(result)

    case ServerMessage.ErrorMessage(message) =>
      println(s"[Drawing] Error: $message")
      dom.window.alert(message)

def updateDrawingLobbyUI(lobby: DrawingLobby): Unit =
  // Update currentPlayerName from lobby data
  currentPlayerId.foreach: id =>
    lobby.players.get(id).foreach: player =>
      currentPlayerName = Some(player.playerName)

  lobby.status match
    case LobbyStatus.Waiting =>
      showOnlyGameScreen("waitingRoom")

      getElementById("lobbyCode").foreach: elem =>
        elem.textContent = s"Code: ${lobby.lobbyId}"

      getElementById("playersList").foreach: elem =>
        elem.innerHTML = ""
        lobby.players.values.foreach: player =>
          elem.appendChild(div(content = s"${player.playerName} - ${player.score} pts"))

      getElementById("startGameBtn").foreach: btn =>
        if lobby.players.isEmpty then
          btn.asInstanceOf[HTMLButtonElement].disabled = true
          btn.textContent = "Need at least 1 player"
        else
          btn.asInstanceOf[HTMLButtonElement].disabled = false
          btn.textContent = "Start Game"

    case LobbyStatus.Drawing =>
      showOnlyGameScreen("drawingArea")

    case LobbyStatus.CollectingDrawings =>
      // Auto-submit drawing if not already submitted
      if !hasSubmittedDrawing then
        submitDrawing()
      // Keep drawing area visible briefly
      showOnlyGameScreen("drawingArea")

    case LobbyStatus.RevealingDrawings | LobbyStatus.RevealingCaptions | LobbyStatus.RevealingAIWinner =>
      // Show gallery for all reveal phases
      showOnlyGameScreen("galleryArea")

    case LobbyStatus.Voting =>
      showOnlyGameScreen("galleryArea")

    case LobbyStatus.Results =>
      showOnlyGameScreen("galleryArea")

def showPrompt(prompt: String): Unit =
  hasSubmittedDrawing = false // Reset for new round

  getElementById("drawingPrompt").foreach: elem =>
    elem.textContent = s"Draw: $prompt"

  // Reset submit button
  getElementById("submitDrawingBtn").foreach: btn =>
    btn.asInstanceOf[HTMLButtonElement].disabled = false
    btn.textContent = "Submit Drawing"

  // Clear canvas for new drawing
  clearCanvas()


def submitDrawing(): Unit =
  if hasSubmittedDrawing then return // Already submitted

  drawingCanvas.foreach: canvas =>
    hasSubmittedDrawing = true
    val imageData = canvas.toDataURL("image/png")
    sendDrawingMessage(ClientMessage.SubmitDrawing(imageData))

    getElementById("submitDrawingBtn").foreach: btn =>
      btn.asInstanceOf[HTMLButtonElement].disabled = true
      btn.textContent = "Submitted!"

// Store current prompt for display
var currentPrompt: String = ""

// Phase 1: Show all drawings without captions
def showGalleryWithDrawings(drawings: Seq[DrawingSubmission], prompt: String): Unit =
  currentPrompt = prompt

  // Update header
  getElementById("galleryPrompt").foreach: elem =>
    elem.textContent = s"Prompt: \"$prompt\""

  getElementById("galleryStatus").foreach: elem =>
    elem.textContent = "Revealing drawings..."
    elem.className = "gallery-status phase-reveal"

  hideElement("galleryTimer")
  hideElement("nextRoundBtn")

  // Build gallery with drawing cards (no captions yet)
  getElementById("drawingsGallery").foreach: elem =>
    elem.innerHTML = ""

    drawings.foreach: drawing =>
      val card = div(cls = "drawing-card", id = s"card-${drawing.playerName}")(
        el("img").tap: img =>
          img.asInstanceOf[HTMLImageElement].src = drawing.imageData
          img.asInstanceOf[HTMLImageElement].alt = drawing.playerName
        ,
        div(cls = "drawing-info")(
          h4(content = drawing.playerName),
          p(id = s"caption-${drawing.playerName}", cls = "caption hidden", content = "...")
        ),
        div(id = s"badges-${drawing.playerName}", cls = "badges"),
        button(id = s"vote-btn-${drawing.playerName}", cls = "vote-btn hidden", content = "Vote").tap: btn =>
          btn.addEventListener("click", (e: Event) =>
            sendDrawingMessage(ClientMessage.SubmitVote(drawing.playerName))
            // Disable all vote buttons after voting
            document.querySelectorAll(".vote-btn").foreach:
              case b: HTMLButtonElement =>
                b.disabled = true
                if b.id == s"vote-btn-${drawing.playerName}" then
                  b.textContent = "✓ Voted"
              case _ => ()
          )
      )
      elem.appendChild(card)

// Phase 2: Reveal caption for a specific player
def revealCaption(playerName: String, caption: String): Unit =
  getElementById("galleryStatus").foreach: elem =>
    elem.textContent = "AI is analyzing the drawings..."

  getElementById(s"caption-${playerName}").foreach: elem =>
    elem.textContent = s"\"$caption\""
    elem.classList.remove("hidden")
    elem.classList.add("caption-reveal")

// Phase 3: AI reveals its vote
def revealAIVote(winnerName: String, reasoning: String): Unit =
  getElementById("galleryStatus").foreach: elem =>
    elem.textContent = s"🤖 AI picked: $winnerName"
    elem.className = "gallery-status phase-ai-vote"

  // Add AI winner badge to the winning card
  getElementById(s"badges-${winnerName}").foreach: elem =>
    elem.appendChild(span(cls = "badge badge-ai", content = "🤖 AI Pick"))

  // Add reasoning to the winning card
  getElementById(s"card-${winnerName}").foreach: card =>
    card.classList.add("ai-winner")
    card.appendChild(div(cls = "ai-reasoning", content = s"\"$reasoning\""))

// Phase 4: Start voting
def startVotingUI(secondsRemaining: Int): Unit =

  getElementById("galleryStatus").foreach: elem =>
    elem.textContent = "Vote for your favorite!"
    elem.className = "gallery-status phase-voting"

  // Show timer
  getElementById("galleryTimer").foreach: elem =>
    elem.textContent = s"⏱️ $secondsRemaining"
    elem.classList.remove("hidden")

  // Show vote buttons (except for own drawing)
  currentPlayerName.foreach: myName =>
    document.querySelectorAll(".vote-btn").foreach:
      case btn: HTMLButtonElement =>
        // Get player name from button id
        val playerName = btn.id.replace("vote-btn-", "")
        // Don't show vote button for own drawing
        if playerName != myName then
          btn.classList.remove("hidden")
      case _ => ()

def updateDrawingTimer(secondsRemaining: Int): Unit =
  getElementById("drawingTimer").foreach: elem =>
    elem.textContent = secondsRemaining.toString
    if secondsRemaining <= 10 then
      elem.classList.add("urgent")
    else
      elem.classList.remove("urgent")

def updateVotingTimer(secondsRemaining: Int): Unit =
  getElementById("galleryTimer").foreach: elem =>
    elem.textContent = s"⏱️ $secondsRemaining"
    if secondsRemaining <= 5 then
      elem.classList.add("urgent")
    else
      elem.classList.remove("urgent")

def updateVoteDisplay(votes: Map[String, Int]): Unit =
  println(s"[Drawing] Votes: $votes")
  // Update vote counts on cards
  votes.foreach: (playerName, count) =>
    getElementById(s"badges-${playerName}").foreach: elem =>
      // Remove old vote badge if exists
      Option(document.getElementById(s"votes-${playerName}")).foreach(_.remove())
      // Add new vote count badge
      if count > 0 then
        elem.appendChild(span(id = s"votes-${playerName}", cls = "badge badge-votes", content = s"👥 $count"))

// Final: Show round complete with all results visible
def showRoundComplete(result: RoundResult): Unit =

  // Hide timer
  hideElement("galleryTimer")

  // Disable all vote buttons
  document.querySelectorAll(".vote-btn").foreach:
    case btn: HTMLButtonElement =>
      btn.disabled = true
      if !btn.textContent.contains("✓") then
        btn.textContent = "Voting closed"
    case _ => ()

  // Update status
  getElementById("galleryStatus").foreach: elem =>
    elem.textContent = "Round Complete!"
    elem.className = "gallery-status phase-complete"

  // Add player winner badge
  result.playerWinner.foreach: winnerName =>
    getElementById(s"badges-${winnerName}").foreach: elem =>
      elem.appendChild(span(cls = "badge badge-player", content = "👥 Player Pick"))
    getElementById(s"card-${winnerName}").foreach: elem =>
      elem.classList.add("player-winner")

  // Show the Next Round button
  showElement("nextRoundBtn")


def startDrawingGame(): Unit =
  sendDrawingMessage(ClientMessage.StartGame())

def nextRound(): Unit =
  sendDrawingMessage(ClientMessage.NextRound())

def returnToDrawingLobby(): Unit =
  clearDrawingSession()
  window.location.reload()

def sendDrawingMessage(msg: ClientMessage): Unit =
  drawingWebSocket.foreach: ws =>
    import upickle.default.*
    val json = write(msg)
    ws.send(json)

def showElement(id: String): Unit =
  getElementById(id).foreach(_.classList.remove("hidden"))

def hideElement(id: String): Unit =
  getElementById(id).foreach(_.classList.add("hidden"))

/** Helper to show only specific game screens and hide all others */
def showOnlyGameScreen(screens: String*): Unit =
  val allScreens = Set("lobbySetup", "waitingRoom", "drawingArea", "galleryArea", "resultsArea")
  val screensToShow = screens.toSet
  
  allScreens.foreach: screen =>
    if screensToShow.contains(screen) then
      showElement(screen)
    else
      hideElement(screen)


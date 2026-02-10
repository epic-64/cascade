package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.DrawingGame.*
import client.{el, form, input, button, *}

import scala.scalajs.js
import scala.util.{Try, Success, Failure}
import scala.util.chaining.*

def initializeDrawing(): Unit =
  println("[Drawing] Starting AI Drawing game client...")
  buildDrawingUI()

var drawingWebSocket: Option[WebSocket] = None
var currentPlayerId: Option[String] = None
var currentLobbyId: Option[String] = None
var drawingCanvas: Option[HTMLCanvasElement] = None
var drawingContext: Option[CanvasRenderingContext2D] = None
var isDrawing = false

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

def createLobbySetup(): HTMLElement =
  div(id = "lobbySetup", cls = "lobby-setup")(
    h2(content = "AI Drawing Challenge"),
    p(cls = "subtitle", content = "Draw a prompt, let AI caption it, and compete for the best match!"),

    div(cls = "setup-options")(
      div(cls = "setup-card")(
        h3(content = "Create Lobby"),
        form(id = "createForm").tap(_.addEventListener("submit", (e: Event) =>
          e.preventDefault()
          createDrawingLobby()
        ))(
          input("text", id = "createPlayerName").tap: el =>
            el.placeholder = "Your name"
            el.required = true
          ,
          input("password", id = "apiKey").tap: el =>
            el.placeholder = "OpenAI API Key"
            el.required = true
          ,
          div(cls = "warning", content = "⚠️ You'll pay for OpenAI API usage (~$0.02-0.04 per round)"),
          button("submit", content = "Create Lobby")
        )
      ),

      div(cls = "setup-card")(
        h3(content = "Join Lobby"),
        form(id = "joinForm").tap(_.addEventListener("submit", (e: Event) =>
          e.preventDefault()
          joinDrawingLobby()
        ))(
          input("text", id = "joinLobbyId").tap: el =>
            el.placeholder = "Lobby Code (e.g., ABC123)"
            el.required = true
            el.maxLength = 6
          ,
          input("text", id = "joinPlayerName").tap: el =>
            el.placeholder = "Your name"
            el.required = true
          ,
          button("submit", content = "Join Lobby")
        )
      )
    )
  )

def createWaitingRoom(): HTMLElement =
  div(id = "waitingRoom", cls = "waiting-room hidden")(
    h3(content = "Lobby:"),
    div(id = "lobbyCode", cls = "lobby-code"),
    div(id = "playersList", cls = "players-list"),
    button(id = "startGameBtn", cls = "btn").tap: btn =>
      btn.textContent = "Start Game"
      btn.addEventListener("click", (e: Event) => startDrawingGame())
  )

def createDrawingArea(): HTMLElement =
  div(id = "drawingArea", cls = "drawing-area hidden")(
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
      button(id = "clearBtn", content = "Clear").tap: btn =>
        btn.addEventListener("click", (e: Event) => clearCanvas())
      ,
      button(id = "submitDrawingBtn", cls = "btn", content = "Submit Drawing").tap: btn =>
        btn.addEventListener("click", (e: Event) => submitDrawing())
    )
  )

def createGalleryArea(): HTMLElement =
  div(id = "galleryArea", cls = "gallery-area hidden")(
    h3(content = "Vote for the Best Drawing!"),
    p(cls = "subtitle", content = "AI captions are shown below each drawing"),
    div(id = "drawingsGallery", cls = "drawings-gallery")
  )

def createResultsArea(): HTMLElement =
  div(id = "resultsArea", cls = "results-area hidden")(
    h2(id = "resultsTitle", content = "Round Results"),
    div(id = "resultsContent"),
    button(id = "nextRoundBtn", cls = "btn", content = "Next Round").tap: btn =>
      btn.addEventListener("click", (e: Event) => nextRound())
  )

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
    lastX = e.clientX - rect.left
    lastY = e.clientY - rect.top
  )

  canvas.addEventListener("mousemove", (e: MouseEvent) =>
    if isDrawing then
      val rect = canvas.getBoundingClientRect()
      val x = e.clientX - rect.left
      val y = e.clientY - rect.top

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
    val touch = e.touches(0)
    lastX = touch.clientX - rect.left
    lastY = touch.clientY - rect.top
  )

  canvas.addEventListener("touchmove", (e: TouchEvent) =>
    e.preventDefault()
    if isDrawing then
      val rect = canvas.getBoundingClientRect()
      val touch = e.touches(0)
      val x = touch.clientX - rect.left
      val y = touch.clientY - rect.top

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
    println("[Drawing] WebSocket connected")

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
      println(s"[Drawing] Lobby created: $lobbyId, player: $playerId")
      
      // Close the temporary connection and reconnect to the proper lobby
      drawingWebSocket.foreach(_.close())
      connectDrawingWebSocket(lobbyId)
      
    case ServerMessage.LobbyUpdate(lobby) =>
      updateDrawingLobbyUI(lobby)
      
    case ServerMessage.PromptAnnounced(prompt) =>
      showPrompt(prompt)
      
    case ServerMessage.TimerUpdate(secondsRemaining) =>
      updateTimer(secondsRemaining)
      
    case ServerMessage.DrawingSubmitted(playerName) =>
      println(s"[Drawing] $playerName submitted their drawing")
      
    case ServerMessage.AllDrawingsReady(drawings) =>
      showGallery(drawings)
      
    case ServerMessage.VoteUpdate(votes) =>
      updateVoteDisplay(votes)
      
    case ServerMessage.RoundComplete(result) =>
      showResults(result)
      
    case ServerMessage.ErrorMessage(message) =>
      println(s"[Drawing] Error: $message")
      dom.window.alert(message)

def updateDrawingLobbyUI(lobby: DrawingLobby): Unit =
  lobby.status match
    case LobbyStatus.Waiting =>
      showElement("waitingRoom")
      hideElement("drawingArea")
      hideElement("galleryArea")
      hideElement("resultsArea")
      hideElement("lobbySetup")

      getElementById("lobbyCode").foreach: elem =>
        elem.textContent = s"Code: ${lobby.lobbyId}"

      getElementById("playersList").foreach: elem =>
        elem.innerHTML = ""
        lobby.players.values.foreach: player =>
          elem.appendChild(div(content = s"${player.playerName} - ${player.score} pts"))

      getElementById("startGameBtn").foreach: btn =>
        if lobby.players.size < 2 then
          btn.asInstanceOf[HTMLButtonElement].disabled = true
          btn.textContent = "Need at least 2 players"
        else
          btn.asInstanceOf[HTMLButtonElement].disabled = false
          btn.textContent = "Start Game"

    case LobbyStatus.Drawing =>
      hideElement("waitingRoom")
      showElement("drawingArea")
      hideElement("galleryArea")
      hideElement("resultsArea")

    case LobbyStatus.Captioning =>
      hideElement("drawingArea")
      showElement("galleryArea")
      getElementById("drawingsGallery").foreach: elem =>
        elem.innerHTML = ""
        elem.appendChild(p(content = "AI is captioning the drawings..."))

    case LobbyStatus.Voting =>
      hideElement("drawingArea")
      showElement("galleryArea")

    case LobbyStatus.Results =>
      hideElement("galleryArea")
      showElement("resultsArea")

def showPrompt(prompt: String): Unit =
  getElementById("drawingPrompt").foreach: elem =>
    elem.textContent = s"Draw: $prompt"

  // Clear canvas for new drawing
  clearCanvas()

def updateTimer(secondsRemaining: Int): Unit =
  getElementById("drawingTimer").foreach: elem =>
    elem.textContent = secondsRemaining.toString
    if secondsRemaining <= 10 then
      elem.classList.add("urgent")
    else
      elem.classList.remove("urgent")

def submitDrawing(): Unit =
  drawingCanvas.foreach: canvas =>
    val imageData = canvas.toDataURL("image/png")
    sendDrawingMessage(ClientMessage.SubmitDrawing(imageData))
    
    getElementById("submitDrawingBtn").foreach: btn =>
      btn.asInstanceOf[HTMLButtonElement].disabled = true
      btn.textContent = "Submitted!"

def showGallery(drawings: Seq[DrawingSubmission]): Unit =
  getElementById("drawingsGallery").foreach: elem =>
    elem.innerHTML = ""
    
    drawings.foreach: drawing =>
      val card = div(cls = "drawing-card")(
        el("img").tap: img =>
          img.asInstanceOf[HTMLImageElement].src = drawing.imageData
          img.asInstanceOf[HTMLImageElement].alt = drawing.playerName
        ,
        div(cls = "drawing-info")(
          h4(content = drawing.playerName),
          p(content = drawing.caption.getOrElse("No caption"))
        ),
        button(cls = "vote-btn", content = "Vote").tap: btn =>
          btn.addEventListener("click", (e: Event) =>
            sendDrawingMessage(ClientMessage.SubmitVote(drawing.playerName))
            btn.disabled = true
            btn.textContent = "Voted!"
          )
      )
      elem.appendChild(card)

def updateVoteDisplay(votes: Map[String, Int]): Unit =
  println(s"[Drawing] Votes: $votes")

def showResults(result: RoundResult): Unit =
  getElementById("resultsContent").foreach: elem =>
    elem.innerHTML = ""

    result.aiWinner.foreach: winner =>
      elem.appendChild(div(cls = "result-item")(
        h3(content = "🤖 AI Winner"),
        p(content = winner),
        p(cls = "points", content = "+100 pts")
      ))

    result.playerWinner.foreach: winner =>
      elem.appendChild(div(cls = "result-item")(
        h3(content = "👥 Player Vote Winner"),
        p(content = winner),
        p(cls = "points", content = "+50 pts")
      ))

    elem.appendChild(div(cls = "vote-results")(
      h4(content = "Vote Breakdown:"),
      div().tap: voteList =>
        result.votes.toSeq.sortBy(-_._2).foreach: (player, count) =>
          voteList.appendChild(p(content = s"$player: $count votes"))
    ))

def startDrawingGame(): Unit =
  sendDrawingMessage(ClientMessage.StartGame())

def nextRound(): Unit =
  sendDrawingMessage(ClientMessage.NextRound())

def sendDrawingMessage(msg: ClientMessage): Unit =
  drawingWebSocket.foreach: ws =>
    import upickle.default.*
    val json = write(msg)
    ws.send(json)

def showElement(id: String): Unit =
  getElementById(id).foreach(_.classList.remove("hidden"))

def hideElement(id: String): Unit =
  getElementById(id).foreach(_.classList.add("hidden"))


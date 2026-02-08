package client

import org.scalajs.dom
import org.scalajs.dom.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

def initializeCounter(): Unit =
  println("[Counter] Starting Counter client...")
  buildCounterUI()
  connectWebSocket()

var counterWebSocket: Option[WebSocket] = None

def buildCounterUI(): Unit =
  // Add navigation bar
  val navBar = NavigationBar.render("Counter").append_to(document.body)

  // Create main container
  val mainContainer = el("div").with_classes("container").append_to(document.body)

  // Create title
  val title = el("h1")
    .with_classes("title")
    .with_content("Real-time Counter")
    .tap_style(_.textAlign = "center")
    .append_to(mainContainer)

  // Create subtitle
  val subtitle = el("p")
    .with_classes("subtitle")
    .with_content("Synchronized across all connected clients")
    .tap_style: style =>
      style.textAlign = "center"
      style.color = "var(--text-secondary)"
      style.marginBottom = "2rem"
    .append_to(mainContainer)

  // Create counter container
  val container      = el("div").with_id("counter-container").append_to(mainContainer)
  val counterDisplay = el("div").with_id("counter-display").with_content("Connecting...").append_to(container)
  val btnContainer   = el("div").with_id("btn-container").append_to(container)
  val btnDecrement   = el("button")
    .with_id("btn-decrement")
    .with_content("-")
    .with_click(_ => modifyCounter("decrement"))
    .append_to(btnContainer)
  val btnIncrement   = el("button")
    .with_id("btn-increment")
    .with_content("+")
    .with_click(_ => modifyCounter("increment"))
    .append_to(btnContainer)

def connectWebSocket(): Unit =
  // Use relative WebSocket URL - will connect to same host/port as the page
  val protocol = if dom.window.location.protocol == "https:" then "wss:" else "ws:"
  val wsUrl    = s"$protocol//${dom.window.location.host}/ws/counter"

  val ws = new WebSocket(wsUrl)
  counterWebSocket = Some(ws)

  ws.onopen = (e: Event) => println("[Counter] WebSocket connected")

  ws.onmessage = (event: MessageEvent) =>
    val data = event.data.toString
    Try(data.toInt) match
      case Success(counter) =>
        println(s"[Counter] Received counter update: $counter")
        updateCounterDisplay(counter)
      case Failure(e)       =>
        println(s"[Counter] Error parsing WebSocket message: $e")

  ws.onclose = (event: CloseEvent) =>
    println(s"[Counter] WebSocket disconnected: ${event.reason}")
    updateCounterDisplay("Disconnected")

  ws.onerror = (event: Event) => println(s"[Counter] WebSocket error")

def modifyCounter(action: String): Unit =
  // Use relative URL - will use same origin as the page
  val url  = s"/api/counter/$action"
  val init = js.Dynamic
    .literal(
      method = "POST"
    )
    .asInstanceOf[RequestInit]

  dom
    .fetch(url, init)
    .toFuture
    .flatMap: response =>
      response.text().toFuture
    .onComplete:
      case Success(value) =>
        // No need to update display here - WebSocket will broadcast the update
        println(s"[Counter] Counter modified via $action")
      case Failure(e)     =>
        println(s"[Counter] Error during $action: $e")

def updateCounterDisplay(value: Int): Unit =
  document.getElementById("counter-display") match
    case el: HTMLElement => el.textContent = value.toString
    case null            => println("[Counter] counter-display element not found")

def updateCounterDisplay(value: String): Unit =
  document.getElementById("counter-display") match
    case el: HTMLElement => el.textContent = value
    case null            => println("[Counter] counter-display element not found")

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
  val container = document.createElement("div")
  container.id = "counter-container"

  val counterDisplay = document.createElement("div")
  counterDisplay.id = "counter-display"
  counterDisplay.textContent = "Connecting..."

  val btnDecrement = document.createElement("button")
  btnDecrement.textContent = "-"
  btnDecrement.id = "btn-decrement"

  val btnIncrement = document.createElement("button")
  btnIncrement.textContent = "+"
  btnIncrement.id = "btn-increment"

  val btnContainer = document.createElement("div")
  btnContainer.id = "btn-container"
  btnContainer.appendChild(btnDecrement)
  btnContainer.appendChild(btnIncrement)

  container.appendChild(counterDisplay)
  container.appendChild(btnContainer)

  document.body.appendChild(container)

  // Set up event listeners
  btnIncrement.addEventListener("click", _ => modifyCounter("increment"))
  btnDecrement.addEventListener("click", _ => modifyCounter("decrement"))

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


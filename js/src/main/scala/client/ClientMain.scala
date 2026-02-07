package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.{SharedGreeter, User}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

@main def clientMain(): Unit =
  println("[client] Starting Cascade client...")

  // Demo: Shared types
  val sample = User(42, "Alice")
  val msg    = SharedGreeter.greet(sample)
  println(s"[client] $msg")

  // Determine which app to initialize based on the current page
  val pathname = dom.window.location.pathname
  println(s"[client] Current page: $pathname")

  pathname match
    case "/game.html" | "/game" =>
      println("[client] Initializing Color Rush game...")
      // Wait for DOM to be ready
      if document.readyState == "loading" then
        document.addEventListener("DOMContentLoaded", (_: Event) => client.initializeGame())
      else client.initializeGame()
    case _                      =>
      println("[client] Initializing Counter app...")
      // Build the counter UI and connect to WebSocket
      buildCounterUI()
      connectWebSocket()

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

  ws.onopen = (_: Event) => println("[client] WebSocket connected")

  ws.onmessage = (event: MessageEvent) =>
    val data = event.data.toString
    Try(data.toInt) match
      case Success(counter) =>
        println(s"[client] Received counter update: $counter")
        updateCounterDisplay(counter)
      case Failure(e)       =>
        println(s"[client] Error parsing WebSocket message: $e")

  ws.onclose = (event: CloseEvent) =>
    println(s"[client] WebSocket disconnected: ${event.reason}")
    updateCounterDisplay("Disconnected")

  ws.onerror = (event: Event) => println(s"[client] WebSocket error")

def modifyCounter(action: String): Unit =
  // Use relative URL - will use same origin as the page
  val url  = s"/counter/$action"
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
        println(s"[client] Counter modified via $action")
      case Failure(e)     =>
        println(s"[client] Error during $action: $e")

def updateCounterDisplay(value: Int): Unit =
  document.getElementById("counter-display") match
    case el: HTMLElement => el.textContent = value.toString
    case null            => println("[client] counter-display element not found")

def updateCounterDisplay(value: String): Unit =
  document.getElementById("counter-display") match
    case el: HTMLElement => el.textContent = value
    case null            => println("[client] counter-display element not found")

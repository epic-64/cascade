package client

import org.scalajs.dom
import org.scalajs.dom.*
import client.{el, button, *}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps
import scala.util.{Failure, Success, Try}

def initializeCounter(): Unit =
  println("[Counter] Starting Counter client...")
  buildCounterUI()
  connectWebSocket()

var counterWebSocket: Option[WebSocket] = None

def buildCounterUI(): Unit =
  // Clear existing content
  document.body.innerHTML = ""

  // Add navigation bar
  document.body.appendChild(NavigationBar.render("Counter"))

  // Add counter container
  document.body.appendChild(createCounterContainer())

def createCounterContainer(): HTMLElement =
  el("div").with_classes("container")(
    el("h1").tap: el =>
      el.className = "title"
      el.textContent = "Real-time Counter"
      el.style.textAlign = "center"
    ,
    el("p").tap: el =>
      el.className = "subtitle"
      el.textContent = "Synchronized across all connected clients"
      el.style.textAlign = "center"
      el.style.color = "var(--text-secondary)"
      el.style.marginBottom = "2rem"
    ,
    el("div").with_id("counter-container")(
      el("div").with_id("counter-display").with_content("Connecting..."),
      el("div").with_id("btn-container")(
        button()
          .with_id("btn-decrement")
          .with_content("-")
          .with_click(_ => modifyCounter("decrement")),
        button()
          .with_id("btn-increment")
          .with_content("+")
          .with_click(_ => modifyCounter("increment"))
      )
    )
  )

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

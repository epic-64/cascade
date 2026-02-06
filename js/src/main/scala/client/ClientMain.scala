package client

import shared.{SharedGreeter, User}
import org.scalajs.dom
import org.scalajs.dom.{document, window, HTMLElement, RequestInit, HttpMethod}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}

@main def clientMain(): Unit =
  println("[client] Starting Cascade client...")

  // Demo: Shared types
  val sample = User(42, "Alice")
  val msg    = SharedGreeter.greet(sample)
  println(s"[client] $msg")

  // Build the counter UI
  buildCounterUI()

def buildCounterUI(): Unit =
  val container = document.createElement("div")
  container.id = "counter-container"

  val counterDisplay = document.createElement("div")
  counterDisplay.id = "counter-display"
  counterDisplay.textContent = "Loading..."

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

  // Fetch initial counter value
  fetchCounter()

  // Set up event listeners
  btnIncrement.addEventListener("click", _ => modifyCounter("increment"))
  btnDecrement.addEventListener("click", _ => modifyCounter("decrement"))

def fetchCounter(): Unit =
  dom.fetch("http://localhost:8080/counter")
    .toFuture
    .flatMap: response =>
      response.text().toFuture
    .onComplete:
      case Success(value) =>
        Try(value.toInt) match
          case Success(counter) => updateCounterDisplay(counter)
          case Failure(e) =>
            println(s"[client] Error parsing counter: $e")
            updateCounterDisplay(0)
      case Failure(e) =>
        println(s"[client] Error fetching counter: $e")
        updateCounterDisplay(0)

def modifyCounter(action: String): Unit =
  val url = s"http://localhost:8080/counter/$action"
  val init = js.Dynamic.literal(
    method = "POST"
  ).asInstanceOf[RequestInit]

  dom.fetch(url, init)
    .toFuture
    .flatMap: response =>
      response.text().toFuture
    .onComplete:
      case Success(value) =>
        Try(value.toInt) match
          case Success(counter) => updateCounterDisplay(counter)
          case Failure(e) => println(s"[client] Error parsing counter after $action: $e")
      case Failure(e) =>
        println(s"[client] Error during $action: $e")

def updateCounterDisplay(value: Int): Unit =
  document.getElementById("counter-display") match
    case el: HTMLElement => el.textContent = value.toString
    case null => println("[client] counter-display element not found")

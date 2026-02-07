package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.{SharedGreeter, User}

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
    case p if p.contains("color-rush.html") || p == "/color-rush" =>
      println("[client] Routing to Color Rush...")
      // Wait for DOM to be ready
      if document.readyState == "loading" then
        document.addEventListener("DOMContentLoaded", (_: Event) => client.initializeGame())
      else client.initializeGame()
    case p if p.contains("counter.html") || p == "/counter" =>
      println("[client] Routing to Counter...")
      // Wait for DOM to be ready
      if document.readyState == "loading" then
        document.addEventListener("DOMContentLoaded", (_: Event) => client.initializeCounter())
      else client.initializeCounter()
    case _ =>
      println("[client] Landing page - no app initialization needed")

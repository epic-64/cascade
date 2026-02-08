package client

import org.scalajs.dom
import org.scalajs.dom.*

def safeInitialize(init: => Unit): Unit =
  if document.readyState == "loading" then
    document.addEventListener("DOMContentLoaded", (e: Event) => init)
  else init

@main def clientMain(): Unit =
  println("[client] Starting Cascade client...")

  // Determine which app to initialize based on the current page
  val pathname = dom.window.location.pathname
  println(s"[client] Current page: $pathname")

  pathname match
    case p if p == "/color-rush" =>
      println("[client] Routing to Color Rush...")
      safeInitialize(client.initializeColorRush())
    case p if p == "/counter" =>
      println("[client] Routing to Counter...")
      safeInitialize(client.initializeCounter())
    case _ =>
      println("[client] Landing page - no app initialization needed")

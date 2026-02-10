package client

import org.scalajs.dom
import org.scalajs.dom.*

enum AppRoute:
  case ColorRush
  case Counter
  case Landing

def routeFromPathname(pathname: String): AppRoute =
  pathname match
    case p if p == "/color-rush" => AppRoute.ColorRush
    case p if p == "/counter" => AppRoute.Counter
    case _ => AppRoute.Landing

def shouldDeferInit(documentState: String): Boolean =
  documentState == "loading"

def safeInitialize(init: => Unit): Unit =
  if shouldDeferInit(document.readyState) then
    document.addEventListener("DOMContentLoaded", (e: Event) => init)
  else init

@main def clientMain(): Unit =
  println("[client] Starting Cascade client...")

  // Determine which app to initialize based on the current page
  val pathname = dom.window.location.pathname
  println(s"[client] Current page: $pathname")

  routeFromPathname(pathname) match
    case AppRoute.ColorRush =>
      println("[client] Routing to Color Rush...")
      safeInitialize(client.initializeColorRush())
    case AppRoute.Counter =>
      println("[client] Routing to Counter...")
      safeInitialize(client.initializeCounter())
    case AppRoute.Landing =>
      println("[client] Landing page - no app initialization needed")

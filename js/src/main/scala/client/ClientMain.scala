package client

import org.scalajs.dom
import org.scalajs.dom.*

enum AppRoute:
  case ColorRush
  case Counter
  case AIDrawing
  case AIChat
  case TugOfWar
  case Trader
  case TileKingdom
  case Landing

/** Route with optional lobby ID extracted from URL path */
case class GameRoute(route: AppRoute, lobbyId: Option[String])

def parseRoute(pathname: String): GameRoute =
  pathname.split("/").filter(_.nonEmpty).toList match
    case "color-rush" :: lobbyId :: _ => GameRoute(AppRoute.ColorRush, Some(lobbyId.toUpperCase))
    case "color-rush" :: Nil          => GameRoute(AppRoute.ColorRush, None)
    case "ai-drawing" :: lobbyId :: _ => GameRoute(AppRoute.AIDrawing, Some(lobbyId.toUpperCase))
    case "ai-drawing" :: Nil          => GameRoute(AppRoute.AIDrawing, None)
    case "ai-chat" :: _               => GameRoute(AppRoute.AIChat, None)
    case "tug-of-war" :: lobbyId :: _ => GameRoute(AppRoute.TugOfWar, Some(lobbyId.toUpperCase))
    case "tug-of-war" :: Nil          => GameRoute(AppRoute.TugOfWar, None)
    case "trader" :: _                => GameRoute(AppRoute.Trader, None)
    case "tile-kingdom" :: _          => GameRoute(AppRoute.TileKingdom, None)
    case "counter" :: _               => GameRoute(AppRoute.Counter, None)
    case _                            => GameRoute(AppRoute.Landing, None)

def shouldDeferInit(documentState: String): Boolean =
  documentState == "loading"

def safeInitialize(init: => Unit): Unit =
  if shouldDeferInit(document.readyState) then
    document.addEventListener("DOMContentLoaded", (e: Event) => init)
  else init

def clientMain(pathnameOverride: Option[String] = None): Unit =
  println("[client] Starting Cascade client...")

  // Determine which app to initialize based on the current page
  val pathname = pathnameOverride.getOrElse(dom.window.location.pathname)
  println(s"[client] Current page: $pathname")

  val gameRoute = parseRoute(pathname)
  gameRoute.lobbyId.foreach(id => println(s"[client] Lobby ID from URL: $id"))

  gameRoute.route match
    case AppRoute.ColorRush =>
      println("[client] Routing to Color Rush...")
      safeInitialize(client.initializeColorRush(gameRoute.lobbyId))
    case AppRoute.Counter =>
      println("[client] Routing to Counter...")
      safeInitialize(client.initializeCounter())
    case AppRoute.AIDrawing =>
      println("[client] Routing to AI Drawing...")
      safeInitialize(client.initializeDrawing(gameRoute.lobbyId))
    case AppRoute.AIChat =>
      println("[client] Routing to AI Chat...")
      safeInitialize(client.initializeAIChat())
    case AppRoute.TugOfWar =>
      println("[client] Routing to Tug of War...")
      safeInitialize(client.initializeTugOfWar(gameRoute.lobbyId))
    case AppRoute.Trader =>
      println("[client] Routing to Trader...")
      safeInitialize(client.initializeTrader())
    case AppRoute.TileKingdom =>
      println("[client] Routing to Tile Kingdom...")
      safeInitialize(client.initializeTileKingdom())
    case AppRoute.Landing =>
      println("[client] Landing page - no app initialization needed")

@main def main(): Unit = clientMain()

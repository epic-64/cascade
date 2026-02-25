package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}

/** Tavern tile component.
  *
  * The simplest tile type - just displays icon and lifespan multiplier badge.
  * No progress bar, no level ups.
  */
object TavernTile:

  /** Tile action callbacks */
  case class Actions(
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    actions: Actions
  ): HtmlElement =
    div(
      cls := "tile-kingdom-tile unlocked tavern",
      cls <-- TileGridState.zoomTierClass,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content tavern-content",
        div(cls := "tile-icon", "🍺"),
        div(cls := "tile-label", "Tavern"),
        span(cls := "tile-badge badge-tavern", s"${TileKingdomLogic.TavernLifespanMultiplier.toInt}x Lifespan")
      ),

      // Right-click to destroy
      onContextMenu --> { e =>
        e.preventDefault()
        actions.onDestroy()
      }
    )


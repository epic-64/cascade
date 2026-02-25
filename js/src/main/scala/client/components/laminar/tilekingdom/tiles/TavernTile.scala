package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.TileGridState
import TileComponents.*

/** Tavern tile component.
  *
  * The simplest tile type - just displays icon and lifespan multiplier badge.
  * No progress bar, no level ups.
  */
object TavernTile:

  def apply(
    coord: Coord,
    actions: BasicActions
  ): HtmlElement =
    tileWrapper(coord, "tavern")(
      div(
        cls := "tile-content tavern-content",
        div(cls := "tile-icon", "🍺"),
        div(cls := "tile-label", "Tavern"),
        span(
          cls := "tile-badge badge-tavern",
          title := "Politicians in nearby Town Halls live longer",
          s"${TileKingdomLogic.TavernLifespanMultiplier.toInt}x Lifespan"
        )
      ),
      destroyTileHandler(actions.onDestroyTile)
    )

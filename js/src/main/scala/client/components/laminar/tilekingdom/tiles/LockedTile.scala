package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}

/** Locked tile component.
  *
  * Displays as a dark gray tile with no content, showing island bounds.
  */
object LockedTile:

  def apply(coord: Coord): HtmlElement =
    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile locked-empty",
      styleAttr <-- TileGridState.tileStyle(coord)
    )


package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState

/** Locked tile component.
  *
  * Displays as a dark gray tile showing island bounds.
  * Shows the gold cost required to unlock when player can't afford it.
  */
object LockedTile:

  def apply(coord: Coord): HtmlElement =
    val costSignal = TileKingdomState.nextTileUnlockCostSignal
    
    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile locked-empty",
      styleAttr <-- TileGridState.tileStyle(coord),
      
      div(
        cls := "locked-cost",
        child.text <-- costSignal.map(cost => TileUtils.formatNumber(cost))
      )
    )


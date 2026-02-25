package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState

/** Unlockable tile component.
  *
  * Shows a lock icon and cost. Click to unlock if affordable.
  */
object UnlockableTile:

  /** Tile action callbacks */
  case class Actions(
    onUnlock: () => Unit
  )

  def apply(
    coord: Coord,
    actions: Actions
  ): HtmlElement =
    val costSignal = TileKingdomState.nextTileUnlockCostSignal

    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile locked unlockable",
      cls <-- TileGridState.zoomTierClass,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🔓"),
        div(
          cls := "tile-cost",
          child.text <-- costSignal.map(c => s"${TileUtils.formatNumber(c)} 💰")
        )
      ),

      // Click to unlock
      onClick --> { _ =>
        if !TileGridState.wasDragging then actions.onUnlock()
      }
    )


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
    val tilePointsSignal = TileKingdomState.tilePointsSignal

    // Note: Uses "locked" instead of "unlocked" - can't use tileWrapper directly
    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile locked unlockable",
      styleAttr <-- TileGridState.tileStyle(coord),

      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🔓"),
        div(
          cls := "tile-cost",
          title <-- tilePointsSignal.map: tp =>
            if tp > 0 then "Use a tile point to unlock this tile"
            else "Gold required to unlock this tile",
          child.text <-- tilePointsSignal.combineWith(costSignal).map:
            case (tp, _) if tp > 0 => "1 🎫"
            case (_, cost) => s"${TileUtils.formatNumber(cost)} 💰"
        )
      ),

      onClick --> { _ =>
        actions.onUnlock()
      }
    )

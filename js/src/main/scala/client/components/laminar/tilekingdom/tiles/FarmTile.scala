package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import TileComponents.*

/** Farm tile component.
  *
  * Boosts nearby wheat fields. Has level and upgrade cost.
  */
object FarmTile:

  def apply(
    coord: Coord,
    level: Int,
    actions: UpgradeActions
  ): HtmlElement =
    val boostPercent = (level * TileKingdomLogic.FarmBoostPerLevel * 100).toInt
    val upgradeCost = TileKingdomLogic.farmLevelUpCost(level)

    tileWrapper(coord, "farm", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🏠"),
        div(cls := "tile-label", s"Lv$level"),
        div(cls := "tile-production", s"+$boostPercent%"),
        upgradeRow(upgradeCost, "🌾", level, actions.onBulkLevelUp)
      ),
      clickToLevelUp(actions),
      destroyHandler(actions)
    )

package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Quarry tile component.
  *
  * Produces stone over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by town halls, wisdom skills, and farms (via Agriculture 2B).
  */
object QuarryTile:

  def apply(
    coord: Coord,
    tile: Tile,
    actions: UpgradeActions
  ): HtmlElement =
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal
    val upgradeCost = TileKingdomLogic.quarryLevelUpCost(level)

    // Computed values from game state
    val stoneAmountSignal = gameSignal.map(g => TileKingdomLogic.stoneProductionPerHarvest(g, tile))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallStoneMultiplier(g, coord))
    val wisdomMultiplierSignal = gameSignal.map(g => TileKingdomLogic.quarryWisdom1Multiplier(g, coord))
    val farmBoostSignal = gameSignal.map(g => TileKingdomLogic.agriculture2BFarmBonusMultiplier(g, coord))

    tileWrapper(coord, "quarry", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "⛏️"),
        levelLabel(level),
        div(
          cls := "tile-production quarry-production",
          child.text <-- stoneAmountSignal.map(s => s"+${TileUtils.formatNumber(s)}🪨")
        ),
        div(
          cls := "tile-modifiers",
          percentBadge(farmBoostSignal, "🏠", "badge-farm",
            "Agriculture skill: farms boost quarries at half strength"),
          multiplierBadge(townHallMultiplierSignal, "🏛️", "badge-townhall",
            "Town Hall bonus: politician multiplier"),
          multiplierBadge(wisdomMultiplierSignal, "📚", "badge-wisdom",
            "Wisdom skill: bonus from nearby forests")
        ),
        upgradeRow(upgradeCost, "🪵", level, actions.onBulkLevelUp)
      ),
      ProgressBar(progressSignal(coord), "quarry-progress"),
      clickToLevelUp(actions),
      destroyTileHandler(actions)
    )

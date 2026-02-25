package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Woodcutter (forest) tile component.
  *
  * Produces wood over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by forest groups and town halls.
  */
object WoodcutterTile:

  def apply(
    coord: Coord,
    tile: Tile,
    actions: UpgradeActions
  ): HtmlElement =
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal
    val upgradeCost = TileKingdomLogic.woodcutterLevelUpCost(level)

    // Computed values from game state
    val harvestAmountSignal = gameSignal.map(g => TileKingdomLogic.woodProductionPerHarvest(g, tile))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallWoodMultiplier(g, coord))
    val farmBoostSignal = gameSignal.map(g => TileKingdomLogic.agriculture2BFarmBonusMultiplier(g, coord))
    val forestBonusSignal = gameSignal.map(g => TileKingdomLogic.forestGroupBonusMultiplier(g, coord))

    tileWrapper(coord, "woodcutter", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🪓"),
        levelLabel(level),
        div(
          cls := "tile-production",
          child.text <-- harvestAmountSignal.map(h => s"+${TileUtils.formatNumber(h)}🪵")
        ),
        div(
          cls := "tile-modifiers",
          percentBadge(farmBoostSignal, "🏠", "badge-farm",
            "Agriculture skill: farms boost forests at half strength"),
          percentBadge(forestBonusSignal, "🌲", "badge-forest",
            "Forest synergy: bonus from adjacent forests"),
          multiplierBadge(townHallMultiplierSignal, "🏛️", "badge-townhall",
            "Town Hall bonus: politician multiplier")
        ),
        upgradeRow(upgradeCost, "🌾", level, actions.onBulkLevelUp)
      ),
      ProgressBar(progressSignal(coord), "woodcutter-progress"),
      clickToLevelUp(actions),
      destroyHandler(actions)
    )


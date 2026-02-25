package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Wheat field tile component.
  *
  * Produces wheat over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by nearby farms and town halls.
  */
object WheatFieldTile:

  def apply(
    coord: Coord,
    tile: Tile,
    actions: UpgradeActions
  ): HtmlElement =
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal

    // Computed values from game state
    val harvestAmountSignal = gameSignal.map(g => TileKingdomLogic.productionPerHarvest(g, tile))
    val farmBonusSignal = gameSignal.map(g => TileKingdomLogic.farmBonusMultiplier(g, coord))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallWheatMultiplier(g, coord))
    val hasSpeedBoostSignal = gameSignal.map(_.hasSkill(Skill.Agriculture1B))
    val hasUpgradeDiscountSignal = gameSignal.map(_.hasSkill(Skill.Agriculture3A))
    val upgradeCostSignal = gameSignal.map(g => TileKingdomLogic.effectiveUpgradeCost(g, tile).map(_.amount).getOrElse(0))

    tileWrapper(coord, "wheat-field", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🌾"),
        tierLabel(level),
        div(
          cls := "tile-production",
          child.text <-- harvestAmountSignal.map(h => s"+${TileUtils.formatNumber(h)}")
        ),
        div(
          cls := "tile-modifiers",
          staticBadge(hasUpgradeDiscountSignal, "💰-90%", "badge-discount",
            "Agriculture skill: 90% cheaper upgrades"),
          staticBadge(hasSpeedBoostSignal, "⚡+25%", "badge-speed",
            "Agriculture skill: 25% faster production"),
          percentBadge(farmBonusSignal, "🏠", "badge-farm",
            "Farm bonus: nearby farms boost wheat production"),
          multiplierBadge(townHallMultiplierSignal, "🏛️", "badge-townhall",
            "Town Hall bonus: politician multiplier")
        ),
        upgradeRowSignal(upgradeCostSignal, "🌾", level, actions.onBulkLevelUp)
      ),
      ProgressBar(progressSignal(coord)),
      clickToLevelUp(actions),
      destroyHandler(actions)
    )


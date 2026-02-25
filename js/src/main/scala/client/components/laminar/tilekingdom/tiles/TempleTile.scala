package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Temple tile component.
  *
  * Produces faith over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by town halls and wisdom skills.
  */
object TempleTile:

  def apply(
    coord: Coord,
    tile: Tile,
    actions: UpgradeActions
  ): HtmlElement =
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal
    val upgradeCost = TileKingdomLogic.templeLevelUpCost(level)

    // Computed values from game state
    val faithAmountSignal = gameSignal.map(g => TileKingdomLogic.faithProductionPerHarvest(g, tile))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallFaithMultiplier(g, coord))
    val wisdomMultiplierSignal = gameSignal.map(g => TileKingdomLogic.templeWisdom2Multiplier(g, coord))

    tileWrapper(coord, "temple", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "⛪"),
        levelLabel(level),
        div(
          cls := "tile-production temple-production",
          child.text <-- faithAmountSignal.map(f => s"+${TileUtils.formatNumber(f)}✨")
        ),
        div(
          cls := "tile-modifiers",
          multiplierBadge(townHallMultiplierSignal, "🏛️", "badge-townhall",
            "Town Hall bonus: politician multiplier"),
          multiplierBadge(wisdomMultiplierSignal, "📚", "badge-wisdom",
            "Wisdom skill: bonus from nearby forests")
        ),
        upgradeRow(upgradeCost, "🪵", level, actions.onBulkLevelUp)
      ),
      ProgressBar(progressSignal(coord), "temple-progress"),
      clickToLevelUp(actions),
      destroyTileHandler(actions)
    )

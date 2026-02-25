package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState

/** Wheat field tile component.
  *
  * Produces wheat over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by nearby farms and town halls.
  */
object WheatFieldTile:

  /** Tile action callbacks */
  case class Actions(
    onLevelUp: () => Unit,
    onBulkLevelUp: Int => Unit,
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    tile: Tile,
    actions: Actions
  ): HtmlElement =
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal
    val progressSignal = TileGridState.tileProgress.signal.map(_.getOrElse(coord, 0.0))

    // Computed values from game state
    val harvestAmountSignal = gameSignal.map(g => TileKingdomLogic.productionPerHarvest(g, tile))
    val farmBonusSignal = gameSignal.map(g => TileKingdomLogic.farmBonusMultiplier(g, coord))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallWheatMultiplier(g, coord))
    val hasSpeedBoostSignal = gameSignal.map(_.hasSkill(Skill.Agriculture1B))
    val hasUpgradeDiscountSignal = gameSignal.map(_.hasSkill(Skill.Agriculture3A))
    val upgradeCostSignal = gameSignal.map(g => TileKingdomLogic.effectiveUpgradeCost(g, tile).map(_.amount).getOrElse(0))

    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile unlocked wheat-field",
      cls <-- TileGridState.zoomTierClass,
      dataAttr("level") := level.toString,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🌾"),
        div(cls := "tile-label", s"Lv$level"),

        // Production amount
        div(
          cls := "tile-production",
          child.text <-- harvestAmountSignal.map(h => s"+${TileUtils.formatNumber(h)}")
        ),

        // Modifier badges
        div(
          cls := "tile-modifiers",

          // Upgrade discount badge
          child.maybe <-- hasUpgradeDiscountSignal.map: has =>
            Option.when(has):
              span(
                cls := "tile-badge badge-speed",
                title := "Agriculture skill: Wheat field upgrades cost 90% less",
                "💰-90%"
              ),

          // Speed boost badge
          child.maybe <-- hasSpeedBoostSignal.map: has =>
            Option.when(has):
              span(
                cls := "tile-badge badge-speed",
                title := "Agriculture skill: Fields produce 25% faster",
                "⚡+25%"
              ),

          // Farm bonus badge
          child.maybe <-- farmBonusSignal.map: bonus =>
            Option.when(bonus > 1.0):
              val bonusPercent = ((bonus - 1) * 100).toInt
              span(cls := "tile-badge badge-farm", s"🏠+$bonusPercent%"),

          // Town hall bonus badge
          child.maybe <-- townHallMultiplierSignal.map: mult =>
            Option.when(mult > 1.0):
              val multiplierText = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
              span(cls := "tile-badge badge-townhall", s"🏛️$multiplierText")
        ),

        // Upgrade row
        div(
          cls := "tile-upgrade-row",
          span(
            cls := "tile-upgrade",
            child.text <-- upgradeCostSignal.map(c => s"⬆${TileUtils.formatNumber(c)}🌾")
          ),
          button(
            cls := "btn-x10",
            "x10",
            onClick --> { e =>
              e.stopPropagation()
              actions.onBulkLevelUp(TileUtils.levelsToNextTen(level))
            }
          )
        )
      ),

      // Progress bar
      ProgressBar(progressSignal),

      // Click to level up
      onClick --> { _ =>
        if !TileGridState.wasDragging then actions.onLevelUp()
      },

      // Right-click to destroy
      onContextMenu --> { e =>
        e.preventDefault()
        actions.onDestroy()
      }
    )


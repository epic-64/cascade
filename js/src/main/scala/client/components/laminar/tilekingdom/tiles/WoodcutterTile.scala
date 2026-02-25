package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState

/** Woodcutter (forest) tile component.
  *
  * Produces wood over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by forest groups and town halls.
  */
object WoodcutterTile:

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
    val upgradeCost = TileKingdomLogic.woodcutterLevelUpCost(level)

    // Computed values from game state
    val harvestAmountSignal = gameSignal.map(g => TileKingdomLogic.woodProductionPerHarvest(g, tile))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallWoodMultiplier(g, coord))
    val farmBoostSignal = gameSignal.map(g => TileKingdomLogic.agriculture2BFarmBonusMultiplier(g, coord))
    val forestBonusSignal = gameSignal.map(g => TileKingdomLogic.forestGroupBonusMultiplier(g, coord))

    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile unlocked woodcutter",
      cls <-- TileGridState.zoomTierClass,
      dataAttr("level") := level.toString,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🪓"),
        div(cls := "tile-label", s"Lv$level"),

        // Production amount
        div(
          cls := "tile-production",
          child.text <-- harvestAmountSignal.map(h => s"+${TileUtils.formatNumber(h)}🪵")
        ),

        // Modifier badges
        div(
          cls := "tile-modifiers",

          // Farm boost badge (from Agriculture 2B skill)
          child.maybe <-- farmBoostSignal.map: bonus =>
            Option.when(bonus > 1.0):
              val boostPercent = ((bonus - 1) * 100).toInt
              span(
                cls := "tile-badge badge-farm",
                title := "Agriculture skill: Farms boost forests at half strength",
                s"🏠+$boostPercent%"
              ),

          // Forest group bonus badge
          child.maybe <-- forestBonusSignal.map: bonus =>
            Option.when(bonus > 1.0):
              val bonusPercent = ((bonus - 1) * 100).toInt
              span(cls := "tile-badge badge-forest", s"🌲+$bonusPercent%"),

          // Town hall bonus badge
          child.maybe <-- townHallMultiplierSignal.map: mult =>
            Option.when(mult > 1.0):
              val multiplierText = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
              span(cls := "tile-badge badge-townhall", s"🏛️$multiplierText")
        ),

        // Upgrade row
        div(
          cls := "tile-upgrade-row",
          span(cls := "tile-upgrade", s"⬆${TileUtils.formatNumber(upgradeCost)}🌾"),
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
      ProgressBar(progressSignal, "woodcutter-progress"),

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


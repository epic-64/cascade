package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState

/** Temple tile component.
  *
  * Produces faith over time. Has level, upgrade cost, and progress bar.
  * Can be boosted by town halls and wisdom skills.
  */
object TempleTile:

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
    val upgradeCost = TileKingdomLogic.templeLevelUpCost(level)

    // Computed values from game state
    val faithAmountSignal = gameSignal.map(g => TileKingdomLogic.faithProductionPerHarvest(g, tile))
    val townHallMultiplierSignal = gameSignal.map(g => TileKingdomLogic.townHallFaithMultiplier(g, coord))
    val wisdomMultiplierSignal = gameSignal.map(g => TileKingdomLogic.templeWisdom2Multiplier(g, coord))

    div(
      cls := "tile-kingdom-tile unlocked temple",
      cls <-- TileGridState.zoomTierClass,
      dataAttr("level") := level.toString,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "⛪"),
        div(cls := "tile-label", s"Lv$level"),

        // Production amount
        div(
          cls := "tile-production temple-production",
          child.text <-- faithAmountSignal.map(f => s"+${TileUtils.formatNumber(f)}✨")
        ),

        // Modifier badges
        div(
          cls := "tile-modifiers",

          // Town hall bonus badge
          child.maybe <-- townHallMultiplierSignal.map: mult =>
            Option.when(mult > 1.0):
              val multiplierText = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
              span(cls := "tile-badge badge-townhall", s"🏛️$multiplierText"),

          // Wisdom skill bonus badge
          child.maybe <-- wisdomMultiplierSignal.map: mult =>
            Option.when(mult > 1.0):
              val multiplierText = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
              span(cls := "tile-badge badge-wisdom", s"🌲$multiplierText")
        ),

        // Upgrade row
        div(
          cls := "tile-upgrade-row",
          span(cls := "tile-upgrade", s"⬆${TileUtils.formatNumber(upgradeCost)}🪵"),
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
      ProgressBar(progressSignal, "temple-progress"),

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


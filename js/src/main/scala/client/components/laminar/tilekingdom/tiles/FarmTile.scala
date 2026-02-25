package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState

/** Farm tile component.
  *
  * Boosts nearby wheat fields. Has level and upgrade cost.
  */
object FarmTile:

  /** Tile action callbacks */
  case class Actions(
    onLevelUp: () => Unit,
    onBulkLevelUp: Int => Unit,
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    level: Int,
    actions: Actions
  ): HtmlElement =
    val boostPercent = (level * TileKingdomLogic.FarmBoostPerLevel * 100).toInt
    val upgradeCost = TileKingdomLogic.farmLevelUpCost(level)

    div(
      cls := "tile-kingdom-tile unlocked farm",
      cls <-- TileGridState.zoomTierClass,
      dataAttr("level") := level.toString,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🏠"),
        div(cls := "tile-label", s"Lv$level"),
        div(cls := "tile-production", s"+$boostPercent%"),

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


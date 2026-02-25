package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState

/** Bureau tile component.
  *
  * Auto-upgrades nearby buildings. Has three modes: Slow, Turbo, Disabled.
  * Turbo mode costs faith per upgrade but runs 10x faster.
  */
object BureauTile:

  /** Tile action callbacks */
  case class Actions(
    onSetMode: BureauMode => Unit,
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    level: Int,
    actions: Actions
  ): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val progressSignal = TileGridState.tileProgress.signal.map(_.getOrElse(coord, 0.0))

    // Computed values from game state
    val bureauModeSignal = gameSignal.map(g => TileKingdomLogic.getBureauMode(g, coord))
    val speedMultiplierSignal = gameSignal.map(g => TileKingdomLogic.bureauSpeedMultiplier(g, coord))
    val effectiveWoodCostSignal = gameSignal.map(g => TileKingdomLogic.effectiveBureauWoodCost(g))

    // Calculate min level of nearby upgradeable tiles for faith cost
    val minFaithCostSignal = gameSignal.map: g =>
      val nearbyCoords = coord.neighborsWithinRadius(TileKingdomLogic.BureauRadius)
      val minLevel = nearbyCoords
        .flatMap(c => g.tiles.get(c))
        .filter(_.isUpgradeable)
        .map(_.level)
        .minOption
        .getOrElse(1)
      TileKingdomLogic.effectiveBureauFaithCostForLevel(g, minLevel)

    val canAffordTurboSignal = gameSignal.combineWith(minFaithCostSignal).map:
      case (g, cost) => g.faith >= cost

    div(
      idAttr := TileUtils.tileId(coord),
      cls := "tile-kingdom-tile unlocked bureau",
      cls <-- TileGridState.zoomTierClass.combineWith(bureauModeSignal).map:
        case (zoomCls, BureauMode.Turbo) => s"$zoomCls turbo".trim
        case (zoomCls, BureauMode.Disabled) => s"$zoomCls disabled".trim
        case (zoomCls, _) => zoomCls,
      dataAttr("level") := level.toString,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🏛️"),

        // Mode label
        div(
          cls := "tile-label",
          child.text <-- bureauModeSignal.combineWith(speedMultiplierSignal).map:
            case (BureauMode.Slow, _) => "Bureau"
            case (BureauMode.Turbo, mult) => s"⚡x${mult.toInt}"
            case (BureauMode.Disabled, _) => "⏸️ Paused"
        ),

        // Auto-upgrade indicator (hidden when disabled)
        div(
          cls := "tile-production",
          display <-- bureauModeSignal.map(m => if m == BureauMode.Disabled then "none" else "block"),
          "Auto⬆"
        ),

        // Cost badge
        div(
          cls := "tile-badge badge-cost",
          child.text <-- bureauModeSignal.combineWith(effectiveWoodCostSignal).map:
            case (BureauMode.Turbo, woodCost) => s"${woodCost}🪵 Lv×10✨"
            case (BureauMode.Slow, woodCost) => s"${woodCost}🪵"
            case (BureauMode.Disabled, _) => "—"
        ),

        // Mode toggle buttons
        div(
          cls := "bureau-mode-row",

          // Slow mode button
          button(
            cls <-- bureauModeSignal.map(m =>
              if m == BureauMode.Slow then "btn-bureau-mode slow active"
              else "btn-bureau-mode slow"
            ),
            title := "Slow mode (1x speed)",
            "🐢",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Slow)
            }
          ),

          // Turbo mode button
          button(
            cls <-- bureauModeSignal.combineWith(canAffordTurboSignal).map:
              case (BureauMode.Turbo, _) => "btn-bureau-mode turbo active"
              case (_, false) => "btn-bureau-mode turbo insufficient"
              case _ => "btn-bureau-mode turbo",
            title <-- minFaithCostSignal.map(cost => s"Turbo mode (10x speed, ${cost}✨/upgrade)"),
            "⚡",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Turbo)
            }
          ),

          // Disabled/pause button
          button(
            cls <-- bureauModeSignal.map(m =>
              if m == BureauMode.Disabled then "btn-bureau-mode pause active"
              else "btn-bureau-mode pause"
            ),
            title := "Pause bureau",
            "⏸️",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Disabled)
            }
          )
        )
      ),

      // Progress bar (hidden when disabled)
      ProgressBar.withVisibility(
        progressSignal,
        bureauModeSignal.map(_ != BureauMode.Disabled),
        "bureau-progress"
      ),

      // Right-click to destroy
      onContextMenu --> { e =>
        e.preventDefault()
        actions.onDestroy()
      }
    )


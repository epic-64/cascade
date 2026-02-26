package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Bureau tile component.
  *
  * Auto-upgrades nearby buildings. Has three modes: Slow, Turbo, Disabled.
  * Turbo mode costs faith per upgrade but runs 10x faster.
  */
object BureauTile:

  /** Bureau-specific actions (has mode switching and direction) */
  case class Actions(
    onSetMode: BureauMode => Unit,
    onSetDirection: BureauDirection => Unit,
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    level: Int,
    actions: Actions
  ): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal

    // Computed values from game state
    val bureauModeSignal = gameSignal.map(g => TileKingdomLogic.getBureauMode(g, coord))
    val speedMultiplierSignal = gameSignal.map(g => TileKingdomLogic.bureauSpeedMultiplier(g, coord))
    val effectiveWoodCostSignal = gameSignal.map(g => TileKingdomLogic.effectiveBureauWoodCost(g))

    // Calculate min level of nearby upgradeable tiles for faith cost
    val minFaithCostSignal = gameSignal.map: g =>
      val nearbyCoords = TileKingdomLogic.bureauAffectedCoords(g, coord)
      val minLevel = nearbyCoords
        .flatMap(c => g.tiles.get(c))
        .filter(_.isUpgradeable)
        .map(_.level)
        .minOption
        .getOrElse(1)
      TileKingdomLogic.effectiveBureauFaithCostForLevel(g, minLevel)

    val canAffordTurboSignal = gameSignal.combineWith(minFaithCostSignal).map:
      case (g, cost) => g.faith >= cost

    // Direction signal (only relevant with Management3)
    val hasDirectionSkillSignal = gameSignal.map(_.hasSkill(Skill.Management3))
    val directionSignal = gameSignal.map(g => TileKingdomLogic.getBureauDirection(g, coord))

    // Mode-dependent extra CSS class
    val modeClsSignal = bureauModeSignal.map:
      case BureauMode.Turbo => "turbo"
      case BureauMode.Disabled => "disabled"
      case BureauMode.Slow => ""

    tileWrapper(coord, "bureau", Some(level), modeClsSignal)(
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
          title := "Cost per auto-upgrade",
          child.text <-- bureauModeSignal.combineWith(effectiveWoodCostSignal).map:
            case (BureauMode.Turbo, woodCost) => s"${woodCost}🪵 Lv×10✨"
            case (BureauMode.Slow, woodCost) => s"${woodCost}🪵"
            case (BureauMode.Disabled, _) => "—"
        ),

        // Mode toggle buttons
        div(
          cls := "bureau-mode-row",

          button(
            cls <-- bureauModeSignal.map(m =>
              if m == BureauMode.Slow then "btn-bureau-mode slow active"
              else "btn-bureau-mode slow"
            ),
            title := "Slow mode: 1x speed, wood cost only",
            "🐢",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Slow)
            }
          ),

          button(
            cls <-- bureauModeSignal.combineWith(canAffordTurboSignal).map:
              case (BureauMode.Turbo, _) => "btn-bureau-mode turbo active"
              case (_, false) => "btn-bureau-mode turbo insufficient"
              case _ => "btn-bureau-mode turbo",
            title <-- minFaithCostSignal.map(cost => s"Turbo mode: 10x speed, costs ${cost}✨ per upgrade"),
            "⚡",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Turbo)
            }
          ),

          button(
            cls <-- bureauModeSignal.map(m =>
              if m == BureauMode.Disabled then "btn-bureau-mode pause active"
              else "btn-bureau-mode pause"
            ),
            title := "Pause: stop auto-upgrading",
            "⏸️",
            onClick --> { e =>
              e.stopPropagation()
              actions.onSetMode(BureauMode.Disabled)
            }
          )
        ),

        // Direction toggle buttons (only shown with Management3 skill)
        div(
          cls := "bureau-direction-row",
          display <-- hasDirectionSkillSignal.map(has => if has then "flex" else "none"),

          directionButton(BureauDirection.Left, "⬅️", "Direct left (3×5)", directionSignal, actions),
          directionButton(BureauDirection.Up, "⬆️", "Direct up (5×3)", directionSignal, actions),
          directionButton(BureauDirection.Center, "⊙", "Centered (2-tile radius)", directionSignal, actions),
          directionButton(BureauDirection.Down, "⬇️", "Direct down (5×3)", directionSignal, actions),
          directionButton(BureauDirection.Right, "➡️", "Direct right (3×5)", directionSignal, actions)
        )
      ),

      // Progress bar (hidden when disabled)
      ProgressBar.withVisibility(
        progressSignal(coord),
        bureauModeSignal.map(_ != BureauMode.Disabled),
        "bureau-progress"
      ),

      destroyTileHandler(actions.onDestroy)
    )

  private def directionButton(
    dir: BureauDirection,
    label: String,
    titleText: String,
    directionSignal: Signal[BureauDirection],
    actions: Actions
  ): HtmlElement =
    button(
      cls <-- directionSignal.map(d =>
        if d == dir then "btn-bureau-dir active" else "btn-bureau-dir"
      ),
      title := titleText,
      label,
      onClick --> { e =>
        e.stopPropagation()
        actions.onSetDirection(dir)
      }
    )


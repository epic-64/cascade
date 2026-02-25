package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils}
import client.components.laminar.TileKingdomState
import TileComponents.*

/** Academy tile component.
  *
  * Has a mode toggle (Faster Politicians / Rare Chance) unless Education2 skill is active.
  */
object AcademyTile:

  /** Academy-specific actions (has toggle mode) */
  case class Actions(
    onToggleMode: () => Unit,
    onDestroy: () => Unit
  )

  def apply(
    coord: Coord,
    mode: AcademyMode,
    actions: Actions
  ): HtmlElement =
    val hasEducation2Signal = TileKingdomState.gameSignal.map(_.hasSkill(Skill.Education2))

    tileWrapper(coord, "academy")(
      div(
        cls := "tile-content academy-content",
        div(cls := "tile-icon", "🎓"),
        div(cls := "tile-label", "Academy"),

        // Mode badge - changes based on Education2 skill
        span(
          cls <-- hasEducation2Signal.map(has =>
            if has then "tile-badge badge-academy badge-academy-dual"
            else "tile-badge badge-academy"
          ),
          title <-- hasEducation2Signal.map: has =>
            if has then "Education skill: both bonuses active"
            else mode match
              case AcademyMode.FasterPoliticians => "Politicians spawn 2x faster"
              case AcademyMode.RareChance => "+10% chance for rare politicians",
          child.text <-- hasEducation2Signal.map: has =>
            if has then "⚡ 2x  ⭐ +10%"
            else mode match
              case AcademyMode.FasterPoliticians => "⚡ 2x Speed"
              case AcademyMode.RareChance => "⭐ +10% Rare"
        ),

        // Mode toggle button (only shown if Education2 not active)
        button(
          cls := "btn-toggle-mode",
          "⇄ Mode",
          display <-- hasEducation2Signal.map(has => if has then "none" else "inline-block"),
          onClick --> { e =>
            e.stopPropagation()
            actions.onToggleMode()
          }
        )
      ),
      destroyHandler(actions.onDestroy)
    )


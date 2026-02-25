package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based skills button for TileKingdom.
  *
  * Shows a glow animation when the player has skill points to spend.
  */
object SkillsButton:

  /** The skills button element */
  def apply(onToggleSkillTree: () => Unit): HtmlElement =
    import TileKingdomState.*

    val hasPointsToSpendSignal: Signal[Boolean] =
      hasSailedSignal.combineWith(skillPointsSignal).map:
        case (hasSailed, points) => hasSailed && points > 0

    button(
      idAttr := "tile-kingdom-skills-btn",
      cls := "btn-skills",
      cls <-- hasPointsToSpendSignal.map(has => if has then "has-points" else ""),
      "🌳 Skills",
      onClick --> { _ => onToggleSkillTree() }
    )


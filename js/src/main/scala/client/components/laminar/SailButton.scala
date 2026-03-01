package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based sail button for TileKingdom.
  *
  * Automatically updates the button text and enabled state
  * based on whether the player can sail and the skill point reward.
  */
object SailButton:

  /** The sail button element */
  def apply(onSail: () => Unit): HtmlElement =
    import TileKingdomState.*

    val buttonTextSignal: Signal[String] = 
      canSailSignal.combineWith(sailSkillPointRewardSignal, tileCountSignal, sailTileThresholdSignal).map:
        case (true, skillPoints, _, _) => s"⛵ Sail (+$skillPoints ⭐)"
        case (false, _, tileCount, threshold) => s"⛵ Sail ($tileCount/$threshold tiles)"

    button(
      idAttr := "tile-kingdom-sail-btn",
      cls := "btn-sail",
      cls <-- canSailSignal.map(enabled => if enabled then "" else "disabled"),
      disabled <-- canSailSignal.map(!_),
      child.text <-- buttonTextSignal,
      onClick --> { _ => onSail() }
    )


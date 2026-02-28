package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based sail button for TileKingdom.
  *
  * Automatically updates the button text and enabled state
  * based on whether the player can sail and the legacy reward.
  */
object SailButton:

  /** The sail button element */
  def apply(onSail: () => Unit): HtmlElement =
    import TileKingdomState.*

    val buttonTextSignal: Signal[String] = 
      canSailSignal.combineWith(sailLegacyRewardSignal, totalIslandsSignal).map:
        case (true, reward, _) => s"⛵ Sail (+$reward 🏅)"
        case (false, _, islandCount) => s"⛵ Sail ($islandCount/${TileKingdomLogic.SailMinIslands} islands)"

    button(
      idAttr := "tile-kingdom-sail-btn",
      cls := "btn-sail",
      cls <-- canSailSignal.map(enabled => if enabled then "" else "disabled"),
      disabled <-- canSailSignal.map(!_),
      child.text <-- buttonTextSignal,
      onClick --> { _ => onSail() }
    )


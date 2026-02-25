package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based abdication button for TileKingdom.
  *
  * Automatically updates the button text and enabled state
  * when the game state changes.
  */
object AbdicationButton:

  /** The abdication button element */
  def apply(onAbdicate: () => Unit): HtmlElement =
    import TileKingdomState.*

    button(
      idAttr := "tile-kingdom-abdicate-btn",
      cls := "btn-primary",
      cls <-- allTilesFilledSignal.map(enabled => if enabled then "" else "disabled"),
      disabled <-- allTilesFilledSignal.map(!_),
      child.text <-- allTilesFilledSignal.combineWith(abdicationRewardSignal).map:
        case (true, reward) => s"Abdicate (+$reward 💰)"
        case (false, _) => "Abdicate",
      onClick --> { _ => onAbdicate() }
    )


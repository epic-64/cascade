package client.components.laminar

import com.raquo.laminar.api.L.*
import client.components.laminar.tilekingdom.TileGridState

/** Laminar-based Zen Mode toggle button for TileKingdom.
  * 
  * Toggles between normal mode (with text) and zen mode (emojis only).
  */
object ZenModeButton:

  def apply(): HtmlElement =
    val zenModeSignal = TileGridState.zenMode.signal

    button(
      idAttr := "tile-kingdom-zen-btn",
      cls := "btn-secondary",
      "🧘 Zen",
      title <-- zenModeSignal.map: isZen =>
        if isZen then "Show tile text" else "Hide tile text (Zen Mode)",
      onClick --> { _ => TileGridState.toggleZenMode() }
    )


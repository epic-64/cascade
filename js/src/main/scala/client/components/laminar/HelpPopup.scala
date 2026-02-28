package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based help popup for TileKingdom.
  *
  * Displays game instructions.
  */
object HelpPopup:

  /** The help popup element */
  def apply(onClose: () => Unit): HtmlElement =
    div(
      idAttr := "tile-kingdom-help-popup",
      cls := "help-popup",
      div(
        cls := "help-popup-content",
        div(
          cls := "help-popup-header",
          h3("How to Play"),
          button(
            cls := "help-close-btn",
            "✕",
            onClick --> { _ => onClose() }
          )
        ),
        div(
          cls := "help-popup-body",
          p("🏝️ Each island is a 3×5 grid of 15 tiles"),
          p("🔓 Unlock adjacent tiles with gold or tile points"),
          p("🌾 Click empty tiles to build wheat fields"),
          p("⬆️ Click buildings to level them up"),
          p("🏠 After your first wheat field, you can build farms"),
          p("📈 Farms boost nearby wheat fields by 25% per level"),
          p("🏛️ Politicians and bureaus only affect their own island"),
          p("👑 Fill all tiles to abdicate (keeps tiles, earns gold)"),
          p("🏝️ Unlock new islands when one is complete"),
          p("⛵ With 2+ islands, Sail for legacy points (resets islands)"),
          p("🏅 25 legacy points = 1 skill point"),
          p("⬅️➡️ Use arrow keys to navigate between islands"),
          p("🗑️ Right-click a building to destroy it")
        )
      )
    )


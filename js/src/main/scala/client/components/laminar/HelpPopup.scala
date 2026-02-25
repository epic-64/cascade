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
          p("🌾 Click empty tiles to build wheat fields"),
          p("⬆️ Click buildings to level them up"),
          p("🏠 After your first wheat field, you can build farms"),
          p("📈 Farms boost nearby wheat fields by 25% per level"),
          p("👑 Fill all unlocked tiles to abdicate"),
          p("💰 Abdication earns gold based on income rate"),
          p("🔓 Click adjacent tiles to expand your territory"),
          p("⛵ At 25 tiles, you can Sail for legacy points"),
          p("🏅 25 legacy points = 1 skill point"),
          p("🖱️ Drag to pan, scroll to zoom"),
          p("🗑️ Right-click a building to destroy it")
        )
      )
    )


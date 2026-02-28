package client.components.laminar.mobile

import com.raquo.laminar.api.L.*
import client.components.laminar.TileKingdomState
import client.components.laminar.tilekingdom.TileGridState

/** Mobile menu popup with secondary actions. */
object MobileMenu:

  private val isOpen: Var[Boolean] = Var(false)

  def open(): Unit = isOpen.set(true)
  def close(): Unit = isOpen.set(false)
  def toggle(): Unit = isOpen.update(!_)

  def apply(
    onHelp: () => Unit,
    onReset: () => Unit,
    onDevTools: () => Unit
  ): HtmlElement =
    val legacySignal = TileKingdomState.legacyPointsSignal
    val skillPointsSignal = TileKingdomState.skillPointsSignal
    val tileCountSignal = TileKingdomState.tileCountSignal
    val totalIslandsSignal = TileKingdomState.totalIslandsSignal
    val abdicationsSignal = TileKingdomState.totalAbdicationsSignal
    val influenceLinesSignal = TileGridState.showInfluenceLines.signal

    div(
      cls := "mobile-menu-overlay",
      cls <-- isOpen.signal.map(open => if open then "show" else ""),

      // Backdrop - click to close
      div(
        cls := "mobile-menu-backdrop",
        onClick --> { _ => close() }
      ),

      // Menu content
      div(
        cls := "mobile-menu-content",

        // Header
        div(
          cls := "mobile-menu-header",
          h3("Menu"),
          button(
            cls := "mobile-menu-close",
            "✕",
            onClick --> { _ => close() }
          )
        ),

        // Stats section
        div(
          cls := "mobile-menu-section",
          h4("Progress"),
          div(
            cls := "mobile-menu-stats",
            statItem("👑", "Reigns", abdicationsSignal.map(_.toString)),
            statItem("🏝️", "Islands", totalIslandsSignal.map(_.toString)),
            statItem("🗺️", "Tiles", tileCountSignal.map(_.toString)),
            statItem("🏅", "Legacy", legacySignal.map(_.toString)),
            statItem("⭐", "Skills", skillPointsSignal.map(_.toString))
          )
        ),

        // Actions section
        div(
          cls := "mobile-menu-section",
          h4("Actions"),

          // Influence lines toggle
          button(
            cls := "mobile-menu-item",
            cls <-- influenceLinesSignal.map(on => if on then "active" else ""),
            span(cls := "menu-item-icon", "📐"),
            span(cls := "menu-item-label", "Influence Lines"),
            span(cls := "menu-item-status", child.text <-- influenceLinesSignal.map(on => if on then "ON" else "OFF")),
            onClick --> { _ => TileGridState.toggleInfluenceLines() }
          ),

          // Help button
          button(
            cls := "mobile-menu-item",
            span(cls := "menu-item-icon", "❓"),
            span(cls := "menu-item-label", "Help"),
            onClick --> { _ =>
              close()
              onHelp()
            }
          ),

          // Dev Tools button
          button(
            cls := "mobile-menu-item",
            span(cls := "menu-item-icon", "🛠️"),
            span(cls := "menu-item-label", "Dev Tools"),
            onClick --> { _ =>
              close()
              onDevTools()
            }
          ),

          // Reset button
          button(
            cls := "mobile-menu-item danger",
            span(cls := "menu-item-icon", "🗑️"),
            span(cls := "menu-item-label", "Reset Game"),
            onClick --> { _ =>
              close()
              onReset()
            }
          )
        )
      )
    )

  private def statItem(icon: String, label: String, valueSignal: Signal[String]): HtmlElement =
    div(
      cls := "mobile-stat-item",
      span(cls := "stat-icon", icon),
      span(cls := "stat-label", label),
      span(cls := "stat-value", child.text <-- valueSignal)
    )


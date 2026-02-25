package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based dev tools button for TileKingdom. */
object DevButton:

  def apply(onToggleDevTools: () => Unit): HtmlElement =
    button(
      idAttr := "tile-kingdom-dev-btn",
      cls := "btn-dev",
      "🛠️ Dev",
      onClick --> { _ => onToggleDevTools() }
    )


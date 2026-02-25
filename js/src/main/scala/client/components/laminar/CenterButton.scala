package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based center button for TileKingdom. */
object CenterButton:

  def apply(onCenter: () => Unit): HtmlElement =
    button(
      idAttr := "tile-kingdom-center-btn",
      cls := "btn-secondary",
      "⌖ Center",
      onClick --> { _ => onCenter() }
    )


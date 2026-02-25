package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based reset button for TileKingdom. */
object ResetButton:

  def apply(onReset: () => Unit): HtmlElement =
    button(
      idAttr := "tile-kingdom-reset-btn",
      cls := "btn-danger",
      "Reset",
      onClick --> { _ => onReset() }
    )


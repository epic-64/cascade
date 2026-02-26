package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based auto layout button for TileKingdom. */
object AutoLayoutButton:

  def apply(): HtmlElement =
    button(
      idAttr := "tile-kingdom-layout-btn",
      cls := "btn-layout",
      "📐 Layout",
      onClick --> { _ => DraggablePanel.resetAllPositions() }
    )


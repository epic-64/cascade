package client.components.laminar

import com.raquo.laminar.api.L.*
import client.components.laminar.tilekingdom.TileGridState

/** Toggle button to always show influence lines on the grid. */
object InfluenceLinesButton:

  def apply(): HtmlElement =
    val activeSignal = TileGridState.showInfluenceLines.signal

    button(
      idAttr := "tile-kingdom-influence-btn",
      cls := "btn-secondary",
      cls <-- activeSignal.map(active => if active then "active" else ""),
      child.text <-- activeSignal.map(active => if active then "🔗 Lines On" else "🔗 Lines"),
      title := "Toggle influence lines between tiles",
      onClick --> { _ => TileGridState.toggleInfluenceLines() }
    )


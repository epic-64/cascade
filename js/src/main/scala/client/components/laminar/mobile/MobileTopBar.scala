package client.components.laminar.mobile

import com.raquo.laminar.api.L.*
import client.components.laminar.TileKingdomState

/** Mobile top bar showing compact resource display. */
object MobileTopBar:

  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.0f"

  def apply(onMenuClick: () => Unit): HtmlElement =
    div(
      cls := "mobile-top-bar",
      
      // Resources row
      div(
        cls := "mobile-resources",
        resourceItem("🌾", TileKingdomState.wheatSignal),
        resourceItem("🪵", TileKingdomState.woodSignal),
        resourceItem("🪨", TileKingdomState.stoneSignal),
        resourceItem("✨", TileKingdomState.faithSignal),
        resourceItem("💰", TileKingdomState.goldSignal.map(_.toDouble))
      ),
      
      // Menu button
      button(
        cls := "mobile-menu-btn",
        "☰",
        onClick --> { _ => onMenuClick() }
      )
    )

  private def resourceItem(emoji: String, valueSignal: Signal[Double]): HtmlElement =
    div(
      cls := "mobile-resource-item",
      span(cls := "mobile-resource-icon", emoji),
      span(cls := "mobile-resource-value", child.text <-- valueSignal.map(formatNumber))
    )


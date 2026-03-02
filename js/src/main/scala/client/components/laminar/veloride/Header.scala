package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Header component with title and gold display */
object Header:

  def apply(): HtmlElement =
    div(
      cls := "velor-header",
      div(
        cls := "velor-header-title",
        "⚔️",
        span("Velor Idle")
      ),
      div(
        cls := "velor-gold-display",
        "💰",
        child.text <-- VelorIdleState.goldSignal.map(formatGold)
      )
    )

  private def formatGold(gold: Long): String =
    if gold >= 1_000_000 then f"${gold / 1_000_000.0}%.1fM"
    else if gold >= 1_000 then f"${gold / 1_000.0}%.1fk"
    else gold.toString


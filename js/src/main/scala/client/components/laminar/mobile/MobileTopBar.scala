package client.components.laminar.mobile

import com.raquo.laminar.api.L.*
import client.components.laminar.TileKingdomState
import shared.TileKingdom.TileKingdomLogic

/** Mobile top bar showing compact resource display. */
object MobileTopBar:

  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.0f"

  private def formatIncome(rate: Double): String =
    if rate <= 0 then ""
    else s"+${formatNumber(rate)}"

  def apply(onMenuClick: () => Unit): HtmlElement =
    val politicianCountSignal = TileKingdomState.gameSignal.map: game =>
      val current = game.politicianRoster.size
      val max = TileKingdomLogic.maxPoliticianRosterSize(game)
      s"$current/$max"

    div(
      cls := "mobile-top-bar",
      
      // Resources row with integrated income
      div(
        cls := "mobile-resources",
        resourceItemWithIncome("🌾", TileKingdomState.wheatSignal, TileKingdomState.wheatIncomeSignal),
        resourceItemWithIncome("🪵", TileKingdomState.woodSignal, TileKingdomState.woodIncomeSignal),
        resourceItemWithIncome("🪨", TileKingdomState.stoneSignal, TileKingdomState.stoneIncomeSignal),
        resourceItemWithIncome("✨", TileKingdomState.faithSignal, TileKingdomState.faithIncomeSignal),
        resourceItem("💰", TileKingdomState.goldSignal.map(_.toDouble)),
        // Politician counter
        div(
          cls := "mobile-resource-item politicians",
          span(cls := "mobile-resource-icon", "👔"),
          span(cls := "mobile-resource-value", child.text <-- politicianCountSignal)
        )
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

  private def resourceItemWithIncome(emoji: String, valueSignal: Signal[Double], incomeSignal: Signal[Double]): HtmlElement =
    div(
      cls := "mobile-resource-item",
      span(cls := "mobile-resource-icon", emoji),
      span(cls := "mobile-resource-value", child.text <-- valueSignal.map(formatNumber)),
      span(cls := "mobile-resource-income", child.text <-- incomeSignal.map(formatIncome))
    )


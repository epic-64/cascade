package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based resource panel for TileKingdom.
  *
  * This component automatically updates when the game state changes.
  * No manual DOM manipulation needed - just update TileKingdomState.gameVar
  * and all displayed values refresh automatically.
  */
object ResourcePanel:

  /** Format a number for display (e.g., 1234 -> "1.2k") */
  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.1f"

  /** Format income rate for display */
  private def formatIncome(rate: Double): String =
    if rate <= 0 then ""
    else s"+${formatNumber(rate)}/s"

  /** A single resource row with value and optional income */
  private def resourceItem(
      emoji: String,
      valueSignal: Signal[String],
      incomeSignal: Option[Signal[String]] = None,
      extraClass: String = "",
      label: String = ""
  ): HtmlElement =
    val baseCls = if extraClass.isEmpty then "resource-item" else s"resource-item $extraClass"
    div(
      cls := baseCls,
      span(cls := "resource-label", emoji),
      span(cls := "resource-value", child.text <-- valueSignal),
      incomeSignal.map(sig => span(cls := "resource-income", child.text <-- sig)),
      if label.nonEmpty then span(cls := "resource-name", label.toUpperCase) else emptyNode
    )

  /** The main resource panel element - now draggable */
  def apply(): HtmlElement =
    import TileKingdomState.*

    DraggablePanel("resource-panel", "Resources")(
      // Wheat with income
      resourceItem(
        "🌾",
        wheatSignal.map(w => formatNumber(w)),
        Some(wheatIncomeSignal.map(formatIncome)),
        label = "wheat"
      ),

      // Wood with income
      resourceItem(
        "🪵",
        woodSignal.map(w => formatNumber(w)),
        Some(woodIncomeSignal.map(formatIncome)),
        label = "wood"
      ),

      // Stone with income
      resourceItem(
        "🪨",
        stoneSignal.map(s => formatNumber(s)),
        Some(stoneIncomeSignal.map(formatIncome)),
        label = "stone"
      ),

      // Faith with income
      resourceItem(
        "✨",
        faithSignal.map(f => formatNumber(f)),
        Some(faithIncomeSignal.map(formatIncome)),
        label = "faith"
      ),

      // Total income rate (moved here, below faith)
      div(
        cls := "resource-item income",
        span(cls := "resource-label", "📈"),
        span(cls := "resource-value", child.text <-- totalIncomeSignal.map(i => s"${formatNumber(i)}/s")),
        span(cls := "resource-name", "INCOME")
      ),

      // Gold (no income rate)
      resourceItem("💰", goldSignal.map(g => formatNumber(g.toDouble)), label = "gold")
    )

  /** Draggable meta panel with prestige stats and tile info */
  def metaPanel(): HtmlElement =
    import TileKingdomState.*

    DraggablePanel("meta-panel", "Progress")(
      // Abdications
      resourceItem("👑", totalAbdicationsSignal.map(_.toString), label = "reigns"),

      // Legacy points with /25 label
      div(
        cls := "resource-item prestige",
        span(cls := "resource-label", "🏅"),
        span(cls := "resource-value", child.text <-- legacyPointsSignal.map(_.toString)),
        span(cls := "resource-label-small", "/25"),
        span(cls := "resource-name", "LEGACY")
      ),

      // Skill points
      resourceItem("⭐", skillPointsSignal.map(_.toString), extraClass = "prestige", label = "skills"),

      // Tile points (only show if > 0)
      div(
        cls := "resource-item prestige",
        display <-- tilePointsSignal.map(tp => if tp > 0 then "flex" else "none"),
        span(cls := "resource-label", "🎫"),
        span(cls := "resource-value", child.text <-- tilePointsSignal.map(_.toString)),
        span(cls := "resource-name", "TILES")
      ),

      // Unlocked tiles count
      resourceItem("🗺️", tileCountSignal.map(_.toString), label = "tiles"),

      // Next tile unlock costs
      div(
        cls := "resource-item unlock-costs",
        span(cls := "resource-label", "🔓 Next tiles cost:"),
        span(
          cls := "resource-value",
          child.text <-- nextTileUnlockCostsSignal.map(costs => costs.map(c => formatNumber(c.toDouble)).mkString(" → "))
        )
      )
    )


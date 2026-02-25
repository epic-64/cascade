package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based politician timer display.
  *
  * Shows countdown until the next politician arrives.
  */
object PoliticianTimer:

  /** Format seconds as "Xm Ys" or "Xs" */
  private def formatTime(totalSeconds: Int): String =
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    f"$minutes%d:$seconds%02d"

  /** The timer display element */
  def apply(): HtmlElement =
    import TileKingdomState.*

    val timerTextSignal: Signal[String] = 
      hasTownHallSignal.combineWith(rosterFullSignal, politicianTimerSignal).map:
        case (false, _, _) => ""
        case (true, true, _) => "Full"
        case (true, false, Some((seconds, isFast))) => 
          val suffix = if isFast then " ⚡" else ""
          s"Next: ${formatTime(seconds)}$suffix"
        case (true, false, None) => ""

    span(
      idAttr := "politician-timer",
      cls := "roster-timer",
      child.text <-- timerTextSignal
    )

  /** The rare chance display element */
  def rareChance(): HtmlElement =
    import TileKingdomState.*

    span(
      idAttr := "politician-rare-chance",
      cls := "roster-rare-chance",
      child.text <-- hasTownHallSignal.combineWith(rareChanceSignal).map:
        case (false, _) => ""
        case (true, chance) => s"⭐ ${(chance * 100).toInt}%"
    )


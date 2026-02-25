package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based politician roster panel containing the header, timer, roster list, and trash zone. */
object PoliticianRosterPanel:

  def apply(onDiscard: String => Unit): HtmlElement =
    div(
      idAttr := "tile-kingdom-politician-roster",
      cls := "politician-roster",
      div(
        cls := "roster-header",
        span(cls := "roster-title", "🏛️ Politicians"),
        div(
          cls := "roster-stats",
          PoliticianTimer(),
          PoliticianTimer.rareChance()
        )
      ),
      PoliticianRoster(),
      PoliticianRoster.trashZone(onDiscard)
    )


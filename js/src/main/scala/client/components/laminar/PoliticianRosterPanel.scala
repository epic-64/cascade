package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Laminar-based politician roster panel containing the header, timer, roster list, and trash zone. */
object PoliticianRosterPanel:

  def apply(onDiscard: String => Unit): HtmlElement =
    import TileKingdomState.*

    val rosterCountSignal = gameSignal.map: game =>
      val current = game.politicianRoster.size
      val max = TileKingdomLogic.maxPoliticianRosterSize(game)
      s"Politicians ($current/$max)"
    .distinct

    DraggablePanel("politician-panel", "")(
      // Reactive header with count
      div(
        cls := "panel-title roster-title",
        child.text <-- rosterCountSignal
      ),
      div(
        cls := "roster-header",
        div(
          cls := "roster-stats",
          PoliticianTimer(),
          PoliticianTimer.rareChance()
        )
      ),
      PoliticianRoster(),
      PoliticianRoster.trashZone(onDiscard)
    )


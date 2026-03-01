package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Island navigation component for switching between islands.
  *
  * Displays current island indicator and left/right navigation buttons.
  */
object IslandNavigator:

  /** Actions for island navigation */
  case class Actions(
    onPreviousIsland: () => Unit,
    onNextIsland: () => Unit,
    onUnlockNewIsland: () => Unit
  )

  def apply(actions: Actions): HtmlElement =
    val currentIndexSignal = TileKingdomState.currentIslandIndexSignal
    val totalIslandsSignal = TileKingdomState.totalIslandsSignal
    val canGoPreviousSignal = TileKingdomState.canGoPreviousIslandSignal
    val canGoNextSignal = TileKingdomState.canGoNextIslandSignal
    val canUnlockNewIslandSignal = TileKingdomState.canUnlockNewIslandSignal
    val currentIslandCompleteSignal = TileKingdomState.currentIslandCompleteSignal
    val goldSignal = TileKingdomState.goldSignal
    val gameSignal = TileKingdomState.gameSignal

    // Calculate unlock cost for next island
    val unlockCostSignal = gameSignal.map: game =>
      TileKingdomLogic.islandUnlockCost(game.islands.size)
    .distinct

    div(
      cls := "island-navigator",


      // Previous island button
      button(
        cls := "island-nav-btn island-nav-prev",
        cls <-- canGoPreviousSignal.map(can => if can then "" else "disabled"),
        disabled <-- canGoPreviousSignal.map(!_),
        onClick --> (_ => actions.onPreviousIsland()),
        "←"
      ),

      // Island indicator
      div(
        cls := "island-indicator",
        child <-- currentIndexSignal.combineWith(totalIslandsSignal).map: (index, total) =>
          span(s"🏝️ Island ${index + 1} / $total")
      ),

      // Next island button (or unlock button if at last island and can unlock)
      child <-- canGoNextSignal.combineWith(canUnlockNewIslandSignal).combineWith(currentIslandCompleteSignal)
        .combineWith(unlockCostSignal).map: (canGoNext, canUnlock, isComplete, cost) =>
          if canGoNext then
            // Navigate to next island
            button(
              cls := "island-nav-btn island-nav-next",
              onClick --> (_ => actions.onNextIsland()),
              "→"
            )
          else if canUnlock then
            // Unlock new island button
            button(
              cls := "island-nav-btn island-unlock-btn",
              onClick --> (_ => actions.onUnlockNewIsland()),
              s"🏝️ +New ($cost 💰)"
            )
          else if isComplete then
            // Island complete but can't afford new island
            button(
              cls := "island-nav-btn island-nav-next disabled",
              disabled := true,
              title := s"Need $cost gold to unlock new island",
              s"🔒 $cost 💰"
            )
          else
            // Not at last island yet, or island not complete
            button(
              cls := "island-nav-btn island-nav-next disabled",
              disabled := true,
              "→"
            )
    )


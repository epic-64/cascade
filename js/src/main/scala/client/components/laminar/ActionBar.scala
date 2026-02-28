package client.components.laminar

import com.raquo.laminar.api.L.*
import shared.TileKingdom.TileKingdomGame

/** Laminar-based action bar containing all game action buttons. */
object ActionBar:

  def apply(
    currentGame: () => TileKingdomGame,
    onAbdicate: () => Unit,
    onSail: () => Unit,
    onToggleSkillTree: () => Unit,
    onReset: () => Unit,
    onToggleDevTools: () => Unit
  ): HtmlElement =
    div(
      cls := "tile-kingdom-actions",
      AbdicationButton(onAbdicate),
      SailButton(onSail),
      SkillsButton(onToggleSkillTree),
      InfluenceLinesButton(),
      AutoLayoutButton(),
      ResetButton(onReset),
      DevButton(onToggleDevTools)
    )


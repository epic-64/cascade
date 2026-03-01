package client.components.laminar.mobile

import com.raquo.laminar.api.L.*
import client.components.laminar.TileKingdomState
import client.components.laminar.tilekingdom.TileGridState

/** Mobile bottom action bar with main game actions. */
object MobileActionBar:

  def apply(
    onAbdicate: () => Unit,
    onSail: () => Unit,
    onToggleSkillTree: () => Unit
  ): HtmlElement =
    val canAbdicateSignal = TileKingdomState.allTilesFilledSignal
    val canSailSignal = TileKingdomState.canSailSignal
    val hasSailedSignal = TileKingdomState.hasSailedSignal
    val zenModeSignal = TileGridState.zenMode.signal

    div(
      cls := "mobile-action-bar",
      
      // Abdicate button
      button(
        cls := "mobile-action-btn",
        cls <-- canAbdicateSignal.map(can => if can then "enabled" else "disabled"),
        disabled <-- canAbdicateSignal.map(!_),
        span(cls := "mobile-action-icon", "👑"),
        span(cls := "mobile-action-label", "Abdicate"),
        onClick --> { _ => onAbdicate() }
      ),
      
      // Sail button
      button(
        cls := "mobile-action-btn sail",
        cls <-- canSailSignal.map(can => if can then "enabled" else "disabled"),
        disabled <-- canSailSignal.map(!_),
        span(cls := "mobile-action-icon", "⛵"),
        span(cls := "mobile-action-label", "Sail"),
        onClick --> { _ => onSail() }
      ),
      
      // Skills button
      button(
        cls := "mobile-action-btn",
        cls <-- hasSailedSignal.map(has => if has then "enabled" else "disabled"),
        disabled <-- hasSailedSignal.map(!_),
        span(cls := "mobile-action-icon", "⭐"),
        span(cls := "mobile-action-label", "Skills"),
        onClick --> { _ => onToggleSkillTree() }
      ),
      
      // Zen mode toggle
      button(
        cls := "mobile-action-btn zen",
        cls <-- zenModeSignal.map(zen => if zen then "active" else ""),
        span(cls := "mobile-action-icon", child.text <-- zenModeSignal.map(z => if z then "📝" else "🧘")),
        span(cls := "mobile-action-label", child.text <-- zenModeSignal.map(z => if z then "Text" else "Zen")),
        onClick --> { _ => TileGridState.toggleZenMode() }
      )
    )


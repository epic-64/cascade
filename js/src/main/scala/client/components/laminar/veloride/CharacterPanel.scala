package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Character panel with sub-tabs for Potions, Tablets, and future Equipment */
object CharacterPanel:

  def apply(
    onDrinkPotion: Item => Unit,
    onRemovePotion: () => Unit,
    onEquipTablet: (Item, Int) => Unit,
    onUnequipTablet: Int => Unit
  ): HtmlElement =
    div(
      cls := "velor-character-panel",
      
      // Sub-tab navigation
      tabNavigation(),
      
      // Tab content
      child <-- VelorIdleState.characterTabSignal.map:
        case VelorIdleState.CharacterTab.Potions =>
          PotionPanel(onDrinkPotion, onRemovePotion)
        case VelorIdleState.CharacterTab.Tablets =>
          TabletPanel(onEquipTablet, onUnequipTablet)
    )

  private def tabNavigation(): HtmlElement =
    div(
      cls := "velor-character-tabs",
      tabButton("🧪", "Potions", VelorIdleState.CharacterTab.Potions),
      tabButton("📜", "Tablets", VelorIdleState.CharacterTab.Tablets)
      // Future: tabButton("🛡️", "Equipment", VelorIdleState.CharacterTab.Equipment)
    )

  private def tabButton(icon: String, label: String, tab: VelorIdleState.CharacterTab): HtmlElement =
    val isActive = VelorIdleState.characterTabSignal.map(_ == tab)
    
    button(
      cls <-- isActive.map(active => if active then "velor-char-tab-btn active" else "velor-char-tab-btn"),
      onClick --> { _ => VelorIdleState.setCharacterTab(tab) },
      span(cls := "velor-char-tab-icon", icon),
      span(label)
    )


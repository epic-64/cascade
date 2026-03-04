package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Character panel with sub-tabs for Potions, Tablets, and Equipment */
object CharacterPanel:

  def apply(
    onDrinkPotion: Item => Unit,
    onRemovePotion: () => Unit,
    onEquipTablet: (Item, Int) => Unit,
    onUnequipTablet: Int => Unit,
    onEquipWeapon: Long => Unit,
    onUnequipWeapon: () => Unit,
    onEquipArmor: Long => Unit,
    onUnequipArmor: () => Unit,
    onSellEquipment: Long => Unit
  ): HtmlElement =
    div(
      cls := "velor-view velor-view-fill velor-character-panel",

      // Sub-tab navigation
      tabNavigation(),
      
      // Tab content
      child <-- VelorIdleState.characterTabSignal.map:
        case VelorIdleState.CharacterTab.Potions =>
          PotionPanel(onDrinkPotion, onRemovePotion)
        case VelorIdleState.CharacterTab.Tablets =>
          TabletPanel(onEquipTablet, onUnequipTablet)
        case VelorIdleState.CharacterTab.Equipment =>
          EquipmentPanel(onEquipWeapon, onUnequipWeapon, onEquipArmor, onUnequipArmor, onSellEquipment)
    )

  private def tabNavigation(): HtmlElement =
    div(
      cls := "velor-character-tabs",
      tabButton("🧪", "Potions", VelorIdleState.CharacterTab.Potions),
      tabButton("📜", "Tablets", VelorIdleState.CharacterTab.Tablets),
      tabButton("⚔️", "Equipment", VelorIdleState.CharacterTab.Equipment)
    )

  private def tabButton(icon: String, label: String, tab: VelorIdleState.CharacterTab): HtmlElement =
    val isActive = VelorIdleState.characterTabSignal.map(_ == tab)
    
    button(
      cls <-- isActive.map(active => if active then "velor-char-tab-btn active" else "velor-char-tab-btn"),
      onClick --> { _ => VelorIdleState.setCharacterTab(tab) },
      span(cls := "velor-char-tab-icon", icon),
      span(label)
    )


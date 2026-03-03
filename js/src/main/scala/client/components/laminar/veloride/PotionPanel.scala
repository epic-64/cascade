package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Potion management panel - drink potions from inventory and view active effects */
object PotionPanel:

  def apply(onDrinkPotion: Item => Unit, onRemovePotion: () => Unit): HtmlElement =
    div(
      cls := "velor-potion-panel",
      
      // Header
      div(
        cls := "velor-skill-card",
        h3("🧪 Potions"),
        p(cls := "velor-text-muted", "Drink potions to boost your skills. One potion lasts for 30 actions.")
      ),
      
      // Active potion display
      activePotion(onRemovePotion),
      
      // Available potions from inventory
      availablePotions(onDrinkPotion)
    )

  private def activePotion(onRemovePotion: () => Unit): HtmlElement =
    div(
      cls := "velor-active-potion-section",
      h4("Active Effect"),
      child <-- VelorIdleState.potionSlotsSignal.map: slots =>
        slots.activePotion match
          case None =>
            div(
              cls := "velor-no-active-potion",
              span(cls := "velor-text-muted", "No active potion")
            )
          case Some(active) =>
            div(
              cls := "velor-active-potion-card",
              div(
                cls := "velor-active-potion-header",
                span(cls := "velor-active-potion-icon", Item.icon(active.potion)),
                span(cls := "velor-active-potion-name", Item.displayName(active.potion))
              ),
              div(
                cls := "velor-active-potion-effect",
                PotionEffect.description(active.effect)
              ),
              div(
                cls := "velor-active-potion-remaining",
                span(s"${active.actionsRemaining} actions remaining")
              ),
              // Progress bar showing remaining duration
              div(
                cls := "velor-potion-progress-bar",
                div(
                  cls := "velor-potion-progress-fill",
                  styleAttr := s"width: ${(active.actionsRemaining * 100 / ActivePotion.DefaultDuration)}%"
                )
              ),
              button(
                cls := "btn btn-danger btn-sm",
                "Remove",
                onClick --> { _ => onRemovePotion() }
              )
            )
    )

  private def availablePotions(onDrinkPotion: Item => Unit): HtmlElement =
    div(
      cls := "velor-available-potions-section",
      h4("Inventory Potions"),
      children <-- VelorIdleState.inventorySignal.map: inventory =>
        val potionItems = inventory.slots.flatten
          .filter(stack => PotionEffect.isPotion(stack.item))
        
        if potionItems.isEmpty then
          Vector(
            div(
              cls := "velor-no-potions",
              span(cls := "velor-text-muted", "No potions in inventory. Brew some with Alchemy!")
            )
          )
        else
          potionItems.map: stack =>
            potionCard(stack.item, stack.count, onDrinkPotion)
    )

  private def potionCard(potion: Item, count: Long, onDrinkPotion: Item => Unit): HtmlElement =
    val effect = PotionEffect.forPotion(potion)
    
    div(
      cls := "velor-potion-card",
      div(
        cls := "velor-potion-card-header",
        span(cls := "velor-potion-icon", Item.icon(potion)),
        span(cls := "velor-potion-name", Item.displayName(potion)),
        span(cls := "velor-potion-count", s"x$count")
      ),
      effect.map: eff =>
        div(
          cls := "velor-potion-effect-desc",
          PotionEffect.description(eff)
        )
      .getOrElse(emptyNode),
      div(
        cls := "velor-potion-duration",
        s"Lasts ${ActivePotion.DefaultDuration} actions"
      ),
      button(
        cls := "btn btn-primary",
        disabled <-- VelorIdleState.potionSlotsSignal.map(_.activePotion.isDefined),
        "Drink",
        onClick --> { _ => onDrinkPotion(potion) }
      )
    )


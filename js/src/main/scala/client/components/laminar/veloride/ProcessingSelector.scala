package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Processing action selector - list of available recipes with ingredient requirements */
object ProcessingSelector:

  def apply(skill: Skill, onStartAction: String => Unit): HtmlElement =
    val actionsSignal = VelorIdleState.availableProcessingActionsSignal
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val activeActionSignal = VelorIdleState.activeActionSignal
    val inventorySignal = VelorIdleState.inventorySignal
    
    div(
      cls := "velor-action-list",
      children <-- actionsSignal.combineWith(skillStateSignal, activeActionSignal, inventorySignal).map {
        case (actions, state, active, inventory) =>
          actions.map(action => processingItem(action, state.level, active, inventory, onStartAction))
      }
    )

  private def processingItem(
    action: ProcessingAction,
    playerLevel: Int,
    activeAction: ActiveAction,
    inventory: Inventory,
    onStart: String => Unit
  ): HtmlElement =
    val isLocked = playerLevel < action.levelRequired
    val hasIngredients = action.inputs.forall { case (item, count) =>
      inventory.getCount(item) >= count
    }
    val isActive = activeAction match
      case ActiveAction.Processing(a) => a.id == action.id
      case _ => false

    val itemCls =
      if isLocked then "velor-action-item locked"
      else if !hasIngredients then "velor-action-item locked"
      else if isActive then "velor-action-item active"
      else "velor-action-item"

    div(
      cls := itemCls,
      onClick --> { _ => if !isLocked && hasIngredients then onStart(action.id) },

      div(
        cls := "velor-action-item-left",
        div(cls := "velor-action-item-icon", action.icon),
        div(
          div(cls := "velor-action-item-name", action.name),
          div(
            cls := "velor-action-item-level",
            if isLocked then s"🔒 Level ${action.levelRequired}"
            else ingredientsList(action.inputs, inventory)
          )
        )
      ),
      div(
        cls := "velor-action-item-right",
        div(cls := "velor-action-item-xp", s"+${action.xpGain} XP"),
        div(s"${action.timeSeconds}s")
      )
    )

  private def ingredientsList(inputs: Vector[(Item, Int)], inventory: Inventory): String =
    inputs.map { case (item, count) =>
      val have = inventory.getCount(item)
      val icon = Item.icon(item)
      s"$icon $have/$count"
    }.mkString(" ")


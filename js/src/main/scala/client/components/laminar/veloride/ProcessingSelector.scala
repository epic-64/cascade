package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Processing action selector - list of available recipes with ingredient requirements.
  *
  * Uses stable element creation to prevent scroll reset on state updates.
  */
object ProcessingSelector:

  def apply(skill: Skill, onStartAction: String => Unit): HtmlElement =
    // Get actions once (they don't change for a skill)
    val actions = ProcessingActions.forSkill(skill)
    
    div(
      cls := "velor-action-list",
      // Create items once, use reactive bindings for dynamic parts
      actions.map(action => processingItem(skill, action, onStartAction))
    )

  private def processingItem(
    skill: Skill,
    action: ProcessingAction,
    onStart: String => Unit
  ): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val activeActionSignal = VelorIdleState.activeActionSignal
    val inventorySignal = VelorIdleState.inventorySignal

    // Derive reactive signals for this specific action
    val isLockedSignal = skillStateSignal.map(_.level < action.levelRequired)
    val hasIngredientsSignal = inventorySignal.map { inventory =>
      action.inputs.forall { case (item, count) =>
        inventory.getCount(item) >= count
      }
    }
    val isActiveSignal = activeActionSignal.map:
      case ActiveAction.Processing(s, a) => s == skill && a.id == action.id
      case _ => false

    val itemClsSignal = isLockedSignal.combineWith(hasIngredientsSignal, isActiveSignal).map:
      case (true, _, _) => "velor-action-item locked"
      case (_, false, _) => "velor-action-item locked"
      case (_, _, true) => "velor-action-item active"
      case _ => "velor-action-item"

    div(
      cls <-- itemClsSignal,
      onClick --> { _ => 
        // Check state at click time via current game state
        val game = VelorIdleState.current
        val currentLevel = game.skills.getOrElse(skill, SkillState.initial).level
        val hasIngredients = action.inputs.forall { case (item, count) =>
          game.inventory.getCount(item) >= count
        }
        if currentLevel >= action.levelRequired && hasIngredients then onStart(action.id)
      },

      div(
        cls := "velor-action-item-left",
        div(cls := "velor-action-item-icon", action.icon),
        div(
          div(cls := "velor-action-item-name", action.name),
          div(
            cls := "velor-action-item-level",
            child.text <-- isLockedSignal.combineWith(inventorySignal).map { case (locked, inventory) =>
              if locked then s"🔒 Level ${action.levelRequired}"
              else ingredientsList(action.inputs, inventory)
            }
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

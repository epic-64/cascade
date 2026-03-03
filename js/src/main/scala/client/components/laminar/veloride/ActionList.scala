package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Unified action list component for both gathering and processing skills.
  *
  * Uses a common trait to abstract over the differences between action types.
  */
object ActionList:

  /** Common interface for actions that can be displayed in the list */
  trait ActionInfo:
    def id: String
    def name: String
    def icon: String
    def levelRequired: Int
    def xpGain: Int
    def timeSeconds: Double
    def isGathering: Boolean
    def subtitle(inventory: Inventory, isLocked: Boolean): String
    def isActive(activeAction: ActiveAction, skill: Skill): Boolean
    def canStart(game: VelorIdleGame, skill: Skill): Boolean
    def hasRequiredItems(inventory: Inventory): Boolean

  /** Wrapper for gathering actions */
  private class GatheringActionInfo(action: GatheringAction) extends ActionInfo:
    def id: String = action.id
    def name: String = action.name
    def icon: String = action.icon
    def levelRequired: Int = action.levelRequired
    def xpGain: Int = action.xpGain
    def timeSeconds: Double = action.timeSeconds
    def isGathering: Boolean = true

    def subtitle(inventory: Inventory, isLocked: Boolean): String =
      if isLocked then s"🔒 Level $levelRequired"
      else s"Level $levelRequired"
    
    def isActive(activeAction: ActiveAction, skill: Skill): Boolean =
      activeAction match
        case ActiveAction.Gathering(s, a) => s == skill && a.id == id
        case _ => false
    
    def canStart(game: VelorIdleGame, skill: Skill): Boolean =
      game.skills.getOrElse(skill, SkillState.initial).level >= levelRequired

    def hasRequiredItems(inventory: Inventory): Boolean = true // Gathering doesn't need items

  /** Wrapper for processing actions */
  private class ProcessingActionInfo(action: ProcessingAction) extends ActionInfo:
    def id: String = action.id
    def name: String = action.name
    def icon: String = action.icon
    def levelRequired: Int = action.levelRequired
    def xpGain: Int = action.xpGain
    def timeSeconds: Double = action.timeSeconds
    def isGathering: Boolean = false

    def subtitle(inventory: Inventory, isLocked: Boolean): String =
      if isLocked then s"🔒 Level $levelRequired"
      else ingredientsList(inventory)
    
    private def ingredientsList(inventory: Inventory): String =
      action.inputs.map { case (item, count) =>
        val have = inventory.getCount(item)
        s"${Item.icon(item)} $have/$count"
      }.mkString(" ")
    
    def isActive(activeAction: ActiveAction, skill: Skill): Boolean =
      activeAction match
        case ActiveAction.Processing(s, a) => s == skill && a.id == id
        case _ => false
    
    def canStart(game: VelorIdleGame, skill: Skill): Boolean =
      val level = game.skills.getOrElse(skill, SkillState.initial).level
      level >= levelRequired && hasRequiredItems(game.inventory)

    def hasRequiredItems(inventory: Inventory): Boolean =
      action.inputs.forall { case (item, count) =>
        inventory.getCount(item) >= count
      }

  /** Create action list for a gathering skill */
  def forGathering(skill: Skill, onStartAction: String => Unit): HtmlElement =
    val actions = GatheringActions.forSkill(skill).map(GatheringActionInfo(_))
    renderList(skill, actions, onStartAction)

  /** Create action list for a processing skill */
  def forProcessing(skill: Skill, onStartAction: String => Unit): HtmlElement =
    val actions = ProcessingActions.forSkill(skill).map(ProcessingActionInfo(_))
    renderList(skill, actions, onStartAction)

  private def renderList(
    skill: Skill,
    actions: Vector[ActionInfo],
    onStart: String => Unit
  ): HtmlElement =
    div(
      cls := "velor-action-list",
      actions.map(action => actionItem(skill, action, onStart))
    )

  private def actionItem(
    skill: Skill,
    action: ActionInfo,
    onStart: String => Unit
  ): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val actionStateSignal = VelorIdleState.actionStateSignal(action.id)
    val activeActionSignal = VelorIdleState.activeActionSignal
    val inventorySignal = VelorIdleState.inventorySignal

    val isLockedSignal = skillStateSignal.map(_.level < action.levelRequired)
    val isActiveSignal = activeActionSignal.map(action.isActive(_, skill))
    
    // Check both level and items (items check is no-op for gathering)
    val isDisabledSignal = isLockedSignal.combineWith(inventorySignal).map { case (locked, inv) =>
      locked || !action.hasRequiredItems(inv)
    }

    val itemClsSignal = isDisabledSignal.combineWith(isActiveSignal).map:
      case (true, _) => "velor-action-item locked"
      case (_, true) => "velor-action-item active"
      case _ => "velor-action-item"

    div(
      cls <-- itemClsSignal,
      onClick --> { _ =>
        if action.canStart(VelorIdleState.current, skill) then onStart(action.id)
      },
      div(
        cls := "velor-action-item-left",
        div(cls := "velor-action-item-icon", action.icon),
        div(
          div(
            cls := "velor-action-item-name",
            span(action.name),
            span(
              cls := "velor-action-item-action-level",
              child.text <-- actionStateSignal.map(s => s" Lv.${s.level}")
            )
          ),
          div(
            cls := "velor-action-item-level",
            child.text <-- isLockedSignal.combineWith(inventorySignal, actionStateSignal).map { case (locked, inv, actionState) =>
              if locked then action.subtitle(inv, locked)
              else if action.isGathering then
                val yieldBonus = VelorIdleLogic.calculateYieldBonus(actionState.level)
                f"Yield: ${yieldBonus * 100}%.0f%%"
              else action.subtitle(inv, locked)
            }
          )
        )
      ),
      div(
        cls := "velor-action-item-right",
        div(cls := "velor-action-item-xp", s"+${action.xpGain} XP"),
        div(
          child.text <-- skillStateSignal.map { state =>
            val efficiency = VelorIdleLogic.calculateEfficiencyBonus(state.level)
            val effectiveTime = action.timeSeconds * (1.0 - efficiency)
            f"$effectiveTime%.1fs"
          }
        )
      )
    )



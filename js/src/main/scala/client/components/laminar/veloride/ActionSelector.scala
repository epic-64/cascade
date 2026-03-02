package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Action selector - list of available actions within a skill.
  * 
  * Uses stable element creation to prevent scroll reset on state updates.
  */
object ActionSelector:

  def apply(skill: Skill, onStartAction: String => Unit): HtmlElement =
    // Get actions once (they don't change for a skill)
    val actions = GatheringActions.forSkill(skill)
    
    div(
      cls := "velor-action-list",
      // Create items once, use reactive bindings for dynamic parts
      actions.map(action => actionItem(skill, action, onStartAction))
    )

  private def actionItem(
    skill: Skill,
    action: GatheringAction,
    onStart: String => Unit
  ): HtmlElement =
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val activeActionSignal = VelorIdleState.activeActionSignal
    
    // Derive reactive signals for this specific action
    val isLockedSignal = skillStateSignal.map(_.level < action.levelRequired)
    val isActiveSignal = activeActionSignal.map:
      case ActiveAction.Gathering(a) => a.id == action.id
      case _ => false
    
    val itemClsSignal = isLockedSignal.combineWith(isActiveSignal).map:
      case (true, _) => "velor-action-item locked"
      case (_, true) => "velor-action-item active"
      case _ => "velor-action-item"
    
    div(
      cls <-- itemClsSignal,
      onClick --> { _ => 
        // Check locked state at click time via current game state
        val currentLevel = VelorIdleState.current.skills.getOrElse(skill, SkillState.initial).level
        if currentLevel >= action.levelRequired then onStart(action.id)
      },
      
      div(
        cls := "velor-action-item-left",
        div(cls := "velor-action-item-icon", action.icon),
        div(
          div(cls := "velor-action-item-name", action.name),
          div(
            cls := "velor-action-item-level",
            child.text <-- isLockedSignal.map(locked =>
              if locked then s"🔒 Level ${action.levelRequired}"
              else s"Level ${action.levelRequired}"
            )
          )
        )
      ),
      div(
        cls := "velor-action-item-right",
        div(cls := "velor-action-item-xp", s"+${action.xpGain} XP"),
        div(s"${action.timeSeconds}s")
      )
    )


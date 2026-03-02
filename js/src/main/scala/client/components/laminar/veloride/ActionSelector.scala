package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Action selector - list of available actions within a skill */
object ActionSelector:

  def apply(skill: Skill, onStartAction: String => Unit): HtmlElement =
    val actionsSignal = VelorIdleState.availableActionsSignal
    val skillStateSignal = VelorIdleState.skillStateSignal(skill)
    val activeActionSignal = VelorIdleState.activeActionSignal
    
    div(
      cls := "velor-action-selector",
      div(cls := "velor-action-selector-title", "Actions"),
      div(
        cls := "velor-action-list",
        children <-- actionsSignal.combineWith(skillStateSignal, activeActionSignal).map {
          case (actions, state, active) =>
            actions.map(action => actionItem(action, state.level, active, onStartAction))
        }
      )
    )

  private def actionItem(
    action: GatheringAction,
    playerLevel: Int,
    activeAction: ActiveAction,
    onStart: String => Unit
  ): HtmlElement =
    val isLocked = playerLevel < action.levelRequired
    val isActive = activeAction match
      case ActiveAction.Gathering(a) => a.id == action.id
      case _ => false
    
    val itemCls = 
      if isLocked then "velor-action-item locked"
      else if isActive then "velor-action-item active"
      else "velor-action-item"
    
    div(
      cls := itemCls,
      onClick --> { _ => if !isLocked then onStart(action.id) },
      
      div(
        cls := "velor-action-item-left",
        div(cls := "velor-action-item-icon", action.icon),
        div(
          div(cls := "velor-action-item-name", action.name),
          div(
            cls := "velor-action-item-level",
            if isLocked then s"🔒 Level ${action.levelRequired}"
            else s"Level ${action.levelRequired}"
          )
        )
      ),
      div(
        cls := "velor-action-item-right",
        div(cls := "velor-action-item-xp", s"+${action.xpGain} XP"),
        div(s"${action.timeSeconds}s")
      )
    )


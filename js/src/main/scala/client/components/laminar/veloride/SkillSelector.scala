package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Skill selector grid - displays all skills with levels */
object SkillSelector:

  def apply(onSelectSkill: Skill => Unit): HtmlElement =
    div(
      cls := "velor-skill-grid",
      Skill.values.toSeq.map(skill => skillTile(skill, onSelectSkill))
    )

  private def skillTile(skill: Skill, onSelect: Skill => Unit): HtmlElement =
    val levelSignal = VelorIdleState.skillStateSignal(skill).map(_.level)
    
    // Check if this skill has an active action
    val isActiveSignal = VelorIdleState.activeActionSignal.map:
      case ActiveAction.Gathering(s, _) => s == skill
      case ActiveAction.Processing(s, _) => s == skill
      case ActiveAction.Thieving(_) => skill == Skill.Thieving
      case ActiveAction.Adventure => skill == Skill.Adventure
      case ActiveAction.Rest => skill == Skill.Adventure
      case ActiveAction.Idle => false
    
    val tileClsSignal = isActiveSignal.map:
      case true => "velor-skill-tile active"
      case false => "velor-skill-tile"
    
    div(
      cls <-- tileClsSignal,
      dataAttr("skill") := skill.toString.toLowerCase,
      onClick --> { _ => onSelect(skill) },
      
      div(cls := "velor-skill-tile-icon", Skill.icon(skill)),
      div(cls := "velor-skill-tile-name", Skill.displayName(skill)),
      div(cls := "velor-skill-tile-level", child.text <-- levelSignal.map(l => s"Lv.$l"))
    )


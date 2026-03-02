package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Skill selector grid - displays all skills with levels */
object SkillSelector:

  def apply(): HtmlElement =
    div(
      cls := "velor-skill-grid",
      Skill.values.toSeq.map(skill => skillTile(skill))
    )

  private def skillTile(skill: Skill): HtmlElement =
    val levelSignal = VelorIdleState.skillStateSignal(skill).map(_.level)
    
    div(
      cls := "velor-skill-tile",
      dataAttr("skill") := skill.toString.toLowerCase,
      onClick --> { _ => VelorIdleState.enterSkill(skill) },
      
      div(cls := "velor-skill-tile-icon", Skill.icon(skill)),
      div(cls := "velor-skill-tile-name", Skill.displayName(skill)),
      div(cls := "velor-skill-tile-level", child.text <-- levelSignal.map(l => s"Lv.$l"))
    )


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
    val isActive = VelorIdleState.currentSkillSignal.map(_.contains(skill))
    val levelSignal = VelorIdleState.skillStateSignal(skill).map(_.level)

    div(
      cls <-- isActive.map(active => if active then "velor-skill-tile active" else "velor-skill-tile"),
      dataAttr("skill") := skill.toString.toLowerCase,
      onClick --> { _ => onSelect(skill) },

      div(cls := "velor-skill-tile-icon", Skill.icon(skill)),
      div(cls := "velor-skill-tile-name", Skill.displayName(skill)),
      div(cls := "velor-skill-tile-level", child.text <-- levelSignal.map(l => s"Lv.$l"))
    )


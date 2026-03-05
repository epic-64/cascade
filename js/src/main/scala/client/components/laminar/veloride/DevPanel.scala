package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Dev panel for testing - allows modifying skill levels */
object DevPanel:

  def apply(): HtmlElement =
    div(
      cls := "velor-dev-panel",
      div(cls := "velor-dev-panel-title", "🛠️ Dev Panel"),
      // Dev actions
      div(
        cls := "velor-dev-actions",
        button(
          cls := "btn btn-secondary",
          "🔄 Reset Combat",
          onClick --> { _ => resetCombat() }
        )
      ),
      div(
        cls := "velor-dev-skill-list",
        Skill.values.toSeq.map(skillRow)
      )
    )

  private def resetCombat(): Unit =
    VelorIdleState.modify { game =>
      val clearedAdvState = game.adventureState.copy(
        inCombat = false,
        combatState = None,
        currentHp = game.adventureState.maxHp,
        currentMana = game.adventureState.maxMana,
        restManaRegenAccumulator = 0.0
      )
      game.copy(
        adventureState = clearedAdvState,
        activeAction = ActiveAction.Idle
      )
    }
    ToastSystem.show("Combat state reset!")

  private def skillRow(skill: Skill): HtmlElement =
    val levelSignal = VelorIdleState.skillStateSignal(skill).map(_.level)

    div(
      cls := "velor-dev-skill-row",
      div(
        cls := "velor-dev-skill-info",
        span(Skill.icon(skill)),
        span(Skill.displayName(skill)),
        span(cls := "velor-dev-skill-level", child.text <-- levelSignal.map(l => s"Lv.$l"))
      ),
      div(
        cls := "velor-dev-skill-buttons",
        button(
          cls := "velor-dev-btn",
          "-10",
          onClick --> { _ => adjustLevel(skill, -10) }
        ),
        button(
          cls := "velor-dev-btn",
          "+10",
          onClick --> { _ => adjustLevel(skill, 10) }
        )
      )
    )

  private def adjustLevel(skill: Skill, delta: Int): Unit =
    VelorIdleState.modify { game =>
      val currentState = game.skills.getOrElse(skill, SkillState.initial)
      val oldLevel = currentState.level
      val newLevel = (oldLevel + delta).max(1).min(99)
      
      // Calculate XP needed for new level
      val newXp = SkillState.totalXpForLevel(newLevel)
      val newState = currentState.copy(level = newLevel, xp = newXp)
      
      val updatedGame = game.copy(skills = game.skills.updated(skill, newState))
      
      // Award skill points if Adventure level increased
      if skill == Skill.Adventure && newLevel > oldLevel then
        val (finalGame, _) = VelorIdleLogic.checkAndAwardAdventureSkillPoints(updatedGame, oldLevel)
        finalGame
      else
        updatedGame
    }


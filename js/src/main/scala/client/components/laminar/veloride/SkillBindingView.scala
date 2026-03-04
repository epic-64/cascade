package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** View for binding unlocked skills to combat slots */
object SkillBindingView:

  def apply(
    onBindSkill: (String, Int) => Unit,
    onUnbindSkill: Int => Unit,
    onBack: () => Unit
  ): HtmlElement =
    val combatSkillStateSignal = VelorIdleState.gameSignal.map(_.adventureState.combatSkillState)

    div(
      cls := "velor-skill-binding-view",

      // Header
      div(
        cls := "velor-skill-binding-header",
        button(
          cls := "btn btn-secondary",
          "← Skill Trees",
          onClick --> { _ => VelorIdleState.setViewMode(VelorIdleState.ViewMode.SkillTrees) }
        ),
        h2("Bind Skills to Combat Slots"),
        button(
          cls := "btn btn-primary",
          "⚔️ Go to Combat",
          onClick --> { _ => VelorIdleState.setViewMode(VelorIdleState.ViewMode.Adventure) }
        )
      ),

      // Current skill slots
      div(
        cls := "velor-skill-binding-slots",
        h3("Combat Skill Slots"),
        div(
          cls := "velor-binding-slots-row",
          (0 until 4).map { slot =>
            boundSlotCard(slot, combatSkillStateSignal, onUnbindSkill)
          }
        )
      ),

      // Available skills to bind
      div(
        cls := "velor-skill-binding-available",
        h3("Available Skills"),
        p(cls := "velor-skill-binding-hint", "Click a skill to bind it to a slot. Skills must have at least 1 point to be bindable."),
        div(
          cls := "velor-binding-skills-grid",
          child <-- combatSkillStateSignal.map { state =>
            val unlockedSkills = SkillTreeLogic.getUnlockedSkills(state)
            if unlockedSkills.isEmpty then
              div(
                cls := "velor-no-skills-message",
                "No skills unlocked yet. Allocate points in skill trees to unlock skills."
              )
            else
              div(
                cls := "velor-binding-skills-list",
                unlockedSkills.groupBy { case (skill, _) =>
                  SkillTrees.getTreeForSkill(skill.id).map(_.name).getOrElse("Unknown")
                }.toVector.sortBy(_._1).map { case (treeName, skills) =>
                  treeSkillsSection(treeName, skills, state, onBindSkill)
                }
              )
          }
        )
      )
    )

  private def boundSlotCard(
    slot: Int,
    combatSkillStateSignal: Signal[CombatSkillState],
    onUnbindSkill: Int => Unit
  ): HtmlElement =
    val boundSkillSignal = combatSkillStateSignal.map { state =>
      state.getBoundSkill(slot).flatMap(SkillTrees.getSkillById)
    }.distinct
    val levelSignal = combatSkillStateSignal.map { state =>
      state.getBoundSkill(slot).map(state.getSkillLevel).getOrElse(0)
    }.distinct

    div(
      cls := "velor-binding-slot-card",
      cls <-- boundSkillSignal.map(_.map(_ => "has-skill").getOrElse("empty")),

      div(cls := "velor-binding-slot-number", s"Slot ${slot + 1}"),
      div(cls := "velor-binding-slot-key", s"Press: ${slot + 1}"),

      child <-- boundSkillSignal.combineWith(levelSignal).map {
        case (Some(skill), level) =>
          div(
            cls := "velor-binding-slot-skill",
            div(cls := "velor-binding-skill-icon", skill.icon),
            div(cls := "velor-binding-skill-name", skill.name),
            div(cls := "velor-binding-skill-level", s"Lv. $level"),
            div(
              cls := "velor-binding-skill-stats",
              span(s"💥 ${skill.damageAtLevel(level)}"),
              span(s"💧 ${skill.manaCost}")
            ),
            if skill.chainSkills.nonEmpty then
              div(cls := "velor-binding-chain-info", s"🔗 ${skill.chainSkills.head.skill.name}")
            else emptyNode,
            button(
              cls := "btn btn-secondary btn-sm velor-unbind-btn",
              "✖",
              title := "Unbind skill",
              onClick.stopPropagation --> { _ => onUnbindSkill(slot) }
            )
          )
        case (None, _) =>
          div(
            cls := "velor-binding-slot-empty",
            div(cls := "velor-binding-empty-icon", "➖"),
            div(cls := "velor-binding-empty-text", "Empty Slot"),
            div(cls := "velor-binding-empty-hint", "Click a skill below to bind")
          )
      }
    )

  private def treeSkillsSection(
    treeName: String,
    skills: Vector[(TreeSkill, Int)],
    state: CombatSkillState,
    onBindSkill: (String, Int) => Unit
  ): HtmlElement =
    val treeIcon = SkillTrees.all.find(_.name == treeName).map(_.icon).getOrElse("❓")

    div(
      cls := "velor-binding-tree-section",
      div(cls := "velor-binding-tree-header", s"$treeIcon $treeName"),
      div(
        cls := "velor-binding-tree-skills",
        skills.map { case (skill, level) =>
          bindableSkillCard(skill, level, state, onBindSkill)
        }
      )
    )

  private def bindableSkillCard(
    skill: TreeSkill,
    level: Int,
    state: CombatSkillState,
    onBindSkill: (String, Int) => Unit
  ): HtmlElement =
    // Find which slot (if any) this skill is bound to
    val boundSlot = state.boundSkills.zipWithIndex.find(_._1.contains(skill.id)).map(_._2)

    div(
      cls := "velor-bindable-skill-card",
      cls := (if boundSlot.isDefined then "already-bound" else ""),

      div(
        cls := "velor-bindable-skill-header",
        span(cls := "velor-bindable-skill-icon", skill.icon),
        span(cls := "velor-bindable-skill-name", skill.name),
        span(cls := "velor-bindable-skill-level", s"Lv. $level")
      ),

      div(
        cls := "velor-bindable-skill-stats",
        span(s"💥 ${skill.damageAtLevel(level)}"),
        span(s"💧 ${skill.manaCost}"),
        span(s"⏱️ ${skill.cooldownMs / 1000.0}s")
      ),

      if skill.chainSkills.nonEmpty then
        div(
          cls := "velor-bindable-chain-info",
          s"🔗 Chains: ${skill.chainSkills.map(_.skill.name).mkString(" → ")}"
        )
      else emptyNode,

      // Bind buttons for each slot
      div(
        cls := "velor-bind-buttons",
        boundSlot match
          case Some(slot) =>
            span(cls := "velor-bound-indicator", s"Bound to Slot ${slot + 1}")
          case None =>
            (0 until 4).map { slot =>
              val slotOccupied = state.boundSkills(slot).isDefined
              button(
                cls := "btn btn-primary btn-sm",
                cls := (if slotOccupied then "slot-occupied" else ""),
                s"${slot + 1}",
                title := s"Bind to Slot ${slot + 1}",
                onClick --> { _ => onBindSkill(skill.id, slot) }
              )
            }
      )
    )


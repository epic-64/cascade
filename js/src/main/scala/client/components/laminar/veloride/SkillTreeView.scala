package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** View for selecting skill trees and allocating skill points */
object SkillTreeView:

  /** Selected tree for viewing */
  private val selectedTreeVar: Var[Option[String]] = Var(None)
  val selectedTreeSignal: Signal[Option[String]] = selectedTreeVar.signal

  def apply(
    onAllocatePoint: String => Unit,
    onBindSkill: (String, Int) => Unit,
    onBack: () => Unit
  ): HtmlElement =
    val combatSkillStateSignal = VelorIdleState.gameSignal.map(_.adventureState.combatSkillState)

    div(
      cls := "velor-view velor-skill-tree-view",

      // Header with available points and current bindings
      div(
        cls := "velor-skill-tree-header",
        button(
          cls := "btn btn-secondary",
          "← Back",
          onClick --> { _ => onBack() }
        ),
        div(
          cls := "velor-skill-points-display",
          child.text <-- combatSkillStateSignal.map { state =>
            s"🔷 Skill Points: ${state.availablePoints}"
          }.distinct
        ),
        // Show current skill bindings summary
        div(
          cls := "velor-current-bindings",
          children <-- combatSkillStateSignal.map { state =>
            (0 until 4).map { slot =>
              val boundSkill = state.getBoundSkill(slot).flatMap(SkillTrees.getSkillById)
              div(
                cls := "velor-binding-preview",
                cls := (if boundSkill.isDefined then "has-skill" else "empty"),
                span(cls := "velor-binding-preview-key", s"${slot + 1}"),
                span(cls := "velor-binding-preview-icon", boundSkill.map(_.icon).getOrElse("➖"))
              )
            }.toVector
          }.distinct
        )
      ),

      // Main content - tree selector or tree detail
      child <-- selectedTreeSignal.map:
        case None => treeSelector()
        case Some(treeId) =>
          SkillTrees.getById(treeId) match
            case Some(tree) => treeDetail(tree, onAllocatePoint, onBindSkill)
            case None => div("Unknown skill tree")
    )

  private def treeSelector(): HtmlElement =
    div(
      cls := "velor-skill-tree-selector",
      h2("Choose a Skill Tree"),
      div(
        cls := "velor-skill-tree-grid",
        SkillTrees.all.map(treeCard)
      )
    )

  private def treeCard(tree: SkillTree): HtmlElement =
    val combatSkillStateSignal = VelorIdleState.gameSignal.map(_.adventureState.combatSkillState)

    div(
      cls := "velor-skill-tree-card",
      onClick --> { _ => selectedTreeVar.set(Some(tree.id)) },

      div(cls := "velor-skill-tree-icon", tree.icon),
      div(cls := "velor-skill-tree-name", tree.name),
      div(cls := "velor-skill-tree-desc", tree.description),

      // Show points spent in this tree
      div(
        cls := "velor-skill-tree-points",
        child.text <-- combatSkillStateSignal.map { state =>
          val pointsInTree = tree.skills.map(s => state.getSkillLevel(s.id)).sum
          s"Points: $pointsInTree"
        }.distinct
      )
    )

  private def treeDetail(tree: SkillTree, onAllocatePoint: String => Unit, onBindSkill: (String, Int) => Unit): HtmlElement =
    val combatSkillStateSignal = VelorIdleState.gameSignal.map(_.adventureState.combatSkillState)

    div(
      cls := "velor-skill-tree-detail",

      // Tree header
      div(
        cls := "velor-skill-tree-detail-header",
        button(
          cls := "btn btn-secondary",
          "← Trees",
          onClick --> { _ => selectedTreeVar.set(None) }
        ),
        span(cls := "velor-skill-tree-detail-icon", tree.icon),
        span(cls := "velor-skill-tree-detail-name", tree.name)
      ),

      // Skills grid
      div(
        cls := "velor-skill-tree-skills",
        tree.skills.map(skill => skillNode(skill, combatSkillStateSignal, onAllocatePoint, onBindSkill))
      )
    )

  private def skillNode(
    skill: TreeSkill,
    combatSkillStateSignal: Signal[CombatSkillState],
    onAllocatePoint: String => Unit,
    onBindSkill: (String, Int) => Unit
  ): HtmlElement =
    val levelSignal = combatSkillStateSignal.map(_.getSkillLevel(skill.id)).distinct
    val canAllocateSignal = combatSkillStateSignal.map { state =>
      state.availablePoints > 0 && state.getSkillLevel(skill.id) < skill.maxLevel
    }.distinct
    val isUnlockedSignal = levelSignal.map(_ > 0)
    
    // Which slot (if any) this skill is bound to
    val boundSlotSignal = combatSkillStateSignal.map { state =>
      state.boundSkills.zipWithIndex.find(_._1.contains(skill.id)).map(_._2)
    }.distinct

    div(
      cls := "velor-skill-node",
      cls <-- levelSignal.map(level => if level > 0 then "unlocked" else "locked"),

      // Skill icon and name
      div(
        cls := "velor-skill-node-header",
        span(cls := "velor-skill-node-icon", skill.icon),
        span(cls := "velor-skill-node-name", skill.name)
      ),

      // Level display
      div(
        cls := "velor-skill-node-level",
        child.text <-- levelSignal.map(level => s"Level: $level / ${skill.maxLevel}")
      ),

      // Description
      div(cls := "velor-skill-node-desc", skill.description),

      // Stats at current level
      div(
        cls := "velor-skill-node-stats",
        child <-- levelSignal.map { level =>
          val damage = skill.damageAtLevel(level.max(1))
          val nextDamage = if level < skill.maxLevel then skill.damageAtLevel(level + 1) else damage
          div(
            span(s"💥 $damage"),
            span(s"💧 ${skill.manaCost}"),
            span(s"⏱️ ${skill.cooldownMs / 1000.0}s"),
            if level > 0 && level < skill.maxLevel then
              span(cls := "velor-skill-next-level", s"→ 💥 $nextDamage")
            else
              emptyNode
          )
        }
      ),

      // Chain skills indicator
      if skill.chainSkills.nonEmpty then
        div(
          cls := "velor-skill-chain-indicator",
          s"🔗 Chains into: ${skill.chainSkills.map(_.skill.name).mkString(", ")}"
        )
      else emptyNode,

      // Action buttons row
      div(
        cls := "velor-skill-node-actions",
        
        // Allocate button
        button(
          cls := "btn btn-primary velor-skill-allocate-btn",
          "➕",
          title := "Allocate Point",
          disabled <-- canAllocateSignal.map(!_),
          onClick --> { _ => onAllocatePoint(skill.id) }
        ),
        
        // Bind buttons (1, 2, 3, 4) - only shown when skill is unlocked
        div(
          cls := "velor-skill-bind-buttons",
          display <-- isUnlockedSignal.map(if _ then "flex" else "none"),
          
          (0 until 4).map { slot =>
            button(
              cls := "btn velor-bind-slot-btn",
              cls <-- boundSlotSignal.map(bs => if bs.contains(slot) then "btn-accent active" else "btn-secondary"),
              s"${slot + 1}",
              title := s"Bind to slot ${slot + 1}",
              onClick.stopPropagation --> { _ => onBindSkill(skill.id, slot) }
            )
          }
        )
      ),
      
      // Show current binding status
      child <-- boundSlotSignal.map:
        case Some(slot) => 
          div(cls := "velor-skill-bound-indicator", s"✓ Bound to slot ${slot + 1}")
        case None => 
          emptyNode
    )

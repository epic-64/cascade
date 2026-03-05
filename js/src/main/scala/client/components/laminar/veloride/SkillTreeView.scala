package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** View for selecting skill trees and allocating skill points */
object SkillTreeView:

  /** Selected tree for viewing */
  private val selectedTreeVar: Var[Option[String]] = Var(None)
  val selectedTreeSignal: Signal[Option[String]] = selectedTreeVar.signal

  /** Get effective skill level including equipment bonuses */
  private def getEffectiveLevel(state: CombatSkillState, equipment: EquipmentSlots, skillId: String): Int =
    val baseLevel = state.getSkillLevel(skillId)
    // Find which tree this skill belongs to
    val treeId = SkillTrees.all.find(_.skills.exists(_.id == skillId)).map(_.id)
    val equipBonus = treeId.map(equipment.allSkillBonuses.getOrElse(_, 0)).getOrElse(0)
    baseLevel + equipBonus

  /** Get equipment bonus for a skill tree */
  private def getEquipmentBonus(equipment: EquipmentSlots, treeId: String): Int =
    equipment.allSkillBonuses.getOrElse(treeId, 0)

  def apply(
    onAllocatePoint: String => Unit,
    onDeallocatePoint: String => Unit,
    onBindSkill: (String, Int) => Unit,
    onBack: () => Unit
  ): HtmlElement =
    val combatSkillStateSignal = VelorIdleState.gameSignal.map(_.adventureState.combatSkillState)

    div(
      cls := "velor-view velor-view-fill velor-skill-tree-view",

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
            case Some(tree) => treeDetail(tree, onAllocatePoint, onDeallocatePoint, onBindSkill)
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

      // Show points spent in this tree
      div(
        cls := "velor-skill-tree-points",
        child.text <-- combatSkillStateSignal.map { state =>
          val pointsInTree = tree.skills.map(s => state.getSkillLevel(s.id)).sum
          s"Points: $pointsInTree"
        }.distinct
      )
    )

  private def treeDetail(tree: SkillTree, onAllocatePoint: String => Unit, onDeallocatePoint: String => Unit, onBindSkill: (String, Int) => Unit): HtmlElement =
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
        tree.skills.map(skill => skillNode(skill, combatSkillStateSignal, onAllocatePoint, onDeallocatePoint, onBindSkill))
      )
    )

  /** Recursively render a chain skill and its nested chain skills */
  private def renderChainSkill(chain: TreeChainSkill, levelSignal: Signal[Int]): Vector[HtmlElement] =
    val isUnlockedSignal = levelSignal.map(_ >= chain.requiredLevel).distinct
    val thisSkill = div(
      cls := "velor-chain-skill-item",
      cls <-- isUnlockedSignal.map(if _ then "unlocked" else "locked"),
      div(
        cls := "velor-chain-skill-header",
        span(cls := "velor-chain-skill-icon", chain.skill.icon),
        span(chain.skill.name),
        span(
          cls := "velor-chain-skill-req",
          child.text <-- isUnlockedSignal.map(unlocked =>
            if unlocked then s"✓ Lv${chain.requiredLevel}+ | ${chain.windowMs / 1000.0}s window"
            else s"🔒 Requires Lv${chain.requiredLevel}"
          )
        )
      ),
      div(cls := "velor-chain-skill-desc", chain.skill.description),
      div(
        cls := "velor-chain-skill-stats",
        child <-- levelSignal.map { level =>
          val damage = chain.skill.damageAtLevel(level.max(1))
          div(
            span(s"💥 $damage"),
            span(s"💧 ${chain.skill.manaCost}"),
            if chain.skill.castTimeMs > 0 then span(s"🎯 ${chain.skill.castTimeMs / 1000.0}s") else emptyNode,
            span(s"⏱️ ${chain.skill.cooldownMs / 1000.0}s")
          )
        }
      )
    )
    // Recursively add nested chain skills
    val nested = chain.skill.chainSkills.flatMap(c => renderChainSkill(c, levelSignal))
    thisSkill +: nested

  private def skillNode(
    skill: TreeSkill,
    combatSkillStateSignal: Signal[CombatSkillState],
    onAllocatePoint: String => Unit,
    onDeallocatePoint: String => Unit,
    onBindSkill: (String, Int) => Unit
  ): HtmlElement =
    val gameSignal = VelorIdleState.gameSignal
    val levelSignal = combatSkillStateSignal.map(_.getSkillLevel(skill.id)).distinct
    val equipmentSignal = gameSignal.map(_.adventureState.equipment)
    
    // Find which tree this skill belongs to for equipment bonuses
    val treeId = SkillTrees.all.find(_.skills.exists(_.id == skill.id)).map(_.id).getOrElse("")
    
    // Effective level = base level + equipment bonus
    val effectiveLevelSignal = levelSignal.combineWith(equipmentSignal).map { case (base, equip) =>
      val bonus = equip.allSkillBonuses.getOrElse(treeId, 0)
      (base, base + bonus, bonus)  // (baseLevel, effectiveLevel, bonus)
    }.distinct
    
    val canAllocateSignal = combatSkillStateSignal.map { state =>
      state.availablePoints > 0 && state.getSkillLevel(skill.id) < skill.maxLevel
    }.distinct
    val canDeallocateSignal = levelSignal.map(_ > 0)
    val isUnlockedSignal = levelSignal.map(_ > 0)
    
    // Which slot (if any) this skill is bound to
    val boundSlotSignal = combatSkillStateSignal.map { state =>
      state.boundSkills.zipWithIndex.find(_._1.contains(skill.id)).map(_._2)
    }.distinct

    div(
      cls := "velor-skill-node",
      cls <-- levelSignal.map(level => if level > 0 then "unlocked" else "locked"),

      // Skill icon, name, level, and allocate/deallocate buttons
      div(
        cls := "velor-skill-node-header",
        span(cls := "velor-skill-node-icon", skill.icon),
        span(cls := "velor-skill-node-name", skill.name),
        span(
          cls := "velor-skill-node-level",
          child.text <-- effectiveLevelSignal.map { case (base, effective, bonus) =>
            if bonus > 0 then
              s"$effective ($base+$bonus) / ${skill.maxLevel}"
            else
              s"$base / ${skill.maxLevel}"
          }
        ),
        button(
          cls := "btn btn-secondary velor-skill-deallocate-btn",
          "➖",
          title := s"Refund Point (${SkillTreeLogic.RefundCostGold} gold)",
          disabled <-- canDeallocateSignal.map(!_),
          onClick --> { _ => onDeallocatePoint(skill.id) }
        ),
        button(
          cls := "btn btn-primary velor-skill-allocate-btn",
          "➕",
          title := "Allocate Point",
          disabled <-- canAllocateSignal.map(!_),
          onClick --> { _ => onAllocatePoint(skill.id) }
        )
      ),

      // Description
      div(cls := "velor-skill-node-desc", skill.description),

      // Stats at current level (use effective level for damage calculation)
      div(
        cls := "velor-skill-node-stats",
        child <-- effectiveLevelSignal.map { case (baseLevel, effective, bonus) =>
          val damage = skill.damageAtLevel(effective.max(1))
          val nextDamage = if baseLevel < skill.maxLevel then skill.damageAtLevel(effective + 1) else damage
          div(
            span(s"💥 $damage"),
            span(s"💧 ${skill.manaCost}"),
            if skill.castTimeMs > 0 then span(s"🎯 ${skill.castTimeMs / 1000.0}s") else emptyNode,
            span(s"⏱️ ${skill.cooldownMs / 1000.0}s"),
            if baseLevel > 0 && baseLevel < skill.maxLevel then
              span(cls := "velor-skill-next-level", s"→ 💥 $nextDamage")
            else
              emptyNode
          )
        }
      ),

      // Chain skills detailed section
      // Chain skills detailed section (recursive for 3-level chains)
      if skill.chainSkills.nonEmpty then
        val effectiveOnlySignal = effectiveLevelSignal.map(_._2) // Just the effective level
        div(
          cls := "velor-chain-skills-section",
          skill.chainSkills.flatMap(chain => renderChainSkill(chain, effectiveOnlySignal))
        )
      else emptyNode,

      // Bind buttons row (1, 2, 3, 4) - only shown when skill is unlocked
      div(
        cls := "velor-skill-node-actions",
        display <-- isUnlockedSignal.map(if _ then "flex" else "none"),

        (0 until 4).map { slot =>
          button(
            cls := "btn velor-bind-slot-btn",
            cls <-- boundSlotSignal.map(bs => if bs.contains(slot) then "btn-accent active" else "btn-secondary"),
            s"${slot + 1}",
            title := s"Bind to slot ${slot + 1}",
            onClick.stopPropagation --> { _ => onBindSkill(skill.id, slot) }
          )
        },

        // Show current binding status
        child <-- boundSlotSignal.map:
          case Some(slot) =>
            span(cls := "velor-skill-bound-indicator", s"✓ Slot ${slot + 1}")
          case None =>
            span(cls := "velor-skill-unbound-indicator", "Not bound")
      )
    )

package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*
import org.scalajs.dom

/** Adventure mode combat view */
object AdventureView:

  // Key bindings: 1,2,3,4 for weapon skills, Q,W,E,R for armor skills
  private val skillKeyBindings = Map(
    "1" -> 0, "2" -> 1, "3" -> 2, "4" -> 3,  // Weapon skills
    "q" -> 4, "w" -> 5, "e" -> 6, "r" -> 7   // Armor skills
  )

  // Store the key handler reference for cleanup
  private var currentKeyHandler: Option[scalajs.js.Function1[dom.KeyboardEvent, Unit]] = None

  def apply(
    onStartCombat: String => Unit,
    onUseSkill: Int => Unit,
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit
  ): HtmlElement =
    val adventureStateSignal = VelorIdleState.gameSignal.map(_.adventureState)
    val inCombatSignal = adventureStateSignal.map(_.inCombat)
    
    div(
      cls := "velor-adventure-view",
      
      // Set up keyboard listener for skills
      onMountCallback { _ =>
        val keyHandler: scalajs.js.Function1[dom.KeyboardEvent, Unit] = { (e: dom.KeyboardEvent) =>
          val key = e.key.toLowerCase
          skillKeyBindings.get(key).foreach { slotIndex =>
            e.preventDefault()
            onUseSkill(slotIndex)
          }
        }
        currentKeyHandler = Some(keyHandler)
        dom.document.addEventListener("keydown", keyHandler)
      },
      
      onUnmountCallback { _ =>
        currentKeyHandler.foreach { handler =>
          dom.document.removeEventListener("keydown", handler)
        }
        currentKeyHandler = None
      },
      
      // Header with back button
      div(
        cls := "velor-adventure-header",
        button(
          cls := "velor-back-btn",
          "←",
          onClick --> { _ => 
            onStopCombat()
            VelorIdleState.setViewMode(VelorIdleState.ViewMode.SkillSelect) 
          }
        ),
        div(
          cls := "velor-adventure-title",
          span("⚔️"),
          span("Adventure")
        ),
        // Level display
        div(
          cls := "velor-adventure-level",
          child.text <-- VelorIdleState.skillStateSignal(Skill.Adventure).map(s => s"Lv.${s.level}")
        )
      ),
      
      // Main content - either enemy select or combat
      child <-- inCombatSignal.map {
        case false => enemySelectView(onStartCombat)
        case true => combatView(adventureStateSignal, onUseSkill, onStopCombat, onRestartCombat)
      }
    )

  private def enemySelectView(onStartCombat: String => Unit): HtmlElement =
    val adventureLevel = VelorIdleState.skillStateSignal(Skill.Adventure)
    
    div(
      cls := "velor-enemy-select",
      h3(cls := "velor-enemy-select-title", "Select an Enemy"),
      div(
        cls := "velor-enemy-list",
        Enemies.all.map { enemy =>
          div(
            cls := "velor-enemy-card",
            cls <-- adventureLevel.map(s => 
              if s.level >= enemy.levelRequired then "unlocked" else "locked"
            ),
            div(cls := "velor-enemy-icon", enemy.icon),
            div(
              cls := "velor-enemy-info",
              div(cls := "velor-enemy-name", enemy.name),
              div(cls := "velor-enemy-stats",
                span(s"❤️ ${enemy.maxHp}"),
                span(s"⚔️ ${enemy.attackDamage}"),
                span(s"💰 ${enemy.goldReward._1}-${enemy.goldReward._2}")
              ),
              div(cls := "velor-enemy-level-req",
                child.text <-- adventureLevel.map { s =>
                  if s.level >= enemy.levelRequired then s"XP: ${enemy.xpReward}"
                  else s"Requires Lv.${enemy.levelRequired}"
                }
              )
            ),
            onClick --> { _ =>
              val level = VelorIdleState.current.skills.getOrElse(Skill.Adventure, SkillState.initial).level
              if level >= enemy.levelRequired then
                onStartCombat(enemy.id)
            }
          )
        }
      )
    )

  private def combatView(
    adventureStateSignal: Signal[AdventureState],
    onUseSkill: Int => Unit,
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit
  ): HtmlElement =
    val combatSignal = adventureStateSignal.map(_.combatState)
    
    div(
      cls := "velor-combat-view",
      
      // Enemy section
      child <-- combatSignal.map {
        case None => div("No combat active")
        case Some(combat) => enemyDisplay(combat)
      },
      
      // Player section
      child <-- combatSignal.map {
        case None => emptyNode
        case Some(combat) => playerDisplay(combat)
      },
      
      // Skill bar
      child <-- combatSignal.map {
        case None => emptyNode
        case Some(combat) => skillBar(combat, onUseSkill)
      },
      
      // Combat end overlay
      child <-- combatSignal.map {
        case Some(combat) if combat.isCombatOver =>
          combatEndOverlay(combat, onStopCombat, onRestartCombat)
        case _ => emptyNode
      }
    )

  private def enemyDisplay(combat: CombatState): HtmlElement =
    val hpPercent = (combat.enemyCurrentHp.toDouble / combat.enemy.maxHp * 100).max(0)
    val isStunned = combat.enemyStun.isDefined
    
    div(
      cls := "velor-combat-entity velor-combat-enemy",
      cls := (if isStunned then "stunned" else ""),
      
      // Enemy icon and name
      div(
        cls := "velor-entity-header",
        div(cls := "velor-entity-icon", combat.enemy.icon),
        div(cls := "velor-entity-name", combat.enemy.name),
        if isStunned then span(cls := "velor-status-badge stunned", "💫 STUNNED") else emptyNode
      ),
      
      // HP bar
      div(
        cls := "velor-hp-bar-container",
        div(
          cls := "velor-hp-bar enemy",
          styleAttr := s"width: ${hpPercent}%"
        ),
        div(
          cls := "velor-hp-text",
          s"${combat.enemyCurrentHp} / ${combat.enemy.maxHp}"
        )
      ),
      
      // DoT indicators
      if combat.enemyDoTs.nonEmpty then
        div(
          cls := "velor-dot-indicators",
          combat.enemyDoTs.map { dot =>
            span(cls := "velor-dot-badge", s"🩸 ${dot.ticksRemaining}")
          }
        )
      else emptyNode
    )

  private def playerDisplay(combat: CombatState): HtmlElement =
    val hpPercent = (combat.playerCurrentHp.toDouble / combat.playerMaxHp * 100).max(0)
    val manaPercent = (combat.playerMana.toDouble / combat.playerMaxMana * 100).max(0)
    
    div(
      cls := "velor-combat-entity velor-combat-player",
      
      // Player header
      div(
        cls := "velor-entity-header",
        div(cls := "velor-entity-icon", "🧙"),
        div(cls := "velor-entity-name", "You"),
        // Shield indicator
        combat.playerShield.map { shield =>
          span(cls := "velor-status-badge shield", s"🛡️ ${shield.remainingAbsorb}")
        }.getOrElse(emptyNode),
        // Damage buff indicator
        combat.playerDamageBuff.map { buff =>
          span(cls := "velor-status-badge buff", s"⬆️ +${(buff.percent * 100).toInt}%")
        }.getOrElse(emptyNode)
      ),
      
      // HP bar
      div(
        cls := "velor-hp-bar-container",
        div(
          cls := "velor-hp-bar player",
          styleAttr := s"width: ${hpPercent}%"
        ),
        div(
          cls := "velor-hp-text",
          s"❤️ ${combat.playerCurrentHp} / ${combat.playerMaxHp}"
        )
      ),
      
      // Mana bar
      div(
        cls := "velor-mana-bar-container",
        div(
          cls := "velor-mana-bar",
          styleAttr := s"width: ${manaPercent}%"
        ),
        div(
          cls := "velor-mana-text",
          s"💧 ${combat.playerMana} / ${combat.playerMaxMana}"
        )
      )
    )

  private def skillBar(combat: CombatState, onUseSkill: Int => Unit): HtmlElement =
    val currentTime = System.currentTimeMillis()
    
    div(
      cls := "velor-skill-bar",
      
      // Weapon skills (slots 0-3)
      div(
        cls := "velor-skill-row weapon-skills",
        div(cls := "velor-skill-row-label", "Weapon"),
        div(
          cls := "velor-skill-slots",
          combat.skillSlots.take(4).zipWithIndex.map { case (slot, idx) =>
            skillSlotButton(slot, idx, s"${idx + 1}", combat.playerMana, currentTime, onUseSkill)
          }
        )
      ),
      
      // Armor skills (slots 4-7)
      div(
        cls := "velor-skill-row armor-skills",
        div(cls := "velor-skill-row-label", "Armor"),
        div(
          cls := "velor-skill-slots",
          combat.skillSlots.drop(4).zipWithIndex.map { case (slot, idx) =>
            val actualIdx = idx + 4
            val key = Vector("Q", "W", "E", "R")(idx)
            skillSlotButton(slot, actualIdx, key, combat.playerMana, currentTime, onUseSkill)
          }
        )
      )
    )

  private def skillSlotButton(
    slot: SkillSlotState,
    slotIndex: Int,
    keyLabel: String,
    playerMana: Int,
    currentTime: Long,
    onUseSkill: Int => Unit
  ): HtmlElement =
    val skill = slot.currentSkill
    val isOnCooldown = slot.isOnCooldown(currentTime)
    val cooldownRemaining = slot.cooldownRemainingMs(currentTime)
    val notEnoughMana = playerMana < skill.manaCost
    val isEmpty = skill.id == "empty"
    val isChainSkill = slot.isInChainWindow(currentTime) && skill.id != slot.baseSkill.id
    
    button(
      cls := "velor-skill-slot",
      cls := (if isEmpty then "empty" else ""),
      cls := (if isOnCooldown then "on-cooldown" else ""),
      cls := (if notEnoughMana && !isEmpty then "no-mana" else ""),
      cls := (if isChainSkill then "chain-skill" else ""),
      disabled := isEmpty || isOnCooldown || notEnoughMana,
      
      // Key binding label
      div(cls := "velor-skill-key", keyLabel),
      
      // Skill icon
      div(cls := "velor-skill-icon", skill.icon),
      
      // Skill name (truncated)
      div(cls := "velor-skill-name", skill.name.take(8)),
      
      // Mana cost
      if !isEmpty then
        div(cls := "velor-skill-mana", s"${skill.manaCost}💧")
      else emptyNode,
      
      // Cooldown overlay
      if isOnCooldown then
        div(
          cls := "velor-skill-cooldown-overlay",
          f"${cooldownRemaining / 1000.0}%.1fs"
        )
      else emptyNode,
      
      // Chain indicator
      if isChainSkill then
        div(cls := "velor-chain-indicator", "⚡")
      else emptyNode,
      
      onClick --> { _ =>
        if !isEmpty && !isOnCooldown && !notEnoughMana then
          onUseSkill(slotIndex)
      },
      
      // Tooltip on hover
      title := s"${skill.name}\n${skill.description}\nMana: ${skill.manaCost}\nCooldown: ${skill.cooldownMs / 1000.0}s"
    )

  private def combatEndOverlay(
    combat: CombatState,
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit
  ): HtmlElement =
    val isVictory = combat.isEnemyDead
    
    div(
      cls := "velor-combat-end-overlay",
      cls := (if isVictory then "victory" else "defeat"),
      
      div(
        cls := "velor-combat-end-content",
        div(
          cls := "velor-combat-end-icon",
          if isVictory then "🏆" else "💀"
        ),
        div(
          cls := "velor-combat-end-title",
          if isVictory then "Victory!" else "Defeated"
        ),
        div(
          cls := "velor-combat-end-message",
          if isVictory then s"You defeated the ${combat.enemy.name}!"
          else "You were defeated. Try again?"
        ),
        div(
          cls := "velor-combat-end-buttons",
          button(
            cls := "btn btn-primary",
            "Fight Again",
            onClick --> { _ => onRestartCombat() }
          ),
          button(
            cls := "btn btn-secondary",
            "Leave",
            onClick --> { _ => 
              onStopCombat()
              VelorIdleState.setViewMode(VelorIdleState.ViewMode.SkillSelect)
            }
          )
        )
      )
    )


package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*
import org.scalajs.dom
import scala.util.Random

/** Adventure mode combat view */
object AdventureView:

  // Key bindings: 1,2,3,4 for weapon skills, Q,W,E,R for armor skills
  private val skillKeyBindings = Map(
    "1" -> 0, "2" -> 1, "3" -> 2, "4" -> 3,  // Weapon skills
    "q" -> 4, "w" -> 5, "e" -> 6, "r" -> 7   // Armor skills
  )

  // Store the key handler reference for cleanup
  private var currentKeyHandler: Option[scalajs.js.Function1[dom.KeyboardEvent, Unit]] = None

  // Floating damage number state
  case class FloatingNumber(id: Int, text: String, isPlayer: Boolean, isHeal: Boolean, x: Int, y: Int):
    def isEvade: Boolean = text == "Evaded"
  private val random = new Random()
  
  // Projectile animation duration in ms
  private val ProjectileFlightTimeMs = 350
  
  // Effect that triggers when projectile lands
  enum ProjectileEffect:
    case Damage(amount: Int, targetIsPlayer: Boolean)
    case Evade(targetIsPlayer: Boolean)
    case SkillDamage(amount: Int)
  
  // Projectile state - carries effect to trigger on landing
  case class Projectile(
    id: Int, 
    icon: String, 
    startX: Double, 
    startY: Double, 
    endX: Double, 
    endY: Double,
    effect: ProjectileEffect
  )

  def apply(
    onStartCombat: String => Unit,
    onUseSkill: Int => Unit,
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit,
    onRest: () => Unit
  ): HtmlElement =
    val adventureStateSignal = VelorIdleState.gameSignal.map(_.adventureState)
    val inCombatSignal = adventureStateSignal.map(_.inCombat)

    // Per-instance state for floating damage numbers
    val floatingNumbersVar = Var(Vector.empty[FloatingNumber])
    var nextFloatingId = 0
    
    // Projectile state - scoped to this instance
    val projectilesVar = Var(Vector.empty[Projectile])
    var nextProjectileId = 0
    
    // Visual HP state - shows delayed HP that updates when projectiles land
    val visualEnemyHpVar = Var(0)
    val visualPlayerHpVar = Var(0)

    def showDamageNumber(damage: Int, isPlayer: Boolean, isHeal: Boolean = false): Unit =
      val text = if isHeal then s"+$damage" else s"-$damage"
      val x = random.nextInt(60) - 30
      val y = random.nextInt(20) - 10
      val num = FloatingNumber(nextFloatingId, text, isPlayer, isHeal, x, y)
      nextFloatingId += 1
      floatingNumbersVar.update(_ :+ num)
      dom.window.setTimeout(() => floatingNumbersVar.update(_.filterNot(_.id == num.id)), 1000)

    def showEvadedText(isPlayer: Boolean): Unit =
      val x = random.nextInt(60) - 30
      val y = random.nextInt(20) - 10
      val num = FloatingNumber(nextFloatingId, "Evaded", isPlayer, isHeal = false, x, y)
      nextFloatingId += 1
      floatingNumbersVar.update(_ :+ num)
      dom.window.setTimeout(() => floatingNumbersVar.update(_.filterNot(_.id == num.id)), 1000)

    def onProjectileLanded(effect: ProjectileEffect): Unit =
      effect match
        case ProjectileEffect.Damage(amount, targetIsPlayer) =>
          showDamageNumber(amount, targetIsPlayer, isHeal = false)
          // Update visual HP
          if targetIsPlayer then
            visualPlayerHpVar.update(hp => (hp - amount).max(0))
          else
            visualEnemyHpVar.update(hp => (hp - amount).max(0))
        case ProjectileEffect.Evade(targetIsPlayer) =>
          showEvadedText(targetIsPlayer)
        case ProjectileEffect.SkillDamage(amount) =>
          if amount > 0 then
            showDamageNumber(amount, isPlayer = false, isHeal = false)
            visualEnemyHpVar.update(hp => (hp - amount).max(0))

    def fireProjectileWithEffect(
      icon: String,
      sourceSelector: String,
      targetSelector: String,
      effect: ProjectileEffect
    ): Unit =
      val source = dom.document.querySelector(sourceSelector)
      val target = dom.document.querySelector(targetSelector)
      val combatView = dom.document.querySelector(".velor-combat-view")
      
      if source != null && target != null && combatView != null then
        val sourceRect = source.getBoundingClientRect()
        val targetRect = target.getBoundingClientRect()
        val combatRect = combatView.getBoundingClientRect()
        
        val startX = sourceRect.left + sourceRect.width / 2 - combatRect.left
        val startY = sourceRect.top + sourceRect.height / 2 - combatRect.top
        val endX = targetRect.left + targetRect.width / 2 - combatRect.left
        val endY = targetRect.top + targetRect.height / 2 - combatRect.top
        
        val projectile = Projectile(nextProjectileId, icon, startX, startY, endX, endY, effect)
        nextProjectileId += 1
        projectilesVar.update(_ :+ projectile)
        
        // When projectile lands, trigger effect and remove projectile
        dom.window.setTimeout(() => {
          projectilesVar.update(_.filterNot(_.id == projectile.id))
          onProjectileLanded(effect)
        }, ProjectileFlightTimeMs)

    def fireAutoAttackProjectile(isPlayer: Boolean, effect: ProjectileEffect): Unit =
      val sourceSelector = if isPlayer then ".velor-combat-player .velor-entity-icon" else ".velor-combat-enemy .velor-entity-icon"
      val targetSelector = if isPlayer then ".velor-combat-enemy .velor-hp-bar-container" else ".velor-combat-player .velor-hp-bar-container"
      val icon = if isPlayer then "⚔️" else "💥"
      fireProjectileWithEffect(icon, sourceSelector, targetSelector, effect)

    def fireSkillProjectile(icon: String, slotIndex: Int, effect: ProjectileEffect): Unit =
      val sourceSelector = s".velor-skill-slots .velor-skill-slot:nth-child(${slotIndex + 1})"
      val targetSelector = ".velor-combat-enemy .velor-hp-bar-container"
      fireProjectileWithEffect(icon, sourceSelector, targetSelector, effect)

    div(
      cls := "velor-adventure-view",

      // Reset state on mount
      onMountCallback { _ =>
        floatingNumbersVar.set(Vector.empty)
        nextFloatingId = 0
      },

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

      // Header with back button and XP bar
      div(
        cls := "velor-skill-header-card",
        div(
          cls := "velor-adventure-header",
          button(
            cls := "velor-back-btn",
            "←",
            onClick --> { _ =>
              // Just navigate back, don't stop combat - it continues in background
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
        // XP Progress bar
        adventureXpBar(VelorIdleState.skillStateSignal(Skill.Adventure))
      ),

      // Enemy select view (hidden when in combat)
      enemySelectView(onStartCombat, inCombatSignal, onRest),

      // Combat view (hidden when not in combat)
      combatView(
        adventureStateSignal, 
        inCombatSignal, 
        floatingNumbersVar,
        projectilesVar,
        visualEnemyHpVar,
        visualPlayerHpVar,
        fireAutoAttackProjectile,
        fireSkillProjectile,
        showDamageNumber,
        onUseSkill, 
        onStopCombat, 
        onRestartCombat, 
        onRest
      )
    )

  private def adventureXpBar(skillStateSignal: Signal[SkillState]): HtmlElement =
    div(
      cls := "velor-xp-bar-container",
      div(
        cls := "velor-xp-bar-label",
        span(child.text <-- skillStateSignal.map { s =>
          val currentLevelXp = SkillState.totalXpForLevel(s.level)
          val nextLevelXp = SkillState.totalXpForLevel(s.level + 1)
          val xpIntoLevel = s.xp - currentLevelXp
          val xpNeeded = nextLevelXp - currentLevelXp
          s"XP: $xpIntoLevel / $xpNeeded"
        }),
        span(child.text <-- skillStateSignal.map(s => s"Total: ${s.xp}"))
      ),
      div(
        cls := "velor-xp-bar",
        div(
          cls := "velor-xp-bar-fill",
          width <-- skillStateSignal.map { s =>
            val currentLevelXp = SkillState.totalXpForLevel(s.level)
            val nextLevelXp = SkillState.totalXpForLevel(s.level + 1)
            val progress = if nextLevelXp > currentLevelXp then
              ((s.xp - currentLevelXp).toDouble / (nextLevelXp - currentLevelXp) * 100).min(100)
            else 0.0
            s"$progress%"
          }
        )
      )
    )

  private def enemySelectView(onStartCombat: String => Unit, inCombatSignal: Signal[Boolean], onRest: () => Unit): HtmlElement =
    val adventureLevel = VelorIdleState.skillStateSignal(Skill.Adventure)
    val adventureStateSignal = VelorIdleState.gameSignal.map(_.adventureState)
    val isRestingSignal = VelorIdleState.activeActionSignal.map(_ == ActiveAction.Rest).distinct
    val needsRestSignal = adventureStateSignal.map(s => s.currentHp < s.maxHp || s.currentMana < s.maxMana).distinct

    div(
      cls := "velor-enemy-select",
      display <-- inCombatSignal.map(if _ then "none" else "flex"),

      // Player stats card (shows current HP/Mana before entering combat)
      playerStatsCard(adventureStateSignal, isRestingSignal, needsRestSignal, onRest),

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
                span(s"🎯 ${enemy.attackRating}"),
                span(s"🛡️ ${enemy.defenseRating}")
              ),
              // Resistances row (only show if enemy has any)
              if enemy.resistances.asSeq.nonEmpty then
                div(cls := "velor-enemy-resistances",
                  enemy.resistances.asSeq.map { case (icon, _, value) =>
                    span(s"$icon$value%")
                  }
                )
              else emptyNode,
              div(cls := "velor-enemy-level-req",
                child.text <-- adventureLevel.map { s =>
                  if s.level >= enemy.levelRequired then s"XP: ${enemy.xpReward} | 💰 ${enemy.goldReward._1}-${enemy.goldReward._2}"
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

  private def playerStatsCard(
    adventureStateSignal: Signal[AdventureState],
    isRestingSignal: Signal[Boolean],
    needsRestSignal: Signal[Boolean],
    onRest: () => Unit
  ): HtmlElement =
    div(
      cls := "velor-player-stats-card",
      div(
        cls := "velor-player-stats-header",
        span(cls := "velor-player-stats-icon", "🧙"),
        span(cls := "velor-player-stats-title", "Your Status")
      ),
      div(
        cls := "velor-player-stats-bars",
        // HP bar
        div(
          cls := "velor-player-stat-row",
          span(cls := "velor-player-stat-label", "HP"),
          div(
            cls := "velor-hp-bar-container",
            div(
              cls := "velor-hp-bar player",
              width <-- adventureStateSignal.map { state =>
                s"${(state.currentHp.toDouble / state.maxHp * 100).max(0)}%"
              }.distinct
            ),
            div(
              cls := "velor-hp-text",
              child.text <-- adventureStateSignal.map(s => s"${s.currentHp} / ${s.maxHp}").distinct
            )
          )
        ),
        // Mana bar
        div(
          cls := "velor-player-stat-row",
          span(cls := "velor-player-stat-label", "MP"),
          div(
            cls := "velor-mana-bar-container",
            div(
              cls := "velor-mana-bar",
              width <-- adventureStateSignal.map { state =>
                s"${(state.currentMana.toDouble / state.maxMana * 100).max(0)}%"
              }.distinct
            ),
            div(
              cls := "velor-mana-text",
              child.text <-- adventureStateSignal.map(s => s"${s.currentMana} / ${s.maxMana}").distinct
            )
          )
        )
      ),
      // Combat stats row
      div(
        cls := "velor-player-combat-stats",
        span(child.text <-- adventureStateSignal.map(s => s"⚔️ ${s.equippedWeapon.attackDamage}").distinct),
        span(child.text <-- adventureStateSignal.map(s => s"🎯 ${s.attackRating}").distinct),
        span(child.text <-- adventureStateSignal.map(s => s"🛡️ ${s.defenseRating}").distinct)
      ),
      // Resistances row (only show if player has any)
      child <-- adventureStateSignal.map { state =>
        val resistSeq = state.resistances.asSeq
        if resistSeq.nonEmpty then
          div(
            cls := "velor-player-resistances",
            resistSeq.map { case (icon, name, value) =>
              span(title := name, s"$icon$value%")
            }
          )
        else emptyNode
      },
      // Rest button or resting status
      div(
        cls := "velor-player-stats-regen",
        child <-- isRestingSignal.combineWith(needsRestSignal).map {
          case (true, _) => span("🛏️ Resting... regenerating HP and Mana")
          case (false, true) => button(
            cls := "btn btn-secondary",
            "🛏️ Rest",
            onClick --> { _ => onRest() }
          )
          case (false, false) => span("✨ Fully recovered!")
        }
      )
    )

  private def combatView(
    adventureStateSignal: Signal[AdventureState],
    inCombatSignal: Signal[Boolean],
    floatingNumbersVar: Var[Vector[FloatingNumber]],
    projectilesVar: Var[Vector[Projectile]],
    visualEnemyHpVar: Var[Int],
    visualPlayerHpVar: Var[Int],
    fireAutoAttackProjectile: (Boolean, ProjectileEffect) => Unit,
    fireSkillProjectile: (String, Int, ProjectileEffect) => Unit,
    showDamageNumber: (Int, Boolean, Boolean) => Unit,
    onUseSkill: Int => Unit,
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit,
    onRest: () => Unit
  ): HtmlElement =
    val combatSignal = adventureStateSignal.map(_.combatState)
    val combatEndedVar = Var(false)
    val isVictoryVar = Var(false)

    // Event tracking state - reset when combat instance changes
    var lastSeenInstanceId = -1L
    var lastSeenEventCount = 0

    div(
      cls := "velor-combat-view",
      display <-- inCombatSignal.map(if _ then "flex" else "none"),
      
      // Initialize visual HP when combat starts
      onMountBind { _ =>
        combatSignal --> {
          case Some(combat) if combat.instanceId != lastSeenInstanceId =>
            // New combat - sync visual HP to actual HP
            visualEnemyHpVar.set(combat.enemyCurrentHp)
            visualPlayerHpVar.set(combat.playerCurrentHp)
          case _ => ()
        }
      },
      
      // Projectiles layer
      div(
        cls := "velor-projectiles-container",
        children <-- projectilesVar.signal.map(_.map { proj =>
          div(
            cls := "velor-projectile",
            styleAttr := s"--start-x: ${proj.startX}px; --start-y: ${proj.startY}px; --end-x: ${proj.endX}px; --end-y: ${proj.endY}px;",
            proj.icon
          )
        })
      ),

      // Event processing
      onMountBind { _ =>
        combatSignal --> { 
          case Some(combat) if combat.isPlayerDead && !combatEndedVar.now() =>
            // Only show modal on player death - victory auto-restarts
            combatEndedVar.set(true)
            isVictoryVar.set(false)

          case Some(combat) if !combat.isCombatOver =>
            combatEndedVar.set(false)
            
            // New combat instance? Reset our tracking and sync visual HP
            if combat.instanceId != lastSeenInstanceId then
              lastSeenInstanceId = combat.instanceId
              lastSeenEventCount = combat.totalEventCount
              visualEnemyHpVar.set(combat.enemyCurrentHp)
              visualPlayerHpVar.set(combat.playerCurrentHp)
            // Same instance, new events? Process them
            else if combat.totalEventCount > lastSeenEventCount then
              val numNewEvents = combat.totalEventCount - lastSeenEventCount
              val newEvents = combat.recentEvents.takeRight(numNewEvents)
              lastSeenEventCount = combat.totalEventCount
              
              newEvents.foreach {
                case CombatEvent.PlayerAutoAttack(dmg) => 
                  // Fire projectile with damage effect - damage number shows when it lands
                  fireAutoAttackProjectile(true, ProjectileEffect.Damage(dmg, targetIsPlayer = false))
                case CombatEvent.EnemyAutoAttack(dmg) => 
                  fireAutoAttackProjectile(false, ProjectileEffect.Damage(dmg, targetIsPlayer = true))
                case CombatEvent.PlayerEvaded =>
                  // Enemy attacked but player evaded - show enemy's projectile, then "Evaded" on player
                  fireAutoAttackProjectile(false, ProjectileEffect.Evade(targetIsPlayer = true))
                case CombatEvent.EnemyEvaded =>
                  // Player attacked but enemy evaded - show player's projectile, then "Evaded" on enemy
                  fireAutoAttackProjectile(true, ProjectileEffect.Evade(targetIsPlayer = false))
                case CombatEvent.PlayerSkillUsed(skillName, dmg) => 
                  // Find the skill slot - check current, base, chain skills, and nested chains
                  val slotIndex = combat.skillSlots.indexWhere { slot =>
                    slot.currentSkill.name == skillName || 
                    slot.baseSkill.name == skillName ||
                    slot.baseSkill.chainInto.exists(_.skill.name == skillName) ||
                    slot.baseSkill.chainInto.flatMap(_.skill.chainInto).exists(_.skill.name == skillName)
                  }
                  if slotIndex >= 0 then
                    // Find the actual skill icon (could be base, chain, or nested chain)
                    val slot = combat.skillSlots(slotIndex)
                    val icon = if slot.baseSkill.name == skillName then 
                      slot.baseSkill.icon
                    else if slot.baseSkill.chainInto.exists(_.skill.name == skillName) then
                      slot.baseSkill.chainInto.get.skill.icon
                    else if slot.baseSkill.chainInto.flatMap(_.skill.chainInto).exists(_.skill.name == skillName) then
                      slot.baseSkill.chainInto.get.skill.chainInto.get.skill.icon
                    else 
                      slot.currentSkill.icon
                    fireSkillProjectile(icon, slotIndex, ProjectileEffect.SkillDamage(dmg))
                // These effects are instant (no projectile)
                case CombatEvent.PlayerHealed(amt) => showDamageNumber(amt, true, true)
                case CombatEvent.EnemyDotTick(dmg, _) => showDamageNumber(dmg, false, false)
                case CombatEvent.PlayerDotTick(dmg, _) => showDamageNumber(dmg, true, false)
                case _ => ()
              }
              
          case None =>
            lastSeenInstanceId = -1L
            lastSeenEventCount = 0

          case _ => ()
        }
      },

      // Enemy section - reactive (uses visual HP)
      enemyDisplayReactive(combatSignal, floatingNumbersVar, visualEnemyHpVar.signal, onStopCombat),

      // Player section - reactive (uses visual HP)
      playerDisplayReactive(combatSignal, floatingNumbersVar, visualPlayerHpVar.signal),

      // Skill bar - reactive
      skillBarReactive(combatSignal, onUseSkill),

      // Combat end overlay - only shown once combat ends
      combatEndOverlayReactive(combatEndedVar.signal, isVictoryVar.signal, combatSignal, onStopCombat, onRestartCombat, onRest)
    )

  private def enemyDisplayReactive(
    combatSignal: Signal[Option[CombatState]], 
    floatingNumbersVar: Var[Vector[FloatingNumber]], 
    visualHpSignal: Signal[Int],
    onStopCombat: () => Unit
  ): HtmlElement =
    div(
      cls := "velor-combat-entity velor-combat-enemy",
      cls <-- combatSignal.map {
        case Some(c) if c.enemyStun.isDefined => "stunned"
        case _ => ""
      },
      display <-- combatSignal.map(_.map(_ => "block").getOrElse("none")),
      position := "relative",

      // Floating damage numbers (enemy takes damage)
      div(
        cls := "velor-floating-damage-container",
        children <-- floatingNumbersVar.signal.map(_.filterNot(_.isPlayer).map { num =>
          val typeClass = if num.isEvade then "evade" else if num.isHeal then "heal" else "damage"
          div(
            cls := s"velor-floating-damage $typeClass",
            styleAttr := s"--float-x: ${num.x}px; --float-y: ${num.y}px;",
            num.text
          )
        })
      ),

      // Enemy icon and name
      div(
        cls := "velor-entity-header",
        div(cls := "velor-entity-icon", child.text <-- combatSignal.map(_.map(_.enemy.icon).getOrElse(""))),
        div(cls := "velor-entity-name", child.text <-- combatSignal.map(_.map(_.enemy.name).getOrElse(""))),
        // Fixed-size status area
        div(
          cls := "velor-entity-status-area",
          span(
            cls := "velor-status-badge stunned",
            "💫 STUNNED",
            visibility <-- combatSignal.map(c => if c.exists(_.enemyStun.isDefined) then "visible" else "hidden")
          )
        ),
        // Stop button
        button(
          cls := "velor-combat-stop-btn",
          "Stop",
          onClick --> { _ => onStopCombat() }
        )
      ),

      // HP bar - uses visual HP that updates when projectiles land
      div(
        cls := "velor-hp-bar-container",
        div(
          cls := "velor-hp-bar enemy",
          width <-- visualHpSignal.combineWith(combatSignal).map { case (visualHp, c) =>
            c.map(combat => s"${(visualHp.toDouble / combat.enemy.maxHp * 100).max(0)}%").getOrElse("0%")
          }
        ),
        div(
          cls := "velor-hp-text",
          child.text <-- visualHpSignal.combineWith(combatSignal).map { case (visualHp, c) =>
            c.map(combat => s"$visualHp / ${combat.enemy.maxHp}").getOrElse("")
          }
        )
      ),

      // Combat stats row
      div(
        cls := "velor-entity-combat-stats",
        span(child.text <-- combatSignal.map(_.map(c => s"⚔️ ${c.enemy.attackDamage}").getOrElse("")).distinct),
        span(child.text <-- combatSignal.map(_.map(c => s"🎯 ${c.enemy.attackRating}").getOrElse("")).distinct),
        span(child.text <-- combatSignal.map(_.map(c => s"🛡️ ${c.enemy.defenseRating}").getOrElse("")).distinct),
        span(child.text <-- combatSignal.combineWith(VelorIdleState.gameSignal).map { case (c, g) =>
          c.map { combat =>
            val evadeChance = AdventureCombat.calculateEvadeChance(g.adventureState.attackRating, combat.enemy.defenseRating) * 100
            f"🌀 ${evadeChance}%.0f%%"
          }.getOrElse("")
        }.distinct)
      ),

      // Resistances row
      div(
        cls := "velor-entity-resistances",
        children <-- combatSignal.map { c =>
          c.map(_.enemy.resistances.asSeq.map { case (icon, name, value) =>
            span(title := name, s"$icon$value%")
          }).getOrElse(Vector.empty)
        }.distinct
      ),

      // DoT indicators
      div(
        cls := "velor-dot-indicators",
        children <-- combatSignal.map { c =>
          c.map(_.enemyDoTs.map { dot =>
            span(cls := "velor-dot-badge", s"🩸 ${dot.ticksRemaining}")
          }).getOrElse(Vector.empty)
        }
      )
    )

  private def playerDisplayReactive(
    combatSignal: Signal[Option[CombatState]], 
    floatingNumbersVar: Var[Vector[FloatingNumber]],
    visualHpSignal: Signal[Int]
  ): HtmlElement =
    div(
      cls := "velor-combat-entity velor-combat-player",
      display <-- combatSignal.map(_.map(_ => "block").getOrElse("none")),
      position := "relative",

      // Floating damage numbers (player takes damage / heals)
      div(
        cls := "velor-floating-damage-container",
        children <-- floatingNumbersVar.signal.map(_.filter(_.isPlayer).map { num =>
          val typeClass = if num.isEvade then "evade" else if num.isHeal then "heal" else "damage"
          div(
            cls := s"velor-floating-damage $typeClass",
            styleAttr := s"--float-x: ${num.x}px; --float-y: ${num.y}px;",
            num.text
          )
        })
      ),

      // Player header
      div(
        cls := "velor-entity-header",
        div(cls := "velor-entity-icon", "🧙"),
        div(cls := "velor-entity-name", "You"),
        // Fixed-size status area for buffs/debuffs
        div(
          cls := "velor-entity-status-area",
          // Shield indicator
          span(
            cls := "velor-status-badge shield",
            child.text <-- combatSignal.map(_.flatMap(_.playerShield).map(s => s"🛡️ ${s.remainingAbsorb}").getOrElse("🛡️ 0")),
            visibility <-- combatSignal.map(c => if c.exists(_.playerShield.isDefined) then "visible" else "hidden")
          ),
          // Damage buff indicator
          span(
            cls := "velor-status-badge buff",
            child.text <-- combatSignal.map(_.flatMap(_.playerDamageBuff).map(b => s"⬆️ +${(b.percent * 100).toInt}%").getOrElse("⬆️ +0%")),
            visibility <-- combatSignal.map(c => if c.exists(_.playerDamageBuff.isDefined) then "visible" else "hidden")
          )
        )
      ),

      // HP bar - uses visual HP that updates when projectiles land
      div(
        cls := "velor-hp-bar-container",
        div(
          cls := "velor-hp-bar player",
          width <-- visualHpSignal.combineWith(combatSignal).map { case (visualHp, c) =>
            c.map(combat => s"${(visualHp.toDouble / combat.playerMaxHp * 100).max(0)}%").getOrElse("0%")
          }
        ),
        div(
          cls := "velor-hp-text",
          child.text <-- visualHpSignal.combineWith(combatSignal).map { case (visualHp, c) =>
            c.map(combat => s"❤️ $visualHp / ${combat.playerMaxHp}").getOrElse("")
          }
        )
      ),

      // Mana bar
      div(
        cls := "velor-mana-bar-container",
        div(
          cls := "velor-mana-bar",
          width <-- combatSignal.map { c =>
            c.map(combat => s"${(combat.playerMana.toDouble / combat.playerMaxMana * 100).max(0)}%").getOrElse("0%")
          }
        ),
        div(
          cls := "velor-mana-text",
          child.text <-- combatSignal.map(_.map(c => s"💧 ${c.playerMana} / ${c.playerMaxMana}").getOrElse(""))
        )
      ),

      // Combat stats row
      div(
        cls := "velor-entity-combat-stats",
        span(child.text <-- VelorIdleState.gameSignal.map(g => s"⚔️ ${g.adventureState.equippedWeapon.attackDamage}").distinct),
        span(child.text <-- VelorIdleState.gameSignal.map(g => s"🎯 ${g.adventureState.attackRating}").distinct),
        span(child.text <-- VelorIdleState.gameSignal.map(g => s"🛡️ ${g.adventureState.defenseRating}").distinct),
        span(child.text <-- combatSignal.combineWith(VelorIdleState.gameSignal).map { case (c, g) =>
          c.map { combat =>
            val evadeChance = AdventureCombat.calculateEvadeChance(combat.enemy.attackRating, g.adventureState.defenseRating) * 100
            f"🌀 ${evadeChance}%.0f%%"
          }.getOrElse("")
        }.distinct)
      )
    )

  private def skillBarReactive(combatSignal: Signal[Option[CombatState]], onUseSkill: Int => Unit): HtmlElement =
    div(
      cls := "velor-skill-bar",
      display <-- combatSignal.map(_.map(_ => "flex").getOrElse("none")),

      // Single row with 4 skill slots
      div(
        cls := "velor-skill-slots",
        (0 until 4).map { idx =>
          skillSlotReactive(combatSignal, idx, s"${idx + 1}", onUseSkill)
        }
      )
    )

  private def skillSlotReactive(
    combatSignal: Signal[Option[CombatState]],
    slotIndex: Int,
    keyLabel: String,
    onUseSkill: Int => Unit
  ): HtmlElement =
    val slotSignal = combatSignal.map(_.flatMap(c => c.skillSlots.lift(slotIndex)))
    val skillSignal = slotSignal.map(_.map(_.currentSkill))
    // Check if this slot is currently casting
    val isCastingThisSlot = combatSignal.map(_.exists(c => c.castingSkill.exists(_.slotIndex == slotIndex)))

    button(
      cls := "velor-skill-slot",
      cls <-- combatSignal.combineWith(slotSignal).map { case (combat, slot) =>
        val classes = scala.collection.mutable.ListBuffer[String]()
        slot match
          case None => classes += "empty"
          case Some(s) =>
            val now = System.currentTimeMillis()
            val isChainSkill = s.isInChainWindow(now) && s.currentSkill.id != s.baseSkill.id
            val isOnGcd = combat.exists(_.isOnGlobalCooldown(now))
            val isCasting = combat.exists(_.castingSkill.exists(_.slotIndex == slotIndex))
            if s.currentSkill.id == "empty" then classes += "empty"
            // Show on-cooldown if GCD active OR skill cooldown (but not for chain skills), unless casting
            if !isCasting && ((s.isOnCooldown(now) && !isChainSkill) || isOnGcd) then classes += "on-cooldown"
            if isChainSkill then classes += "chain-skill"
            if isCasting then classes += "casting"
        classes.mkString(" ")
      },
      disabled <-- combatSignal.map { c =>
        c.flatMap(_.skillSlots.lift(slotIndex)).forall { slot =>
          val now = System.currentTimeMillis()
          val isChainSkill = slot.isInChainWindow(now) && slot.currentSkill.id != slot.baseSkill.id
          val isEmpty = slot.currentSkill.id == "empty"
          val isOnCooldownAndNotChain = slot.isOnCooldown(now) && !isChainSkill
          val isOnGcd = c.exists(_.isOnGlobalCooldown(now))
          val isCasting = c.exists(_.isCasting)
          val notEnoughMana = c.exists(_.playerMana < slot.currentSkill.manaCost)
          isEmpty || isOnCooldownAndNotChain || isOnGcd || isCasting || notEnoughMana
        }
      },

      // Key binding label
      div(cls := "velor-skill-key", keyLabel),

      // Skill icon
      div(cls := "velor-skill-icon", child.text <-- skillSignal.map(_.map(_.icon).getOrElse("➖"))),

      // Skill name (truncated)
      div(cls := "velor-skill-name", child.text <-- skillSignal.map(_.map(_.name.take(8)).getOrElse(""))),

      // Mana cost
      div(
        cls := "velor-skill-mana",
        child.text <-- skillSignal.map(_.filter(_.id != "empty").map(s => s"${s.manaCost}💧").getOrElse("")),
        display <-- skillSignal.map(s => if s.exists(_.id != "empty") then "block" else "none")
      ),

      // Regular cooldown overlay (skill-specific cooldown)
      div(
        cls := "velor-skill-cooldown-overlay",
        child.text <-- combatSignal.combineWith(slotSignal).map { case (combat, slot) =>
          val now = System.currentTimeMillis()
          val gcdRemaining = combat.map(_.globalCooldownRemainingMs(now)).getOrElse(0L)
          val skillCdRemaining = slot.map { s =>
            val isChainSkill = s.isInChainWindow(now) && s.currentSkill.id != s.baseSkill.id
            if isChainSkill then 0L else s.cooldownRemainingMs(now)
          }.getOrElse(0L)
          // Only show skill CD text when skill CD is longer than GCD
          if skillCdRemaining > gcdRemaining then f"${skillCdRemaining / 1000.0}%.1fs" else ""
        },
        display <-- combatSignal.combineWith(slotSignal).map { case (combat, slot) =>
          val now = System.currentTimeMillis()
          val gcdRemaining = combat.map(_.globalCooldownRemainingMs(now)).getOrElse(0L)
          val skillCdRemaining = slot.map { s =>
            val isChainSkill = s.isInChainWindow(now) && s.currentSkill.id != s.baseSkill.id
            if isChainSkill then 0L else s.cooldownRemainingMs(now)
          }.getOrElse(0L)
          // Show when skill CD is active and longer than GCD
          if skillCdRemaining > gcdRemaining then "flex" else "none"
        }
      ),
      
      // GCD sweep overlay - clockwise vanishing animation
      div(
        cls := "velor-skill-gcd-overlay",
        cls <-- combatSignal.map { combat =>
          val now = System.currentTimeMillis()
          // Don't show GCD if casting this slot
          val isCastingThis = combat.exists(_.castingSkill.exists(_.slotIndex == slotIndex))
          if !isCastingThis && combat.exists(_.isOnGlobalCooldown(now)) then "active" else ""
        },
        display <-- combatSignal.map { combat =>
          val now = System.currentTimeMillis()
          val isCastingThis = combat.exists(_.castingSkill.exists(_.slotIndex == slotIndex))
          if !isCastingThis && combat.exists(_.isOnGlobalCooldown(now)) then "block" else "none"
        }
      ),
      
      // Cast bar overlay - fills up from bottom to top
      div(
        cls := "velor-skill-cast-overlay",
        styleAttr <-- combatSignal.map { combat =>
          val now = System.currentTimeMillis()
          combat.flatMap(_.castingSkill).filter(_.slotIndex == slotIndex).map { casting =>
            val progress = casting.progress(now) * 100
            s"--cast-progress: $progress%"
          }.getOrElse("--cast-progress: 0%")
        },
        display <-- combatSignal.map { combat =>
          if combat.exists(_.castingSkill.exists(_.slotIndex == slotIndex)) then "block" else "none"
        }
      ),

      // Chain indicator
      div(
        cls := "velor-chain-indicator",
        "⚡",
        display <-- slotSignal.map { s =>
          val now = System.currentTimeMillis()
          if s.exists(slot => slot.isInChainWindow(now) && slot.currentSkill.id != slot.baseSkill.id) then "block" else "none"
        }
      ),

      onClick --> { _ => onUseSkill(slotIndex) }
    )

  private def combatEndOverlayReactive(
    combatEndedSignal: Signal[Boolean],
    isVictorySignal: Signal[Boolean],
    combatSignal: Signal[Option[CombatState]],
    onStopCombat: () => Unit,
    onRestartCombat: () => Unit,
    onRest: () => Unit
  ): HtmlElement =
    div(
      cls := "velor-combat-end-overlay defeat",
      display <-- combatEndedSignal.map(if _ then "flex" else "none"),

      div(
        cls := "velor-combat-end-content",
        div(
          cls := "velor-combat-end-icon",
          "💀"
        ),
        div(
          cls := "velor-combat-end-title",
          "Defeated"
        ),
        div(
          cls := "velor-combat-end-message",
          "You were defeated. Rest to recover your HP!"
        ),
        div(
          cls := "velor-combat-end-buttons",
          button(
            cls := "btn btn-primary",
            "🛏️ Rest",
            onClick --> { _ => onRest() }
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


package shared.VelorIdle

import upickle.default.{ReadWriter, readwriter}

// ============================================================================
// Adventure (Combat) System
// ============================================================================

/** A combat skill that can be used in adventure mode */
case class CombatSkill(
  id: String,
  name: String,
  icon: String,
  description: String,
  manaCost: Int,
  cooldownMs: Long,          // Cooldown in milliseconds
  damage: Int,               // Base damage (0 for utility skills)
  castTimeMs: Long = 0L,     // Cast time in milliseconds (0 = instant)
  effects: Vector[SkillEffect] = Vector.empty,
  chainInto: Option[ChainSkill] = None  // If present, this skill chains into another
) derives ReadWriter

/** A chain skill that replaces the original skill temporarily after activation */
case class ChainSkill(
  skill: CombatSkill,
  windowMs: Long  // How long the chain window is open
) derives ReadWriter

/** Effects that combat skills can apply */
enum SkillEffect derives ReadWriter:
  case Damage(amount: Int)                    // Instant damage
  case DamageOverTime(damagePerTick: Int, ticks: Int, tickIntervalMs: Long)  // DoT
  case Stun(durationMs: Long)                 // Stun enemy
  case Heal(amount: Int)                      // Heal player
  case LifeDrain(percent: Double)             // Heal % of damage dealt
  case Shield(amount: Int, durationMs: Long)  // Absorb damage
  case IncreaseNextDamage(percent: Double)    // Buff next attack

/** A weapon that provides combat skills */
case class Weapon(
  id: String,
  name: String,
  icon: String,
  attackDamage: Int,         // Auto-attack damage
  attackSpeedMs: Long,       // Time between auto-attacks (default 2000ms)
  skills: Vector[CombatSkill]  // 4 skills from weapon
) derives ReadWriter

/** An armor piece that provides combat skills (future) */
case class Armor(
  id: String,
  name: String,
  icon: String,
  defense: Int,
  maxHpBonus: Int,
  skills: Vector[CombatSkill]  // 4 skills from armor
) derives ReadWriter

/** An enemy that can be fought */
case class Enemy(
  id: String,
  name: String,
  icon: String,
  levelRequired: Int,
  maxHp: Int,
  attackDamage: Int,
  attackSpeedMs: Long,
  xpReward: Int,
  goldReward: (Int, Int),    // (min, max)
  lootTable: Vector[(Item, Double)] = Vector.empty
) derives ReadWriter

/** Active DoT effect on an entity */
case class ActiveDoT(
  name: String,
  damagePerTick: Int,
  ticksRemaining: Int,
  tickIntervalMs: Long,
  lastTickTime: Long
) derives ReadWriter

/** Active stun effect */
case class ActiveStun(
  endsAt: Long
) derives ReadWriter

/** Active shield effect */
case class ActiveShield(
  remainingAbsorb: Int,
  endsAt: Long
) derives ReadWriter

/** Active damage buff */
case class ActiveDamageBuff(
  percent: Double,
  stackable: Boolean = false
) derives ReadWriter

/** State of a combat skill slot (handles chain skills) */
case class SkillSlotState(
  baseSkill: CombatSkill,
  currentSkill: CombatSkill,        // May be a chain skill
  cooldownEndsAt: Long = 0L,        // When skill comes off cooldown
  chainWindowEndsAt: Long = 0L      // When chain skill window closes (reverts to base)
) derives ReadWriter:
  def isOnCooldown(now: Long): Boolean = now < cooldownEndsAt
  def cooldownRemainingMs(now: Long): Long = (cooldownEndsAt - now).max(0)
  def isInChainWindow(now: Long): Boolean = now < chainWindowEndsAt

object SkillSlotState:
  def fromSkill(skill: CombatSkill): SkillSlotState =
    SkillSlotState(skill, skill)

/** Current state of combat */
case class CombatState(
  // Unique identifier for this combat instance - increments on each new combat
  // Used by UI to detect combat restarts and reset event tracking
  instanceId: Long,
  
  enemy: Enemy,
  enemyCurrentHp: Int,
  enemyDoTs: Vector[ActiveDoT] = Vector.empty,
  enemyStun: Option[ActiveStun] = None,
  
  playerCurrentHp: Int,
  playerMaxHp: Int,
  playerMana: Int,
  playerMaxMana: Int,
  playerDoTs: Vector[ActiveDoT] = Vector.empty,
  playerShield: Option[ActiveShield] = None,
  playerDamageBuff: Option[ActiveDamageBuff] = None,
  
  // Timing
  lastPlayerAutoAttack: Long = 0L,
  lastEnemyAutoAttack: Long = 0L,
  
  // Global cooldown - prevents spamming all skills at once
  globalCooldownEndsAt: Long = 0L,
  
  // Casting state - when casting a skill with cast time
  castingSkill: Option[CastingState] = None,
  
  // Skill slots (weapon skills in slots 0-3, armor skills in slots 4-7)
  skillSlots: Vector[SkillSlotState] = Vector.empty,
  
  // Combat log for display - capped at 10 events
  recentEvents: Vector[CombatEvent] = Vector.empty,
  // Total event count within this combat instance - resets with each new combat
  totalEventCount: Int = 0
) derives ReadWriter:
  def isEnemyDead: Boolean = enemyCurrentHp <= 0
  def isPlayerDead: Boolean = playerCurrentHp <= 0
  def isCombatOver: Boolean = isEnemyDead || isPlayerDead
  def isOnGlobalCooldown(now: Long): Boolean = now < globalCooldownEndsAt
  def globalCooldownRemainingMs(now: Long): Long = (globalCooldownEndsAt - now).max(0)
  def isCasting: Boolean = castingSkill.isDefined
  def castProgress(now: Long): Double = castingSkill.map(_.progress(now)).getOrElse(0.0)

/** State of a skill being cast */
case class CastingState(
  slotIndex: Int,
  skill: CombatSkill,
  startedAt: Long,
  completesAt: Long
) derives ReadWriter:
  def progress(now: Long): Double = 
    val total = completesAt - startedAt
    if total <= 0 then 1.0
    else ((now - startedAt).toDouble / total).min(1.0).max(0.0)
  def isComplete(now: Long): Boolean = now >= completesAt

/** Events that occur during combat (for UI feedback) */
enum CombatEvent derives ReadWriter:
  case PlayerAutoAttack(damage: Int)
  case EnemyAutoAttack(damage: Int)
  case PlayerSkillUsed(skillName: String, damage: Int)
  case PlayerHealed(amount: Int)
  case EnemyDotTick(damage: Int, sourceName: String)
  case PlayerDotTick(damage: Int, sourceName: String)
  case EnemyStunned(durationMs: Long)
  case EnemyDied
  case PlayerDied
  case LootGained(item: Item, count: Int)
  case GoldGained(amount: Int)
  case XpGained(amount: Int)
  case ShieldApplied(amount: Int)
  case ShieldBroken
  case DamageBuffApplied(percent: Double)
  case SkillOnCooldown(skillName: String)
  case NotEnoughMana(skillName: String, required: Int, current: Int)

// ============================================================================
// Predefined Content
// ============================================================================

object Weapons:
  val starterSword: Weapon = Weapon(
    id = "starter_sword",
    name = "Rusty Sword",
    icon = "🗡️",
    attackDamage = 5,
    attackSpeedMs = 2000,
    skills = Vector(
      CombatSkill(
        id = "slash",
        name = "Slash",
        icon = "⚔️",
        description = "A quick slash dealing moderate damage",
        manaCost = 10,
        cooldownMs = 3000,
        damage = 15,
        chainInto = Some(ChainSkill(
          CombatSkill(
            id = "double_slash",
            name = "Double Slash",
            icon = "⚔️⚔️",
            description = "Follow-up slash dealing heavy damage",
            manaCost = 15,
            cooldownMs = 5000,
            damage = 25,
            chainInto = Some(ChainSkill(
              CombatSkill(
                id = "triple_slash",
                name = "Triple Slash",
                icon = "⚔️⚔️⚔️",
                description = "Devastating finishing blow",
                manaCost = 20,
                cooldownMs = 8000,
                damage = 40
              ),
              windowMs = 2500
            ))
          ),
          windowMs = 3000
        ))
      ),
      CombatSkill(
        id = "bleed",
        name = "Bleed",
        icon = "🩸",
        description = "Inflict bleeding, dealing damage over time",
        manaCost = 20,
        cooldownMs = 8000,
        damage = 5,
        castTimeMs = 1500,  // 1.5 second cast time
        effects = Vector(SkillEffect.DamageOverTime(3, 5, 1000)),
        chainInto = Some(ChainSkill(
          CombatSkill(
            id = "hemorrhage",
            name = "Hemorrhage",
            icon = "🩸💀",
            description = "Cause severe bleeding on an already wounded target",
            manaCost = 25,
            cooldownMs = 10000,
            damage = 10,
            castTimeMs = 1000,
            effects = Vector(SkillEffect.DamageOverTime(5, 6, 1000))
          ),
          windowMs = 4000
        ))
      ),
      CombatSkill(
        id = "vampiric_strike",
        name = "Vampiric Strike",
        icon = "🧛",
        description = "Drain life from the enemy",
        manaCost = 25,
        cooldownMs = 10000,
        damage = 12,
        effects = Vector(SkillEffect.LifeDrain(0.5))
      ),
      CombatSkill(
        id = "power_strike",
        name = "Power Strike",
        icon = "💥",
        description = "Charge up for a powerful blow",
        manaCost = 30,
        cooldownMs = 15000,
        damage = 40
      )
    )
  )

  val ironSword: Weapon = Weapon(
    id = "iron_sword",
    name = "Iron Sword",
    icon = "🗡️",
    attackDamage = 8,
    attackSpeedMs = 2000,
    skills = Vector(
      CombatSkill(
        id = "cleave",
        name = "Cleave",
        icon = "🪓",
        description = "A wide cleaving attack",
        manaCost = 12,
        cooldownMs = 4000,
        damage = 20,
        chainInto = Some(ChainSkill(
          CombatSkill(
            id = "execute",
            name = "Execute",
            icon = "☠️",
            description = "Finish off wounded enemies",
            manaCost = 20,
            cooldownMs = 6000,
            damage = 35
          ),
          windowMs = 2500
        ))
      ),
      CombatSkill(
        id = "deep_wound",
        name = "Deep Wound",
        icon = "🩸",
        description = "Cause severe bleeding",
        manaCost = 25,
        cooldownMs = 10000,
        damage = 8,
        effects = Vector(SkillEffect.DamageOverTime(5, 6, 1000))
      ),
      CombatSkill(
        id = "life_steal",
        name = "Life Steal",
        icon = "💚",
        description = "Steal life force from the enemy",
        manaCost = 30,
        cooldownMs = 12000,
        damage = 18,
        effects = Vector(SkillEffect.LifeDrain(0.6))
      ),
      CombatSkill(
        id = "stunning_blow",
        name = "Stunning Blow",
        icon = "💫",
        description = "Stun the enemy briefly",
        manaCost = 35,
        cooldownMs = 18000,
        damage = 15,
        effects = Vector(SkillEffect.Stun(2000))
      )
    )
  )

  val all: Vector[Weapon] = Vector(starterSword, ironSword)

object Enemies:
  val goblin: Enemy = Enemy(
    id = "goblin",
    name = "Goblin",
    icon = "👺",
    levelRequired = 1,
    maxHp = 30,
    attackDamage = 3,
    attackSpeedMs = 2500,
    xpReward = 20,
    goldReward = (5, 15)
  )

  val skeleton: Enemy = Enemy(
    id = "skeleton",
    name = "Skeleton",
    icon = "💀",
    levelRequired = 5,
    maxHp = 50,
    attackDamage = 5,
    attackSpeedMs = 2000,
    xpReward = 40,
    goldReward = (10, 30),
    lootTable = Vector((Item.BronzeBar, 0.1))
  )

  val orc: Enemy = Enemy(
    id = "orc",
    name = "Orc",
    icon = "👹",
    levelRequired = 10,
    maxHp = 80,
    attackDamage = 8,
    attackSpeedMs = 2200,
    xpReward = 70,
    goldReward = (20, 50),
    lootTable = Vector((Item.IronBar, 0.08))
  )

  val darkKnight: Enemy = Enemy(
    id = "dark_knight",
    name = "Dark Knight",
    icon = "🖤",
    levelRequired = 20,
    maxHp = 150,
    attackDamage = 12,
    attackSpeedMs = 1800,
    xpReward = 150,
    goldReward = (50, 100),
    lootTable = Vector((Item.SteelBar, 0.1), (Item.Gem, 0.05))
  )

  val dragon: Enemy = Enemy(
    id = "dragon",
    name = "Dragon",
    icon = "🐉",
    levelRequired = 35,
    maxHp = 300,
    attackDamage = 20,
    attackSpeedMs = 2500,
    xpReward = 400,
    goldReward = (100, 250),
    lootTable = Vector((Item.MithrilBar, 0.15), (Item.Gem, 0.2))
  )

  val trainingDummy: Enemy = Enemy(
    id = "training_dummy",
    name = "Training Dummy",
    icon = "🎯",
    levelRequired = 1,
    maxHp = 1000,
    attackDamage = 0,
    attackSpeedMs = 9999999,  // Effectively never attacks
    xpReward = 0,
    goldReward = (0, 0),
    lootTable = Vector.empty
  )

  val all: Vector[Enemy] = Vector(trainingDummy, goblin, skeleton, orc, darkKnight, dragon)

// ============================================================================
// Adventure State (for VelorIdleGame)
// ============================================================================

/** State of adventure mode */
case class AdventureState(
  inCombat: Boolean = false,
  combatState: Option[CombatState] = None,
  selectedEnemyId: Option[String] = None,
  equippedWeapon: Weapon = Weapons.starterSword,
  equippedArmor: Option[Armor] = None,  // Future
  // Persistent player stats (persists between combats)
  currentHp: Int = AdventureState.BaseMaxHp,
  currentMana: Int = AdventureState.BaseMaxMana,
  // Counter for generating unique combat instance IDs
  nextCombatInstanceId: Long = 1L
) derives ReadWriter:
  def maxHp: Int = AdventureState.BaseMaxHp + equippedArmor.map(_.maxHpBonus).getOrElse(0)
  def maxMana: Int = AdventureState.BaseMaxMana

object AdventureState:
  val initial: AdventureState = AdventureState()

  /** Base player stats */
  val BaseMaxHp: Int = 100
  val BaseMaxMana: Int = 50
  val ManaRegenPerSecond: Double = 10.0  // Mana regen while resting
  val HpRegenPerSecond: Double = 5.0     // HP regen while resting


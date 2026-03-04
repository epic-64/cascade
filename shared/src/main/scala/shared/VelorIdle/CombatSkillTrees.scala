package shared.VelorIdle

import upickle.default.{ReadWriter, readwriter}

// ============================================================================
// Combat Skill Trees System
// ============================================================================

/** A skill tree containing combat skills the player can invest points into */
case class SkillTree(
  id: String,
  name: String,
  icon: String,
  description: String,
  skills: Vector[TreeSkill]
) derives ReadWriter

/** A skill within a skill tree */
case class TreeSkill(
  id: String,
  name: String,
  icon: String,
  description: String,
  manaCost: Int,
  cooldownMs: Long,
  baseDamage: Int,               // Base damage at level 1
  damagePerLevel: Int,           // Additional damage per level
  castTimeMs: Long = 0L,
  effects: Vector[SkillEffect] = Vector.empty,
  chainSkills: Vector[TreeChainSkill] = Vector.empty,  // Built-in chain skills
  maxLevel: Int = 10,            // Maximum level for this skill
  unlockRequirement: Option[TreeSkillRequirement] = None  // Required skill level to unlock
) derives ReadWriter:
  /** Calculate damage at a given level */
  def damageAtLevel(level: Int): Int =
    if level <= 0 then 0
    else baseDamage + (damagePerLevel * (level - 1))

/** A chain skill that automatically becomes available after using the parent skill */
case class TreeChainSkill(
  skill: TreeSkill,
  windowMs: Long,           // How long the chain window is open
  requiredLevel: Int = 1    // Parent skill level required to unlock this chain
) derives ReadWriter

/** Requirement to unlock a skill in the tree */
case class TreeSkillRequirement(
  skillId: String,
  level: Int
) derives ReadWriter

/** Player's combat skill allocation state */
case class CombatSkillState(
  // Skill points allocated to each skill: skillId -> level
  allocatedPoints: Map[String, Int] = Map.empty,
  // Skills bound to combat slots (1-4): slotIndex -> skillId
  boundSkills: Vector[Option[String]] = Vector.fill(4)(None),
  // Total skill points available to spend
  availablePoints: Int = 0,
  // Total skill points earned (for tracking)
  totalPointsEarned: Int = 0
) derives ReadWriter:
  /** Get level of a skill (0 if not allocated) */
  def getSkillLevel(skillId: String): Int =
    allocatedPoints.getOrElse(skillId, 0)

  /** Get total points spent */
  def spentPoints: Int = allocatedPoints.values.sum

  /** Check if a skill is unlocked (has at least 1 point) */
  def isSkillUnlocked(skillId: String): Boolean =
    getSkillLevel(skillId) > 0

  /** Get skill bound to a slot */
  def getBoundSkill(slot: Int): Option[String] =
    boundSkills.lift(slot).flatten

object CombatSkillState:
  val initial: CombatSkillState = CombatSkillState(
    availablePoints = 3  // Start with 3 skill points
  )

// ============================================================================
// Skill Tree Definitions
// ============================================================================

object SkillTrees:

  // --------------------------------------------------------------------------
  // Warrior Tree - Physical melee combat
  // --------------------------------------------------------------------------
  val warrior: SkillTree = SkillTree(
    id = "warrior",
    name = "Warrior",
    icon = "⚔️",
    description = "Master of physical combat with swords and heavy attacks",
    skills = Vector(
      TreeSkill(
        id = "slash",
        name = "Slash",
        icon = "⚔️",
        description = "A quick slash dealing moderate damage",
        manaCost = 10,
        cooldownMs = 3000,
        baseDamage = 15,
        damagePerLevel = 3,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "double_slash",
              name = "Double Slash",
              icon = "⚔️⚔️",
              description = "Follow-up slash dealing heavy damage",
              manaCost = 15,
              cooldownMs = 5000,
              baseDamage = 25,
              damagePerLevel = 5,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "triple_slash",
                    name = "Triple Slash",
                    icon = "⚔️⚔️⚔️",
                    description = "Final devastating slash",
                    manaCost = 20,
                    cooldownMs = 6000,
                    baseDamage = 40,
                    damagePerLevel = 8
                  ),
                  windowMs = 2500,
                  requiredLevel = 6
                )
              )
            ),
            windowMs = 2500,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "cleave",
        name = "Cleave",
        icon = "🪓",
        description = "A wide cleaving attack",
        manaCost = 12,
        cooldownMs = 4000,
        baseDamage = 20,
        damagePerLevel = 4,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "execute",
              name = "Execute",
              icon = "☠️",
              description = "Finish off wounded enemies",
              manaCost = 20,
              cooldownMs = 6000,
              baseDamage = 35,
              damagePerLevel = 7
            ),
            windowMs = 2500,
            requiredLevel = 4
          )
        )
      ),
      TreeSkill(
        id = "shield_bash",
        name = "Shield Bash",
        icon = "🛡️",
        description = "Bash the enemy with your shield, stunning them",
        manaCost = 15,
        cooldownMs = 8000,
        baseDamage = 10,
        damagePerLevel = 2,
        effects = Vector(SkillEffect.Stun(1500))
      ),
      TreeSkill(
        id = "battle_cry",
        name = "Battle Cry",
        icon = "📢",
        description = "Boost your next attack's damage",
        manaCost = 8,
        cooldownMs = 10000,
        baseDamage = 0,
        damagePerLevel = 0,
        effects = Vector(SkillEffect.IncreaseNextDamage(0.5))  // 50% damage boost
      )
    )
  )

  // --------------------------------------------------------------------------
  // Mage Tree - Elemental magic damage
  // --------------------------------------------------------------------------
  val mage: SkillTree = SkillTree(
    id = "mage",
    name = "Mage",
    icon = "🔮",
    description = "Wielder of elemental magic and devastating spells",
    skills = Vector(
      TreeSkill(
        id = "fireball",
        name = "Fireball",
        icon = "🔥",
        description = "Hurl a ball of fire at your enemy",
        manaCost = 15,
        cooldownMs = 4000,
        baseDamage = 25,
        damagePerLevel = 5,
        castTimeMs = 800,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "flame_burst",
              name = "Flame Burst",
              icon = "🔥💥",
              description = "Ignite the flames for additional damage",
              manaCost = 20,
              cooldownMs = 5000,
              baseDamage = 35,
              damagePerLevel = 7,
              castTimeMs = 600,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "pyroblast",
                    name = "Pyroblast",
                    icon = "☀️",
                    description = "A massive explosion of concentrated fire",
                    manaCost = 30,
                    cooldownMs = 8000,
                    baseDamage = 60,
                    damagePerLevel = 12,
                    castTimeMs = 1200
                  ),
                  windowMs = 3000,
                  requiredLevel = 6
                )
              )
            ),
            windowMs = 3000,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "ice_shard",
        name = "Ice Shard",
        icon = "❄️",
        description = "Launch a piercing shard of ice",
        manaCost = 12,
        cooldownMs = 3500,
        baseDamage = 18,
        damagePerLevel = 4,
        castTimeMs = 500,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "frost_lance",
              name = "Frost Lance",
              icon = "🧊",
              description = "A razor-sharp lance of frozen magic",
              manaCost = 18,
              cooldownMs = 4500,
              baseDamage = 28,
              damagePerLevel = 6,
              castTimeMs = 700,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "frost_nova",
                    name = "Frost Nova",
                    icon = "💠",
                    description = "Freeze and shatter enemies with a frozen explosion",
                    manaCost = 25,
                    cooldownMs = 6000,
                    baseDamage = 45,
                    damagePerLevel = 9,
                    castTimeMs = 1000,
                    effects = Vector(SkillEffect.Stun(1500))
                  ),
                  windowMs = 2500,
                  requiredLevel = 6
                )
              )
            ),
            windowMs = 2500,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "lightning_bolt",
        name = "Lightning Bolt",
        icon = "⚡",
        description = "Strike with crackling lightning",
        manaCost = 18,
        cooldownMs = 5000,
        baseDamage = 30,
        damagePerLevel = 6,
        castTimeMs = 600,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "thunder_strike",
              name = "Thunder Strike",
              icon = "⚡⚡",
              description = "Call down a powerful thunderbolt",
              manaCost = 24,
              cooldownMs = 6000,
              baseDamage = 42,
              damagePerLevel = 8,
              castTimeMs = 800,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "chain_lightning",
                    name = "Chain Lightning",
                    icon = "⚡⚡⚡",
                    description = "Lightning that arcs to multiple targets",
                    manaCost = 30,
                    cooldownMs = 8000,
                    baseDamage = 55,
                    damagePerLevel = 11,
                    castTimeMs = 1000
                  ),
                  windowMs = 2500,
                  requiredLevel = 6
                )
              )
            ),
            windowMs = 2500,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "arcane_missiles",
        name = "Arcane Missiles",
        icon = "✨",
        description = "Channel a stream of arcane energy",
        manaCost = 14,
        cooldownMs = 3000,
        baseDamage = 20,
        damagePerLevel = 4,
        castTimeMs = 1500,  // Long channel time
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "arcane_barrage",
              name = "Arcane Barrage",
              icon = "✨✨",
              description = "Unleash stored arcane power",
              manaCost = 22,
              cooldownMs = 5000,
              baseDamage = 38,
              damagePerLevel = 8,
              castTimeMs = 400,  // Quick follow-up
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "arcane_explosion",
                    name = "Arcane Explosion",
                    icon = "💫",
                    description = "Detonate all arcane energy in a devastating blast",
                    manaCost = 28,
                    cooldownMs = 7000,
                    baseDamage = 52,
                    damagePerLevel = 10,
                    castTimeMs = 600
                  ),
                  windowMs = 3000,
                  requiredLevel = 6
                )
              )
            ),
            windowMs = 3000,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "arcane_shield",
        name = "Arcane Shield",
        icon = "🔷",
        description = "Conjure a magical barrier",
        manaCost = 20,
        cooldownMs = 12000,
        baseDamage = 0,
        damagePerLevel = 0,
        castTimeMs = 300,
        effects = Vector(SkillEffect.Shield(30, 10000))  // 30 absorb, 10s duration
      )
    )
  )

  // --------------------------------------------------------------------------
  // Rogue Tree - Fast attacks and damage over time
  // --------------------------------------------------------------------------
  val rogue: SkillTree = SkillTree(
    id = "rogue",
    name = "Rogue",
    icon = "🗡️",
    description = "Swift strikes and deadly poisons",
    skills = Vector(
      TreeSkill(
        id = "backstab",
        name = "Backstab",
        icon = "🔪",
        description = "A quick stab from the shadows",
        manaCost = 8,
        cooldownMs = 2500,
        baseDamage = 12,
        damagePerLevel = 3,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "eviscerate",
              name = "Eviscerate",
              icon = "💀",
              description = "A vicious finishing move",
              manaCost = 15,
              cooldownMs = 4000,
              baseDamage = 28,
              damagePerLevel = 6
            ),
            windowMs = 2000,
            requiredLevel = 3
          )
        )
      ),
      TreeSkill(
        id = "poison_blade",
        name = "Poison Blade",
        icon = "☠️",
        description = "Coat your blade in deadly poison",
        manaCost = 12,
        cooldownMs = 6000,
        baseDamage = 8,
        damagePerLevel = 2,
        effects = Vector(SkillEffect.DamageOverTime(5, 5, 1000))  // 5 damage every second for 5 ticks
      ),
      TreeSkill(
        id = "smoke_bomb",
        name = "Smoke Bomb",
        icon = "💨",
        description = "Blind the enemy temporarily",
        manaCost = 15,
        cooldownMs = 10000,
        baseDamage = 5,
        damagePerLevel = 1,
        effects = Vector(SkillEffect.Stun(2000))
      ),
      TreeSkill(
        id = "life_drain",
        name = "Life Drain",
        icon = "🩸",
        description = "Steal health from your enemy",
        manaCost = 18,
        cooldownMs = 8000,
        baseDamage = 15,
        damagePerLevel = 3,
        effects = Vector(SkillEffect.LifeDrain(0.5))  // Heal 50% of damage dealt
      )
    )
  )

  // --------------------------------------------------------------------------
  // Cleric Tree - Healing and support
  // --------------------------------------------------------------------------
  val cleric: SkillTree = SkillTree(
    id = "cleric",
    name = "Cleric",
    icon = "✝️",
    description = "Divine healing and holy damage",
    skills = Vector(
      TreeSkill(
        id = "smite",
        name = "Smite",
        icon = "✨",
        description = "Strike with holy light",
        manaCost = 10,
        cooldownMs = 3000,
        baseDamage = 18,
        damagePerLevel = 4,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "divine_wrath",
              name = "Divine Wrath",
              icon = "💫",
              description = "Unleash the fury of the heavens",
              manaCost = 20,
              cooldownMs = 6000,
              baseDamage = 35,
              damagePerLevel = 7
            ),
            windowMs = 2500,
            requiredLevel = 4
          )
        )
      ),
      TreeSkill(
        id = "heal",
        name = "Heal",
        icon = "💚",
        description = "Restore your health",
        manaCost = 20,
        cooldownMs = 6000,
        baseDamage = 0,
        damagePerLevel = 0,
        effects = Vector(SkillEffect.Heal(25))
      ),
      TreeSkill(
        id = "holy_shield",
        name = "Holy Shield",
        icon = "🛡️✨",
        description = "Surround yourself with divine protection",
        manaCost = 18,
        cooldownMs = 10000,
        baseDamage = 0,
        damagePerLevel = 0,
        effects = Vector(SkillEffect.Shield(40, 12000))
      ),
      TreeSkill(
        id = "purify",
        name = "Purify",
        icon = "🌟",
        description = "Cleanse and heal over time",
        manaCost = 15,
        cooldownMs = 8000,
        baseDamage = 5,
        damagePerLevel = 1,
        effects = Vector(SkillEffect.Heal(15))  // Instant heal component
      )
    )
  )

  /** All available skill trees */
  val all: Vector[SkillTree] = Vector(warrior, mage, rogue, cleric)

  /** Get a skill tree by ID */
  def getById(id: String): Option[SkillTree] =
    all.find(_.id == id)

  /** Get a skill by ID across all trees */
  def getSkillById(skillId: String): Option[TreeSkill] =
    all.flatMap(_.skills).find(_.id == skillId).orElse(
      // Also search chain skills
      all.flatMap(_.skills).flatMap(getAllChainSkills).find(_.id == skillId)
    )

  /** Get the tree that contains a specific skill */
  def getTreeForSkill(skillId: String): Option[SkillTree] =
    all.find { tree =>
      tree.skills.exists(_.id == skillId) ||
      tree.skills.flatMap(getAllChainSkills).exists(_.id == skillId)
    }

  /** Extract all chain skills from a skill (recursively) */
  def getAllChainSkills(skill: TreeSkill): Vector[TreeSkill] =
    skill.chainSkills.flatMap { chain =>
      chain.skill +: getAllChainSkills(chain.skill)
    }

// ============================================================================
// Combat Skill Helper Functions
// ============================================================================

object CombatSkillHelpers:

  /** Convert a TreeSkill to a CombatSkill at a specific level */
  def toCombatSkill(treeSkill: TreeSkill, level: Int): CombatSkill =
    CombatSkill(
      id = treeSkill.id,
      name = treeSkill.name,
      icon = treeSkill.icon,
      description = treeSkill.description,
      manaCost = treeSkill.manaCost,
      cooldownMs = treeSkill.cooldownMs,
      damage = treeSkill.damageAtLevel(level),
      castTimeMs = treeSkill.castTimeMs,
      effects = treeSkill.effects,
      chainInto = treeSkill.chainSkills.headOption.map { chain =>
        ChainSkill(
          skill = toCombatSkill(chain.skill, level),  // Chain skills inherit level
          windowMs = chain.windowMs
        )
      }
    )

  /** Build combat skill slots from bound skills and their levels */
  def buildSkillSlots(combatSkillState: CombatSkillState): Vector[SkillSlotState] =
    combatSkillState.boundSkills.map { maybeSkillId =>
      maybeSkillId.flatMap(SkillTrees.getSkillById) match
        case Some(treeSkill) =>
          val level = combatSkillState.getSkillLevel(treeSkill.id).max(1)
          val combatSkill = toCombatSkill(treeSkill, level)
          SkillSlotState.fromSkill(combatSkill)
        case None =>
          SkillSlotState.fromSkill(emptySkill)
    }

  private val emptySkill = CombatSkill(
    id = "empty",
    name = "Empty",
    icon = "➖",
    description = "No skill equipped",
    manaCost = 0,
    cooldownMs = 0,
    damage = 0
  )

// ============================================================================
// Skill Point Allocation Logic
// ============================================================================

object SkillTreeLogic:

  /** Allocate a point to a skill */
  def allocatePoint(state: CombatSkillState, skillId: String): Either[String, CombatSkillState] =
    if state.availablePoints <= 0 then
      Left("No skill points available")
    else
      SkillTrees.getSkillById(skillId) match
        case None => Left("Unknown skill")
        case Some(skill) =>
          val currentLevel = state.getSkillLevel(skillId)
          if currentLevel >= skill.maxLevel then
            Left(s"${skill.name} is already at max level")
          else
            // Check unlock requirements
            skill.unlockRequirement match
              case Some(req) if state.getSkillLevel(req.skillId) < req.level =>
                SkillTrees.getSkillById(req.skillId) match
                  case Some(reqSkill) =>
                    Left(s"Requires ${reqSkill.name} level ${req.level}")
                  case None =>
                    Left("Invalid requirement")
              case _ =>
                Right(state.copy(
                  allocatedPoints = state.allocatedPoints.updated(skillId, currentLevel + 1),
                  availablePoints = state.availablePoints - 1
                ))

  /** Bind a skill to a combat slot */
  def bindSkill(state: CombatSkillState, skillId: String, slot: Int): Either[String, CombatSkillState] =
    if slot < 0 || slot >= 4 then
      Left("Invalid slot")
    else if !state.isSkillUnlocked(skillId) then
      Left("Skill not unlocked")
    else
      // Unbind from other slots if already bound
      val clearedSlots = state.boundSkills.map {
        case Some(id) if id == skillId => None
        case other => other
      }
      Right(state.copy(
        boundSkills = clearedSlots.updated(slot, Some(skillId))
      ))

  /** Unbind a skill from a combat slot */
  def unbindSkill(state: CombatSkillState, slot: Int): Either[String, CombatSkillState] =
    if slot < 0 || slot >= 4 then
      Left("Invalid slot")
    else
      Right(state.copy(
        boundSkills = state.boundSkills.updated(slot, None)
      ))

  /** Award skill points (e.g., on level up) */
  def awardPoints(state: CombatSkillState, points: Int): CombatSkillState =
    state.copy(
      availablePoints = state.availablePoints + points,
      totalPointsEarned = state.totalPointsEarned + points
    )

  /** Reset all allocated points (refund) */
  def resetPoints(state: CombatSkillState): CombatSkillState =
    val refundedPoints = state.spentPoints
    state.copy(
      allocatedPoints = Map.empty,
      boundSkills = Vector.fill(4)(None),
      availablePoints = state.availablePoints + refundedPoints
    )

  /** Get all skills that are unlocked (have at least 1 point) */
  def getUnlockedSkills(state: CombatSkillState): Vector[(TreeSkill, Int)] =
    state.allocatedPoints.toVector.flatMap { case (skillId, level) =>
      SkillTrees.getSkillById(skillId).map(skill => (skill, level))
    }


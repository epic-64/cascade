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
  /** Calculate damage at a given level using triangular growth.
    * Level 1: baseDamage
    * Level 2: baseDamage + 1 * damagePerLevel (triangular(1) = 1)
    * Level 3: baseDamage + 3 * damagePerLevel (triangular(2) = 3)
    * Level 4: baseDamage + 6 * damagePerLevel (triangular(3) = 6)
    * etc.
    */
  def damageAtLevel(level: Int): Int =
    if level <= 0 then 0
    else baseDamage + (AdventureState.triangular(level - 1) * damagePerLevel)

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
        description = "A quick slash that can chain into a devastating combo",
        manaCost = 10,
        cooldownMs = 10000,  // Long cooldown - has 3-skill chain
        baseDamage = 3,
        damagePerLevel = 1,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "double_slash",
              name = "Double Slash",
              icon = "⚔️⚔️",
              description = "Follow-up slash dealing heavy damage",
              manaCost = 15,
              cooldownMs = 0,  // Chain skills don't need cooldown
              baseDamage = 5,
              damagePerLevel = 1,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "triple_slash",
                    name = "Triple Slash",
                    icon = "⚔️⚔️⚔️",
                    description = "Final devastating slash",
                    manaCost = 20,
                    cooldownMs = 0,
                    baseDamage = 8,
                    damagePerLevel = 2
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
        description = "A wide cleaving attack that can execute wounded foes",
        manaCost = 12,
        cooldownMs = 8000,  // Medium cooldown - has 2-skill chain
        baseDamage = 4,
        damagePerLevel = 1,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "execute",
              name = "Execute",
              icon = "☠️",
              description = "Finish off wounded enemies",
              manaCost = 20,
              cooldownMs = 0,
              baseDamage = 7,
              damagePerLevel = 2
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
        cooldownMs = 2000,  // Short cooldown - no chain, utility skill
        baseDamage = 2,
        damagePerLevel = 1,
        effects = Vector(SkillEffect.Stun(1500))
      ),
      TreeSkill(
        id = "battle_cry",
        name = "Battle Cry",
        icon = "📢",
        description = "Boost your next attack's damage",
        manaCost = 8,
        cooldownMs = 1500,  // Short cooldown - buff skill
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
        description = "Hurl a ball of fire that ignites into a devastating combo",
        manaCost = 15,
        cooldownMs = 12000,  // Long cooldown - has 3-skill chain
        baseDamage = 5,
        damagePerLevel = 1,
        castTimeMs = 1000,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "flame_burst",
              name = "Flame Burst",
              icon = "🔥💥",
              description = "Ignite the flames for additional damage",
              manaCost = 20,
              cooldownMs = 0,
              baseDamage = 7,
              damagePerLevel = 1,
              castTimeMs = 1200,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "pyroblast",
                    name = "Pyroblast",
                    icon = "☀️",
                    description = "A massive explosion of concentrated fire",
                    manaCost = 30,
                    cooldownMs = 0,
                    baseDamage = 12,
                    damagePerLevel = 2,
                    castTimeMs = 2000  // Very powerful finisher - long cast
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
        id = "frost_nova",
        name = "Frost Nova",
        icon = "💠",
        description = "Unleash a frozen explosion with a high chance to freeze the enemy",
        manaCost = 12,
        cooldownMs = 10000,  // Long cooldown - has 3-skill chain
        baseDamage = 3,
        damagePerLevel = 1,
        castTimeMs = 1000,
        effects = Vector(SkillEffect.Freeze(75, 5000)),  // 75% chance to freeze for 5s
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "ice_shard",
              name = "Ice Shard",
              icon = "❄️",
              description = "Launch a piercing shard of ice at the frozen target",
              manaCost = 15,
              cooldownMs = 0,
              baseDamage = 5,
              damagePerLevel = 1,
              castTimeMs = 1200,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "frost_lance",
                    name = "Frost Lance",
                    icon = "🧊",
                    description = "Shatter the frozen enemy for massive bonus damage",
                    manaCost = 20,
                    cooldownMs = 0,
                    baseDamage = 6,
                    damagePerLevel = 1,
                    castTimeMs = 1500,
                    effects = Vector(SkillEffect.ConsumeFreeze(2.0))  // 200% bonus damage if frozen
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
        id = "lightning_bolt",
        name = "Lightning Bolt",
        icon = "⚡",
        description = "Strike with crackling lightning that arcs into a storm",
        manaCost = 18,
        cooldownMs = 12000,  // Long cooldown - has 3-skill chain
        baseDamage = 6,
        damagePerLevel = 1,
        castTimeMs = 1000,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "thunder_strike",
              name = "Thunder Strike",
              icon = "⚡⚡",
              description = "Call down a powerful thunderbolt",
              manaCost = 24,
              cooldownMs = 0,
              baseDamage = 8,
              damagePerLevel = 2,
              castTimeMs = 1200,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "chain_lightning",
                    name = "Chain Lightning",
                    icon = "⚡⚡⚡",
                    description = "Lightning that arcs to multiple targets",
                    manaCost = 30,
                    cooldownMs = 0,
                    baseDamage = 11,
                    damagePerLevel = 2,
                    castTimeMs = 1800
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
        description = "Channel a stream of arcane energy that builds power",
        manaCost = 14,
        cooldownMs = 10000,  // Long cooldown - has 3-skill chain
        baseDamage = 4,
        damagePerLevel = 1,
        castTimeMs = 2000,  // Long channel time
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "arcane_barrage",
              name = "Arcane Barrage",
              icon = "✨✨",
              description = "Unleash stored arcane power",
              manaCost = 22,
              cooldownMs = 0,
              baseDamage = 8,
              damagePerLevel = 2,
              castTimeMs = 1000,
              chainSkills = Vector(
                TreeChainSkill(
                  skill = TreeSkill(
                    id = "arcane_explosion",
                    name = "Arcane Explosion",
                    icon = "💫",
                    description = "Detonate all arcane energy in a devastating blast",
                    manaCost = 28,
                    cooldownMs = 0,
                    baseDamage = 10,
                    damagePerLevel = 2,
                    castTimeMs = 1500
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
        cooldownMs = 2000,  // Short cooldown - defensive utility, no chain
        baseDamage = 0,
        damagePerLevel = 0,
        castTimeMs = 1000,
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
        description = "A quick stab from the shadows that sets up a finishing move",
        manaCost = 8,
        cooldownMs = 8000,  // Medium cooldown - has 2-skill chain
        baseDamage = 4,
        damagePerLevel = 1,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "eviscerate",
              name = "Eviscerate",
              icon = "💀",
              description = "A vicious finishing move",
              manaCost = 15,
              cooldownMs = 0,
              baseDamage = 6,
              damagePerLevel = 1
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
        cooldownMs = 2000,  // Short cooldown - no chain, DoT utility
        baseDamage = 2,
        damagePerLevel = 1,
        effects = Vector(SkillEffect.DamageOverTime(1, 5, 1000))  // 1 damage every second for 5 ticks
      ),
      TreeSkill(
        id = "smoke_bomb",
        name = "Smoke Bomb",
        icon = "💨",
        description = "Blind the enemy temporarily",
        manaCost = 15,
        cooldownMs = 2000,  // Short cooldown - CC utility
        baseDamage = 2,
        damagePerLevel = 1,
        effects = Vector(SkillEffect.Stun(2000))
      ),
      TreeSkill(
        id = "life_drain",
        name = "Life Drain",
        icon = "🩸",
        description = "Steal health from your enemy",
        manaCost = 18,
        cooldownMs = 2000,  // Short cooldown - sustain utility
        baseDamage = 4,
        damagePerLevel = 1,
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
        description = "Strike with holy light that can unleash divine wrath",
        manaCost = 10,
        cooldownMs = 8000,  // Medium cooldown - has 2-skill chain
        baseDamage = 4,
        damagePerLevel = 1,
        castTimeMs = 1000,
        chainSkills = Vector(
          TreeChainSkill(
            skill = TreeSkill(
              id = "divine_wrath",
              name = "Divine Wrath",
              icon = "💫",
              description = "Unleash the fury of the heavens",
              manaCost = 20,
              cooldownMs = 0,
              baseDamage = 7,
              damagePerLevel = 2,
              castTimeMs = 1500
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
        description = "Restore your health with divine energy",
        manaCost = 20,
        cooldownMs = 2000,  // Short cooldown - core healing utility
        baseDamage = 0,
        damagePerLevel = 0,
        castTimeMs = 1500,  // Significant heal needs cast time
        effects = Vector(SkillEffect.Heal(10))
      ),
      TreeSkill(
        id = "holy_shield",
        name = "Holy Shield",
        icon = "🛡️✨",
        description = "Surround yourself with divine protection",
        manaCost = 18,
        cooldownMs = 2000,  // Short cooldown - defensive utility
        baseDamage = 0,
        damagePerLevel = 0,
        castTimeMs = 1000,
        effects = Vector(SkillEffect.Shield(15, 12000))
      ),
      TreeSkill(
        id = "purify",
        name = "Purify",
        icon = "🌟",
        description = "Cleanse and heal over time",
        manaCost = 15,
        cooldownMs = 2000,  // Short cooldown - utility
        baseDamage = 2,
        damagePerLevel = 1,
        castTimeMs = 1000,
        effects = Vector(SkillEffect.Heal(5))  // Instant heal component
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
      chainInto = getUnlockedChainSkill(treeSkill, level)
    )

  /** Get the first unlocked chain skill (if any) at the given level */
  private def getUnlockedChainSkill(treeSkill: TreeSkill, level: Int): Option[ChainSkill] =
    treeSkill.chainSkills.headOption.flatMap { chain =>
      if level >= chain.requiredLevel then
        Some(ChainSkill(
          skill = toCombatSkill(chain.skill, level),  // Recursive - will check nested chain requirements
          windowMs = chain.windowMs
        ))
      else
        None  // Chain skill not unlocked yet
    }

  /** Build combat skill slots from bound skills and their levels */
  def buildSkillSlots(combatSkillState: CombatSkillState): Vector[SkillSlotState] =
    combatSkillState.boundSkills.map { maybeSkillId =>
      maybeSkillId.flatMap(SkillTrees.getSkillById) match
        case Some(treeSkill) =>
          val level = combatSkillState.getSkillLevel(treeSkill.id).max(1)
          val combatSkill = toCombatSkill(treeSkill, level)
          SkillSlotState.fromSkill(combatSkill)
        case None =>
          SkillSlotState.fromSkill(CombatSkill.empty)
    }

// ============================================================================
// Skill Point Allocation Logic
// ============================================================================

object SkillTreeLogic:

  /** Cost in gold to refund a single skill point */
  val RefundCostGold: Long = 100

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

  /** Deallocate a point from a skill (refund). Returns new state or error. */
  def deallocatePoint(state: CombatSkillState, skillId: String): Either[String, CombatSkillState] =
    SkillTrees.getSkillById(skillId) match
      case None => Left("Unknown skill")
      case Some(skill) =>
        val currentLevel = state.getSkillLevel(skillId)
        if currentLevel <= 0 then
          Left(s"${skill.name} has no points to refund")
        else
          val newLevel = currentLevel - 1
          val newAllocatedPoints = 
            if newLevel == 0 then state.allocatedPoints - skillId
            else state.allocatedPoints.updated(skillId, newLevel)
          
          // If skill becomes level 0, unbind it from any slots
          val newBoundSkills = 
            if newLevel == 0 then state.boundSkills.map {
              case Some(id) if id == skillId => None
              case other => other
            }
            else state.boundSkills
          
          Right(state.copy(
            allocatedPoints = newAllocatedPoints,
            boundSkills = newBoundSkills,
            availablePoints = state.availablePoints + 1
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


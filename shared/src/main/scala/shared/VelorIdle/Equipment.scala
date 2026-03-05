package shared.VelorIdle

import upickle.default.{ReadWriter, readwriter}
import scala.util.Random

// ============================================================================
// Equipment System
// ============================================================================

/** Equipment slot types */
enum EquipmentSlot derives ReadWriter:
  case Weapon
  case Armor

/** Equipment quality - determines stat bonus */
enum EquipmentQuality derives ReadWriter:
  case Normal    // Base stats
  case Superior  // +10% to base stats

object EquipmentQuality:
  /** Icon for displaying quality */
  def icon(quality: EquipmentQuality): String = quality match
    case Normal   => ""
    case Superior => "✨"

/** Equipment rarity - determines if item has magical affixes */
enum EquipmentRarity derives ReadWriter:
  case Normal    // No affixes
  case Magical   // Has magical affixes

object EquipmentRarity:
  /** Icon for displaying rarity */
  def icon(rarity: EquipmentRarity): String = rarity match
    case Normal   => ""
    case Magical  => "🔮"

  /** Color class for CSS styling */
  def cssClass(quality: EquipmentQuality, rarity: EquipmentRarity): String = 
    (quality, rarity) match
      case (EquipmentQuality.Superior, EquipmentRarity.Magical) => "rarity-superior-magical"
      case (EquipmentQuality.Superior, EquipmentRarity.Normal)  => "rarity-superior"
      case (EquipmentQuality.Normal, EquipmentRarity.Magical)   => "rarity-magical"
      case _ => "rarity-normal"

/** Magical affixes that can appear on magical equipment */
enum MagicalAffix derives ReadWriter:
  case SkillLevelBonus(treeId: String, amount: Int)  // +X to a skill tree level
  case AttackDamageBonus(amount: Int)   // +X attack damage
  case DefenseBonus(amount: Int)        // +X defense
  case MaxHpBonus(amount: Int)          // +X max HP
  case MaxManaBonus(amount: Int)        // +X max mana
  case ManaRegenBonus(percent: Int)     // +X% mana regeneration

object MagicalAffix:
  /** Human-readable description of an affix */
  def description(affix: MagicalAffix): String = affix match
    case SkillLevelBonus(treeId, amt) => 
      val treeName = SkillTrees.all.find(_.id == treeId).map(_.name).getOrElse(treeId)
      s"+$amt to $treeName"
    case AttackDamageBonus(amt)  => s"+$amt Attack Damage"
    case DefenseBonus(amt)       => s"+$amt Defense"
    case MaxHpBonus(amt)         => s"+$amt Max HP"
    case MaxManaBonus(amt)       => s"+$amt Max Mana"
    case ManaRegenBonus(pct)     => s"+$pct% Mana Regen"

/** Base stats for equipment */
case class EquipmentBaseStats(
  attackDamage: Int = 0,   // Weapon stat: damage per auto-attack
  defense: Int = 0,        // Armor stat: added to defense rating
  maxHpBonus: Int = 0      // Armor stat: added to max HP
) derives ReadWriter

/** An equipment definition (template) */
case class EquipmentDef(
  id: String,
  name: String,
  icon: String,
  slot: EquipmentSlot,
  tier: Int,                    // 1=Bronze, 2=Iron, 3=Steel, 4=Mithril
  levelRequired: Int,           // Adventure level required to equip
  baseStats: EquipmentBaseStats
) derives ReadWriter

/** An actual equipment instance with quality, rarity, and affixes */
case class EquipmentInstance(
  defId: String,           // Reference to EquipmentDef.id
  quality: EquipmentQuality,
  rarity: EquipmentRarity,
  affixes: Vector[MagicalAffix] = Vector.empty,
  instanceId: Long = 0L    // Unique ID for this specific item
) derives ReadWriter:

  /** Get the definition for this equipment */
  def definition: Option[EquipmentDef] = EquipmentDefs.byId.get(defId)

  /** Calculate effective attack damage (base + quality modifier + affixes) */
  def attackDamage: Int = definition.map { d =>
    val base = d.baseStats.attackDamage
    val qualityMod = if quality == EquipmentQuality.Superior then (base * 0.1).toInt else 0
    val affixMod = affixes.collect { case MagicalAffix.AttackDamageBonus(amt) => amt }.sum
    base + qualityMod + affixMod
  }.getOrElse(0)

  /** Calculate effective defense (base + quality modifier + affixes) */
  def defense: Int = definition.map { d =>
    val base = d.baseStats.defense
    val qualityMod = if quality == EquipmentQuality.Superior then (base * 0.1).toInt else 0
    val affixMod = affixes.collect { case MagicalAffix.DefenseBonus(amt) => amt }.sum
    base + qualityMod + affixMod
  }.getOrElse(0)

  /** Calculate effective max HP bonus (base + quality modifier + affixes) */
  def maxHpBonus: Int = definition.map { d =>
    val base = d.baseStats.maxHpBonus
    val qualityMod = if quality == EquipmentQuality.Superior then (base * 0.1).toInt else 0
    val affixMod = affixes.collect { case MagicalAffix.MaxHpBonus(amt) => amt }.sum
    base + qualityMod + affixMod
  }.getOrElse(0)

  /** Calculate max mana bonus from affixes */
  def maxManaBonus: Int = affixes.collect { case MagicalAffix.MaxManaBonus(amt) => amt }.sum

  /** Calculate mana regeneration percentage bonus from affixes */
  def manaRegenBonus: Int = affixes.collect { case MagicalAffix.ManaRegenBonus(pct) => pct }.sum

  /** Get skill level bonuses from affixes */
  def skillBonuses: Map[String, Int] =
    affixes.collect { case MagicalAffix.SkillLevelBonus(treeId, amt) => treeId -> amt }.toMap

  /** Display name with quality and rarity indicators */
  def displayName: String = definition.map { d =>
    val qualityPrefix = EquipmentQuality.icon(quality)
    val rarityPrefix = EquipmentRarity.icon(rarity)
    val prefix = (qualityPrefix + rarityPrefix).trim
    if prefix.isEmpty then d.name else s"$prefix ${d.name}"
  }.getOrElse("Unknown Equipment")
  
  /** CSS class based on quality and rarity */
  def cssClass: String = EquipmentRarity.cssClass(quality, rarity)

/** Equipment definitions (templates) */
object EquipmentDefs:
  // ============================================================================
  // Weapons
  // ============================================================================

  val bronzeSword: EquipmentDef = EquipmentDef(
    id = "bronze_sword",
    name = "Bronze Sword",
    icon = "🗡️",
    slot = EquipmentSlot.Weapon,
    tier = 1,
    levelRequired = 1,
    baseStats = EquipmentBaseStats(attackDamage = 3)
  )

  val ironSword: EquipmentDef = EquipmentDef(
    id = "iron_sword",
    name = "Iron Sword",
    icon = "🗡️",
    slot = EquipmentSlot.Weapon,
    tier = 2,
    levelRequired = 10,
    baseStats = EquipmentBaseStats(attackDamage = 6)
  )

  val steelSword: EquipmentDef = EquipmentDef(
    id = "steel_sword",
    name = "Steel Sword",
    icon = "🗡️",
    slot = EquipmentSlot.Weapon,
    tier = 3,
    levelRequired = 25,
    baseStats = EquipmentBaseStats(attackDamage = 10)
  )

  val mithrilSword: EquipmentDef = EquipmentDef(
    id = "mithril_sword",
    name = "Mithril Sword",
    icon = "🗡️",
    slot = EquipmentSlot.Weapon,
    tier = 4,
    levelRequired = 45,
    baseStats = EquipmentBaseStats(attackDamage = 15)
  )

  // ============================================================================
  // Armor
  // ============================================================================

  val bronzeArmor: EquipmentDef = EquipmentDef(
    id = "bronze_armor",
    name = "Bronze Armor",
    icon = "🛡️",
    slot = EquipmentSlot.Armor,
    tier = 1,
    levelRequired = 1,
    baseStats = EquipmentBaseStats(defense = 2, maxHpBonus = 10)
  )

  val ironArmor: EquipmentDef = EquipmentDef(
    id = "iron_armor",
    name = "Iron Armor",
    icon = "🛡️",
    slot = EquipmentSlot.Armor,
    tier = 2,
    levelRequired = 10,
    baseStats = EquipmentBaseStats(defense = 5, maxHpBonus = 25)
  )

  val steelArmor: EquipmentDef = EquipmentDef(
    id = "steel_armor",
    name = "Steel Armor",
    icon = "🛡️",
    slot = EquipmentSlot.Armor,
    tier = 3,
    levelRequired = 25,
    baseStats = EquipmentBaseStats(defense = 10, maxHpBonus = 50)
  )

  val mithrilArmor: EquipmentDef = EquipmentDef(
    id = "mithril_armor",
    name = "Mithril Armor",
    icon = "🛡️",
    slot = EquipmentSlot.Armor,
    tier = 4,
    levelRequired = 45,
    baseStats = EquipmentBaseStats(defense = 18, maxHpBonus = 100)
  )

  // ============================================================================
  // All Equipment
  // ============================================================================

  val allWeapons: Vector[EquipmentDef] = Vector(bronzeSword, ironSword, steelSword, mithrilSword)
  val allArmor: Vector[EquipmentDef] = Vector(bronzeArmor, ironArmor, steelArmor, mithrilArmor)
  val all: Vector[EquipmentDef] = allWeapons ++ allArmor

  val byId: Map[String, EquipmentDef] = all.map(e => e.id -> e).toMap

/** Equipment crafting and generation logic */
object EquipmentCrafting:
  // Chances for quality and rarity
  val SuperiorChance: Double = 0.15  // 15% chance for superior quality
  val MagicalChance: Double = 0.10   // 10% chance for magical rarity

  /** Roll for equipment quality (Normal or Superior) */
  def rollQuality(random: Random): EquipmentQuality =
    if random.nextDouble() < SuperiorChance then EquipmentQuality.Superior
    else EquipmentQuality.Normal

  /** Roll for equipment rarity (Normal or Magical) - independent of quality */
  def rollRarity(random: Random): EquipmentRarity =
    if random.nextDouble() < MagicalChance then EquipmentRarity.Magical
    else EquipmentRarity.Normal

  /** Generate magical affixes for an equipment piece */
  def rollAffixes(def_ : EquipmentDef, random: Random): Vector[MagicalAffix] =
    // Magical items get 1-2 affixes
    val numAffixes = 1 + random.nextInt(2)

    val possibleAffixes = def_.slot match
      case EquipmentSlot.Weapon =>
        Vector(
          () => MagicalAffix.AttackDamageBonus(1 + random.nextInt(def_.tier * 2)),
          () => MagicalAffix.MaxManaBonus(5 + random.nextInt(def_.tier * 5)),
          () => randomSkillBonus(random)
        )
      case EquipmentSlot.Armor =>
        Vector(
          () => MagicalAffix.DefenseBonus(1 + random.nextInt(def_.tier * 2)),
          () => MagicalAffix.MaxHpBonus(5 + random.nextInt(def_.tier * 10)),
          () => MagicalAffix.MaxManaBonus(5 + random.nextInt(def_.tier * 5)),
          () => randomSkillBonus(random)
        )

    // Pick random unique affixes
    random.shuffle(possibleAffixes).take(numAffixes).map(_.apply())

  /** Generate a random skill level bonus affix */
  private def randomSkillBonus(random: Random): MagicalAffix =
    val trees = SkillTrees.all
    val tree = trees(random.nextInt(trees.length))
    MagicalAffix.SkillLevelBonus(tree.id, 1 + random.nextInt(2))

  /** Create an equipment instance from a definition */
  def createEquipment(defId: String, instanceId: Long, random: Random): Option[EquipmentInstance] =
    EquipmentDefs.byId.get(defId).map { def_ =>
      val quality = rollQuality(random)
      val rarity = rollRarity(random)
      val affixes = if rarity == EquipmentRarity.Magical then rollAffixes(def_, random) else Vector.empty
      EquipmentInstance(defId, quality, rarity, affixes, instanceId)
    }

/** Equipment slots in the player's gear */
case class EquipmentSlots(
  weapon: Option[EquipmentInstance] = None,
  armor: Option[EquipmentInstance] = None
) derives ReadWriter:

  /** Total attack damage from equipped weapon */
  def totalAttackDamage: Int = weapon.map(_.attackDamage).getOrElse(0)

  /** Total defense from equipped armor */
  def totalDefense: Int = armor.map(_.defense).getOrElse(0)

  /** Total max HP bonus from equipment */
  def totalMaxHpBonus: Int = armor.map(_.maxHpBonus).getOrElse(0)

  /** Total max mana bonus from equipment */
  def totalMaxManaBonus: Int =
    weapon.map(_.maxManaBonus).getOrElse(0) + armor.map(_.maxManaBonus).getOrElse(0)

  /** Total mana regeneration percentage bonus from equipment */
  def totalManaRegenPercent: Int =
    weapon.map(_.manaRegenBonus).getOrElse(0) + armor.map(_.manaRegenBonus).getOrElse(0)

  /** Combined skill bonuses from all equipment */
  def allSkillBonuses: Map[String, Int] =
    val weaponBonuses = weapon.map(_.skillBonuses).getOrElse(Map.empty)
    val armorBonuses = armor.map(_.skillBonuses).getOrElse(Map.empty)
    (weaponBonuses.toSeq ++ armorBonuses.toSeq)
      .groupMapReduce(_._1)(_._2)(_ + _)

object EquipmentSlots:
  val empty: EquipmentSlots = EquipmentSlots()

/** Crafting action for equipment */
case class EquipmentCraftingAction(
  id: String,
  name: String,
  icon: String,
  levelRequired: Int,         // Smithing level required
  xpGain: Int,
  timeSeconds: Double,
  inputs: Vector[(Item, Int)],
  outputDefId: String         // Equipment definition ID
) derives ReadWriter

object EquipmentCraftingActions:
  // Bronze tier (Smithing 1)
  val craftBronzeSword: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_bronze_sword",
    name = "Forge Bronze Sword",
    icon = "🗡️",
    levelRequired = 1,
    xpGain = 25,
    timeSeconds = 6.0,
    inputs = Vector((Item.BronzeBar, 2)),
    outputDefId = "bronze_sword"
  )

  val craftBronzeArmor: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_bronze_armor",
    name = "Forge Bronze Armor",
    icon = "🛡️",
    levelRequired = 5,
    xpGain = 40,
    timeSeconds = 8.0,
    inputs = Vector((Item.BronzeBar, 4)),
    outputDefId = "bronze_armor"
  )

  // Iron tier (Smithing 15)
  val craftIronSword: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_iron_sword",
    name = "Forge Iron Sword",
    icon = "🗡️",
    levelRequired = 15,
    xpGain = 50,
    timeSeconds = 7.0,
    inputs = Vector((Item.IronBar, 2)),
    outputDefId = "iron_sword"
  )

  val craftIronArmor: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_iron_armor",
    name = "Forge Iron Armor",
    icon = "🛡️",
    levelRequired = 20,
    xpGain = 75,
    timeSeconds = 9.0,
    inputs = Vector((Item.IronBar, 4)),
    outputDefId = "iron_armor"
  )

  // Steel tier (Smithing 30)
  val craftSteelSword: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_steel_sword",
    name = "Forge Steel Sword",
    icon = "🗡️",
    levelRequired = 30,
    xpGain = 100,
    timeSeconds = 8.0,
    inputs = Vector((Item.SteelBar, 2)),
    outputDefId = "steel_sword"
  )

  val craftSteelArmor: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_steel_armor",
    name = "Forge Steel Armor",
    icon = "🛡️",
    levelRequired = 35,
    xpGain = 150,
    timeSeconds = 10.0,
    inputs = Vector((Item.SteelBar, 4)),
    outputDefId = "steel_armor"
  )

  // Mithril tier (Smithing 50)
  val craftMithrilSword: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_mithril_sword",
    name = "Forge Mithril Sword",
    icon = "🗡️",
    levelRequired = 50,
    xpGain = 180,
    timeSeconds = 10.0,
    inputs = Vector((Item.MithrilBar, 2)),
    outputDefId = "mithril_sword"
  )

  val craftMithrilArmor: EquipmentCraftingAction = EquipmentCraftingAction(
    id = "craft_mithril_armor",
    name = "Forge Mithril Armor",
    icon = "🛡️",
    levelRequired = 55,
    xpGain = 250,
    timeSeconds = 12.0,
    inputs = Vector((Item.MithrilBar, 4)),
    outputDefId = "mithril_armor"
  )

  val all: Vector[EquipmentCraftingAction] = Vector(
    craftBronzeSword, craftBronzeArmor,
    craftIronSword, craftIronArmor,
    craftSteelSword, craftSteelArmor,
    craftMithrilSword, craftMithrilArmor
  )

  val byId: Map[String, EquipmentCraftingAction] = all.map(a => a.id -> a).toMap


package shared.VelorIdle

import upickle.default.{ReadWriter, readwriter}

// ============================================================================
// Skills
// ============================================================================

enum Skill derives ReadWriter:
  case Woodcutting
  case Mining
  case Fishing
  case Herbalism
  case Cooking
  case Smithing
  case Alchemy
  case Summoning
  case Thieving
  case Astrology

/** Skill metadata: icon and display name */
case class SkillData(icon: String, displayName: String)

object Skill:
  val gathering: Set[Skill] = Set(Woodcutting, Mining, Fishing, Herbalism)
  val processing: Set[Skill] = Set(Cooking, Smithing, Alchemy, Summoning)
  val special: Set[Skill] = Set(Thieving, Astrology)

  def isGathering(skill: Skill): Boolean = gathering.contains(skill)
  def isProcessing(skill: Skill): Boolean = processing.contains(skill)

  /** All skill metadata in one place */
  private val metadata: Map[Skill, SkillData] = Map(
    Woodcutting -> SkillData("🪓", "Woodcutting"),
    Mining      -> SkillData("⛏️", "Mining"),
    Fishing     -> SkillData("🎣", "Fishing"),
    Herbalism   -> SkillData("🌿", "Herbalism"),
    Cooking     -> SkillData("🍳", "Cooking"),
    Smithing    -> SkillData("🔨", "Smithing"),
    Alchemy     -> SkillData("🧪", "Alchemy"),
    Summoning   -> SkillData("📜", "Summoning"),
    Thieving    -> SkillData("🗡️", "Thieving"),
    Astrology   -> SkillData("⭐", "Astrology")
  )

  private def get(skill: Skill): SkillData =
    metadata.getOrElse(skill, SkillData("❓", skill.toString))

  def icon(skill: Skill): String = get(skill).icon
  def displayName(skill: Skill): String = get(skill).displayName

// ============================================================================
// Skill State
// ============================================================================

case class SkillState(
  level: Int = 1,
  xp: Long = 0,
  masteryLevel: Int = 0,
  masteryXp: Long = 0
) derives ReadWriter

object SkillState:
  val initial: SkillState = SkillState()

  /** XP required to go from level N to level N+1 (linear: N * 100) */
  def xpForLevel(level: Int): Long = level * 100L

  /** Total XP required to reach a given level from level 1 */
  def totalXpForLevel(level: Int): Long =
    if level <= 1 then 0L
    else (1 until level).map(l => xpForLevel(l)).sum

  /** Calculate level from total XP */
  def levelFromXp(totalXp: Long): Int =
    var level = 1
    var xpNeeded = 0L
    while level < 99 && totalXp >= xpNeeded + xpForLevel(level) do
      xpNeeded += xpForLevel(level)
      level += 1
    level

  /** XP progress within current level (0.0 to 1.0) */
  def xpProgress(state: SkillState): Double =
    if state.level >= 99 then 1.0
    else
      val currentLevelXp = totalXpForLevel(state.level)
      val nextLevelXp = totalXpForLevel(state.level + 1)
      val xpInLevel = state.xp - currentLevelXp
      val xpNeeded = nextLevelXp - currentLevelXp
      if xpNeeded <= 0 then 1.0
      else (xpInLevel.toDouble / xpNeeded).min(1.0).max(0.0)

// ============================================================================
// Action State (individual action mastery)
// ============================================================================

case class ActionState(
  level: Int = 1,
  xp: Long = 0
) derives ReadWriter

object ActionState:
  val initial: ActionState = ActionState()

  /** XP required to go from level N to level N+1 (slower progression than skills) */
  def xpForLevel(level: Int): Long = level * 50L

  /** Total XP required to reach a given level from level 1 */
  def totalXpForLevel(level: Int): Long =
    if level <= 1 then 0L
    else (1 until level).map(l => xpForLevel(l)).sum

  /** Calculate level from total XP */
  def levelFromXp(totalXp: Long): Int =
    var level = 1
    var xpNeeded = 0L
    while level < 99 && totalXp >= xpNeeded + xpForLevel(level) do
      xpNeeded += xpForLevel(level)
      level += 1
    level

  /** XP progress within current level (0.0 to 1.0) */
  def xpProgress(state: ActionState): Double =
    if state.level >= 99 then 1.0
    else
      val currentLevelXp = totalXpForLevel(state.level)
      val nextLevelXp = totalXpForLevel(state.level + 1)
      val xpInLevel = state.xp - currentLevelXp
      val xpNeeded = nextLevelXp - currentLevelXp
      if xpNeeded <= 0 then 1.0
      else (xpInLevel.toDouble / xpNeeded).min(1.0).max(0.0)

// ============================================================================
// Items
// ============================================================================

enum Item derives ReadWriter:
  // Woodcutting outputs
  case NormalLogs, OakLogs, WillowLogs, MapleLogs, YewLogs, MagicLogs
  // Mining outputs
  case CopperOre, TinOre, IronOre, Coal, GoldOre, MithrilOre
  // Fishing outputs
  case RawShrimp, RawSardine, RawTrout, RawSalmon, RawLobster, RawSwordfish
  // Herbalism outputs
  case GuamLeaf, Marrentill, Tarromin, Harralander, RanarrWeed, IritLeaf, Kwuarm, Cadantine
  // Cooked fish
  case CookedShrimp, CookedSardine, CookedTrout, CookedSalmon, CookedLobster, CookedSwordfish
  case BurntFish
  // Smithing outputs
  case BronzeBar, IronBar, SteelBar, GoldBar, MithrilBar
  // Alchemy outputs (potions)
  case WoodcuttingPotion, MiningPotion, FishingPotion, HerbalismPotion
  case CookingPotion, SmithingPotion, ThievingPotion, AstrologyPotion
  // Secondary ingredient for potions
  case Vial
  // Rare drops
  case BirdNest, Gem

/** Item metadata: icon, display name, and sell value */
case class ItemData(icon: String, displayName: String, sellValue: Int)

object Item:
  /** All item metadata in one place - add new items here */
  private val metadata: Map[Item, ItemData] = Map(
    // Logs
    Item.NormalLogs -> ItemData("🪵", "Normal Logs", 2),
    Item.OakLogs -> ItemData("🪵", "Oak Logs", 5),
    Item.WillowLogs -> ItemData("🪵", "Willow Logs", 10),
    Item.MapleLogs -> ItemData("🍁", "Maple Logs", 20),
    Item.YewLogs -> ItemData("🌲", "Yew Logs", 40),
    Item.MagicLogs -> ItemData("✨", "Magic Logs", 80),
    // Ores
    Item.CopperOre -> ItemData("🟤", "Copper Ore", 3),
    Item.TinOre -> ItemData("⚪", "Tin Ore", 3),
    Item.IronOre -> ItemData("🔶", "Iron Ore", 8),
    Item.Coal -> ItemData("⬛", "Coal", 12),
    Item.GoldOre -> ItemData("🟡", "Gold Ore", 30),
    Item.MithrilOre -> ItemData("🔵", "Mithril Ore", 60),
    // Raw fish
    Item.RawShrimp -> ItemData("🦐", "Raw Shrimp", 1),
    Item.RawSardine -> ItemData("🐟", "Raw Sardine", 3),
    Item.RawTrout -> ItemData("🐟", "Raw Trout", 8),
    Item.RawSalmon -> ItemData("🐟", "Raw Salmon", 15),
    Item.RawLobster -> ItemData("🦞", "Raw Lobster", 30),
    Item.RawSwordfish -> ItemData("🐠", "Raw Swordfish", 50),
    // Herbs
    Item.GuamLeaf -> ItemData("🌿", "Guam Leaf", 2),
    Item.Marrentill -> ItemData("🌿", "Marrentill", 5),
    Item.Tarromin -> ItemData("🌿", "Tarromin", 10),
    Item.Harralander -> ItemData("🌿", "Harralander", 20),
    Item.RanarrWeed -> ItemData("🌿", "Ranarr Weed", 40),
    Item.IritLeaf -> ItemData("🌿", "Irit Leaf", 60),
    Item.Kwuarm -> ItemData("🌿", "Kwuarm", 80),
    Item.Cadantine -> ItemData("🌿", "Cadantine", 100),
    // Cooked fish
    Item.CookedShrimp -> ItemData("🍤", "Cooked Shrimp", 3),
    Item.CookedSardine -> ItemData("🍣", "Cooked Sardine", 8),
    Item.CookedTrout -> ItemData("🍣", "Cooked Trout", 20),
    Item.CookedSalmon -> ItemData("🍣", "Cooked Salmon", 40),
    Item.CookedLobster -> ItemData("🦞", "Cooked Lobster", 80),
    Item.CookedSwordfish -> ItemData("🍣", "Cooked Swordfish", 130),
    Item.BurntFish -> ItemData("💨", "Burnt Fish", 1),
    // Bars
    Item.BronzeBar -> ItemData("🟫", "Bronze Bar", 10),
    Item.IronBar -> ItemData("⬜", "Iron Bar", 25),
    Item.SteelBar -> ItemData("🔲", "Steel Bar", 50),
    Item.GoldBar -> ItemData("🟨", "Gold Bar", 80),
    Item.MithrilBar -> ItemData("🟦", "Mithril Bar", 150),
    // Potions
    Item.WoodcuttingPotion -> ItemData("🧪", "Woodcutting Potion", 50),
    Item.MiningPotion -> ItemData("🧪", "Mining Potion", 50),
    Item.FishingPotion -> ItemData("🧪", "Fishing Potion", 50),
    Item.HerbalismPotion -> ItemData("🧪", "Herbalism Potion", 50),
    Item.CookingPotion -> ItemData("🧪", "Cooking Potion", 75),
    Item.SmithingPotion -> ItemData("🧪", "Smithing Potion", 75),
    Item.ThievingPotion -> ItemData("🧪", "Thieving Potion", 100),
    Item.AstrologyPotion -> ItemData("🧪", "Astrology Potion", 100),
    // Secondary ingredients
    Item.Vial -> ItemData("🫙", "Vial", 5),
    // Rare drops
    Item.BirdNest -> ItemData("🪺", "Bird Nest", 100),
    Item.Gem -> ItemData("💎", "Gem", 200)
  )

  private def get(item: Item): ItemData =
    metadata.getOrElse(item, ItemData("❓", item.toString, 0))

  def icon(item: Item): String = get(item).icon
  def displayName(item: Item): String = get(item).displayName
  def sellValue(item: Item): Int = get(item).sellValue

// ============================================================================
// Actions (what player can do within a skill)
// ============================================================================

case class GatheringAction(
  id: String,
  name: String,
  icon: String,
  levelRequired: Int,
  xpGain: Int,
  timeSeconds: Double,
  output: Item,
  rareOutput: Option[(Item, Double)] = None // (item, chance 0.0-1.0)
) derives ReadWriter

object GatheringActions:
  // Woodcutting
  val woodcutting: Vector[GatheringAction] = Vector(
    GatheringAction("normal_tree", "Normal Tree", "🌳", 1, 10, 3.0, Item.NormalLogs, Some((Item.BirdNest, 0.02))),
    GatheringAction("oak_tree", "Oak Tree", "🌳", 10, 25, 4.0, Item.OakLogs, Some((Item.BirdNest, 0.02))),
    GatheringAction("willow_tree", "Willow Tree", "🌳", 25, 45, 5.0, Item.WillowLogs, Some((Item.BirdNest, 0.02))),
    GatheringAction("maple_tree", "Maple Tree", "🍁", 40, 80, 6.0, Item.MapleLogs, Some((Item.BirdNest, 0.03))),
    GatheringAction("yew_tree", "Yew Tree", "🌲", 55, 140, 7.0, Item.YewLogs, Some((Item.BirdNest, 0.03))),
    GatheringAction("magic_tree", "Magic Tree", "✨", 70, 250, 8.0, Item.MagicLogs, Some((Item.BirdNest, 0.05)))
  )

  // Mining
  val mining: Vector[GatheringAction] = Vector(
    GatheringAction("copper_rock", "Copper Rock", "🪨", 1, 12, 3.5, Item.CopperOre, Some((Item.Gem, 0.01))),
    GatheringAction("tin_rock", "Tin Rock", "🪨", 1, 12, 3.5, Item.TinOre, Some((Item.Gem, 0.01))),
    GatheringAction("iron_rock", "Iron Rock", "🪨", 15, 30, 4.5, Item.IronOre, Some((Item.Gem, 0.02))),
    GatheringAction("coal_rock", "Coal Rock", "🪨", 30, 50, 5.5, Item.Coal, Some((Item.Gem, 0.02))),
    GatheringAction("gold_rock", "Gold Rock", "🪨", 45, 90, 6.5, Item.GoldOre, Some((Item.Gem, 0.05))),
    GatheringAction("mithril_rock", "Mithril Rock", "🪨", 60, 160, 7.5, Item.MithrilOre, Some((Item.Gem, 0.03)))
  )

  // Fishing
  val fishing: Vector[GatheringAction] = Vector(
    GatheringAction("shrimp", "Shrimp", "🦐", 1, 8, 2.5, Item.RawShrimp),
    GatheringAction("sardine", "Sardine", "🐟", 10, 18, 3.0, Item.RawSardine),
    GatheringAction("trout", "Trout", "🐟", 20, 40, 4.0, Item.RawTrout),
    GatheringAction("salmon", "Salmon", "🐟", 35, 70, 5.0, Item.RawSalmon),
    GatheringAction("lobster", "Lobster", "🦞", 50, 110, 6.0, Item.RawLobster),
    GatheringAction("swordfish", "Swordfish", "🐠", 65, 180, 7.0, Item.RawSwordfish)
  )

  // Herbalism
  val herbalism: Vector[GatheringAction] = Vector(
    GatheringAction("guam", "Guam", "🌿", 1, 9, 3.0, Item.GuamLeaf),
    GatheringAction("marrentill", "Marrentill", "🌿", 10, 20, 3.5, Item.Marrentill),
    GatheringAction("tarromin", "Tarromin", "🌿", 20, 35, 4.0, Item.Tarromin),
    GatheringAction("harralander", "Harralander", "🌿", 35, 60, 4.5, Item.Harralander),
    GatheringAction("ranarr", "Ranarr", "🌿", 50, 100, 5.5, Item.RanarrWeed),
    GatheringAction("irit", "Irit", "🌿", 60, 150, 6.0, Item.IritLeaf),
    GatheringAction("kwuarm", "Kwuarm", "🌿", 70, 200, 6.5, Item.Kwuarm),
    GatheringAction("cadantine", "Cadantine", "🌿", 80, 280, 7.0, Item.Cadantine)
  )

  def forSkill(skill: Skill): Vector[GatheringAction] = skill match
    case Skill.Woodcutting => woodcutting
    case Skill.Mining => mining
    case Skill.Fishing => fishing
    case Skill.Herbalism => herbalism
    case _ => Vector.empty

// ============================================================================
// Processing Actions
// ============================================================================

case class ProcessingAction(
  id: String,
  name: String,
  icon: String,
  levelRequired: Int,
  xpGain: Int,
  timeSeconds: Double,
  inputs: Vector[(Item, Int)],  // (item, count) pairs
  output: Item,
  outputCount: Int = 1,
  burnChance: Option[Double] = None,  // For cooking - base burn chance
  burnOutput: Option[Item] = None
) derives ReadWriter

object ProcessingActions:
  // Cooking recipes
  val cooking: Vector[ProcessingAction] = Vector(
    ProcessingAction("cook_shrimp", "Cook Shrimp", "🍤", 1, 10, 2.5,
      Vector((Item.RawShrimp, 1)), Item.CookedShrimp, burnChance = Some(0.30), burnOutput = Some(Item.BurntFish)),
    ProcessingAction("cook_sardine", "Cook Sardine", "🍣", 10, 20, 3.0,
      Vector((Item.RawSardine, 1)), Item.CookedSardine, burnChance = Some(0.25), burnOutput = Some(Item.BurntFish)),
    ProcessingAction("cook_trout", "Cook Trout", "🍣", 20, 40, 3.5,
      Vector((Item.RawTrout, 1)), Item.CookedTrout, burnChance = Some(0.25), burnOutput = Some(Item.BurntFish)),
    ProcessingAction("cook_salmon", "Cook Salmon", "🍣", 35, 65, 4.0,
      Vector((Item.RawSalmon, 1)), Item.CookedSalmon, burnChance = Some(0.20), burnOutput = Some(Item.BurntFish)),
    ProcessingAction("cook_lobster", "Cook Lobster", "🦞", 50, 100, 4.5,
      Vector((Item.RawLobster, 1)), Item.CookedLobster, burnChance = Some(0.15), burnOutput = Some(Item.BurntFish)),
    ProcessingAction("cook_swordfish", "Cook Swordfish", "🍣", 65, 150, 5.0,
      Vector((Item.RawSwordfish, 1)), Item.CookedSwordfish, burnChance = Some(0.10), burnOutput = Some(Item.BurntFish))
  )

  // Smithing recipes (bars)
  val smithing: Vector[ProcessingAction] = Vector(
    ProcessingAction("smelt_bronze", "Smelt Bronze Bar", "🟫", 1, 15, 4.0,
      Vector((Item.CopperOre, 1), (Item.TinOre, 1)), Item.BronzeBar),
    ProcessingAction("smelt_iron", "Smelt Iron Bar", "⬜", 15, 30, 5.0,
      Vector((Item.IronOre, 1)), Item.IronBar),
    ProcessingAction("smelt_steel", "Smelt Steel Bar", "🔲", 30, 55, 6.0,
      Vector((Item.IronOre, 1), (Item.Coal, 2)), Item.SteelBar),
    ProcessingAction("smelt_gold", "Smelt Gold Bar", "🟨", 45, 80, 5.5,
      Vector((Item.GoldOre, 1)), Item.GoldBar),
    ProcessingAction("smelt_mithril", "Smelt Mithril Bar", "🟦", 60, 130, 7.0,
      Vector((Item.MithrilOre, 1), (Item.Coal, 4)), Item.MithrilBar)
  )

  // Alchemy recipes (potions)
  val alchemy: Vector[ProcessingAction] = Vector(
    ProcessingAction("brew_woodcutting", "Brew Woodcutting Potion", "🪓", 1, 20, 4.0,
      Vector((Item.GuamLeaf, 2), (Item.Vial, 1)), Item.WoodcuttingPotion),
    ProcessingAction("brew_mining", "Brew Mining Potion", "⛏️", 10, 30, 4.5,
      Vector((Item.Marrentill, 2), (Item.Vial, 1)), Item.MiningPotion),
    ProcessingAction("brew_fishing", "Brew Fishing Potion", "🎣", 20, 45, 5.0,
      Vector((Item.Tarromin, 2), (Item.Vial, 1)), Item.FishingPotion),
    ProcessingAction("brew_herbalism", "Brew Herbalism Potion", "🌿", 30, 65, 5.5,
      Vector((Item.Harralander, 2), (Item.Vial, 1)), Item.HerbalismPotion),
    ProcessingAction("brew_cooking", "Brew Cooking Potion", "🍳", 40, 90, 6.0,
      Vector((Item.RanarrWeed, 2), (Item.Vial, 1)), Item.CookingPotion),
    ProcessingAction("brew_smithing", "Brew Smithing Potion", "🔨", 50, 120, 6.5,
      Vector((Item.IritLeaf, 2), (Item.Vial, 1)), Item.SmithingPotion),
    ProcessingAction("brew_thieving", "Brew Thieving Potion", "🗡️", 65, 160, 7.0,
      Vector((Item.Kwuarm, 2), (Item.Vial, 1)), Item.ThievingPotion),
    ProcessingAction("brew_astrology", "Brew Astrology Potion", "⭐", 80, 220, 8.0,
      Vector((Item.Cadantine, 2), (Item.Vial, 1)), Item.AstrologyPotion)
  )

  def forSkill(skill: Skill): Vector[ProcessingAction] = skill match
    case Skill.Cooking => cooking
    case Skill.Smithing => smithing
    case Skill.Alchemy => alchemy
    case _ => Vector.empty

// ============================================================================
// Inventory
// ============================================================================

case class ItemStack(
  item: Item,
  count: Long
) derives ReadWriter

case class Inventory(
  slots: Vector[Option[ItemStack]],
  maxSlots: Int
) derives ReadWriter:

  def usedSlots: Int = slots.count(_.isDefined)
  def freeSlots: Int = maxSlots - usedSlots
  def isFull: Boolean = freeSlots == 0

  /** Find existing stack of an item, or first empty slot */
  def findSlotFor(item: Item): Option[Int] =
    slots.indexWhere(_.exists(_.item == item)) match
      case -1 => slots.indexWhere(_.isEmpty) match
        case -1 => None
        case idx => Some(idx)
      case idx => Some(idx)

  /** Add items to inventory. Returns updated inventory and any overflow */
  def addItem(item: Item, count: Long): (Inventory, Long) =
    findSlotFor(item) match
      case None => (this, count) // No space, all overflow
      case Some(idx) =>
        val existing = slots(idx).map(_.count).getOrElse(0L)
        val newStack = ItemStack(item, existing + count)
        val updated = copy(slots = slots.updated(idx, Some(newStack)))
        (updated, 0L)

  /** Remove items from inventory. Returns updated inventory and actual removed count */
  def removeItem(item: Item, count: Long): (Inventory, Long) =
    slots.indexWhere(_.exists(_.item == item)) match
      case -1 => (this, 0L)
      case idx =>
        val stack = slots(idx).get
        val toRemove = count.min(stack.count)
        val remaining = stack.count - toRemove
        val newSlot = if remaining <= 0 then None else Some(stack.copy(count = remaining))
        (copy(slots = slots.updated(idx, newSlot)), toRemove)

  def getCount(item: Item): Long =
    slots.flatten.find(_.item == item).map(_.count).getOrElse(0L)

object Inventory:
  val StartingSlots = 12
  val MaxSlots = 100

  def empty(slots: Int = StartingSlots): Inventory =
    Inventory(Vector.fill(slots)(None), slots)

  /** Cost to upgrade by 4 slots from current amount.
    * Uses quadratic scaling: base * (upgradeNumber^1.8)
    * Caps at 5 million gold.
    */
  def nextUpgradeCost(currentSlots: Int): Option[Long] =
    if currentSlots >= MaxSlots then None
    else
      // Each upgrade adds 4 slots
      // upgradeNumber = how many upgrades have been done (0 = still at 12 slots)
      val upgradeNumber = (currentSlots - StartingSlots) / 4
      // Base cost 100, scaling with upgradeNumber^1.8
      val cost = (100 * Math.pow(upgradeNumber + 1, 1.8)).toLong.min(5_000_000L)
      Some(cost)

  /** Legacy method for compatibility */
  def upgradeCost(currentSlots: Int, targetSlots: Int): Option[Int] =
    nextUpgradeCost(currentSlots).map(_.toInt)

// ============================================================================
// Potion System
// ============================================================================

/** Effect granted by drinking a potion */
enum PotionEffect derives ReadWriter:
  case SkillBoost(skill: Skill, bonusPercent: Double)  // +X% XP and speed for a skill

object PotionEffect:
  /** Get the effect for a potion item */
  def forPotion(potion: Item): Option[PotionEffect] = potion match
    case Item.WoodcuttingPotion => Some(SkillBoost(Skill.Woodcutting, 0.10))
    case Item.MiningPotion      => Some(SkillBoost(Skill.Mining, 0.10))
    case Item.FishingPotion     => Some(SkillBoost(Skill.Fishing, 0.10))
    case Item.HerbalismPotion   => Some(SkillBoost(Skill.Herbalism, 0.10))
    case Item.CookingPotion     => Some(SkillBoost(Skill.Cooking, 0.10))
    case Item.SmithingPotion    => Some(SkillBoost(Skill.Smithing, 0.10))
    case Item.ThievingPotion    => Some(SkillBoost(Skill.Thieving, 0.10))
    case Item.AstrologyPotion   => Some(SkillBoost(Skill.Astrology, 0.10))
    case _ => None

  def isPotion(item: Item): Boolean = forPotion(item).isDefined

  def description(effect: PotionEffect): String = effect match
    case SkillBoost(skill, bonus) =>
      s"+${(bonus * 100).toInt}% ${Skill.displayName(skill)} XP and speed"

/** An active potion effect with remaining actions */
case class ActivePotion(
  potion: Item,
  effect: PotionEffect,
  actionsRemaining: Int
) derives ReadWriter

object ActivePotion:
  val DefaultDuration: Int = 30  // 30 actions per potion

  def fromItem(potion: Item): Option[ActivePotion] =
    PotionEffect.forPotion(potion).map: effect =>
      ActivePotion(potion, effect, DefaultDuration)

/** Tracks equipped/active potions - player can have one active potion */
case class PotionSlots(
  activePotion: Option[ActivePotion]
) derives ReadWriter:

  /** Consume one action's worth of potion. Returns updated slots. */
  def consumeAction: PotionSlots =
    activePotion match
      case None => this
      case Some(active) =>
        val remaining = active.actionsRemaining - 1
        if remaining <= 0 then PotionSlots(None)
        else copy(activePotion = Some(active.copy(actionsRemaining = remaining)))

  /** Check if we have an active boost for a skill */
  def hasBoostFor(skill: Skill): Boolean =
    activePotion.exists:
      case ActivePotion(_, PotionEffect.SkillBoost(s, _), _) => s == skill

  /** Get the XP bonus from active potions for a skill (0.0 to 1.0) */
  def xpBonusFor(skill: Skill): Double =
    activePotion.collect:
      case ActivePotion(_, PotionEffect.SkillBoost(s, bonus), _) if s == skill => bonus
    .getOrElse(0.0)

  /** Get the speed bonus from active potions for a skill (0.0 to 1.0) */
  def speedBonusFor(skill: Skill): Double = xpBonusFor(skill)  // Same bonus for now

object PotionSlots:
  val empty: PotionSlots = PotionSlots(None)

// ============================================================================
// Game State
// ============================================================================

enum ActiveAction derives ReadWriter:
  case Gathering(skill: Skill, action: GatheringAction)
  case Processing(skill: Skill, action: ProcessingAction)
  case Idle

case class VelorIdleGame(
  skills: Map[Skill, SkillState],
  actionLevels: Map[String, ActionState],  // action id -> state
  inventory: Inventory,
  gold: Long,
  currentSkill: Option[Skill],
  activeAction: ActiveAction,
  actionProgress: Double,       // 0.0 to 1.0
  lastTickTime: Long,
  potionSlots: PotionSlots = PotionSlots.empty,  // Active potions
  junkItems: Set[Item] = Set.empty  // Items marked as junk for quick selling
) derives ReadWriter

object VelorIdleGame:
  def newGame(timestamp: Long): VelorIdleGame =
    VelorIdleGame(
      skills = Skill.values.map(s => s -> SkillState.initial).toMap,
      actionLevels = Map.empty,
      inventory = Inventory.empty(),
      gold = 0L,
      currentSkill = None,
      activeAction = ActiveAction.Idle,
      actionProgress = 0.0,
      lastTickTime = timestamp,
      potionSlots = PotionSlots.empty,
      junkItems = Set.empty
    )


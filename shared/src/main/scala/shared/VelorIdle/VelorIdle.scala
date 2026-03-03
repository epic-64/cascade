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
  // Summoning tablets
  case GathererTablet, MinerTablet, FisherTablet, LumberjackTablet
  case ArtisanTablet, HerbalistTablet, AlchemistTablet
  case ThiefTablet, StargazerTablet, MasterTablet
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
    // Summoning tablets
    Item.GathererTablet -> ItemData("📜", "Gatherer Tablet", 50),
    Item.MinerTablet -> ItemData("📜", "Miner Tablet", 80),
    Item.FisherTablet -> ItemData("📜", "Fisher Tablet", 80),
    Item.LumberjackTablet -> ItemData("📜", "Lumberjack Tablet", 80),
    Item.ArtisanTablet -> ItemData("📜", "Artisan Tablet", 100),
    Item.HerbalistTablet -> ItemData("📜", "Herbalist Tablet", 100),
    Item.AlchemistTablet -> ItemData("📜", "Alchemist Tablet", 150),
    Item.ThiefTablet -> ItemData("📜", "Thief Tablet", 200),
    Item.StargazerTablet -> ItemData("📜", "Stargazer Tablet", 250),
    Item.MasterTablet -> ItemData("📜", "Master Tablet", 1000),
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

  // Summoning recipes (tablets) - require large quantities of resources
  val summoning: Vector[ProcessingAction] = Vector(
    ProcessingAction("create_gatherer", "Create Gatherer Tablet", "📜", 1, 50, 6.0,
      Vector((Item.NormalLogs, 100), (Item.CopperOre, 50)), Item.GathererTablet),
    ProcessingAction("create_miner", "Create Miner Tablet", "📜", 10, 80, 7.0,
      Vector((Item.IronOre, 200), (Item.Coal, 100)), Item.MinerTablet),
    ProcessingAction("create_fisher", "Create Fisher Tablet", "📜", 15, 90, 7.0,
      Vector((Item.RawTrout, 150), (Item.RawSalmon, 50)), Item.FisherTablet),
    ProcessingAction("create_lumberjack", "Create Lumberjack Tablet", "📜", 20, 100, 7.5,
      Vector((Item.OakLogs, 200), (Item.WillowLogs, 100)), Item.LumberjackTablet),
    ProcessingAction("create_artisan", "Create Artisan Tablet", "📜", 25, 120, 8.0,
      Vector((Item.BronzeBar, 50), (Item.IronBar, 30)), Item.ArtisanTablet),
    ProcessingAction("create_herbalist", "Create Herbalist Tablet", "📜", 30, 140, 8.0,
      Vector((Item.GuamLeaf, 100), (Item.Tarromin, 50)), Item.HerbalistTablet),
    ProcessingAction("create_alchemist", "Create Alchemist Tablet", "📜", 40, 180, 9.0,
      Vector((Item.WoodcuttingPotion, 5), (Item.MiningPotion, 5)), Item.AlchemistTablet),
    ProcessingAction("create_thief", "Create Thief Tablet", "📜", 50, 220, 9.5,
      Vector((Item.SteelBar, 50)), Item.ThiefTablet),
    ProcessingAction("create_stargazer", "Create Stargazer Tablet", "📜", 60, 280, 10.0,
      Vector((Item.MagicLogs, 100)), Item.StargazerTablet),
    ProcessingAction("create_master", "Create Master Tablet", "📜", 75, 500, 15.0,
      Vector(
        (Item.GathererTablet, 10), (Item.MinerTablet, 10), (Item.FisherTablet, 10),
        (Item.LumberjackTablet, 10), (Item.ArtisanTablet, 10), (Item.HerbalistTablet, 10),
        (Item.AlchemistTablet, 10), (Item.ThiefTablet, 10), (Item.StargazerTablet, 10)
      ), Item.MasterTablet)
  )

  def forSkill(skill: Skill): Vector[ProcessingAction] = skill match
    case Skill.Cooking => cooking
    case Skill.Smithing => smithing
    case Skill.Alchemy => alchemy
    case Skill.Summoning => summoning
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
// Summoning Tablet System
// ============================================================================

/** Type of tablet - determines its passive effect */
enum TabletType derives ReadWriter:
  case Gatherer     // +5% gathering yield
  case Miner        // +8% mining speed
  case Fisher       // +8% fishing speed
  case Lumberjack   // +8% woodcutting speed
  case Artisan      // +10% crafting success (double chance)
  case Herbalist    // +10% herb yield
  case Alchemist    // +15% potion potency (double chance)
  case Thief        // +10% thieving success (future)
  case Stargazer    // +20% stardust gain (future)
  case Master       // +5% all skills

object TabletType:
  /** Get tablet type from item */
  def fromItem(item: Item): Option[TabletType] = item match
    case Item.GathererTablet => Some(Gatherer)
    case Item.MinerTablet => Some(Miner)
    case Item.FisherTablet => Some(Fisher)
    case Item.LumberjackTablet => Some(Lumberjack)
    case Item.ArtisanTablet => Some(Artisan)
    case Item.HerbalistTablet => Some(Herbalist)
    case Item.AlchemistTablet => Some(Alchemist)
    case Item.ThiefTablet => Some(Thief)
    case Item.StargazerTablet => Some(Stargazer)
    case Item.MasterTablet => Some(Master)
    case _ => None

  def isTablet(item: Item): Boolean = fromItem(item).isDefined

  /** Get the item for a tablet type */
  def toItem(tabletType: TabletType): Item = tabletType match
    case Gatherer => Item.GathererTablet
    case Miner => Item.MinerTablet
    case Fisher => Item.FisherTablet
    case Lumberjack => Item.LumberjackTablet
    case Artisan => Item.ArtisanTablet
    case Herbalist => Item.HerbalistTablet
    case Alchemist => Item.AlchemistTablet
    case Thief => Item.ThiefTablet
    case Stargazer => Item.StargazerTablet
    case Master => Item.MasterTablet

  /** Consumption rate - actions per tablet consumed */
  def consumptionRate(tabletType: TabletType): Int = tabletType match
    case Gatherer | Miner | Fisher | Lumberjack | Herbalist | Stargazer => 10
    case Artisan | Alchemist => 8
    case Thief | Master => 5

  /** Description of the tablet's effect */
  def description(tabletType: TabletType): String = tabletType match
    case Gatherer => "+5% gathering yield"
    case Miner => "+8% mining speed"
    case Fisher => "+8% fishing speed"
    case Lumberjack => "+8% woodcutting speed"
    case Artisan => "+10% crafting success"
    case Herbalist => "+10% herb yield"
    case Alchemist => "+15% potion potency"
    case Thief => "+10% thieving success"
    case Stargazer => "+20% stardust gain"
    case Master => "+5% all skills"

/** Synergy effects when two compatible tablets are equipped together */
enum SynergyEffect derives ReadWriter:
  case EarthAffinity    // Gatherer + Miner: 10% chance for double ore
  case NaturesBounty    // Gatherer + Fisher: 10% chance for double fish
  case ForestSpirit     // Gatherer + Lumberjack: 10% chance for double logs
  case Metalworker      // Miner + Artisan: +15% recycle chance when smithing
  case SeaChef          // Fisher + Artisan: Never burn fish when cooking
  case PotionMaster     // Herbalist + Alchemist: +20% double chance for potions
  case EfficientBrewer  // Artisan + Alchemist: +15% recycle chance for alchemy
  case ShadowWalker     // Thief + Stargazer: No stun on thieving failure (future)
  case GroveKeeper      // Lumberjack + Herbalist: Find herbs while woodcutting
  case MasteryBoost     // Any + Master: Double the effect of the other tablet

object SynergyEffect:
  /** Find synergy between two tablet types (order doesn't matter) */
  def find(t1: TabletType, t2: TabletType): Option[SynergyEffect] =
    val pair = Set(t1, t2)
    synergies.find((types, _) => types == pair).map(_._2)

  /** All defined synergies */
  private val synergies: Vector[(Set[TabletType], SynergyEffect)] = Vector(
    Set(TabletType.Gatherer, TabletType.Miner) -> EarthAffinity,
    Set(TabletType.Gatherer, TabletType.Fisher) -> NaturesBounty,
    Set(TabletType.Gatherer, TabletType.Lumberjack) -> ForestSpirit,
    Set(TabletType.Miner, TabletType.Artisan) -> Metalworker,
    Set(TabletType.Fisher, TabletType.Artisan) -> SeaChef,
    Set(TabletType.Herbalist, TabletType.Alchemist) -> PotionMaster,
    Set(TabletType.Artisan, TabletType.Alchemist) -> EfficientBrewer,
    Set(TabletType.Thief, TabletType.Stargazer) -> ShadowWalker,
    Set(TabletType.Lumberjack, TabletType.Herbalist) -> GroveKeeper
  )

  /** Check if Master tablet is involved - Master synergy applies with any tablet */
  def hasMasterSynergy(t1: TabletType, t2: TabletType): Boolean =
    (t1 == TabletType.Master || t2 == TabletType.Master) && t1 != t2

  def displayName(effect: SynergyEffect): String = effect match
    case EarthAffinity => "Earth Affinity"
    case NaturesBounty => "Nature's Bounty"
    case ForestSpirit => "Forest Spirit"
    case Metalworker => "Metalworker"
    case SeaChef => "Sea Chef"
    case PotionMaster => "Potion Master"
    case EfficientBrewer => "Efficient Brewer"
    case ShadowWalker => "Shadow Walker"
    case GroveKeeper => "Grove Keeper"
    case MasteryBoost => "Mastery Boost"

  def description(effect: SynergyEffect): String = effect match
    case EarthAffinity => "10% chance for double ore"
    case NaturesBounty => "10% chance for double fish"
    case ForestSpirit => "10% chance for double logs"
    case Metalworker => "+15% recycle chance when smithing"
    case SeaChef => "Never burn fish when cooking"
    case PotionMaster => "+20% double chance for potions"
    case EfficientBrewer => "+15% recycle chance for alchemy"
    case ShadowWalker => "No stun on thieving failure"
    case GroveKeeper => "Find herbs while woodcutting"
    case MasteryBoost => "Double the effect of the other tablet"

/** An equipped tablet with remaining charges */
case class EquippedTablet(
  item: Item,
  tabletType: TabletType,
  actionsRemaining: Int
) derives ReadWriter

object EquippedTablet:
  def fromItem(item: Item): Option[EquippedTablet] =
    TabletType.fromItem(item).map: tabletType =>
      val charges = TabletType.consumptionRate(tabletType)
      EquippedTablet(item, tabletType, charges)

/** Tracks equipped tablets - player can have up to two */
case class TabletSlots(
  slot1: Option[EquippedTablet],
  slot2: Option[EquippedTablet]
) derives ReadWriter:

  /** Check if a slot is unlocked (slot 2 requires Summoning level 25) */
  def isSlot2Unlocked(summoningLevel: Int): Boolean = summoningLevel >= 25

  /** Get all equipped tablet types */
  def equippedTypes: Vector[TabletType] =
    Vector(slot1, slot2).flatten.map(_.tabletType)

  /** Get active synergy effect (if any) */
  def activeSynergy: Option[SynergyEffect] =
    (slot1, slot2) match
      case (Some(t1), Some(t2)) =>
        if SynergyEffect.hasMasterSynergy(t1.tabletType, t2.tabletType) then
          Some(SynergyEffect.MasteryBoost)
        else
          SynergyEffect.find(t1.tabletType, t2.tabletType)
      case _ => None

  /** Consume one action's worth of tablets. Returns updated slots. */
  def consumeAction: TabletSlots =
    val newSlot1 = slot1.flatMap(consumeTablet)
    val newSlot2 = slot2.flatMap(consumeTablet)
    TabletSlots(newSlot1, newSlot2)

  private def consumeTablet(tablet: EquippedTablet): Option[EquippedTablet] =
    val newRemaining = tablet.actionsRemaining - 1
    if newRemaining <= 0 then None
    else Some(tablet.copy(actionsRemaining = newRemaining))

  // ============================================================================
  // Bonus Calculations
  // ============================================================================

  /** Get gathering yield bonus (base + synergy) */
  def gatheringYieldBonus: Double =
    val baseBonus = equippedTypes.collect:
      case TabletType.Gatherer => 0.05
      case TabletType.Master => 0.05
    .sum
    val synergyBonus = activeSynergy match
      case Some(SynergyEffect.MasteryBoost) if equippedTypes.contains(TabletType.Gatherer) => 0.05
      case _ => 0.0
    baseBonus + synergyBonus

  /** Get speed bonus for a specific skill */
  def speedBonusFor(skill: Skill): Double =
    val baseBonus = (skill, equippedTypes) match
      case (Skill.Mining, types) if types.contains(TabletType.Miner) => 0.08
      case (Skill.Fishing, types) if types.contains(TabletType.Fisher) => 0.08
      case (Skill.Woodcutting, types) if types.contains(TabletType.Lumberjack) => 0.08
      case _ => 0.0
    val masterBonus = if equippedTypes.contains(TabletType.Master) then 0.05 else 0.0
    val masteryMultiplier = activeSynergy match
      case Some(SynergyEffect.MasteryBoost) => 2.0
      case _ => 1.0
    (baseBonus * masteryMultiplier) + masterBonus

  /** Get double chance bonus for specific skill/context */
  def doubleBonusFor(skill: Skill): Double =
    val baseBonus = skill match
      case Skill.Mining =>
        if activeSynergy.contains(SynergyEffect.EarthAffinity) then 0.10 else 0.0
      case Skill.Fishing =>
        if activeSynergy.contains(SynergyEffect.NaturesBounty) then 0.10 else 0.0
      case Skill.Woodcutting =>
        if activeSynergy.contains(SynergyEffect.ForestSpirit) then 0.10 else 0.0
      case Skill.Alchemy =>
        val potionMaster = if activeSynergy.contains(SynergyEffect.PotionMaster) then 0.20 else 0.0
        val alchemist = if equippedTypes.contains(TabletType.Alchemist) then 0.15 else 0.0
        potionMaster + alchemist
      case _ =>
        if equippedTypes.contains(TabletType.Artisan) then 0.10 else 0.0
    val masterBonus = if equippedTypes.contains(TabletType.Master) then 0.05 else 0.0
    baseBonus + masterBonus

  /** Get recycle bonus for specific skill */
  def recycleBonusFor(skill: Skill): Double =
    val baseBonus = skill match
      case Skill.Smithing =>
        if activeSynergy.contains(SynergyEffect.Metalworker) then 0.15 else 0.0
      case Skill.Alchemy =>
        if activeSynergy.contains(SynergyEffect.EfficientBrewer) then 0.15 else 0.0
      case _ => 0.0
    baseBonus

  /** Check if Sea Chef synergy prevents burning (for cooking) */
  def preventsBurning: Boolean =
    activeSynergy.contains(SynergyEffect.SeaChef)

  /** Get herbalism yield bonus */
  def herbalismYieldBonus: Double =
    val base = if equippedTypes.contains(TabletType.Herbalist) then 0.10 else 0.0
    val masteryMultiplier = activeSynergy match
      case Some(SynergyEffect.MasteryBoost) if equippedTypes.contains(TabletType.Herbalist) => 2.0
      case _ => 1.0
    base * masteryMultiplier

  /** Check if Grove Keeper synergy is active (find herbs while woodcutting) */
  def hasGroveKeeper: Boolean =
    activeSynergy.contains(SynergyEffect.GroveKeeper)

object TabletSlots:
  val empty: TabletSlots = TabletSlots(None, None)

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
  tabletSlots: TabletSlots = TabletSlots.empty,  // Equipped summoning tablets
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
      tabletSlots = TabletSlots.empty,
      junkItems = Set.empty
    )


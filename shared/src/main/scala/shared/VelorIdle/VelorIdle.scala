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

object Skill:
  val gathering: Set[Skill] = Set(Woodcutting, Mining, Fishing, Herbalism)
  val processing: Set[Skill] = Set(Cooking, Smithing, Alchemy, Summoning)
  val special: Set[Skill] = Set(Thieving, Astrology)

  def isGathering(skill: Skill): Boolean = gathering.contains(skill)
  def isProcessing(skill: Skill): Boolean = processing.contains(skill)

  def icon(skill: Skill): String = skill match
    case Woodcutting => "🪓"
    case Mining      => "⛏️"
    case Fishing     => "🎣"
    case Herbalism   => "🌿"
    case Cooking     => "🍳"
    case Smithing    => "🔨"
    case Alchemy     => "🧪"
    case Summoning   => "📜"
    case Thieving    => "🗡️"
    case Astrology   => "⭐"

  def displayName(skill: Skill): String = skill match
    case Woodcutting => "Woodcutting"
    case Mining      => "Mining"
    case Fishing     => "Fishing"
    case Herbalism   => "Herbalism"
    case Cooking     => "Cooking"
    case Smithing    => "Smithing"
    case Alchemy     => "Alchemy"
    case Summoning   => "Summoning"
    case Thieving    => "Thieving"
    case Astrology   => "Astrology"

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
  // Rare drops
  case BirdNest, Gem

object Item:
  def icon(item: Item): String = item match
    // Logs
    case Item.NormalLogs => "🪵"
    case Item.OakLogs => "🪵"
    case Item.WillowLogs => "🪵"
    case Item.MapleLogs => "🍁"
    case Item.YewLogs => "🌲"
    case Item.MagicLogs => "✨"
    // Ores
    case Item.CopperOre => "🟤"
    case Item.TinOre => "⚪"
    case Item.IronOre => "🔶"
    case Item.Coal => "⬛"
    case Item.GoldOre => "🟡"
    case Item.MithrilOre => "🔵"
    // Raw fish
    case Item.RawShrimp => "🦐"
    case Item.RawSardine => "🐟"
    case Item.RawTrout => "🐟"
    case Item.RawSalmon => "🐟"
    case Item.RawLobster => "🦞"
    case Item.RawSwordfish => "🐠"
    // Herbs
    case Item.GuamLeaf => "🌿"
    case Item.Marrentill => "🌿"
    case Item.Tarromin => "🌿"
    case Item.Harralander => "🌿"
    case Item.RanarrWeed => "🌿"
    case Item.IritLeaf => "🌿"
    case Item.Kwuarm => "🌿"
    case Item.Cadantine => "🌿"
    // Cooked
    case Item.CookedShrimp => "🍤"
    case Item.CookedSardine => "🍣"
    case Item.CookedTrout => "🍣"
    case Item.CookedSalmon => "🍣"
    case Item.CookedLobster => "🦞"
    case Item.CookedSwordfish => "🍣"
    case Item.BurntFish => "💨"
    // Bars
    case Item.BronzeBar => "🟫"
    case Item.IronBar => "⬜"
    case Item.SteelBar => "🔲"
    case Item.GoldBar => "🟨"
    case Item.MithrilBar => "🟦"
    // Rare
    case Item.BirdNest => "🪺"
    case Item.Gem => "💎"

  def displayName(item: Item): String = item match
    case Item.NormalLogs => "Normal Logs"
    case Item.OakLogs => "Oak Logs"
    case Item.WillowLogs => "Willow Logs"
    case Item.MapleLogs => "Maple Logs"
    case Item.YewLogs => "Yew Logs"
    case Item.MagicLogs => "Magic Logs"
    case Item.CopperOre => "Copper Ore"
    case Item.TinOre => "Tin Ore"
    case Item.IronOre => "Iron Ore"
    case Item.Coal => "Coal"
    case Item.GoldOre => "Gold Ore"
    case Item.MithrilOre => "Mithril Ore"
    case Item.RawShrimp => "Raw Shrimp"
    case Item.RawSardine => "Raw Sardine"
    case Item.RawTrout => "Raw Trout"
    case Item.RawSalmon => "Raw Salmon"
    case Item.RawLobster => "Raw Lobster"
    case Item.RawSwordfish => "Raw Swordfish"
    case Item.GuamLeaf => "Guam Leaf"
    case Item.Marrentill => "Marrentill"
    case Item.Tarromin => "Tarromin"
    case Item.Harralander => "Harralander"
    case Item.RanarrWeed => "Ranarr Weed"
    case Item.IritLeaf => "Irit Leaf"
    case Item.Kwuarm => "Kwuarm"
    case Item.Cadantine => "Cadantine"
    case Item.CookedShrimp => "Cooked Shrimp"
    case Item.CookedSardine => "Cooked Sardine"
    case Item.CookedTrout => "Cooked Trout"
    case Item.CookedSalmon => "Cooked Salmon"
    case Item.CookedLobster => "Cooked Lobster"
    case Item.CookedSwordfish => "Cooked Swordfish"
    case Item.BurntFish => "Burnt Fish"
    case Item.BronzeBar => "Bronze Bar"
    case Item.IronBar => "Iron Bar"
    case Item.SteelBar => "Steel Bar"
    case Item.GoldBar => "Gold Bar"
    case Item.MithrilBar => "Mithril Bar"
    case Item.BirdNest => "Bird Nest"
    case Item.Gem => "Gem"

  def sellValue(item: Item): Int = item match
    case Item.NormalLogs => 2
    case Item.OakLogs => 5
    case Item.WillowLogs => 10
    case Item.MapleLogs => 20
    case Item.YewLogs => 40
    case Item.MagicLogs => 80
    case Item.CopperOre => 3
    case Item.TinOre => 3
    case Item.IronOre => 8
    case Item.Coal => 12
    case Item.GoldOre => 30
    case Item.MithrilOre => 60
    case Item.RawShrimp => 1
    case Item.RawSardine => 3
    case Item.RawTrout => 8
    case Item.RawSalmon => 15
    case Item.RawLobster => 30
    case Item.RawSwordfish => 50
    case Item.GuamLeaf => 2
    case Item.Marrentill => 5
    case Item.Tarromin => 10
    case Item.Harralander => 20
    case Item.RanarrWeed => 40
    case Item.IritLeaf => 60
    case Item.Kwuarm => 80
    case Item.Cadantine => 100
    case Item.CookedShrimp => 3
    case Item.CookedSardine => 8
    case Item.CookedTrout => 20
    case Item.CookedSalmon => 40
    case Item.CookedLobster => 80
    case Item.CookedSwordfish => 130
    case Item.BurntFish => 1
    case Item.BronzeBar => 10
    case Item.IronBar => 25
    case Item.SteelBar => 50
    case Item.GoldBar => 80
    case Item.MithrilBar => 150
    case Item.BirdNest => 100
    case Item.Gem => 200

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

  def empty(slots: Int = StartingSlots): Inventory =
    Inventory(Vector.fill(slots)(None), slots)

  /** Cost to upgrade to a certain number of slots */
  def upgradeCost(currentSlots: Int, targetSlots: Int): Option[Int] =
    val costs = Map(
      16 -> 500,
      20 -> 1500,
      24 -> 4000,
      28 -> 8000,
      32 -> 15000,
      40 -> 30000,
      50 -> 60000,
      60 -> 100000
    )
    if targetSlots > currentSlots then costs.get(targetSlots)
    else None

// ============================================================================
// Game State
// ============================================================================

enum ActiveAction derives ReadWriter:
  case Gathering(action: GatheringAction)
  case Idle

case class VelorIdleGame(
  skills: Map[Skill, SkillState],
  inventory: Inventory,
  gold: Long,
  currentSkill: Option[Skill],
  activeAction: ActiveAction,
  actionProgress: Double,       // 0.0 to 1.0
  lastTickTime: Long
) derives ReadWriter

object VelorIdleGame:
  def newGame(timestamp: Long): VelorIdleGame =
    VelorIdleGame(
      skills = Skill.values.map(s => s -> SkillState.initial).toMap,
      inventory = Inventory.empty(),
      gold = 0L,
      currentSkill = None,
      activeAction = ActiveAction.Idle,
      actionProgress = 0.0,
      lastTickTime = timestamp
    )


package shared.TileKingdom

import upickle.default.ReadWriter

// ============================================================================
// Resource System
// ============================================================================

enum Resource derives ReadWriter:
  case Wheat, Wood, Faith, Gold, Stone

case class Cost(amount: Int, resource: Resource) derives ReadWriter

case class Resources(
    wheat: Double = 0.0,
    wood: Double = 0.0,
    faith: Double = 0.0,
    gold: Int = 0,
    stone: Double = 0.0
) derives ReadWriter:
  def get(resource: Resource): Double = resource match
    case Resource.Wheat => wheat
    case Resource.Wood  => wood
    case Resource.Faith => faith
    case Resource.Gold  => gold.toDouble
    case Resource.Stone => stone

  def canAfford(cost: Cost): Boolean = get(cost.resource) >= cost.amount

  def canAfford(cost: Int, resource: Resource): Boolean = get(resource) >= cost

  def deduct(cost: Cost): Resources = deduct(cost.amount, cost.resource)

  def deduct(amount: Int, resource: Resource): Resources = resource match
    case Resource.Wheat => copy(wheat = wheat - amount)
    case Resource.Wood  => copy(wood = wood - amount)
    case Resource.Faith => copy(faith = faith - amount)
    case Resource.Gold  => copy(gold = gold - amount)
    case Resource.Stone => copy(stone = stone - amount)

  def add(amount: Double, resource: Resource): Resources = resource match
    case Resource.Wheat => copy(wheat = wheat + amount)
    case Resource.Wood  => copy(wood = wood + amount)
    case Resource.Faith => copy(faith = faith + amount)
    case Resource.Gold  => copy(gold = gold + amount.toInt)
    case Resource.Stone => copy(stone = stone + amount)

// ============================================================================
// Coordinate type for infinite grid
// ============================================================================

case class Coord(row: Int, col: Int) derives ReadWriter:
  def neighbors: Set[Coord] = neighborsWithinRadius(1)

  def neighborsWithinRadius(radius: Int): Set[Coord] =
    (for
      rowOffset <- -radius to radius
      colOffset <- -radius to radius
      if !(rowOffset == 0 && colOffset == 0)
    yield Coord(row + rowOffset, col + colOffset)).toSet

// ============================================================================
// Tile Types
// ============================================================================

enum AcademyMode derives ReadWriter:
  case FasterPoliticians // 2x politician generation speed
  case RareChance // +10% rare politician chance

// ============================================================================
// Skill Tree System
// ============================================================================

// Individual skill nodes within a branch
enum Skill derives ReadWriter:
  // Agriculture branch
  case Agriculture1 // Fields start at level 10
  case Agriculture2 // Farms start at level 10
  // Management branch
  case Management1 // Politician roster holds 2 additional politicians
  case Management2 // Town halls are 10x cheaper to build
  // Wisdom branch
  case Wisdom1 // Quarries produce 25% more stone for each neighboring forest
  case Wisdom2 // Each forest grants 50% increased faith production to neighboring temples
  // Education branch
  case Education1 // Academies are 10x cheaper
  case Education2 // Academies have both modes active at the same time

object Skill:
  // Get skill branch name
  def branchName(skill: Skill): String = skill match
    case Agriculture1 | Agriculture2 => "Agriculture"
    case Management1 | Management2 => "Management"
    case Wisdom1 | Wisdom2 => "Wisdom"
    case Education1 | Education2 => "Education"

  // Get skill description
  def description(skill: Skill): String = skill match
    case Agriculture1 => "Fields start at level 10"
    case Agriculture2 => "Farms start at level 10"
    case Management1 => "Politician roster holds 2 additional politicians"
    case Management2 => "Town halls are 10x cheaper to build"
    case Wisdom1 => "Quarries produce 25% more stone per neighboring forest"
    case Wisdom2 => "Forests grant 50% faith to neighboring temples"
    case Education1 => "Academies are 10x cheaper"
    case Education2 => "Academies have both modes active"

  // Get skill cost (position in branch)
  def cost(skill: Skill): Int = skill match
    case Agriculture1 | Management1 | Wisdom1 | Education1 => 1
    case Agriculture2 | Management2 | Wisdom2 | Education2 => 2

  // Get prerequisite skill (if any)
  def prerequisite(skill: Skill): Option[Skill] = skill match
    case Agriculture1 | Management1 | Wisdom1 | Education1 => None
    case Agriculture2 => Some(Agriculture1)
    case Management2 => Some(Management1)
    case Wisdom2 => Some(Wisdom1)
    case Education2 => Some(Education1)

  // Get all skills in a branch, in order
  def branchSkills(branchName: String): List[Skill] = branchName match
    case "Agriculture" => List(Agriculture1, Agriculture2)
    case "Management" => List(Management1, Management2)
    case "Wisdom" => List(Wisdom1, Wisdom2)
    case "Education" => List(Education1, Education2)
    case _ => List.empty

  // Get all branch names
  val allBranches: List[String] = List("Agriculture", "Management", "Wisdom", "Education")

  // Get emoji for branch
  def branchEmoji(branchName: String): String = branchName match
    case "Agriculture" => "🌾"
    case "Management" => "📋"
    case "Wisdom" => "📿"
    case "Education" => "📚"
    case _ => "❓"

enum TileType derives ReadWriter:
  case Empty
  case WheatField(level: Int) // level determines production rate
  case Farm(level: Int) // boosts nearby wheat fields
  case Woodcutter(level: Int) // produces wood
  case Bureau(level: Int) // auto-upgrades nearby buildings, costs wood
  case Temple(level: Int) // produces faith, costs wood
  case TownHall(politician: Option[Politician]) // has a slot for a politician
  case Quarry(level: Int) // produces stone
  case Academy(mode: AcademyMode) // boosts politician generation or rare chance
  case Tavern // extends politician lifespan in nearby Town Halls by 2x

// ============================================================================
// Politician System
// ============================================================================

enum PoliticianEffect derives ReadWriter:
  case WheatProductionMultiplier(multiplier: Double) // e.g., 2.0 = 2x wheat production
  case WoodProductionMultiplier(multiplier: Double)
  case FaithProductionMultiplier(multiplier: Double)
  case StoneProductionMultiplier(multiplier: Double)
  case AllProductionMultiplier(multiplier: Double)

case class Politician(
    id: String,
    name: String,
    title: String,
    effect: PoliticianEffect,
    emoji: String,
    secondaryEffect: Option[PoliticianEffect] = None, // Rare politicians have two effects
    remainingLifespanMs: Long = 600000L // 10 minutes = 600,000 ms
) derives ReadWriter:
  def isRare: Boolean = secondaryEffect.isDefined
  
  private def describeEffect(eff: PoliticianEffect): String = eff match
    case PoliticianEffect.WheatProductionMultiplier(m) => s"${(m * 100).toInt}% wheat"
    case PoliticianEffect.WoodProductionMultiplier(m)  => s"${(m * 100).toInt}% wood"
    case PoliticianEffect.FaithProductionMultiplier(m) => s"${(m * 100).toInt}% faith"
    case PoliticianEffect.StoneProductionMultiplier(m) => s"${(m * 100).toInt}% stone"
    case PoliticianEffect.AllProductionMultiplier(m)   => s"${(m * 100).toInt}% all"
  
  def effectDescription: String = secondaryEffect match
    case Some(secondary) => s"${describeEffect(effect)} + ${describeEffect(secondary)}"
    case None => effect match
      case PoliticianEffect.WheatProductionMultiplier(m) => s"${(m * 100).toInt}% wheat production"
      case PoliticianEffect.WoodProductionMultiplier(m)  => s"${(m * 100).toInt}% wood production"
      case PoliticianEffect.FaithProductionMultiplier(m) => s"${(m * 100).toInt}% faith production"
      case PoliticianEffect.StoneProductionMultiplier(m) => s"${(m * 100).toInt}% stone production"
      case PoliticianEffect.AllProductionMultiplier(m)   => s"${(m * 100).toInt}% all production"

// ============================================================================
// Tile
// ============================================================================

case class Tile(
    coord: Coord,
    tileType: TileType,
    unlocked: Boolean
) derives ReadWriter:
  def isEmpty: Boolean = tileType match
    case TileType.Empty => true
    case _              => false

  def isWheatField: Boolean = tileType match
    case TileType.WheatField(_) => true
    case _                      => false

  def isFarm: Boolean = tileType match
    case TileType.Farm(_) => true
    case _                => false

  def isWoodcutter: Boolean = tileType match
    case TileType.Woodcutter(_) => true
    case _                      => false

  def isBureau: Boolean = tileType match
    case TileType.Bureau(_) => true
    case _                  => false

  def isTemple: Boolean = tileType match
    case TileType.Temple(_) => true
    case _                  => false

  def isTownHall: Boolean = tileType match
    case TileType.TownHall(_) => true
    case _                    => false

  def isQuarry: Boolean = tileType match
    case TileType.Quarry(_) => true
    case _                  => false

  def isAcademy: Boolean = tileType match
    case TileType.Academy(_) => true
    case _                   => false

  def isTavern: Boolean = tileType match
    case TileType.Tavern => true
    case _               => false

  def isBuilding: Boolean = isWheatField || isFarm || isWoodcutter || isBureau || isTemple || isTownHall || isQuarry || isAcademy || isTavern

  def isUpgradeable: Boolean = isWheatField || isFarm || isWoodcutter || isTemple || isQuarry

  def level: Int = tileType match
    case TileType.WheatField(lvl) => lvl
    case TileType.Farm(lvl)       => lvl
    case TileType.Woodcutter(lvl) => lvl
    case TileType.Bureau(lvl)     => lvl
    case TileType.Temple(lvl)     => lvl
    case TileType.Quarry(lvl)     => lvl
    case _                        => 0

  def upgradeCost: Option[Cost] = tileType match
    case TileType.WheatField(lvl) => Some(Cost(TileKingdomLogic.wheatFieldLevelUpCost(lvl), Resource.Wheat))
    case TileType.Farm(lvl)       => Some(Cost(TileKingdomLogic.farmLevelUpCost(lvl), Resource.Wheat))
    case TileType.Woodcutter(lvl) => Some(Cost(TileKingdomLogic.woodcutterLevelUpCost(lvl), Resource.Wheat))
    case TileType.Temple(lvl)     => Some(Cost(TileKingdomLogic.templeLevelUpCost(lvl), Resource.Wood))
    case TileType.Quarry(lvl)     => Some(Cost(TileKingdomLogic.quarryLevelUpCost(lvl), Resource.Wood))
    case _                        => None

// ============================================================================
// Game State
// ============================================================================

case class TileKingdomGame(
    tiles: Map[Coord, Tile],
    wheat: Double, // Can be fractional for smooth accumulation
    wood: Double, // Wood resource
    faith: Double, // Faith resource from temples
    gold: Int,
    stone: Double = 0.0, // Stone resource from quarries
    lastTickTime: Long, // Timestamp in milliseconds for offline progress
    totalAbdications: Int,
    bureauTurboMode: Map[Coord, Boolean] = Map.empty, // Whether turbo mode is enabled per bureau
    upgradeCooldowns: Map[Coord, Long] = Map.empty, // Deprecated, kept for save compatibility
    politicianRoster: List[Politician] = List.empty, // Available politicians to assign
    lastPoliticianGeneration: Long = 0L, // Timestamp of last politician generation tick
    politicianGenerationProgress: Double = 0.0, // Progress towards next politician (0.0 to 1.0)
    legacyPoints: Int = 0, // Legacy points earned from Sailing (second tier prestige)
    skillPoints: Int = 0, // Skill points (1 per 25 legacy points)
    unlockedSkills: Set[Skill] = Set.empty, // Skills unlocked via skill tree
    hasSailed: Boolean = false // Whether player has sailed at least once (unlocks skill tree)
) derives ReadWriter:

  // Resource helpers
  def resources: Resources = Resources(wheat, wood, faith, gold, stone)

  def canAfford(cost: Cost): Boolean = resources.canAfford(cost)

  def canAfford(amount: Int, resource: Resource): Boolean = resources.canAfford(amount, resource)

  def deduct(cost: Cost): TileKingdomGame = cost.resource match
    case Resource.Wheat => copy(wheat = wheat - cost.amount)
    case Resource.Wood  => copy(wood = wood - cost.amount)
    case Resource.Faith => copy(faith = faith - cost.amount)
    case Resource.Gold  => copy(gold = gold - cost.amount)
    case Resource.Stone => copy(stone = stone - cost.amount)

  def unlockedTiles: List[Tile] =
    tiles.values.filter(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def lockedTiles: List[Tile] =
    tiles.values.filterNot(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  def allTilesFilled: Boolean =
    unlockedTiles.nonEmpty && unlockedTiles.forall(_.isBuilding)

  def hasWheatField: Boolean =
    unlockedTiles.exists(_.isWheatField)

  def hasFarm: Boolean =
    unlockedTiles.exists(_.isFarm)

  def hasWoodcutter: Boolean =
    unlockedTiles.exists(_.isWoodcutter)

  def hasQuarry: Boolean =
    unlockedTiles.exists(_.isQuarry)

  def hasTownHall: Boolean =
    unlockedTiles.exists(_.isTownHall)

  // Building unlock progression:
  // Wheat Field -> Farm -> Forest/Quarry -> Bureau/Temple (from Forest), Town Hall/Academy (from Quarry)
  def canBuildFarm: Boolean = hasWheatField
  def canBuildWoodcutter: Boolean = hasFarm
  def canBuildQuarry: Boolean = hasFarm
  def canBuildBureau: Boolean = hasWoodcutter
  def canBuildTemple: Boolean = hasWoodcutter
  def canBuildTownHall: Boolean = hasQuarry
  def canBuildAcademy: Boolean = hasQuarry
  def canBuildTavern: Boolean = unlockedTiles.exists(_.isTownHall)

  def totalIncomeRate: Double =
    TileKingdomLogic.totalWheatProductionRate(this) + 
    TileKingdomLogic.totalWoodProductionRate(this) +
    TileKingdomLogic.totalStoneProductionRate(this) +
    TileKingdomLogic.totalFaithProductionRate(this)

  def nextTileUnlockCost: Int =
    TileKingdomLogic.tileUnlockCost(unlockedTiles.size)

  def abdicationGoldReward: Int =
    TileKingdomLogic.abdicationReward(totalIncomeRate)

  // Sail (second tier prestige) - requires 25 tiles
  def canSail: Boolean = unlockedTiles.size >= TileKingdomLogic.SailMinTiles

  def sailLegacyReward: Int = unlockedTiles.size // 1 legacy point per tile destroyed

  // Skill helpers
  def hasSkill(skill: Skill): Boolean = unlockedSkills.contains(skill)

  def canUnlockSkill(skill: Skill): Boolean =
    if unlockedSkills.contains(skill) then false
    else if skillPoints < Skill.cost(skill) then false
    else Skill.prerequisite(skill).forall(unlockedSkills.contains)

  def totalSkillPointsSpent: Int =
    unlockedSkills.toList.map(Skill.cost).sum

// ============================================================================
// Game Logic
// ============================================================================

object TileKingdomLogic:

  // Constants
  val TickIntervalSeconds: Double = 0.25 // Tick four times per second
  val ProductionIntervalSeconds: Int = 10 // Wheat fields produce every 10 seconds
  val InitialTileCount: Int = 4
  val FarmBoostPerLevel: Double = 0.25 // 25% boost per farm level

  // Bureau constants
  val BureauIntervalSeconds: Int = 5 // Bureau attempts upgrade every 5 seconds
  val BureauRadius: Int = 2 // Bureau affects tiles within 2 tile radius
  val BureauWoodCostPerUpgrade: Int = 100 // Wood cost for each auto-upgrade
  val ForestGroupBonusPerTile: Double = 0.10 // 10% bonus per connected woodcutter

  // Temple constants
  val TempleBuildCost: Int = 10000 // Wood cost to build a temple

  // Bureau turbo mode constants
  val BureauTurboFaithCost: Int = 100 // Faith cost per upgrade in turbo mode
  val BureauTurboSpeedMultiplier: Double = 10.0 // 10x speed in turbo mode

  // Town Hall constants
  val TownHallBuildCost: Int = 1000 // Stone cost to build a town hall (first one)
  val TownHallInfluenceRadius: Int = 2 // Town Hall affects tiles within 2 tile radius
  val PoliticianGenerationIntervalSeconds: Int = 300 // 5 minutes = 300 seconds
  val MaxPoliticianRosterSize: Int = 3 // Maximum politicians in roster (base)
  val Management1RosterBonus: Int = 2 // Extra slots from Management1 skill
  val PoliticianLifespanMs: Long = 600000L // 10 minutes = 600,000 ms

  // Calculate actual max roster size including skill bonuses
  def maxPoliticianRosterSize(game: TileKingdomGame): Int =
    MaxPoliticianRosterSize + (if game.hasSkill(Skill.Management1) then Management1RosterBonus else 0)

  // Quarry constants
  val QuarryBuildCost: Int = 500 // Wood cost to build a quarry

  // Tavern constants
  val TavernBuildCost: Int = 500 // Wood cost to build a tavern
  val TavernLifespanMultiplier: Double = 2.0 // 2x lifespan for politicians in nearby Town Halls

  // Academy constants
  val AcademyBaseCost: Int = 10000 // Stone cost for first academy
  val AcademyCostMultiplier: Int = 10 // Each academy costs 10x more
  val AcademySpeedMultiplier: Double = 2.0 // 2x faster politician generation
  val AcademyRareChanceBonus: Double = 0.10 // +10% rare chance per academy in RareChance mode
  val BaseRarePoliticianChance: Double = 0.05 // 5% base chance for rare politician

  // Sail (second tier prestige) constants
  val SailMinTiles: Int = 25 // Minimum tiles required to sail
  val LegacyPointsPerSkillPoint: Int = 25 // Legacy points needed for 1 skill point

  // Politician definitions
  val PoliticianPool: List[(String, String, PoliticianEffect, String)] = List(
    ("Farmer General", "Agricultural Expert", PoliticianEffect.WheatProductionMultiplier(2.0), "👨‍🌾"),
    ("Lumber Baron", "Forestry Minister", PoliticianEffect.WoodProductionMultiplier(2.0), "🪓"),
    ("High Priest", "Spiritual Leader", PoliticianEffect.FaithProductionMultiplier(2.0), "🙏"),
    ("Master Mason", "Stone Guild Leader", PoliticianEffect.StoneProductionMultiplier(2.0), "🪨"),
    ("Chancellor", "Economic Advisor", PoliticianEffect.AllProductionMultiplier(1.5), "📊"),
    ("Harvest Queen", "Fertility Goddess", PoliticianEffect.WheatProductionMultiplier(3.0), "👑"),
    ("Forest Warden", "Nature Guardian", PoliticianEffect.WoodProductionMultiplier(2.5), "🌲"),
    ("Oracle", "Divine Seer", PoliticianEffect.FaithProductionMultiplier(2.5), "🔮"),
    ("Quarry Overseer", "Mining Expert", PoliticianEffect.StoneProductionMultiplier(2.5), "⛏️"),
    ("Grand Vizier", "Master Strategist", PoliticianEffect.AllProductionMultiplier(1.25), "🎭")
  )

  // Initial 2x2 tiles at origin (center of infinite grid)
  val InitialUnlockedCoords: Set[Coord] = Set(
    Coord(0, 0),
    Coord(0, 1),
    Coord(1, 0),
    Coord(1, 1)
  )

  // Find all woodcutters in the same connected group as the given coord
  def findConnectedWoodcutters(game: TileKingdomGame, startCoord: Coord): Set[Coord] =
    def floodFill(toVisit: Set[Coord], visited: Set[Coord]): Set[Coord] =
      if toVisit.isEmpty then visited
      else
        val current = toVisit.head
        val remaining = toVisit.tail
        if visited.contains(current) then floodFill(remaining, visited)
        else
          game.tiles.get(current) match
            case Some(tile) if tile.isWoodcutter =>
              val newNeighbors = current.neighbors.filterNot(visited.contains)
              floodFill(remaining ++ newNeighbors, visited + current)
            case _ =>
              floodFill(remaining, visited)
    floodFill(Set(startCoord), Set.empty)

  // Calculate forest group bonus multiplier for a woodcutter
  // Bonus escalates: 2 tiles = 10%, 3 tiles = 10+20=30%, 4 tiles = 10+20+30=60%, etc.
  def forestGroupBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    val groupSize = findConnectedWoodcutters(game, coord).size
    val n = groupSize - 1 // Number of other woodcutters in group
    val totalBonus = n * (n + 1) / 2.0 * ForestGroupBonusPerTile // Triangular number * bonus per tile
    1.0 + totalBonus

  // Calculate Wisdom1 bonus multiplier for quarries (25% more stone per neighboring forest)
  def quarryWisdom1Multiplier(game: TileKingdomGame, coord: Coord): Double =
    if !game.hasSkill(Skill.Wisdom1) then 1.0
    else
      val neighboringForests = coord.neighbors.count: neighborCoord =>
        game.tiles.get(neighborCoord).exists(_.isWoodcutter)
      1.0 + (neighboringForests * 0.25)

  // Calculate Wisdom2 bonus multiplier for temples (50% more faith per neighboring forest)
  def templeWisdom2Multiplier(game: TileKingdomGame, coord: Coord): Double =
    if !game.hasSkill(Skill.Wisdom2) then 1.0
    else
      val neighboringForests = coord.neighbors.count: neighborCoord =>
        game.tiles.get(neighborCoord).exists(_.isWoodcutter)
      1.0 + (neighboringForests * 0.50)

  // Find all Town Halls that affect a given coord (within their influence radius)
  def townHallsAffecting(game: TileKingdomGame, coord: Coord): List[(Coord, Politician)] =
    game.tiles.toList.flatMap:
      case (townHallCoord, tile) => tile.tileType match
        case TileType.TownHall(Some(politician))
          if townHallCoord.neighborsWithinRadius(TownHallInfluenceRadius).contains(coord) =>
          Some((townHallCoord, politician))
        case _ => None

  // Helper to apply a single effect to a multiplier for a specific resource type
  private def applyEffect(acc: Double, effect: PoliticianEffect, isWheat: Boolean = false, isWood: Boolean = false, isFaith: Boolean = false, isStone: Boolean = false): Double =
    effect match
      case PoliticianEffect.WheatProductionMultiplier(m) if isWheat => acc * m
      case PoliticianEffect.WoodProductionMultiplier(m) if isWood   => acc * m
      case PoliticianEffect.FaithProductionMultiplier(m) if isFaith => acc * m
      case PoliticianEffect.StoneProductionMultiplier(m) if isStone => acc * m
      case PoliticianEffect.AllProductionMultiplier(m)              => acc * m
      case _ => acc

  // Calculate Town Hall bonus multiplier for wheat production at a given coord
  def townHallWheatMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      val afterPrimary = applyEffect(acc, politician.effect, isWheat = true)
      politician.secondaryEffect.map(eff => applyEffect(afterPrimary, eff, isWheat = true)).getOrElse(afterPrimary)

  // Calculate Town Hall bonus multiplier for wood production at a given coord
  def townHallWoodMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      val afterPrimary = applyEffect(acc, politician.effect, isWood = true)
      politician.secondaryEffect.map(eff => applyEffect(afterPrimary, eff, isWood = true)).getOrElse(afterPrimary)

  // Calculate Town Hall bonus multiplier for faith production at a given coord
  def townHallFaithMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      val afterPrimary = applyEffect(acc, politician.effect, isFaith = true)
      politician.secondaryEffect.map(eff => applyEffect(afterPrimary, eff, isFaith = true)).getOrElse(afterPrimary)

  // Calculate Town Hall bonus multiplier for stone production at a given coord
  def townHallStoneMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      val afterPrimary = applyEffect(acc, politician.effect, isStone = true)
      politician.secondaryEffect.map(eff => applyEffect(afterPrimary, eff, isStone = true)).getOrElse(afterPrimary)

  // Count taverns within influence radius of a Town Hall
  def tavernsAffectingTownHall(game: TileKingdomGame, townHallCoord: Coord): Int =
    townHallCoord.neighborsWithinRadius(TownHallInfluenceRadius).count: coord =>
      game.tiles.get(coord).exists(_.isTavern)

  // Calculate lifespan multiplier for a politician in a Town Hall based on nearby taverns
  def politicianLifespanMultiplier(game: TileKingdomGame, townHallCoord: Coord): Double =
    val tavernCount = tavernsAffectingTownHall(game, townHallCoord)
    math.pow(TavernLifespanMultiplier, tavernCount) // 2x per tavern, multiplicative

  // Count academies by mode (with Education2: all academies count for both modes)
  def countAcademies(game: TileKingdomGame, mode: AcademyMode): Int =
    if game.hasSkill(Skill.Education2) then
      // With Education2, all academies count for both modes
      totalAcademies(game)
    else
      game.unlockedTiles.count: tile =>
        tile.tileType match
          case TileType.Academy(m) if m == mode => true
          case _ => false

  // Count total academies
  def totalAcademies(game: TileKingdomGame): Int =
    game.unlockedTiles.count(_.isAcademy)

  // Calculate academy build cost
  def academyBuildCost(game: TileKingdomGame): Int =
    val existingCount = totalAcademies(game)
    val baseCost = AcademyBaseCost * math.pow(AcademyCostMultiplier, existingCount).toInt
    if game.hasSkill(Skill.Education1) then baseCost / 10 else baseCost

  // Calculate rare politician chance with academy bonuses
  def rarePoliticianChance(game: TileKingdomGame): Double =
    val academyBonus = countAcademies(game, AcademyMode.RareChance) * AcademyRareChanceBonus
    BaseRarePoliticianChance + academyBonus

  // Calculate politician generation speed multiplier from academies
  def politicianGenerationSpeedMultiplier(game: TileKingdomGame): Double =
    val speedAcademies = countAcademies(game, AcademyMode.FasterPoliticians)
    math.pow(AcademySpeedMultiplier, speedAcademies)

  // Generate a random politician (possibly rare)
  def generatePolitician(seed: Long, rareChance: Double): Politician =
    val random = new scala.util.Random(seed)
    val isRare = random.nextDouble() < rareChance
    val (name, title, effect, emoji) = PoliticianPool(random.nextInt(PoliticianPool.size))
    
    if isRare then
      // Pick a different second effect
      val secondaryOptions = PoliticianPool.filterNot(_._3 == effect)
      val (_, _, secondEffect, _) = secondaryOptions(random.nextInt(secondaryOptions.size))
      Politician(
        id = s"politician_${seed}_${random.nextInt(10000)}",
        name = s"$name the Great",
        title = s"Legendary $title",
        effect = effect,
        emoji = "⭐",
        secondaryEffect = Some(secondEffect)
      )
    else
      Politician(
        id = s"politician_${seed}_${random.nextInt(10000)}",
        name = name,
        title = title,
        effect = effect,
        emoji = emoji
      )

  // Legacy method without rare chance (for backwards compatibility)
  def generatePolitician(seed: Long): Politician =
    generatePolitician(seed, BaseRarePoliticianChance)

  // Check and generate new politicians based on elapsed time
  def generateNewPoliticians(game: TileKingdomGame, currentTimeMillis: Long): TileKingdomGame =
    // Don't generate politicians if there's no town hall
    if !game.hasTownHall then
      return game.copy(lastPoliticianGeneration = currentTimeMillis, politicianGenerationProgress = 0.0)

    val maxRosterSize = maxPoliticianRosterSize(game)
    
    // Don't generate or accumulate progress if roster is full
    if game.politicianRoster.size >= maxRosterSize then
      return game.copy(lastPoliticianGeneration = currentTimeMillis)

    val speedMultiplier = politicianGenerationSpeedMultiplier(game)
    val baseIntervalMs = PoliticianGenerationIntervalSeconds * 1000L
    val lastTick = if game.lastPoliticianGeneration == 0L then currentTimeMillis else game.lastPoliticianGeneration
    val elapsedMs = currentTimeMillis - lastTick
    
    // Calculate progress increment based on elapsed time and speed multiplier
    // Progress of 1.0 = one full base interval has passed (at 1x speed)
    val progressIncrement = (elapsedMs.toDouble / baseIntervalMs) * speedMultiplier
    val newProgress = game.politicianGenerationProgress + progressIncrement
    
    if newProgress >= 1.0 then
      // Generate politicians for each full progress unit
      val politiciansToGenerate = newProgress.toInt
      val remainingProgress = newProgress - politiciansToGenerate
      
      // Only generate up to the remaining space in roster
      val availableSlots = maxRosterSize - game.politicianRoster.size
      val actualNewCount = math.min(politiciansToGenerate, availableSlots)
      val rareChance = rarePoliticianChance(game)
      val newPoliticians = (0 until actualNewCount).map: i =>
        generatePolitician(currentTimeMillis + i, rareChance)
      .toList
      
      // If we filled the roster, reset progress; otherwise keep remainder
      val finalProgress = if game.politicianRoster.size + actualNewCount >= maxRosterSize then 0.0 else remainingProgress
      
      game.copy(
        politicianRoster = game.politicianRoster ++ newPoliticians,
        lastPoliticianGeneration = currentTimeMillis,
        politicianGenerationProgress = finalProgress
      )
    else 
      game.copy(
        lastPoliticianGeneration = currentTimeMillis,
        politicianGenerationProgress = newProgress
      )

  // Discard a politician from the roster
  def discardPolitician(game: TileKingdomGame, politicianId: String): TileKingdomGame =
    game.copy(politicianRoster = game.politicianRoster.filterNot(_.id == politicianId))

  // Tick politician lifespans - only active politicians (in Town Halls) age
  // Returns the updated game and a list of destroyed politician names
  // Taverns in range slow down the decay by their multiplier
  def tickPoliticianLifespans(game: TileKingdomGame, elapsedMs: Long): (TileKingdomGame, List[String]) =
    val townHallCoords = game.tiles.toList.collect:
      case (coord, tile) if tile.tileType match
        case TileType.TownHall(Some(_)) => true
        case _ => false
      => coord

    var updatedTiles = game.tiles
    var destroyedPoliticians: List[String] = List.empty

    townHallCoords.foreach: coord =>
      updatedTiles.get(coord).foreach: tile =>
        tile.tileType match
          case TileType.TownHall(Some(politician)) =>
            // Calculate effective elapsed time based on tavern multiplier
            val lifespanMultiplier = politicianLifespanMultiplier(game, coord)
            val effectiveElapsedMs = (elapsedMs / lifespanMultiplier).toLong
            val newLifespan = politician.remainingLifespanMs - effectiveElapsedMs
            if newLifespan <= 0 then
              // Politician dies - remove from Town Hall
              val updatedTile = tile.copy(tileType = TileType.TownHall(None))
              updatedTiles = updatedTiles.updated(coord, updatedTile)
              destroyedPoliticians = destroyedPoliticians :+ politician.name
            else
              // Update politician's remaining lifespan
              val updatedPolitician = politician.copy(remainingLifespanMs = newLifespan)
              val updatedTile = tile.copy(tileType = TileType.TownHall(Some(updatedPolitician)))
              updatedTiles = updatedTiles.updated(coord, updatedTile)
          case _ => ()

    (game.copy(tiles = updatedTiles), destroyedPoliticians)

  // Base production per harvest (wheat per 10-second interval) - without bonuses
  def baseWheatProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.WheatField(level) => level * 5.0 // 5 wheat at level 1, 10 at level 2, etc. (per 10s)
    case _                          => 0.0

  // Base wood production per harvest (wood per 10-second interval)
  def baseWoodProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Woodcutter(level) => level * 3.0 // 3 wood at level 1, 6 at level 2, etc. (per 10s)
    case _                          => 0.0

  // Base faith production per harvest (faith per 10-second interval)
  def baseFaithProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Temple(level) => level * 2.0 // 2 faith at level 1, 4 at level 2, etc. (per 10s)
    case _                      => 0.0

  // Base stone production per harvest (stone per 10-second interval)
  def baseStoneProductionRate(tile: Tile): Double = tile.tileType match
    case TileType.Quarry(level) => level * 2.0 // 2 stone at level 1, 4 at level 2, etc. (per 10s)
    case _                      => 0.0

  // Legacy alias
  def baseProductionRate(tile: Tile): Double = baseWheatProductionRate(tile)

  // Production rate per second (for display and total income calculation)
  def productionPerSecond(tile: Tile): Double = baseWheatProductionRate(tile) / ProductionIntervalSeconds
  def woodProductionPerSecond(tile: Tile): Double = baseWoodProductionRate(tile) / ProductionIntervalSeconds
  def faithProductionPerSecond(tile: Tile): Double = baseFaithProductionRate(tile) / ProductionIntervalSeconds
  def stoneProductionPerSecond(tile: Tile): Double = baseStoneProductionRate(tile) / ProductionIntervalSeconds

  // Calculate farm bonus multiplier for a wheat field at given coord
  def farmBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    val farmBonus = coord.neighbors.toList.flatMap(game.tiles.get).collect:
      case tile if tile.isFarm => tile.level * FarmBoostPerLevel
    .sum
    1.0 + farmBonus

  // Production rate for a specific tile per second (with farm bonuses and town hall bonuses applied)
  def productionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = productionPerSecond(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord)
    else 0.0

  // Wood production rate for a specific tile per second (with forest group bonus and town hall bonuses)
  def woodProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = woodProductionPerSecond(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord)
    else 0.0

  // Faith production rate for a specific tile per second (with town hall bonuses and Wisdom2)
  def faithProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = faithProductionPerSecond(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord) * templeWisdom2Multiplier(game, tile.coord)
    else 0.0

  // Stone production rate for a specific tile per second (with town hall bonuses and Wisdom1)
  def stoneProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = stoneProductionPerSecond(tile)
    if base > 0 then base * townHallStoneMultiplier(game, tile.coord) * quarryWisdom1Multiplier(game, tile.coord)
    else 0.0

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = productionPerSecond(tile)

  // Production per harvest for a specific tile (with farm bonuses and town hall bonuses applied)
  def productionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWheatProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord)
    else 0.0

  // Wood production per harvest for a specific tile (with forest group bonus and town hall bonuses)
  def woodProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWoodProductionRate(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord)
    else 0.0

  // Faith production per harvest for a specific tile (with town hall bonuses and Wisdom2)
  def faithProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseFaithProductionRate(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord) * templeWisdom2Multiplier(game, tile.coord)
    else 0.0

  // Stone production per harvest for a specific tile (with town hall bonuses and Wisdom1)
  def stoneProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseStoneProductionRate(tile)
    if base > 0 then base * townHallStoneMultiplier(game, tile.coord) * quarryWisdom1Multiplier(game, tile.coord)
    else 0.0

  // Total wheat production rate for the game (all wheat fields with bonuses)
  def totalWheatProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => productionRate(game, tile)).sum

  // Total wood production rate
  def totalWoodProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => woodProductionRate(game, tile)).sum

  // Total faith production rate
  def totalFaithProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => faithProductionRate(game, tile)).sum

  // Total stone production rate
  def totalStoneProductionRate(game: TileKingdomGame): Double =
    game.unlockedTiles.map(tile => stoneProductionRate(game, tile)).sum

  // Cost to build a wheat field on an empty tile
  def wheatFieldBuildCost: Int = 10

  // Cost to build a farm on an empty tile
  def farmBuildCost: Int = 25

  // Cost to build a woodcutter on an empty tile
  def woodcutterBuildCost: Int = 20

  // Cost to build a bureau on an empty tile (costs wood, not wheat)
  def bureauBuildCost: Int = 500

  // Cost to build a temple on an empty tile (costs wood)
  def templeBuildCost: Int = TempleBuildCost

  // Cost to build a town hall on an empty tile (costs stone, scales with existing town halls)
  def townHallBuildCost(game: TileKingdomGame): Int =
    val existingTownHalls = game.tiles.values.count(_.isTownHall)
    val baseCost = TownHallBuildCost * math.pow(10, existingTownHalls).toInt
    if game.hasSkill(Skill.Management2) then baseCost / 10 else baseCost

  // Cost to build a quarry on an empty tile (costs wheat)
  def quarryBuildCost: Int = QuarryBuildCost

  // Legacy alias
  def buildCost: Int = wheatFieldBuildCost

  // Tier multiplier: 3x for every 10 levels (level 0-9: 1x, 10-19: 3x, 20-29: 9x, etc.)
  private def tierMultiplier(level: Int): Int =
    val tier = level / 10
    math.pow(3, tier).toInt

  // Cost to level up a wheat field
  def wheatFieldLevelUpCost(currentLevel: Int): Int =
    currentLevel * 20 * tierMultiplier(currentLevel) // Level 1→2 costs 20, 2→3 costs 40, etc.

  // Cost to level up a farm
  def farmLevelUpCost(currentLevel: Int): Int =
    currentLevel * 30 * tierMultiplier(currentLevel) // Level 1→2 costs 30, 2→3 costs 60, etc.

  // Cost to level up a woodcutter
  def woodcutterLevelUpCost(currentLevel: Int): Int =
    currentLevel * 25 * tierMultiplier(currentLevel) // Level 1→2 costs 25, 2→3 costs 50, etc.

  // Cost to level up a temple (costs wood)
  def templeLevelUpCost(currentLevel: Int): Int =
    currentLevel * 50 * tierMultiplier(currentLevel) // Level 1→2 costs 50 wood, 2→3 costs 100 wood, etc.

  // Cost to level up a quarry (costs wheat)
  def quarryLevelUpCost(currentLevel: Int): Int =
    currentLevel * 20 * tierMultiplier(currentLevel) // Level 1→2 costs 20 wheat, 2→3 costs 40 wheat, etc.

  // Cost to unlock next tile (linear with tier multiplier every 10 tiles)
  def tileUnlockCost(currentUnlockedCount: Int): Int =
    val tilesAfterInitial = math.max(0, currentUnlockedCount - InitialTileCount)
    if tilesAfterInitial == 0 then 100
    else
      val tier = tilesAfterInitial / 10
      val multiplier = math.pow(3, tier).toInt
      100 + tilesAfterInitial * 50 * multiplier

  // Gold reward for abdication based on total income rate
  def abdicationReward(totalIncomeRate: Double): Int =
    math.max(10, (totalIncomeRate * 20).toInt) // 20 gold per wheat/second

  // Create initial game state
  def newGame(currentTimeMillis: Long): TileKingdomGame =
    val initialTiles = InitialUnlockedCoords.map: coord =>
      coord -> Tile(
        coord = coord,
        tileType = TileType.Empty,
        unlocked = true
      )
    .toMap

    // Start with one politician in the roster
    val initialPolitician = generatePolitician(currentTimeMillis)

    TileKingdomGame(
      tiles = initialTiles,
      wheat = 50.0, // Start with some wheat to build first field
      wood = 0.0,
      faith = 0.0,
      gold = 0,
      lastTickTime = currentTimeMillis,
      totalAbdications = 0,
      politicianRoster = List(initialPolitician),
      lastPoliticianGeneration = currentTimeMillis
    )

  // Tick the game: accumulate wheat based on production rate
  def tick(game: TileKingdomGame, currentTimeMillis: Long): TileKingdomGame =
    val elapsedSeconds = (currentTimeMillis - game.lastTickTime) / 1000.0
    val wheatProduced = totalWheatProductionRate(game) * elapsedSeconds
    val woodProduced = totalWoodProductionRate(game) * elapsedSeconds
    val faithProduced = totalFaithProductionRate(game) * elapsedSeconds
    val stoneProduced = totalStoneProductionRate(game) * elapsedSeconds

    game.copy(
      wheat = game.wheat + wheatProduced,
      wood = game.wood + woodProduced,
      faith = game.faith + faithProduced,
      stone = game.stone + stoneProduced,
      lastTickTime = currentTimeMillis
    )

  // Build a wheat field on an empty tile
  def buildWheatField(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                           => Left("Tile not found")
      case Some(tile) if !tile.unlocked                   => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                    => Left("Tile is not empty")
      case Some(tile) if game.wheat < wheatFieldBuildCost => Left(s"Not enough wheat (need $wheatFieldBuildCost)")
      case Some(tile) =>
        val startLevel = if game.hasSkill(Skill.Agriculture1) then 10 else 1
        val updatedTile = tile.copy(tileType = TileType.WheatField(startLevel))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - wheatFieldBuildCost
        ))

  // Build a farm on an empty tile (requires at least one wheat field)
  def buildFarm(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                     => Left("Tile not found")
      case Some(tile) if !tile.unlocked             => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty              => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField           => Left("Build a wheat field first")
      case Some(tile) if game.wheat < farmBuildCost => Left(s"Not enough wheat (need $farmBuildCost)")
      case Some(tile) =>
        val startLevel = if game.hasSkill(Skill.Agriculture2) then 10 else 1
        val updatedTile = tile.copy(tileType = TileType.Farm(startLevel))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - farmBuildCost
        ))

  // Build a woodcutter on an empty tile (requires at least one wheat field)
  def buildWoodcutter(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                           => Left("Tile not found")
      case Some(tile) if !tile.unlocked                   => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                    => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField                 => Left("Build a wheat field first")
      case Some(tile) if game.wheat < woodcutterBuildCost => Left(s"Not enough wheat (need $woodcutterBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Woodcutter(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wheat = game.wheat - woodcutterBuildCost
        ))

  // Build a bureau on an empty tile (costs wood, requires at least one wheat field)
  def buildBureau(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                      => Left("Tile not found")
      case Some(tile) if !tile.unlocked              => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty               => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField            => Left("Build a wheat field first")
      case Some(tile) if game.wood < bureauBuildCost => Left(s"Not enough wood (need $bureauBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Bureau(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - bureauBuildCost
        ))

  // Build a temple on an empty tile (costs wood, requires at least one wheat field)
  def buildTemple(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                       => Left("Tile not found")
      case Some(tile) if !tile.unlocked               => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty                => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField             => Left("Build a wheat field first")
      case Some(tile) if game.wood < templeBuildCost  => Left(s"Not enough wood (need $templeBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Temple(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - templeBuildCost
        ))

  // Build a town hall on an empty tile (costs stone, requires at least one wheat field)
  def buildTownHall(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    val cost = townHallBuildCost(game)
    game.tiles.get(coord) match
      case None                                => Left("Tile not found")
      case Some(tile) if !tile.unlocked        => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty         => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField      => Left("Build a wheat field first")
      case Some(tile) if game.stone < cost     => Left(s"Not enough stone (need $cost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.TownHall(None))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          stone = game.stone - cost
        ))

  // Build a quarry on an empty tile (costs wood, requires at least one wheat field)
  def buildQuarry(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                      => Left("Tile not found")
      case Some(tile) if !tile.unlocked              => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty               => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField            => Left("Build a wheat field first")
      case Some(tile) if game.wood < quarryBuildCost => Left(s"Not enough wood (need $quarryBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Quarry(1))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - quarryBuildCost
        ))

  // Build an academy on an empty tile (costs stone, requires at least one wheat field)
  def buildAcademy(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    val cost = academyBuildCost(game)
    game.tiles.get(coord) match
      case None                                => Left("Tile not found")
      case Some(tile) if !tile.unlocked        => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty         => Left("Tile is not empty")
      case Some(_) if !game.hasWheatField      => Left("Build a wheat field first")
      case Some(tile) if game.stone < cost     => Left(s"Not enough stone (need $cost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Academy(AcademyMode.FasterPoliticians))
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          stone = game.stone - cost
        ))

  // Build a tavern on an empty tile (costs wood, requires at least one town hall)
  def buildTavern(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                                      => Left("Tile not found")
      case Some(tile) if !tile.unlocked              => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty               => Left("Tile is not empty")
      case Some(_) if !game.canBuildTavern           => Left("Build a town hall first")
      case Some(tile) if game.wood < TavernBuildCost => Left(s"Not enough wood (need $TavernBuildCost)")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Tavern)
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile),
          wood = game.wood - TavernBuildCost
        ))

  // Toggle academy mode between FasterPoliticians and RareChance
  def toggleAcademyMode(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.Academy(currentMode) =>
          val newMode = currentMode match
            case AcademyMode.FasterPoliticians => AcademyMode.RareChance
            case AcademyMode.RareChance => AcademyMode.FasterPoliticians
          val updatedTile = tile.copy(tileType = TileType.Academy(newMode))
          Right(game.copy(tiles = game.tiles.updated(coord, updatedTile)))
        case _ => Left("Tile is not an academy")

  // Assign a politician from the roster to a town hall (allows swapping)
  def assignPolitician(game: TileKingdomGame, politicianId: String, townHallCoord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(existingPolitician) =>
          game.politicianRoster.find(_.id == politicianId) match
            case None => Left("Politician not found in roster")
            case Some(newPolitician) =>
              val updatedTile = tile.copy(tileType = TileType.TownHall(Some(newPolitician)))
              // Remove new politician from roster, add existing one back if present
              val updatedRoster = game.politicianRoster.filterNot(_.id == politicianId) ++ existingPolitician.toList
              Right(game.copy(
                tiles = game.tiles.updated(townHallCoord, updatedTile),
                politicianRoster = updatedRoster
              ))
        case _ => Left("Tile is not a town hall")

  // Remove a politician from a town hall back to the roster
  def removePolitician(game: TileKingdomGame, townHallCoord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(Some(politician)) =>
          val updatedTile = tile.copy(tileType = TileType.TownHall(None))
          Right(game.copy(
            tiles = game.tiles.updated(townHallCoord, updatedTile),
            politicianRoster = game.politicianRoster :+ politician
          ))
        case TileType.TownHall(None) => Left("Town Hall has no politician")
        case _ => Left("Tile is not a town hall")

  // Swap politicians between two town halls (or move from one to an empty one)
  def swapPoliticians(game: TileKingdomGame, fromCoord: Coord, toCoord: Coord): Either[String, TileKingdomGame] =
    if fromCoord == toCoord then return Left("Cannot swap with self")

    (game.tiles.get(fromCoord), game.tiles.get(toCoord)) match
      case (Some(fromTile), Some(toTile)) =>
        (fromTile.tileType, toTile.tileType) match
          case (TileType.TownHall(Some(fromPol)), TileType.TownHall(toPol)) =>
            val updatedFromTile = fromTile.copy(tileType = TileType.TownHall(toPol))
            val updatedToTile = toTile.copy(tileType = TileType.TownHall(Some(fromPol)))
            Right(game.copy(
              tiles = game.tiles
                .updated(fromCoord, updatedFromTile)
                .updated(toCoord, updatedToTile)
            ))
          case (TileType.TownHall(None), _) => Left("Source town hall has no politician")
          case (_, TileType.TownHall(_)) => Left("Source is not a town hall")
          case _ => Left("Target is not a town hall")
      case (None, _) => Left("Source tile not found")
      case (_, None) => Left("Target tile not found")

  // Level up a wheat field
  def levelUpWheatField(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.WheatField(level) =>
            val cost = wheatFieldLevelUpCost(level)
            if game.wheat < cost then
              Left(s"Not enough wheat (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.WheatField(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wheat = game.wheat - cost
              ))
          case _ => Left("Tile is not a wheat field")

  // Level up a farm
  def levelUpFarm(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.Farm(level) =>
            val cost = farmLevelUpCost(level)
            if game.wheat < cost then
              Left(s"Not enough wheat (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.Farm(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wheat = game.wheat - cost
              ))
          case _ => Left("Tile is not a farm")

  // Level up a woodcutter
  def levelUpWoodcutter(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.Woodcutter(level) =>
            val cost = woodcutterLevelUpCost(level)
            if game.wheat < cost then
              Left(s"Not enough wheat (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.Woodcutter(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wheat = game.wheat - cost
              ))
          case _ => Left("Tile is not a woodcutter")

  // Level up a temple (costs wood)
  def levelUpTemple(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.Temple(level) =>
            val cost = templeLevelUpCost(level)
            if game.wood < cost then
              Left(s"Not enough wood (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.Temple(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wood = game.wood - cost
              ))
          case _ => Left("Tile is not a temple")

  // Level up a quarry (costs wood)
  def levelUpQuarry(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
          case TileType.Quarry(level) =>
            val cost = quarryLevelUpCost(level)
            if game.wood < cost then
              Left(s"Not enough wood (need $cost)")
            else
              val updatedTile = tile.copy(tileType = TileType.Quarry(level + 1))
              Right(game.copy(
                tiles = game.tiles.updated(coord, updatedTile),
                wood = game.wood - cost
              ))
          case _ => Left("Tile is not a quarry")

  // Toggle bureau turbo mode on/off
  def toggleBureauTurbo(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.isBureau => Left("Tile is not a bureau")
      case Some(_) =>
        val currentTurbo = game.bureauTurboMode.getOrElse(coord, false)
        Right(game.copy(
          bureauTurboMode = game.bureauTurboMode.updated(coord, !currentTurbo)
        ))

  // Check if bureau is in turbo mode
  def isBureauTurbo(game: TileKingdomGame, bureauCoord: Coord): Boolean =
    game.bureauTurboMode.getOrElse(bureauCoord, false)

  // Calculate turbo faith cost based on target tile level (level × 10)
  def bureauTurboFaithCostForLevel(level: Int): Int = level * 10

  // Get bureau speed multiplier (1.0 = slow mode, 10.0 = turbo mode)
  def bureauSpeedMultiplier(game: TileKingdomGame, bureauCoord: Coord): Double =
    if isBureauTurbo(game, bureauCoord) then BureauTurboSpeedMultiplier else 1.0

  // Bureau auto-upgrade: upgrade the tile with lowest upgrade cost within radius
  // Returns updated game and the coord that was upgraded (if any)
  // Compares costs numerically - all resources are treated as equivalent
  // In turbo mode: costs 100 faith + 100 wood per upgrade, auto-disables if not enough resources
  def bureauAutoUpgrade(
                         game: TileKingdomGame,
                         bureauCoord: Coord,
                         currentTimeMillis: Long
  ): Option[(TileKingdomGame, Coord)] =
    game.tiles.get(bureauCoord) match
      case Some(bureauTile) if bureauTile.isBureau =>
        val isTurbo = isBureauTurbo(game, bureauCoord)
        
        // Find upgradeable tiles within radius with their costs
        val nearbyCoords = bureauCoord.neighborsWithinRadius(BureauRadius)
        val upgradeableTiles = nearbyCoords
          .flatMap(coord => game.tiles.get(coord).map(coord -> _))
          .filter((_, tile) => tile.isUpgradeable)
          .flatMap((coord, tile) => tile.upgradeCost.map(cost => (coord, tile, cost)))

        // Check if we have enough resources for turbo mode
        // Faith cost is level × 10, so check against the cheapest upgradeable tile
        val canAffordWood = game.wood >= BureauWoodCostPerUpgrade
        val minLevelTile = upgradeableTiles.minByOption(_._2.level)
        val minFaithCost = minLevelTile.map(t => bureauTurboFaithCostForLevel(t._2.level)).getOrElse(Int.MaxValue)
        val canAffordFaith = game.faith >= minFaithCost

        // Auto-disable turbo mode if we can't afford wood or faith for the cheapest tile
        val gameWithTurboCheck =
          if isTurbo && (!canAffordWood || !canAffordFaith) then
            game.copy(bureauTurboMode = game.bureauTurboMode.updated(bureauCoord, false))
          else game
        
        val effectiveIsTurbo = isBureauTurbo(gameWithTurboCheck, bureauCoord)

        // Must have wood for the bureau fee regardless of what we upgrade
        if gameWithTurboCheck.wood < BureauWoodCostPerUpgrade then 
          // Return game with turbo disabled if it was disabled
          if gameWithTurboCheck ne game then return Some((gameWithTurboCheck, bureauCoord))
          else return None

        // Filter to only tiles we can afford (including bureau wood fee for wood-cost upgrades)
        // In turbo mode, also check if we can afford the faith cost for each tile's level
        val affordableTiles = upgradeableTiles.filter: (_, tile, cost) =>
          val extraWoodNeeded = if cost.resource == Resource.Wood then BureauWoodCostPerUpgrade else 0
          val canAffordUpgrade = gameWithTurboCheck.canAfford(cost.amount + extraWoodNeeded, cost.resource)
          val canAffordTurboFaith = !effectiveIsTurbo || gameWithTurboCheck.faith >= bureauTurboFaithCostForLevel(tile.level)
          canAffordUpgrade && canAffordTurboFaith

        // Select the tile with the lowest upgrade cost (comparing numerically)
        affordableTiles.minByOption(_._3.amount) match
          case Some((targetCoord, targetTile, cost)) =>
            // Perform the upgrade based on tile type
            val upgradedTileType = targetTile.tileType match
              case TileType.WheatField(lvl) => TileType.WheatField(lvl + 1)
              case TileType.Farm(lvl)       => TileType.Farm(lvl + 1)
              case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl + 1)
              case TileType.Temple(lvl)     => TileType.Temple(lvl + 1)
              case TileType.Quarry(lvl)     => TileType.Quarry(lvl + 1)
              case other                    => other

            val upgradedTile = targetTile.copy(tileType = upgradedTileType)

            // Deduct upgrade cost and bureau fee from the turbo-checked game
            val afterUpgradeCost = gameWithTurboCheck.deduct(cost)
            val afterBureauFee = afterUpgradeCost.copy(wood = afterUpgradeCost.wood - BureauWoodCostPerUpgrade)
            
            // Deduct faith cost if effectively in turbo mode (level × 10)
            val turboFaithCost = bureauTurboFaithCostForLevel(targetTile.level)
            val afterTurboCost =
              if effectiveIsTurbo then afterBureauFee.copy(faith = afterBureauFee.faith - turboFaithCost)
              else afterBureauFee

            val newGame = afterTurboCost.copy(
              tiles = gameWithTurboCheck.tiles.updated(targetCoord, upgradedTile)
            )
            Some((newGame, targetCoord))
          case None =>
            // No affordable upgrade, but return game with turbo disabled if it was disabled
            if gameWithTurboCheck ne game then Some((gameWithTurboCheck, bureauCoord))
            else None
      case _ => None

  // Destroy a building on a tile (returns it to empty state, no refund)
  def destroyBuilding(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                         => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if tile.isEmpty   => Left("Tile is already empty")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Empty)
        Right(game.copy(
          tiles = game.tiles.updated(coord, updatedTile)
        ))

  // Abdicate: reset tiles, gain gold based on income rate
  def abdicate(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
    if !game.allTilesFilled then
      Left("Must fill all unlocked tiles with buildings before abdicating")
    else
      val goldReward = abdicationReward(game.totalIncomeRate)

      val resetTiles = game.tiles.map:
        case (coord, tile) if tile.unlocked =>
          coord -> tile.copy(tileType = TileType.Empty)
        case (coord, tile) =>
          coord -> tile

      Right(game.copy(
        tiles = resetTiles,
        wheat = 50.0, // Reset wheat, give starting amount
        wood = 0.0, // Reset wood
        faith = 0.0, // Reset faith
        stone = 0.0, // Reset stone
        gold = game.gold + goldReward,
        lastTickTime = currentTimeMillis,
        totalAbdications = game.totalAbdications + 1,
        bureauTurboMode = Map.empty, // Reset bureau turbo mode since bureaus are destroyed
        politicianRoster = List.empty, // All politicians are destroyed on abdication
        politicianGenerationProgress = 0.0 // Reset politician generation progress
      ))

  // Sail: second tier prestige - reset everything including gold, gain legacy points for tiles
  def sail(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
    if !game.canSail then
      Left(s"Must have at least $SailMinTiles tiles to sail")
    else
      val tilesDestroyed = game.unlockedTiles.size
      val totalLegacyPoints = game.legacyPoints + tilesDestroyed
      val skillPointsEarned = totalLegacyPoints / LegacyPointsPerSkillPoint
      val remainingLegacyPoints = totalLegacyPoints % LegacyPointsPerSkillPoint

      // Reset to initial 4 tiles, all empty
      val initialTiles = InitialUnlockedCoords.map: coord =>
        coord -> Tile(coord = coord, tileType = TileType.Empty, unlocked = true)
      .toMap

      Right(game.copy(
        tiles = initialTiles,
        wheat = 50.0, // Reset to starting amount
        wood = 0.0,
        faith = 0.0,
        stone = 0.0,
        gold = 0, // Gold resets on sail
        lastTickTime = currentTimeMillis,
        totalAbdications = 0, // Abdications reset on sail
        bureauTurboMode = Map.empty,
        politicianRoster = List.empty,
        politicianGenerationProgress = 0.0,
        legacyPoints = remainingLegacyPoints,
        skillPoints = game.skillPoints + skillPointsEarned,
        hasSailed = true // Mark that player has sailed at least once
      ))

  // Get all coords that can be unlocked (coords adjacent to unlocked tiles that aren't already tiles)
  def unlockableCoords(game: TileKingdomGame): Set[Coord] =
    val unlockedCoords = game.unlockedTiles.map(_.coord).toSet
    val allAdjacentToUnlocked = unlockedCoords.flatMap(_.neighbors)
    allAdjacentToUnlocked.filterNot(game.tiles.contains)

  // Unlock a specific tile with gold (must be adjacent to an unlocked tile)
  def unlockTile(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    if game.tiles.contains(coord) then
      Left("Tile already exists")
    else if !unlockableCoords(game).contains(coord) then
      Left("Can only unlock tiles adjacent to your territory")
    else
      val cost = tileUnlockCost(game.unlockedTiles.size)
      if game.gold < cost then
        Left(s"Not enough gold (need $cost)")
      else
        val newTile = Tile(coord = coord, tileType = TileType.Empty, unlocked = true)
        Right(game.copy(
          tiles = game.tiles.updated(coord, newTile),
          gold = game.gold - cost
        ))

  // Simple Perlin-like noise for continent generation
  private def noise2D(x: Double, y: Double, seed: Long): Double =
    val random = new scala.util.Random(seed ^ (x.toLong * 73856093L) ^ (y.toLong * 19349663L))
    random.nextDouble()

  private def smoothNoise(x: Double, y: Double, seed: Long): Double =
    val x0 = x.floor.toInt
    val y0 = y.floor.toInt
    val fx = x - x0
    val fy = y - y0

    // Get values at corners
    val v00 = noise2D(x0, y0, seed)
    val v10 = noise2D(x0 + 1, y0, seed)
    val v01 = noise2D(x0, y0 + 1, seed)
    val v11 = noise2D(x0 + 1, y0 + 1, seed)

    // Smooth interpolation
    val sx = fx * fx * (3 - 2 * fx)
    val sy = fy * fy * (3 - 2 * fy)

    val i0 = v00 * (1 - sx) + v10 * sx
    val i1 = v01 * (1 - sx) + v11 * sx
    i0 * (1 - sy) + i1 * sy

  private def perlinNoise(x: Double, y: Double, seed: Long, octaves: Int = 3): Double =
    var total = 0.0
    var frequency = 1.0
    var amplitude = 1.0
    var maxValue = 0.0

    for _ <- 0 until octaves do
      total += smoothNoise(x * frequency, y * frequency, seed) * amplitude
      maxValue += amplitude
      amplitude *= 0.5
      frequency *= 2

    total / maxValue

  // Dev tool: Unlock many tiles for free (creates continent-like shapes)
  def unlockManyTiles(game: TileKingdomGame, count: Int): TileKingdomGame =
    val random = new scala.util.Random(System.currentTimeMillis())

    // Pick 3-5 random growth directions (angles in radians)
    val numDirections = 3 + random.nextInt(3)
    val growthDirections = (0 until numDirections).map: _ =>
      random.nextDouble() * 2 * math.Pi
    .toList

    // Each direction has a random "strength"
    val directionStrengths = growthDirections.map(_ => 0.5 + random.nextDouble() * 0.5)

    // Find center of current territory
    val startCoords = game.tiles.keySet
    val startCenterRow = startCoords.map(_.row).sum.toDouble / startCoords.size
    val startCenterCol = startCoords.map(_.col).sum.toDouble / startCoords.size

    (1 to count).foldLeft(game): (currentGame, i) =>
      val available = unlockableCoords(currentGame)
      if available.isEmpty then currentGame
      else
        val currentCoords = currentGame.tiles.keySet

        // Score each candidate
        val scored = available.toList.map: coord =>
          val neighborCount = coord.neighbors.count(currentCoords.contains)

          // Calculate angle from start center to this coord
          val dx = coord.col - startCenterCol
          val dy = coord.row - startCenterRow
          val angle = math.atan2(dy, dx)

          // Score based on alignment with growth directions
          val directionScore = growthDirections.zip(directionStrengths).map: (dir, strength) =>
            val angleDiff = math.abs(((angle - dir) + math.Pi) % (2 * math.Pi) - math.Pi)
            val alignment = math.cos(angleDiff) // 1.0 when aligned, -1.0 when opposite
            if alignment > 0 then alignment * strength else 0.0
          .max

          // Add some noise for organic feel
          val noise = random.nextDouble() * 0.3

          // Only fill holes when really necessary (7-8 neighbors)
          val holeScore = neighborCount match
            case 8 => 3.0 // Must fill
            case 7 => 2.0 // Should fill
            case _ => 0.0 // Don't prioritize filling

          // Prefer tiles on the edge (1-3 neighbors) for exploration
          val edgeBonus = neighborCount match
            case 1 => 0.8
            case 2 => 1.0
            case 3 => 0.9
            case _ => 0.5

          val finalScore = directionScore * edgeBonus + noise + holeScore
          (coord, finalScore)

        // Pick the best candidate
        val best = scored.maxBy(_._2)._1

        val newTile = Tile(coord = best, tileType = TileType.Empty, unlocked = true)
        currentGame.copy(tiles = currentGame.tiles.updated(best, newTile))

  // Unlock a skill from the skill tree
  def unlockSkill(game: TileKingdomGame, skill: Skill): Either[String, TileKingdomGame] =
    if !game.hasSailed then
      Left("Sail at least once to unlock the skill tree")
    else if game.unlockedSkills.contains(skill) then
      Left("Skill already unlocked")
    else if game.skillPoints < Skill.cost(skill) then
      Left(s"Not enough skill points (need ${Skill.cost(skill)})")
    else
      Skill.prerequisite(skill) match
        case Some(prereq) if !game.unlockedSkills.contains(prereq) =>
          Left(s"Must unlock ${Skill.branchName(prereq)} ${Skill.cost(prereq)} first")
        case _ =>
          Right(game.copy(
            skillPoints = game.skillPoints - Skill.cost(skill),
            unlockedSkills = game.unlockedSkills + skill
          ))


package shared.TileKingdom

import upickle.default.{ReadWriter, Reader, Writer, readwriter}

import scala.annotation.tailrec

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

  /** Rectangle of coords in a direction from this coord (excluding self).
    * length extends in the given direction, width extends perpendicular.
    * For example, Up with length=5, halfWidth=1 gives a 3-wide, 5-tall rectangle above.
    */
  def rectangleInDirection(direction: BureauDirection, length: Int, halfWidth: Int): Set[Coord] =
    direction match
      case BureauDirection.Center => neighborsWithinRadius(2) // fallback to default radius
      case BureauDirection.Up =>
        (for
          r <- -length to -1
          c <- -halfWidth to halfWidth
        yield Coord(row + r, col + c)).toSet
      case BureauDirection.Down =>
        (for
          r <- 1 to length
          c <- -halfWidth to halfWidth
        yield Coord(row + r, col + c)).toSet
      case BureauDirection.Left =>
        (for
          r <- -halfWidth to halfWidth
          c <- -length to -1
        yield Coord(row + r, col + c)).toSet
      case BureauDirection.Right =>
        (for
          r <- -halfWidth to halfWidth
          c <- 1 to length
        yield Coord(row + r, col + c)).toSet

// ============================================================================
// Tile Types
// ============================================================================

enum AcademyMode derives ReadWriter:
  case FasterPoliticians // 2x politician generation speed
  case RareChance // +10% rare politician chance

enum BureauMode derives ReadWriter:
  case Slow     // Normal speed, costs wood only
  case Turbo    // 10x speed, costs wood + faith
  case Disabled // Paused, no upgrades

enum BureauDirection derives ReadWriter:
  case Center // Default: 2-tile radius (circle)
  case Up     // 5x3 rectangle extending upward
  case Down   // 5x3 rectangle extending downward
  case Left   // 3x5 rectangle extending left
  case Right  // 3x5 rectangle extending right

// ============================================================================
// Skill Tree System
// ============================================================================

// Individual skill nodes within a branch
enum Skill derives ReadWriter:
  // Agriculture branch (dual track at level 1, 2, and 3)
  case Agriculture1A // Fields start at level 10
  case Agriculture1B // Farms affect neighboring forests at 50% effectiveness
  case Agriculture2A // Fields work 50% faster
  case Agriculture2B // Farms affect neighboring quarries at 50% effectiveness
  case Agriculture3A // Wheat field upgrade costs reduced by 99%
  case Agriculture3B // Farms affect neighboring temples at 50% effectiveness
  // Management branch (dual track at level 1, 2, and 3)
  case Management1A // Bureau costs 0 wood to build and upgrades consume 0 wood
  case Management1B // Bureau can be directed (5x3 rectangle instead of 2-tile radius)
  case Management2A // Town halls cost 90% less stone to build
  case Management2B // Town halls can be directed (5x3 rectangle instead of 2-tile radius)
  case Management3A // Town halls can hold 2 politicians at the same time
  case Management3B // Normal politicians have 2 effects, rare politicians have 3 effects
  // Wisdom branch
  case Wisdom1 // Quarries produce 25% more stone for each neighboring forest
  case Wisdom2 // Each forest grants 50% increased faith production to neighboring temples
  // Education branch
  case Education1 // Academies are 10x cheaper
  case Education2 // Academies have both modes active at the same time
  // Logistics branch (dual track at level 1)
  case Logistics1A // Bureau wood cost reduced by 90%
  case Logistics1B // Bureau turbo faith cost reduced by 90%

object Skill:
  // Get skill branch name
  def branchName(skill: Skill): String = skill match
    case Agriculture1A | Agriculture1B | Agriculture2A | Agriculture2B | Agriculture3A | Agriculture3B => "Agriculture"
    case Management1A | Management1B | Management2A | Management2B | Management3A | Management3B => "Management"
    case Wisdom1 | Wisdom2 => "Wisdom"
    case Education1 | Education2 => "Education"
    case Logistics1A | Logistics1B => "Logistics"

  // Get skill description
  def description(skill: Skill): String = skill match
    case Agriculture1A => "New wheat fields start at level 10"
    case Agriculture1B => "Farms affect neighboring forests at 50% effectiveness"
    case Agriculture2A => "Wheat fields produce twice as fast (50% shorter interval)"
    case Agriculture2B => "Farms affect neighboring quarries at 50% effectiveness"
    case Management1A => "Bureaus cost 0 wood to build and upgrade"
    case Management1B => "Bureaus can be directed (5×3 rectangle)"
    case Management2A => "Town halls cost 90% less stone to build"
    case Management2B => "Town Halls can be directed (5×3 rectangle)"
    case Management3A => "Town Halls hold 2 politicians at once"
    case Management3B => "Normal politicians have 2 effects, rare have 3"
    case Wisdom1 => "+25% quarry stone output per neighboring forest"
    case Wisdom2 => "+50% temple faith output per neighboring forest"
    case Education1 => "Academies cost 10x less stone to build"
    case Education2 => "Academies run both modes simultaneously"
    case Logistics1A => "Bureaus spend 90% less wood per upgrade (100→10)"
    case Logistics1B => "Bureau turbo spends 90% less faith per upgrade"
    case Agriculture3A => "Wheat field upgrade costs reduced by 99%"
    case Agriculture3B => "Farms affect neighboring temples at 50% effectiveness"

  // Get skill cost
  def cost(skill: Skill): Int = 1

  // Get skill tier (for grouping in UI, independent of cost)
  def tier(skill: Skill): Int = skill match
    case Agriculture1A | Agriculture1B => 1
    case Agriculture2A | Agriculture2B => 2
    case Agriculture3A | Agriculture3B => 3
    case Management1A | Management1B => 1
    case Management2A | Management2B => 2
    case Management3A | Management3B => 3
    case Wisdom1 => 1
    case Wisdom2 => 2
    case Education1 => 1
    case Education2 => 2
    case Logistics1A | Logistics1B => 1

  // Get prerequisite skill (if any)
  def prerequisite(skill: Skill): Option[Skill] = skill match
    case Agriculture1A | Agriculture1B | Management1A | Management1B | Wisdom1 | Education1 | Logistics1A | Logistics1B => None
    case Agriculture2A | Agriculture2B => None // Either Agriculture1A or Agriculture1B, handled by alternativePrerequisites
    case Agriculture3A | Agriculture3B => None // Either Agriculture2A or Agriculture2B, handled by alternativePrerequisites
    case Management2A | Management2B => None // Either Management1A or Management1B, handled by alternativePrerequisites
    case Management3A | Management3B => None // Either Management2A or Management2B, handled by alternativePrerequisites
    case Wisdom2 => Some(Wisdom1)
    case Education2 => Some(Education1)

  // Get alternative prerequisites (for dual track choices)
  // Returns the set of skills where having ANY ONE unlocked satisfies the prerequisite
  def alternativePrerequisites(skill: Skill): Option[Set[Skill]] = skill match
    case Agriculture2A | Agriculture2B => Some(Set(Agriculture1A, Agriculture1B))
    case Agriculture3A | Agriculture3B => Some(Set(Agriculture2A, Agriculture2B))
    case Management2A | Management2B => Some(Set(Management1A, Management1B))
    case Management3A | Management3B => Some(Set(Management2A, Management2B))
    case _ => None

  // Get mutually exclusive skill (choosing one locks out the other)
  def mutuallyExclusive(skill: Skill): Option[Skill] = skill match
    case Agriculture1A => Some(Agriculture1B)
    case Agriculture1B => Some(Agriculture1A)
    case Agriculture2A => Some(Agriculture2B)
    case Agriculture2B => Some(Agriculture2A)
    case Agriculture3A => Some(Agriculture3B)
    case Agriculture3B => Some(Agriculture3A)
    case Management1A => Some(Management1B)
    case Management1B => Some(Management1A)
    case Management2A => Some(Management2B)
    case Management2B => Some(Management2A)
    case Management3A => Some(Management3B)
    case Management3B => Some(Management3A)
    case Logistics1A => Some(Logistics1B)
    case Logistics1B => Some(Logistics1A)
    case _ => None

  // Get all skills in a branch, in order (dual track alternatives grouped together)
  def branchSkills(branchName: String): List[Skill] = branchName match
    case "Agriculture" => List(Agriculture1A, Agriculture1B, Agriculture2A, Agriculture2B, Agriculture3A, Agriculture3B)
    case "Management" => List(Management1A, Management1B, Management2A, Management2B, Management3A, Management3B)
    case "Wisdom" => List(Wisdom1, Wisdom2)
    case "Education" => List(Education1, Education2)
    case "Logistics" => List(Logistics1A, Logistics1B)
    case _ => List.empty

  // Get all branch names
  val allBranches: List[String] = List("Agriculture", "Management", "Wisdom", "Education", "Logistics")

  // Get emoji for branch
  def branchEmoji(branchName: String): String = branchName match
    case "Agriculture" => "🌾"
    case "Management" => "📋"
    case "Wisdom" => "📿"
    case "Education" => "📚"
    case "Logistics" => "🏛️"
    case _ => "❓"

enum TileType:
  case Empty
  case WheatField(level: Int) // level determines production rate
  case Farm(level: Int) // boosts nearby wheat fields
  case Woodcutter(level: Int) // produces wood
  case Bureau(level: Int) // auto-upgrades nearby buildings, costs wood
  case Temple(level: Int) // produces faith, costs wood
  case TownHall(politicians: List[Politician]) // holds politician(s) - Management3A allows 2
  case Quarry(level: Int) // produces stone
  case Academy(mode: AcademyMode) // boosts politician generation or rare chance
  case Tavern // extends politician lifespan in nearby Town Halls by 2x

object TileType:
  /** Custom ReadWriter that handles backward-compatible deserialization.
    * Old saves stored TownHall as {"\$type":"TownHall","politician":<Option>}.
    * New format stores TownHall as {"\$type":"TownHall","politicians":<List>}.
    */
  given ReadWriter[TileType] = readwriter[ujson.Value].bimap[TileType](
    // Write: serialize to JSON
    {
      case Empty => ujson.Obj("$type" -> "Empty")
      case WheatField(level) => ujson.Obj("$type" -> "WheatField", "level" -> level)
      case Farm(level) => ujson.Obj("$type" -> "Farm", "level" -> level)
      case Woodcutter(level) => ujson.Obj("$type" -> "Woodcutter", "level" -> level)
      case Bureau(level) => ujson.Obj("$type" -> "Bureau", "level" -> level)
      case Temple(level) => ujson.Obj("$type" -> "Temple", "level" -> level)
      case TownHall(politicians) =>
        val polJson = upickle.default.writeJs(politicians)
        ujson.Obj("$type" -> "TownHall", "politicians" -> polJson)
      case Quarry(level) => ujson.Obj("$type" -> "Quarry", "level" -> level)
      case Academy(mode) =>
        val modeJson = upickle.default.writeJs(mode)
        ujson.Obj("$type" -> "Academy", "mode" -> modeJson)
      case Tavern => ujson.Obj("$type" -> "Tavern")
    },
    // Read: deserialize from JSON, handling old Option format for TownHall
    json =>
      json("$type").str match
        case "Empty" => Empty
        case "WheatField" => WheatField(json("level").num.toInt)
        case "Farm" => Farm(json("level").num.toInt)
        case "Woodcutter" => Woodcutter(json("level").num.toInt)
        case "Bureau" => Bureau(json("level").num.toInt)
        case "Temple" => Temple(json("level").num.toInt)
        case "TownHall" =>
          // Backward compat: old format had "politician" (Option), new has "politicians" (List)
          if json.obj.contains("politicians") then
            val pols = upickle.default.read[List[Politician]](json("politicians"))
            TownHall(pols)
          else if json.obj.contains("politician") then
            val polOpt = upickle.default.read[Option[Politician]](json("politician"))
            TownHall(polOpt.toList)
          else
            TownHall(List.empty)
        case "Quarry" => Quarry(json("level").num.toInt)
        case "Academy" =>
          val mode = upickle.default.read[AcademyMode](json("mode"))
          Academy(mode)
        case "Tavern" => Tavern
  )

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
    tertiaryEffect: Option[PoliticianEffect] = None, // Rare politicians with Management3B have three effects
    remainingLifespanMs: Long = 600000L // 10 minutes = 600,000 ms
) derives ReadWriter:
  def isRare: Boolean = secondaryEffect.isDefined
  
  /** All effects this politician has (1, 2, or 3) */
  def allEffects: List[PoliticianEffect] =
    effect :: secondaryEffect.toList ::: tertiaryEffect.toList
  
  private def describeEffect(eff: PoliticianEffect): String = eff match
    case PoliticianEffect.WheatProductionMultiplier(m) => s"${(m * 100).toInt}% wheat"
    case PoliticianEffect.WoodProductionMultiplier(m)  => s"${(m * 100).toInt}% wood"
    case PoliticianEffect.FaithProductionMultiplier(m) => s"${(m * 100).toInt}% faith"
    case PoliticianEffect.StoneProductionMultiplier(m) => s"${(m * 100).toInt}% stone"
    case PoliticianEffect.AllProductionMultiplier(m)   => s"${(m * 100).toInt}% all"
  
  def effectDescription: String =
    val extras = secondaryEffect.toList ::: tertiaryEffect.toList
    if extras.nonEmpty then
      s"${describeEffect(effect)} + ${extras.map(describeEffect).mkString(" + ")}"
    else effect match
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

  def withNextLevel: Tile = copy(tileType = tileType match
    case TileType.WheatField(lvl) => TileType.WheatField(lvl + 1)
    case TileType.Farm(lvl)       => TileType.Farm(lvl + 1)
    case TileType.Woodcutter(lvl) => TileType.Woodcutter(lvl + 1)
    case TileType.Temple(lvl)     => TileType.Temple(lvl + 1)
    case TileType.Quarry(lvl)     => TileType.Quarry(lvl + 1)
    case TileType.Bureau(lvl)     => TileType.Bureau(lvl + 1)
    case other                    => other
  )

// ============================================================================
// Island - a fixed 3x5 tile grid
// ============================================================================

case class Island(
    id: Int,
    tiles: Map[Coord, Tile]
) derives ReadWriter:
  /** All tiles that are unlocked on this island */
  def unlockedTiles: List[Tile] =
    tiles.values.filter(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  /** All tiles that are locked on this island */
  def lockedTiles: List[Tile] =
    tiles.values.filterNot(_.unlocked).toList.sortBy(t => (t.coord.row, t.coord.col))

  /** Check if all unlocked tiles have buildings */
  def allUnlockedTilesFilled: Boolean =
    unlockedTiles.nonEmpty && unlockedTiles.forall(_.isBuilding)

  /** Check if all 15 tiles are unlocked */
  def allTilesUnlocked: Boolean =
    tiles.values.forall(_.unlocked)

  /** Check if this island is complete (all tiles unlocked AND all have buildings) */
  def isComplete: Boolean =
    allTilesUnlocked && allUnlockedTilesFilled

  /** Get unlockable tile coords (all locked tiles on this island) */
  def unlockableCoords: Set[Coord] =
    tiles.collect { case (coord, tile) if !tile.unlocked => coord }.toSet

object Island:
  val Width: Int = 3   // 3 columns (0-2)
  val Height: Int = 5  // 5 rows (0-4)
  val TileCount: Int = Width * Height // 15 tiles

  /** All valid coordinates for an island */
  val AllCoords: Set[Coord] =
    (for
      row <- 0 until Height
      col <- 0 until Width
    yield Coord(row, col)).toSet

  /** Create a new island with all tiles locked */
  def create(id: Int): Island =
    val tiles = AllCoords.map { coord =>
      coord -> Tile(coord = coord, tileType = TileType.Empty, unlocked = false)
    }.toMap
    Island(id = id, tiles = tiles)

// ============================================================================
// Game State
// ============================================================================

case class TileKingdomGame(
    islands: List[Island],        // List of islands (replaces tiles: Map[Coord, Tile])
    currentIslandIndex: Int,      // Which island is currently being viewed
    wheat: Double,                // Can be fractional for smooth accumulation
    wood: Double,                 // Wood resource
    faith: Double,                // Faith resource from temples
    gold: Int,
    stone: Double = 0.0,          // Stone resource from quarries
    lastTickTime: Long,           // Timestamp in milliseconds for offline progress
    totalAbdications: Int,
    bureauMode: Map[Coord, BureauMode] = Map.empty,      // Bureau mode per coord (on current island)
    bureauDirection: Map[Coord, BureauDirection] = Map.empty, // Bureau direction per coord
    townHallDirection: Map[Coord, BureauDirection] = Map.empty, // Town Hall direction per coord
    politicianRoster: List[Politician] = List.empty, // Available politicians to assign
    lastPoliticianGeneration: Long = 0L,             // Timestamp of last politician generation tick
    politicianGenerationProgress: Double = 0.0,     // Progress towards next politician (0.0 to 1.0)
    legacyPoints: Int = 0,        // Legacy points: total tiles lost due to sailing (never decreases)
    skillPoints: Int = 0,         // Skill points available to spend
    unlockedSkills: Set[Skill] = Set.empty, // Skills unlocked via skill tree
    hasSailed: Boolean = false,   // Whether player has sailed at least once (unlocks skill tree)
    hasPlacedBuilding: Boolean = false, // Whether any building has been placed since last abdication/sail
    tilePoints: Int = 0,          // Tile points earned by destroying tiles, used for free tile unlocks
    totalSkillPointsEarned: Int = 0, // Cumulative skill points ever earned (for save recovery)
    sailedCount: Int = 0,         // Number of times the player has sailed
    sailTileThreshold: Int = 12   // Current tile threshold for sailing (starts at 12, increases with each sail)
) derives ReadWriter:

  // ============================================================================
  // Island helpers
  // ============================================================================

  /** Get the currently viewed island */
  def currentIsland: Island = islands(currentIslandIndex)

  /** Get all unlocked tiles across all islands (for production calculations) */
  def allUnlockedTiles: List[Tile] = islands.flatMap(_.unlockedTiles)

  /** Total number of unlocked tiles across all islands */
  def totalUnlockedTileCount: Int = allUnlockedTiles.size

  /** Total number of islands */
  def totalIslands: Int = islands.size

  /** Check if can navigate to previous island */
  def canGoPreviousIsland: Boolean = currentIslandIndex > 0

  /** Check if can navigate to next island */
  def canGoNextIsland: Boolean = currentIslandIndex < islands.size - 1

  /** Check if current island is complete (all 15 tiles unlocked and filled) */
  def currentIslandComplete: Boolean = currentIsland.isComplete

  /** Check if can unlock a new island */
  def canUnlockNewIsland: Boolean =
    currentIslandComplete && gold >= TileKingdomLogic.islandUnlockCost(islands.size)

  /** Get unlockable tile coords on current island */
  def unlockableCoordsOnCurrentIsland: Set[Coord] = currentIsland.unlockableCoords

  // ============================================================================
  // Tile accessors for current island (backward compatibility)
  // ============================================================================

  /** Get tiles on current island (for backward compatibility with existing logic) */
  def tiles: Map[Coord, Tile] = currentIsland.tiles

  /** Update a tile on the current island */
  def updateTileOnCurrentIsland(coord: Coord, tile: Tile): TileKingdomGame =
    val updatedIsland = currentIsland.copy(tiles = currentIsland.tiles.updated(coord, tile))
    copy(islands = islands.updated(currentIslandIndex, updatedIsland))

  /** Update multiple tiles on the current island */
  def updateTilesOnCurrentIsland(updates: Map[Coord, Tile]): TileKingdomGame =
    val updatedTiles = currentIsland.tiles ++ updates
    val updatedIsland = currentIsland.copy(tiles = updatedTiles)
    copy(islands = islands.updated(currentIslandIndex, updatedIsland))

  /** Remove a tile from the current island (reset to empty locked) */
  def removeTileOnCurrentIsland(coord: Coord): TileKingdomGame =
    val resetTile = Tile(coord = coord, tileType = TileType.Empty, unlocked = false)
    updateTileOnCurrentIsland(coord, resetTile)

  // ============================================================================
  // Resource helpers
  // ============================================================================

  def resources: Resources = Resources(wheat, wood, faith, gold, stone)

  def canAfford(cost: Cost): Boolean = resources.canAfford(cost)

  def canAfford(amount: Int, resource: Resource): Boolean = resources.canAfford(amount, resource)

  def deduct(cost: Cost): TileKingdomGame = cost.resource match
    case Resource.Wheat => copy(wheat = wheat - cost.amount)
    case Resource.Wood  => copy(wood = wood - cost.amount)
    case Resource.Faith => copy(faith = faith - cost.amount)
    case Resource.Gold  => copy(gold = gold - cost.amount)
    case Resource.Stone => copy(stone = stone - cost.amount)

  /** All unlocked tiles (across all islands) */
  def unlockedTiles: List[Tile] = allUnlockedTiles

  /** All locked tiles (across all islands) */
  def lockedTiles: List[Tile] = islands.flatMap(_.lockedTiles)

  /** Check if all unlocked tiles on all islands have buildings */
  def allTilesFilled: Boolean =
    allUnlockedTiles.nonEmpty && allUnlockedTiles.forall(_.isBuilding)

  def hasWheatField: Boolean =
    allUnlockedTiles.exists(_.isWheatField)

  def hasFarm: Boolean =
    allUnlockedTiles.exists(_.isFarm)

  def hasWoodcutter: Boolean =
    allUnlockedTiles.exists(_.isWoodcutter)

  def hasQuarry: Boolean =
    allUnlockedTiles.exists(_.isQuarry)

  def hasTownHall: Boolean =
    allUnlockedTiles.exists(_.isTownHall)

  /** Get all politicians currently assigned to town halls (across all islands) */
  def allAssignedPoliticians: List[Politician] =
    allUnlockedTiles.flatMap: tile =>
      tile.tileType match
        case TileType.TownHall(pols) => pols
        case _ => List.empty

  // Building unlock progression:
  // Wheat Field -> Farm -> Forest -> everything else
  def canBuildFarm: Boolean = hasWheatField
  def canBuildWoodcutter: Boolean = hasFarm
  def canBuildQuarry: Boolean = hasWoodcutter
  def canBuildBureau: Boolean = hasWoodcutter
  def canBuildTemple: Boolean = hasWoodcutter
  def canBuildTownHall: Boolean = hasWoodcutter
  def canBuildAcademy: Boolean = hasWoodcutter
  def canBuildTavern: Boolean = hasWoodcutter

  def totalIncomeRate: Double =
    TileKingdomLogic.totalWheatProductionRate(this) + 
    TileKingdomLogic.totalWoodProductionRate(this) +
    TileKingdomLogic.totalStoneProductionRate(this) +
    TileKingdomLogic.totalFaithProductionRate(this)

  def nextTileUnlockCost: Int =
    TileKingdomLogic.tileUnlockCost(totalUnlockedTileCount)

  def abdicationGoldReward: Int =
    TileKingdomLogic.abdicationReward(totalIncomeRate)

  // Sail (second tier prestige) - requires enough unlocked tiles
  def canSail: Boolean = totalUnlockedTileCount >= sailTileThreshold

  /** Tiles to destroy when sailing (equal to tiles currently unlocked) */
  def sailLegacyReward: Int = totalUnlockedTileCount

  /** Skill points earned from sailing at current tile count */
  def sailSkillPointReward: Int =
    // Earn skill points for tiles above the threshold - 1 (minimum 1)
    math.max(1, totalUnlockedTileCount - sailTileThreshold + 1)

  /** Next tile threshold after sailing at current count */
  def sailNextThreshold: Int = totalUnlockedTileCount + 1

  // Skill helpers
  def hasSkill(skill: Skill): Boolean = unlockedSkills.contains(skill)

  def canUnlockSkill(skill: Skill): Boolean =
    if unlockedSkills.contains(skill) then false
    else if skillPoints < Skill.cost(skill) then false
    // Check if mutually exclusive skill is already unlocked
    else if Skill.mutuallyExclusive(skill).exists(unlockedSkills.contains) then false
    else
      // Check prerequisites - either standard prerequisite or alternative prerequisites
      val standardPrereqMet = Skill.prerequisite(skill).forall(unlockedSkills.contains)
      val alternativePrereqMet = Skill.alternativePrerequisites(skill) match
        case Some(alternatives) => alternatives.exists(unlockedSkills.contains)
        case None => true
      standardPrereqMet && alternativePrereqMet

  def totalSkillPointsSpent: Int =
    unlockedSkills.toList.map(Skill.cost).sum

  /** True when no buildings have been placed since the last abdication/sail. */
  def isFreshAbdication: Boolean = !hasPlacedBuilding

  /** A skill can be refunded if it is unlocked, no other unlocked skill depends on it,
    * the player can afford the gold cost, and no buildings have been placed yet. */
  def canRefundSkill(skill: Skill): Boolean =
    if !hasSailed then false
    else if !isFreshAbdication then false
    else if !unlockedSkills.contains(skill) then false
    else if gold < Skill.cost(skill) * TileKingdomLogic.SkillRefundGoldCost then false
    else
      // Check no other unlocked skill has this skill as a prerequisite
      val dependents = unlockedSkills.filter: other =>
        Skill.prerequisite(other).contains(skill) ||
          Skill.alternativePrerequisites(other).exists: alts =>
            alts.contains(skill) && !alts.exists(alt => alt != skill && unlockedSkills.contains(alt))
      dependents.isEmpty


// ============================================================================
// Game Logic
// ============================================================================

object TileKingdomLogic:

  // Constants
  val TickIntervalSeconds: Double = 0.5 // Tick four times per second
  val ProductionIntervalSeconds: Int = 10 // Wheat fields produce every 10 seconds
  val InitialTileCount: Int = 0 // Start with 0 unlocked tiles per island
  val FarmBoostPerLevel: Double = 0.25 // 25% boost per farm level
  val StartingGold: Int = 1 // Enough to unlock first tile

  // Island constants
  val SailMinIslands: Int = 2 // Minimum islands required to sail

  // Island unlock costs (reduced by 20x)
  def islandUnlockCost(currentIslandCount: Int): Int =
    currentIslandCount match
      case 1 => 50       // Island 2: 50 gold (was 1,000)
      case 2 => 250      // Island 3: 250 gold (was 5,000)
      case 3 => 1250     // Island 4: 1,250 gold (was 25,000)
      case n => 1250 * math.pow(2, n - 3).toInt // Island 5+: exponential

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
  val TownHallDirectionLength: Int = 5 // 5 tiles long in the chosen direction
  val TownHallDirectionHalfWidth: Int = 1 // 1 tile on each side = 3 wide
  val PoliticianGenerationIntervalSeconds: Int = 300 // 5 minutes = 300 seconds
  val MaxPoliticianRosterSize: Int = 3 // Maximum politicians in roster (base)
  val PoliticianLifespanMs: Long = 600000L // 10 minutes = 600,000 ms

  // Calculate actual max roster size including skill bonuses and academies
  def maxPoliticianRosterSize(game: TileKingdomGame): Int =
    val academyBonus = game.allUnlockedTiles.count(_.isAcademy)
    MaxPoliticianRosterSize + academyBonus

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
  val LegacyPointsPerSkillPoint: Int = 25 // Legacy points needed for 1 skill point
  val SkillRefundGoldCost: Int = 50 // Gold cost per skill point refunded (was 1000)

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

  // ============================================================================
  // Island Management
  // ============================================================================


  /** Navigate to previous island */
  def previousIsland(game: TileKingdomGame): TileKingdomGame =
    if game.canGoPreviousIsland then
      game.copy(currentIslandIndex = game.currentIslandIndex - 1)
    else game

  /** Navigate to next island */
  def nextIsland(game: TileKingdomGame): TileKingdomGame =
    if game.canGoNextIsland then
      game.copy(currentIslandIndex = game.currentIslandIndex + 1)
    else game

  /** Unlock a new island */
  def unlockNewIsland(game: TileKingdomGame): Either[String, TileKingdomGame] =
    val cost = islandUnlockCost(game.islands.size)
    if !game.currentIslandComplete then
      Left("Current island must be complete to unlock a new island")
    else if game.gold < cost then
      Left(s"Not enough gold (need $cost)")
    else
      val newIsland = Island.create(game.islands.size)
      Right(game.copy(
        islands = game.islands :+ newIsland,
        currentIslandIndex = game.islands.size, // Navigate to new island
        gold = game.gold - cost
      ))

  /** Unlock a tile on the current island */
  def unlockTileOnCurrentIsland(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    val island = game.currentIsland
    island.tiles.get(coord) match
      case None => Left("Coordinate not on this island")
      case Some(tile) if tile.unlocked => Left("Tile already unlocked")
      case Some(_) if !island.unlockableCoords.contains(coord) =>
        Left("Can only unlock tiles adjacent to your territory")
      case Some(tile) =>
        // Use tile point if available, otherwise use gold
        val cost = tileUnlockCost(game.totalUnlockedTileCount)
        if game.tilePoints > 0 then
          val updatedIsland = island.copy(tiles = island.tiles.updated(coord, tile.copy(unlocked = true)))
          Right(game.copy(
            islands = game.islands.updated(game.currentIslandIndex, updatedIsland),
            tilePoints = game.tilePoints - 1
          ))
        else if game.gold < cost then
          Left(s"Not enough gold (need $cost)")
        else
          val updatedIsland = island.copy(tiles = island.tiles.updated(coord, tile.copy(unlocked = true)))
          Right(game.copy(
            islands = game.islands.updated(game.currentIslandIndex, updatedIsland),
            gold = game.gold - cost
          ))

  // ============================================================================
  // Forest Group Cache (island-scoped)
  // ============================================================================

  // Cache for forest group sizes, keyed by the set of woodcutter coordinates
  // This avoids recalculating expensive flood-fill for every woodcutter every tick
  private var forestGroupCacheKey: Set[Coord] = Set.empty
  private var forestGroupCache: Map[Coord, Int] = Map.empty

  /** Get woodcutter positions from a game state */
  private def woodcutterCoords(game: TileKingdomGame): Set[Coord] =
    game.tiles.filter(_._2.isWoodcutter).keySet

  /** Get cached forest group size for a woodcutter, recomputing cache if needed */
  def cachedForestGroupSize(game: TileKingdomGame, coord: Coord): Int =
    val currentWoodcutters = woodcutterCoords(game)
    if currentWoodcutters != forestGroupCacheKey then
      recomputeForestGroupCache(game, currentWoodcutters)
    forestGroupCache.getOrElse(coord, 1)

  /** Recompute the entire forest group cache */
  private def recomputeForestGroupCache(game: TileKingdomGame, woodcutters: Set[Coord]): Unit =
    forestGroupCacheKey = woodcutters
    var visited = Set.empty[Coord]
    var newCache = Map.empty[Coord, Int]

    woodcutters.foreach: startCoord =>
      if !visited.contains(startCoord) then
        val group = findConnectedWoodcuttersRaw(woodcutters, startCoord)
        visited = visited ++ group
        val groupSize = group.size
        group.foreach: c =>
          newCache = newCache.updated(c, groupSize)

    forestGroupCache = newCache

  /** Raw flood-fill that works on a set of woodcutter coords (no game needed) */
  private def findConnectedWoodcuttersRaw(woodcutters: Set[Coord], startCoord: Coord): Set[Coord] =
    @tailrec
    def floodFill(toVisit: Set[Coord], visited: Set[Coord]): Set[Coord] =
      if toVisit.isEmpty then visited
      else
        val current = toVisit.head
        val remaining = toVisit.tail
        if visited.contains(current) then floodFill(remaining, visited)
        else if woodcutters.contains(current) then
          val newNeighbors = current.neighbors.diff(visited)
          floodFill(remaining ++ newNeighbors, visited + current)
        else
          floodFill(remaining, visited)
    floodFill(Set(startCoord), Set.empty)

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

  // Island-aware version: Find all woodcutters in the same connected group on a specific island
  def findConnectedWoodcuttersOnIsland(island: Island, startCoord: Coord): Set[Coord] =
    def floodFill(toVisit: Set[Coord], visited: Set[Coord]): Set[Coord] =
      if toVisit.isEmpty then visited
      else
        val current = toVisit.head
        val remaining = toVisit.tail
        if visited.contains(current) then floodFill(remaining, visited)
        else
          island.tiles.get(current) match
            case Some(tile) if tile.isWoodcutter =>
              val newNeighbors = current.neighbors.filterNot(visited.contains)
              floodFill(remaining ++ newNeighbors, visited + current)
            case _ =>
              floodFill(remaining, visited)
    floodFill(Set(startCoord), Set.empty)

  // Calculate forest group bonus multiplier for a woodcutter
  // Bonus escalates: 2 tiles = 10%, 3 tiles = 10+20=30%, 4 tiles = 10+20+30=60%, etc.
  // Uses cached group size to avoid expensive recalculation every tick
  def forestGroupBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    val groupSize = cachedForestGroupSize(game, coord)
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

  // Maximum number of politicians a single Town Hall can hold
  def townHallCapacity(game: TileKingdomGame): Int =
    if game.hasSkill(Skill.Management3A) then 2 else 1

  // Get town hall direction (defaults to Center)
  def getTownHallDirection(game: TileKingdomGame, coord: Coord): BureauDirection =
    game.townHallDirection.getOrElse(coord, BureauDirection.Center)

  // Set town hall direction
  def setTownHallDirection(game: TileKingdomGame, coord: Coord, direction: BureauDirection): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.isTownHall => Left("Tile is not a town hall")
      case Some(_) if !game.hasSkill(Skill.Management2B) => Left("Requires Management 2B skill")
      case Some(_) =>
        Right(game.copy(
          townHallDirection = game.townHallDirection.updated(coord, direction)
        ))

  // Get the set of coords affected by a town hall, considering direction skill
  def townHallAffectedCoords(game: TileKingdomGame, townHallCoord: Coord): Set[Coord] =
    if game.hasSkill(Skill.Management2B) then
      val direction = getTownHallDirection(game, townHallCoord)
      townHallCoord.rectangleInDirection(direction, TownHallDirectionLength, TownHallDirectionHalfWidth)
    else
      townHallCoord.neighborsWithinRadius(TownHallInfluenceRadius)

  // Find all Town Halls that affect a given coord (within their influence radius/direction)
  def townHallsAffecting(game: TileKingdomGame, coord: Coord): List[(Coord, Politician)] =
    game.tiles.toList.flatMap:
      case (townHallCoord, tile) => tile.tileType match
        case TileType.TownHall(politicians) if politicians.nonEmpty
          && townHallAffectedCoords(game, townHallCoord).contains(coord) =>
          politicians.map(pol => (townHallCoord, pol))
        case _ => Nil

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
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isWheat = true))

  // Calculate Town Hall bonus multiplier for wood production at a given coord
  def townHallWoodMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isWood = true))

  // Calculate Town Hall bonus multiplier for faith production at a given coord
  def townHallFaithMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isFaith = true))

  // Calculate Town Hall bonus multiplier for stone production at a given coord
  def townHallStoneMultiplier(game: TileKingdomGame, coord: Coord): Double =
    townHallsAffecting(game, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isStone = true))

  // Count taverns within influence radius of a Town Hall
  def tavernsAffectingTownHall(game: TileKingdomGame, townHallCoord: Coord): Int =
    townHallAffectedCoords(game, townHallCoord).count: coord =>
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

  // Generate a random politician (possibly rare, possibly with extra effects from Management3B)
  def generatePolitician(seed: Long, rareChance: Double, hasExtraEffects: Boolean = false): Politician =
    val random = new scala.util.Random(seed)
    val isRare = random.nextDouble() < rareChance
    val (name, title, effect, emoji) = PoliticianPool(random.nextInt(PoliticianPool.size))
    
    // Pick a unique extra effect different from previously chosen ones
    def pickExtraEffect(excludeEffects: List[PoliticianEffect]): PoliticianEffect =
      val options = PoliticianPool.filterNot(p => excludeEffects.contains(p._3))
      options(random.nextInt(options.size))._3
    
    if isRare then
      val secondEffect = pickExtraEffect(List(effect))
      val tertiaryEffect = if hasExtraEffects then Some(pickExtraEffect(List(effect, secondEffect))) else None
      Politician(
        id = s"politician_${seed}_${random.nextInt(10000)}",
        name = s"$name the Great",
        title = s"Legendary $title",
        effect = effect,
        emoji = "⭐",
        secondaryEffect = Some(secondEffect),
        tertiaryEffect = tertiaryEffect
      )
    else if hasExtraEffects then
      val secondEffect = pickExtraEffect(List(effect))
      Politician(
        id = s"politician_${seed}_${random.nextInt(10000)}",
        name = name,
        title = title,
        effect = effect,
        emoji = emoji,
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
    generatePolitician(seed, BaseRarePoliticianChance, false)

  // Generate a politician using game state to determine rare chance and extra effects
  def generatePolitician(game: TileKingdomGame, seed: Long, forceRare: Boolean): Politician =
    val rareChance = if forceRare then 1.0 else rarePoliticianChance(game)
    val hasExtraEffects = game.hasSkill(Skill.Management3B)
    generatePolitician(seed, rareChance, hasExtraEffects)

  def generatePolitician(game: TileKingdomGame, seed: Long): Politician =
    generatePolitician(game, seed, forceRare = false)

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
      val newPoliticians = (0 until actualNewCount).map: i =>
        generatePolitician(game, currentTimeMillis + i)
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
        case TileType.TownHall(pols) if pols.nonEmpty => true
        case _ => false
      => coord

    var updatedTiles = Map.empty[Coord, Tile]
    var destroyedPoliticians: List[String] = List.empty

    townHallCoords.foreach: coord =>
      game.tiles.get(coord).foreach: tile =>
        tile.tileType match
          case TileType.TownHall(politicians) if politicians.nonEmpty =>
            val lifespanMultiplier = politicianLifespanMultiplier(game, coord)
            val effectiveElapsedMs = (elapsedMs / lifespanMultiplier).toLong
            val (surviving, expired) = politicians.partitionMap: politician =>
              val newLifespan = politician.remainingLifespanMs - effectiveElapsedMs
              if newLifespan <= 0 then Right(politician.name)
              else Left(politician.copy(remainingLifespanMs = newLifespan))
            destroyedPoliticians = destroyedPoliticians ++ expired
            val updatedTile = tile.copy(tileType = TileType.TownHall(surviving))
            updatedTiles = updatedTiles.updated(coord, updatedTile)
          case _ => ()

    (game.updateTilesOnCurrentIsland(updatedTiles), destroyedPoliticians)

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

  // Agriculture2A: halves the wheat field production interval (2x faster harvests)
  // Returns the interval multiplier: 0.5 means half the normal interval
  def agriculture2AIntervalMultiplier(game: TileKingdomGame): Double =
    if game.hasSkill(Skill.Agriculture2A) then 0.5 else 1.0

  // Agriculture1B: Farm bonus applies to forests at half strength
  def agriculture1BFarmBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture1B) then
      val farmBonus = coord.neighbors.toList.flatMap(game.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5 // Half strength
      .sum
      1.0 + farmBonus
    else 1.0

  // Agriculture3A: Wheat field upgrade costs reduced by 99%
  def effectiveUpgradeCost(game: TileKingdomGame, tile: Tile): Option[Cost] =
    tile.upgradeCost.map: cost =>
      if game.hasSkill(Skill.Agriculture3A) && tile.isWheatField then
        cost.copy(amount = (cost.amount * 0.01).toInt.max(1))
      else cost

  // Agriculture2B: Farm bonus applies to quarries at half strength
  def agriculture2BFarmBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture2B) then
      val farmBonus = coord.neighbors.toList.flatMap(game.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5 // Half strength
      .sum
      1.0 + farmBonus
    else 1.0

  // Agriculture3B: Farm bonus applies to temples at half strength
  def agriculture3BFarmBonusMultiplier(game: TileKingdomGame, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture3B) then
      val farmBonus = coord.neighbors.toList.flatMap(game.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5 // Half strength
      .sum
      1.0 + farmBonus
    else 1.0

  // Production rate for a specific tile per second (with farm bonuses, town hall bonuses, and Agriculture2A interval)
  def productionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = productionPerSecond(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord) / agriculture2AIntervalMultiplier(game)
    else 0.0

  // Wood production rate for a specific tile per second (with forest group bonus, town hall bonuses, and Agriculture1B)
  def woodProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = woodProductionPerSecond(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord) * agriculture1BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // Faith production rate for a specific tile per second (with town hall bonuses, Wisdom2, and Agriculture3B)
  def faithProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = faithProductionPerSecond(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord) * templeWisdom2Multiplier(game, tile.coord) * agriculture3BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // Stone production rate for a specific tile per second (with town hall bonuses, Wisdom1, and Agriculture2B)
  def stoneProductionRate(game: TileKingdomGame, tile: Tile): Double =
    val base = stoneProductionPerSecond(tile)
    if base > 0 then base * townHallStoneMultiplier(game, tile.coord) * quarryWisdom1Multiplier(game, tile.coord) * agriculture2BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // Legacy method for backwards compatibility
  def productionRate(tile: Tile): Double = productionPerSecond(tile)

  // Production per harvest for a specific tile (with farm bonuses and town hall bonuses applied)
  // Note: Agriculture2A affects interval speed, not per-harvest amount
  def productionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWheatProductionRate(tile)
    if base > 0 then base * farmBonusMultiplier(game, tile.coord) * townHallWheatMultiplier(game, tile.coord)
    else 0.0

  // Wood production per harvest for a specific tile (with forest group bonus, town hall bonuses, and Agriculture1B)
  def woodProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseWoodProductionRate(tile)
    if base > 0 then base * forestGroupBonusMultiplier(game, tile.coord) * townHallWoodMultiplier(game, tile.coord) * agriculture1BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // Faith production per harvest for a specific tile (with town hall bonuses, Wisdom2, and Agriculture3B)
  def faithProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseFaithProductionRate(tile)
    if base > 0 then base * townHallFaithMultiplier(game, tile.coord) * templeWisdom2Multiplier(game, tile.coord) * agriculture3BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // Stone production per harvest for a specific tile (with town hall bonuses, Wisdom1, and Agriculture2B)
  def stoneProductionPerHarvest(game: TileKingdomGame, tile: Tile): Double =
    val base = baseStoneProductionRate(tile)
    if base > 0 then base * townHallStoneMultiplier(game, tile.coord) * quarryWisdom1Multiplier(game, tile.coord) * agriculture2BFarmBonusMultiplier(game, tile.coord)
    else 0.0

  // ============================================================================
  // Island-scoped production multipliers
  // These versions take an Island parameter for calculating bonuses on specific islands
  // ============================================================================

  // Island-scoped: Calculate farm bonus multiplier for a wheat field at given coord on a specific island
  def farmBonusMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    val farmBonus = coord.neighbors.toList.flatMap(island.tiles.get).collect:
      case tile if tile.isFarm => tile.level * FarmBoostPerLevel
    .sum
    1.0 + farmBonus

  // Island-scoped: Agriculture1B farm bonus for forests
  def agriculture1BFarmBonusMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture1B) then
      val farmBonus = coord.neighbors.toList.flatMap(island.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5
      .sum
      1.0 + farmBonus
    else 1.0

  // Island-scoped: Agriculture2B farm bonus for quarries
  def agriculture2BFarmBonusMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture2B) then
      val farmBonus = coord.neighbors.toList.flatMap(island.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5
      .sum
      1.0 + farmBonus
    else 1.0

  // Island-scoped: Agriculture3B farm bonus for temples
  def agriculture3BFarmBonusMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    if game.hasSkill(Skill.Agriculture3B) then
      val farmBonus = coord.neighbors.toList.flatMap(island.tiles.get).collect:
        case tile if tile.isFarm => tile.level * FarmBoostPerLevel * 0.5
      .sum
      1.0 + farmBonus
    else 1.0

  // Island-scoped: Wisdom1 quarry bonus from neighboring forests
  def quarryWisdom1MultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    if !game.hasSkill(Skill.Wisdom1) then 1.0
    else
      val neighboringForests = coord.neighbors.count: neighborCoord =>
        island.tiles.get(neighborCoord).exists(_.isWoodcutter)
      1.0 + (neighboringForests * 0.25)

  // Island-scoped: Wisdom2 temple bonus from neighboring forests
  def templeWisdom2MultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    if !game.hasSkill(Skill.Wisdom2) then 1.0
    else
      val neighboringForests = coord.neighbors.count: neighborCoord =>
        island.tiles.get(neighborCoord).exists(_.isWoodcutter)
      1.0 + (neighboringForests * 0.50)

  // Island-scoped: Forest group bonus using island's woodcutters
  def forestGroupBonusMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    val groupSize = findConnectedWoodcuttersOnIsland(island, coord).size
    val n = groupSize - 1
    val totalBonus = n * (n + 1) / 2.0 * ForestGroupBonusPerTile
    1.0 + totalBonus

  // Island-scoped: Town halls affecting a coord on this island
  def townHallsAffectingOnIsland(game: TileKingdomGame, island: Island, coord: Coord): List[(Coord, Politician)] =
    island.tiles.toList.flatMap:
      case (townHallCoord, tile) => tile.tileType match
        case TileType.TownHall(politicians) if politicians.nonEmpty
          && townHallAffectedCoordsOnIsland(game, island, townHallCoord).contains(coord) =>
          politicians.map(pol => (townHallCoord, pol))
        case _ => Nil

  // Island-scoped: Town hall affected coords (using island-specific direction lookup)
  def townHallAffectedCoordsOnIsland(game: TileKingdomGame, island: Island, townHallCoord: Coord): Set[Coord] =
    if game.hasSkill(Skill.Management2B) then
      val direction = game.townHallDirection.getOrElse(townHallCoord, BureauDirection.Center)
      townHallCoord.rectangleInDirection(direction, TownHallDirectionLength, TownHallDirectionHalfWidth)
    else
      townHallCoord.neighborsWithinRadius(TownHallInfluenceRadius)

  // Island-scoped: Town Hall multipliers
  def townHallWheatMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    townHallsAffectingOnIsland(game, island, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isWheat = true))

  def townHallWoodMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    townHallsAffectingOnIsland(game, island, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isWood = true))

  def townHallFaithMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    townHallsAffectingOnIsland(game, island, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isFaith = true))

  def townHallStoneMultiplierOnIsland(game: TileKingdomGame, island: Island, coord: Coord): Double =
    townHallsAffectingOnIsland(game, island, coord).foldLeft(1.0): (acc, entry) =>
      val (_, politician) = entry
      politician.allEffects.foldLeft(acc)((a, eff) => applyEffect(a, eff, isStone = true))

  // ============================================================================
  // Island-scoped production rate calculations
  // ============================================================================

  // Production rate for a specific tile on a specific island
  def productionRateOnIsland(game: TileKingdomGame, island: Island, tile: Tile): Double =
    val base = productionPerSecond(tile)
    if base > 0 then
      base * farmBonusMultiplierOnIsland(game, island, tile.coord) *
        townHallWheatMultiplierOnIsland(game, island, tile.coord) /
        agriculture2AIntervalMultiplier(game)
    else 0.0

  def woodProductionRateOnIsland(game: TileKingdomGame, island: Island, tile: Tile): Double =
    val base = woodProductionPerSecond(tile)
    if base > 0 then
      base * forestGroupBonusMultiplierOnIsland(game, island, tile.coord) *
        townHallWoodMultiplierOnIsland(game, island, tile.coord) *
        agriculture1BFarmBonusMultiplierOnIsland(game, island, tile.coord)
    else 0.0

  def faithProductionRateOnIsland(game: TileKingdomGame, island: Island, tile: Tile): Double =
    val base = faithProductionPerSecond(tile)
    if base > 0 then
      base * townHallFaithMultiplierOnIsland(game, island, tile.coord) *
        templeWisdom2MultiplierOnIsland(game, island, tile.coord) *
        agriculture3BFarmBonusMultiplierOnIsland(game, island, tile.coord)
    else 0.0

  def stoneProductionRateOnIsland(game: TileKingdomGame, island: Island, tile: Tile): Double =
    val base = stoneProductionPerSecond(tile)
    if base > 0 then
      base * townHallStoneMultiplierOnIsland(game, island, tile.coord) *
        quarryWisdom1MultiplierOnIsland(game, island, tile.coord) *
        agriculture2BFarmBonusMultiplierOnIsland(game, island, tile.coord)
    else 0.0

  // ============================================================================
  // Total production rates (across all islands)
  // ============================================================================

  // Total wheat production rate for the game (all wheat fields with bonuses, island-scoped)
  def totalWheatProductionRate(game: TileKingdomGame): Double =
    game.islands.map: island =>
      island.unlockedTiles.map(tile => productionRateOnIsland(game, island, tile)).sum
    .sum

  // Total wood production rate (island-scoped)
  def totalWoodProductionRate(game: TileKingdomGame): Double =
    game.islands.map: island =>
      island.unlockedTiles.map(tile => woodProductionRateOnIsland(game, island, tile)).sum
    .sum

  // Total faith production rate (island-scoped)
  def totalFaithProductionRate(game: TileKingdomGame): Double =
    game.islands.map: island =>
      island.unlockedTiles.map(tile => faithProductionRateOnIsland(game, island, tile)).sum
    .sum

  // Total stone production rate (island-scoped)
  def totalStoneProductionRate(game: TileKingdomGame): Double =
    game.islands.map: island =>
      island.unlockedTiles.map(tile => stoneProductionRateOnIsland(game, island, tile)).sum
    .sum

  // Cost to build a wheat field on an empty tile
  def wheatFieldBuildCost: Int = 0

  // Cost to build a farm on an empty tile
  def farmBuildCost: Int = 25

  // Cost to build a woodcutter on an empty tile
  def woodcutterBuildCost: Int = 20

  // Cost to build a bureau on an empty tile (costs wood, not wheat)
  val BaseBureauBuildCost: Int = 500
  def bureauBuildCost(game: TileKingdomGame): Int =
    if game.hasSkill(Skill.Management1A) then 0 else BaseBureauBuildCost

  // Cost to build a temple on an empty tile (costs wood)
  def templeBuildCost: Int = TempleBuildCost

  // Cost to build a town hall on an empty tile (costs stone, scales with existing town halls)
  def townHallBuildCost(game: TileKingdomGame): Int =
    val existingTownHalls = game.tiles.values.count(_.isTownHall)
    val baseCost = TownHallBuildCost * math.pow(10, existingTownHalls).toInt
    if game.hasSkill(Skill.Management2A) then baseCost / 10 else baseCost

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

  // Cost to unlock next tile (starts cheap, scales up)
  // Tiles 0-4: fixed costs (25, 50, 100, 200, 400)
  // Tiles 5-14: doubles each tile (800, 1600, ..., 204800)
  // Tiles 15-29: +25% each tile
  // Tiles 30+: +5% each tile
  def tileUnlockCost(currentUnlockedCount: Int): Int =
    currentUnlockedCount match
      case 0 => 1  // Reduced from 25
      case 1 => 2  // Reduced from 50
      case 2 => 5  // Reduced from 100
      case 3 => 10 // Reduced from 200
      case 4 => 20 // Reduced from 400
      case n if n < 15 =>
        val baseCost = 20 * math.pow(2, n - 4).toInt // Doubles every tile (tiles 5-14)
        if baseCost > 100_000_000 then 100_000_000 else baseCost
      case n if n < 30 =>
        // After tile 15, grow by 25% each tile
        val tile14Cost = 20 * math.pow(2, 10).toInt // Cost of tile 14 (20 * 1024 = 20480)
        val growthFactor = math.pow(1.25, n - 14)
        val cost = (tile14Cost * growthFactor).toInt
        if cost > 100_000_000 then 100_000_000 else cost
      case n =>
        // After tile 30, grow by only 5% each tile
        val tile14Cost = 20 * math.pow(2, 10).toInt // 20480
        val tile29Cost = (tile14Cost * math.pow(1.25, 15)).toInt // Cost at tile 29
        val growthFactor = math.pow(1.05, n - 29)
        val cost = (tile29Cost * growthFactor).toInt
        if cost > 100_000_000 then 100_000_000 else cost

  // Gold reward for abdication based on total income rate
  def abdicationReward(totalIncomeRate: Double): Int =
    math.max(1, totalIncomeRate.toInt) // 1 gold per resource/second

  // Create initial game state
  def newGame(currentTimeMillis: Long): TileKingdomGame =
    val initialIsland = Island.create(0)

    TileKingdomGame(
      islands = List(initialIsland),
      currentIslandIndex = 0,
      wheat = 50.0, // Start with some wheat to build first field
      wood = 0.0,
      faith = 0.0,
      gold = StartingGold, // Enough gold to unlock first tile
      lastTickTime = currentTimeMillis,
      totalAbdications = 0,
      politicianRoster = List.empty,
      lastPoliticianGeneration = currentTimeMillis
    )

  // Simple tick: just accumulate resources based on production rate (used for very short intervals)
  /** Simulate a single tick interval worth of game mechanics.
    * This is the core game loop that handles:
    * - Resource production (wheat, wood, faith, stone)
    * - Bureau auto-upgrades (proportional to elapsed time)
    * - Politician lifespan ticking
    * - Politician generation
    * 
    * @param game Current game state
    * @param elapsedMs Milliseconds elapsed since last tick
    * @param currentTime Current timestamp
    * @return Updated game state
    */
  def simulateSingleTick(game: TileKingdomGame, elapsedMs: Long, currentTime: Long): TileKingdomGame =
    val elapsedSeconds = elapsedMs / 1000.0
    
    // 1. Accumulate resources
    val wheatProduced = totalWheatProductionRate(game) * elapsedSeconds
    val woodProduced = totalWoodProductionRate(game) * elapsedSeconds
    val faithProduced = totalFaithProductionRate(game) * elapsedSeconds
    val stoneProduced = totalStoneProductionRate(game) * elapsedSeconds
    
    var currentGame = game.copy(
      wheat = game.wheat + wheatProduced,
      wood = game.wood + woodProduced,
      faith = game.faith + faithProduced,
      stone = game.stone + stoneProduced,
      lastTickTime = currentTime
    )
    
    // 2. Process bureau auto-upgrades (for all islands)
    // Calculate how many upgrades each bureau should perform based on elapsed time
    val bureauIntervalMs = BureauIntervalSeconds * 1000.0
    currentGame.islands.foreach { island =>
      island.unlockedTiles.filter(_.isBureau).foreach { tile =>
        val speedMultiplier = bureauSpeedMultiplier(currentGame, tile.coord)
        val upgradesThisTick = (elapsedMs * speedMultiplier / bureauIntervalMs).toInt.max(0)
        
        // Perform multiple upgrades if enough time has passed
        (0 until upgradesThisTick).foreach { _ =>
          bureauAutoUpgrade(currentGame, tile.coord, currentTime) match
            case Some((newGame, _)) => currentGame = newGame
            case None => () // Can't upgrade (no targets or not enough resources)
        }
      }
    }
    
    // 3. Tick politician lifespans
    val (gameAfterLifespan, _) = tickPoliticianLifespans(currentGame, elapsedMs)
    currentGame = gameAfterLifespan
    
    // 4. Generate new politicians
    currentGame = generateNewPoliticians(currentGame, currentTime)
    
    currentGame

  /** Tick the game forward to the current time.
    * 
    * Simulates all game mechanics by running the appropriate number of tick intervals.
    * For performance, caps simulation at 24 hours and uses adaptive step sizes.
    */
  def tick(game: TileKingdomGame, currentTimeMillis: Long): TileKingdomGame =
    val elapsedMs = currentTimeMillis - game.lastTickTime
    if elapsedMs <= 0 then return game
    
    // Cap at 24 hours to prevent extremely long calculations
    val maxSimulationMs = 24L * 60 * 60 * 1000
    val actualElapsedMs = math.min(elapsedMs, maxSimulationMs)
    
    // Use adaptive step size based on elapsed time:
    // - For real-time play (< 5s): use actual tick interval (500ms)
    // - For short offline (< 1 hour): use 5 second steps (bureau interval)
    // - For long offline (>= 1 hour): use 30 second steps for performance
    val stepMs: Long = 
      if actualElapsedMs < 5000 then (TickIntervalSeconds * 1000).toLong
      else if actualElapsedMs < 3600000 then BureauIntervalSeconds * 1000L
      else 30000L // 30 second steps for very long offline
    
    val numTicks = (actualElapsedMs / stepMs).toInt
    val remainderMs = actualElapsedMs % stepMs
    
    var currentGame = game
    var simulatedTime = game.lastTickTime
    
    // Run full tick intervals
    for _ <- 0 until numTicks do
      simulatedTime += stepMs
      currentGame = simulateSingleTick(currentGame, stepMs, simulatedTime)
    
    // Handle remainder
    if remainderMs > 0 then
      simulatedTime += remainderMs
      currentGame = simulateSingleTick(currentGame, remainderMs, simulatedTime)
    
    // Ensure lastTickTime is set to actual current time
    currentGame.copy(lastTickTime = currentTimeMillis)

  /** Shared validation and placement for all build actions on current island. */
  private def buildOnEmptyTile(
      game: TileKingdomGame,
      coord: Coord,
      tileType: TileType,
      cost: Cost,
      prerequisite: => Boolean = true,
      prerequisiteMsg: String = ""
  ): Either[String, TileKingdomGame] =
    val island = game.currentIsland
    island.tiles.get(coord) match
      case None                                 => Left("Tile not found on this island")
      case Some(tile) if !tile.unlocked         => Left("Tile is locked")
      case Some(tile) if !tile.isEmpty          => Left("Tile is not empty")
      case Some(_) if !prerequisite             => Left(prerequisiteMsg)
      case Some(_) if !game.canAfford(cost)     => Left(s"Not enough ${cost.resource.toString.toLowerCase} (need ${cost.amount})")
      case Some(tile) =>
        val updatedIsland = island.copy(tiles = island.tiles.updated(coord, tile.copy(tileType = tileType)))
        Right(game.deduct(cost).copy(
          islands = game.islands.updated(game.currentIslandIndex, updatedIsland),
          hasPlacedBuilding = true
        ))

  def buildWheatField(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    val startLevel = if game.hasSkill(Skill.Agriculture1A) then 10 else 1
    buildOnEmptyTile(game, coord, TileType.WheatField(startLevel), Cost(wheatFieldBuildCost, Resource.Wheat))

  def buildFarm(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Farm(1), Cost(farmBuildCost, Resource.Wheat),
      game.canBuildFarm, "Build a wheat field first")

  def buildWoodcutter(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Woodcutter(1), Cost(woodcutterBuildCost, Resource.Wheat),
      game.canBuildWoodcutter, "Build a farm first")

  def buildBureau(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Bureau(1), Cost(bureauBuildCost(game), Resource.Wood),
      game.canBuildBureau, "Build a forest first")

  def buildTemple(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Temple(1), Cost(templeBuildCost, Resource.Wood),
      game.canBuildTemple, "Build a forest first")

  def buildTownHall(game: TileKingdomGame, coord: Coord, currentTimeMillis: Long = System.currentTimeMillis()): Either[String, TileKingdomGame] =
    val cost = townHallBuildCost(game)
    buildOnEmptyTile(game, coord, TileType.TownHall(List.empty), Cost(cost, Resource.Stone),
      game.canBuildTownHall, "Build a forest first").map: baseGame =>
        // If roster is empty, generate a politician immediately
        if baseGame.politicianRoster.isEmpty then
          val rareChance = rarePoliticianChance(baseGame)
          val hasExtraEffects = baseGame.hasSkill(Skill.Management3B)
          val newPolitician = generatePolitician(currentTimeMillis, rareChance, hasExtraEffects)
          baseGame.copy(
            politicianRoster = List(newPolitician),
            lastPoliticianGeneration = currentTimeMillis,
            politicianGenerationProgress = 0.0
          )
        else baseGame

  def buildQuarry(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Quarry(1), Cost(quarryBuildCost, Resource.Wood),
      game.canBuildQuarry, "Build a forest first")

  def buildAcademy(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Academy(AcademyMode.FasterPoliticians), Cost(academyBuildCost(game), Resource.Stone),
      game.canBuildAcademy, "Build a forest first")

  def buildTavern(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    buildOnEmptyTile(game, coord, TileType.Tavern, Cost(TavernBuildCost, Resource.Wood),
      game.canBuildTavern, "Build a forest first")

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
          Right(game.updateTileOnCurrentIsland(coord, updatedTile))
        case _ => Left("Tile is not an academy")

  // Assign a politician from the roster to a town hall (adds to list, or replaces oldest if at capacity)
  def assignPolitician(game: TileKingdomGame, politicianId: String, townHallCoord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(existing) =>
          game.politicianRoster.find(_.id == politicianId) match
            case None => Left("Politician not found in roster")
            case Some(newPolitician) =>
              val capacity = townHallCapacity(game)
              val (updatedPols, returnedToRoster) =
                if existing.size < capacity then
                  (existing :+ newPolitician, List.empty)
                else
                  // At capacity — replace the first (oldest) politician
                  (existing.tail :+ newPolitician, List(existing.head))
              val updatedTile = tile.copy(tileType = TileType.TownHall(updatedPols))
              val updatedRoster = game.politicianRoster.filterNot(_.id == politicianId) ++ returnedToRoster
              Right(game.updateTileOnCurrentIsland(townHallCoord, updatedTile).copy(
                politicianRoster = updatedRoster
              ))
        case _ => Left("Tile is not a town hall")

  // Remove a politician from a town hall back to the roster (by ID, or the only one)
  def removePolitician(game: TileKingdomGame, townHallCoord: Coord, politicianId: Option[String] = None): Either[String, TileKingdomGame] =
    game.tiles.get(townHallCoord) match
      case None => Left("Tile not found")
      case Some(tile) => tile.tileType match
        case TileType.TownHall(politicians) if politicians.nonEmpty =>
          val toRemove = politicianId match
            case Some(id) => politicians.find(_.id == id)
            case None => Some(politicians.head) // Default: remove first
          toRemove match
            case None => Left("Politician not found in this Town Hall")
            case Some(politician) =>
              val remaining = politicians.filterNot(_.id == politician.id)
              val updatedTile = tile.copy(tileType = TileType.TownHall(remaining))
              Right(game.updateTileOnCurrentIsland(townHallCoord, updatedTile).copy(
                politicianRoster = game.politicianRoster :+ politician
              ))
        case TileType.TownHall(_) => Left("Town Hall has no politician")
        case _ => Left("Tile is not a town hall")

  // Swap politicians between two town halls (or move from one to an empty one)
  // Moves the first politician from source to target; if target is full, swaps with target's first
  def swapPoliticians(game: TileKingdomGame, fromCoord: Coord, toCoord: Coord): Either[String, TileKingdomGame] =
    if fromCoord == toCoord then return Left("Cannot swap with self")

    (game.tiles.get(fromCoord), game.tiles.get(toCoord)) match
      case (Some(fromTile), Some(toTile)) =>
        (fromTile.tileType, toTile.tileType) match
          case (TileType.TownHall(fromPols), TileType.TownHall(toPols)) if fromPols.nonEmpty =>
            val capacity = townHallCapacity(game)
            val movedPol = fromPols.head
            val fromRemaining = fromPols.tail
            if toPols.size < capacity then
              // Target has room: just move
              val updatedFrom = fromTile.copy(tileType = TileType.TownHall(fromRemaining))
              val updatedTo = toTile.copy(tileType = TileType.TownHall(toPols :+ movedPol))
              Right(game.updateTilesOnCurrentIsland(Map(
                fromCoord -> updatedFrom,
                toCoord -> updatedTo
              )))
            else
              // Target full: swap first politician
              val swappedPol = toPols.head
              val updatedFrom = fromTile.copy(tileType = TileType.TownHall(fromRemaining :+ swappedPol))
              val updatedTo = toTile.copy(tileType = TileType.TownHall(toPols.tail :+ movedPol))
              Right(game.updateTilesOnCurrentIsland(Map(
                fromCoord -> updatedFrom,
                toCoord -> updatedTo
              )))
          case (TileType.TownHall(pols), _) if pols.isEmpty => Left("Source town hall has no politician")
          case (_, TileType.TownHall(_)) => Left("Source is not a town hall")
          case _ => Left("Target is not a town hall")
      case (None, _) => Left("Source tile not found")
      case (_, None) => Left("Target tile not found")

  // Level up any upgradeable tile (wheat field, farm, woodcutter, temple, quarry)
  def levelUp(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) =>
        effectiveUpgradeCost(game, tile) match
          case None => Left("Tile is not upgradeable")
          case Some(cost) =>
            if !game.canAfford(cost) then
              Left(s"Not enough ${cost.resource.toString.toLowerCase} (need ${cost.amount})")
            else
              Right(game.deduct(cost).updateTileOnCurrentIsland(coord, tile.withNextLevel))


  // Cycle bureau mode: Slow -> Turbo -> Disabled -> Slow
  def cycleBureauMode(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.isBureau => Left("Tile is not a bureau")
      case Some(_) =>
        val currentMode = game.bureauMode.getOrElse(coord, BureauMode.Slow)
        val nextMode = currentMode match
          case BureauMode.Slow => BureauMode.Turbo
          case BureauMode.Turbo => BureauMode.Disabled
          case BureauMode.Disabled => BureauMode.Slow
        Right(game.copy(
          bureauMode = game.bureauMode.updated(coord, nextMode)
        ))

  // Get bureau mode (defaults to Slow)
  def getBureauMode(game: TileKingdomGame, bureauCoord: Coord): BureauMode =
    game.bureauMode.getOrElse(bureauCoord, BureauMode.Slow)

  // Check if bureau is in turbo mode
  def isBureauTurbo(game: TileKingdomGame, bureauCoord: Coord): Boolean =
    getBureauMode(game, bureauCoord) == BureauMode.Turbo

  // Check if bureau is disabled
  def isBureauDisabled(game: TileKingdomGame, bureauCoord: Coord): Boolean =
    getBureauMode(game, bureauCoord) == BureauMode.Disabled

  // Bureau direction constants
  val BureauDirectionLength: Int = 5 // 5 tiles long in the chosen direction
  val BureauDirectionHalfWidth: Int = 1 // 1 tile on each side = 3 wide

  // Get bureau direction (defaults to Center)
  def getBureauDirection(game: TileKingdomGame, bureauCoord: Coord): BureauDirection =
    game.bureauDirection.getOrElse(bureauCoord, BureauDirection.Center)

  // Set bureau direction
  def setBureauDirection(game: TileKingdomGame, coord: Coord, direction: BureauDirection): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.isBureau => Left("Tile is not a bureau")
      case Some(_) if !game.hasSkill(Skill.Management1B) => Left("Requires Management 1B skill")
      case Some(_) =>
        Right(game.copy(
          bureauDirection = game.bureauDirection.updated(coord, direction)
        ))

  // Get the set of coords affected by a bureau, considering direction skill
  def bureauAffectedCoords(game: TileKingdomGame, bureauCoord: Coord): Set[Coord] =
    if game.hasSkill(Skill.Management1B) then
      val direction = getBureauDirection(game, bureauCoord)
      bureauCoord.rectangleInDirection(direction, BureauDirectionLength, BureauDirectionHalfWidth)
    else
      bureauCoord.neighborsWithinRadius(BureauRadius)

  // Calculate turbo faith cost based on target tile level (level × 10)
  def bureauTurboFaithCostForLevel(level: Int): Int = level * 10

  // Effective bureau wood cost per upgrade (0 with Management1A, reduced by 90% with Logistics1A)
  def effectiveBureauWoodCost(game: TileKingdomGame): Int =
    if game.hasSkill(Skill.Management1A) then 0
    else if game.hasSkill(Skill.Logistics1A) then (BureauWoodCostPerUpgrade * 0.1).toInt.max(1)
    else BureauWoodCostPerUpgrade

  // Effective bureau turbo faith cost (reduced by 90% with Logistics1B skill)
  def effectiveBureauFaithCostForLevel(game: TileKingdomGame, level: Int): Int =
    val base = bureauTurboFaithCostForLevel(level)
    if game.hasSkill(Skill.Logistics1B) then (base * 0.1).toInt.max(1)
    else base

  // Get bureau speed multiplier (0.0 = disabled, 1.0 = slow mode, 10.0 = turbo mode)
  def bureauSpeedMultiplier(game: TileKingdomGame, bureauCoord: Coord): Double =
    getBureauMode(game, bureauCoord) match
      case BureauMode.Slow => 1.0
      case BureauMode.Turbo => BureauTurboSpeedMultiplier
      case BureauMode.Disabled => 0.0

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
        val woodCost = effectiveBureauWoodCost(game)
        
        // Find upgradeable tiles within affected area with their costs
        val nearbyCoords = bureauAffectedCoords(game, bureauCoord)
        val upgradeableTiles = nearbyCoords
          .flatMap(coord => game.tiles.get(coord).map(coord -> _))
          .filter((_, tile) => tile.isUpgradeable)
          .flatMap((coord, tile) => effectiveUpgradeCost(game, tile).map(cost => (coord, tile, cost)))

        // Check if we have enough resources for turbo mode
        // Faith cost is level × 10 (possibly reduced), so check against the cheapest upgradeable tile
        val canAffordWood = game.wood >= woodCost
        val minLevelTile = upgradeableTiles.minByOption(_._2.level)
        val minFaithCost = minLevelTile.map(t => effectiveBureauFaithCostForLevel(game, t._2.level)).getOrElse(Int.MaxValue)
        val canAffordFaith = game.faith >= minFaithCost

        // Auto-disable turbo mode if we can't afford wood or faith for the cheapest tile
        val gameWithTurboCheck =
          if isTurbo && (!canAffordWood || !canAffordFaith) then
            game.copy(bureauMode = game.bureauMode.updated(bureauCoord, BureauMode.Slow))
          else game
        
        val effectiveIsTurbo = isBureauTurbo(gameWithTurboCheck, bureauCoord)

        // Must have wood for the bureau fee regardless of what we upgrade
        if gameWithTurboCheck.wood < woodCost then 
          // Return game with turbo disabled if it was disabled
          if gameWithTurboCheck ne game then return Some((gameWithTurboCheck, bureauCoord))
          else return None

        // Filter to only tiles we can afford (including bureau wood fee for wood-cost upgrades)
        // In turbo mode, also check if we can afford the faith cost for each tile's level
        val affordableTiles = upgradeableTiles.filter: (_, tile, cost) =>
          val extraWoodNeeded = if cost.resource == Resource.Wood then woodCost else 0
          val canAffordUpgrade = gameWithTurboCheck.canAfford(cost.amount + extraWoodNeeded, cost.resource)
          val canAffordTurboFaith = !effectiveIsTurbo || gameWithTurboCheck.faith >= effectiveBureauFaithCostForLevel(gameWithTurboCheck, tile.level)
          canAffordUpgrade && canAffordTurboFaith

        // Select the tile with the lowest upgrade cost (comparing numerically)
        affordableTiles.minByOption(_._3.amount) match
          case Some((targetCoord, targetTile, cost)) =>
            // Perform the upgrade
            val upgradedTile = targetTile.withNextLevel

            // Deduct upgrade cost and bureau fee from the turbo-checked game
            val afterUpgradeCost = gameWithTurboCheck.deduct(cost)
            val afterBureauFee = afterUpgradeCost.copy(wood = afterUpgradeCost.wood - woodCost)
            
            // Deduct faith cost if effectively in turbo mode
            val turboFaithCost = effectiveBureauFaithCostForLevel(gameWithTurboCheck, targetTile.level)
            val afterTurboCost =
              if effectiveIsTurbo then afterBureauFee.copy(faith = afterBureauFee.faith - turboFaithCost)
              else afterBureauFee

            val newGame = afterTurboCost.updateTileOnCurrentIsland(targetCoord, upgradedTile)
            Some((newGame, targetCoord))
          case None =>
            // No affordable upgrade, but return game with turbo disabled if it was disabled
            if gameWithTurboCheck ne game then Some((gameWithTurboCheck, bureauCoord))
            else None
      case _ => None

  // Destroy a building on a tile (returns it to empty state, no refund)
  // If destroying a Town Hall, its politicians are returned to the roster
  def destroyBuilding(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None                         => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) if tile.isEmpty   => Left("Tile is already empty")
      case Some(tile) =>
        val updatedTile = tile.copy(tileType = TileType.Empty)
        val returnedPoliticians = tile.tileType match
          case TileType.TownHall(politicians) => politicians
          case _ => List.empty
        Right(game.updateTileOnCurrentIsland(coord, updatedTile).copy(
          politicianRoster = game.politicianRoster ++ returnedPoliticians
        ))

  // Destroy a tile entirely (resets it to locked empty), awarding a tile point
  // If destroying a Town Hall, its politicians are returned to the roster
  def destroyTile(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    game.tiles.get(coord) match
      case None => Left("Tile not found")
      case Some(tile) if !tile.unlocked => Left("Tile is locked")
      case Some(tile) =>
        val returnedPoliticians = tile.tileType match
          case TileType.TownHall(politicians) => politicians
          case _ => List.empty
        Right(game.removeTileOnCurrentIsland(coord).copy(
          tilePoints = game.tilePoints + 1,
          politicianRoster = game.politicianRoster ++ returnedPoliticians
        ))

  // Abdicate: reset buildings on all islands, gain gold based on income rate
  // Can abdicate at any time (no restrictions)
  def abdicate(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
    val goldReward = abdicationReward(game.totalIncomeRate)

    // Clear all buildings but keep all islands and tile unlock status
    val resetIslands = game.islands.map { island =>
      island.copy(tiles = island.tiles.map { case (coord, tile) =>
        coord -> tile.copy(tileType = TileType.Empty) // Keep unlocked status
      })
    }

    Right(game.copy(
      islands = resetIslands,
      currentIslandIndex = 0, // Go back to first island
      wheat = 50.0, // Reset wheat, give starting amount
      wood = 0.0, // Reset wood
      faith = 0.0, // Reset faith
      stone = 0.0, // Reset stone
      gold = game.gold + goldReward,
      lastTickTime = currentTimeMillis,
      totalAbdications = game.totalAbdications + 1,
      bureauMode = Map.empty, // Reset bureau mode since bureaus are destroyed
      bureauDirection = Map.empty,
      townHallDirection = Map.empty,
      politicianRoster = List.empty, // All politicians are destroyed on abdication
      politicianGenerationProgress = 0.0, // Reset politician generation progress
      hasPlacedBuilding = false // Fresh abdication
    ))

  // Sail: second tier prestige - reset everything including gold, gain skill points directly
  def sail(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
    if !game.canSail then
      Left(s"Need at least ${game.sailTileThreshold} tiles to sail (have ${game.totalUnlockedTileCount})")
    else
      val tilesDestroyed = game.totalUnlockedTileCount
      // Legacy points track total tiles lost to sailing (only goes up)
      val newLegacyPoints = game.legacyPoints + tilesDestroyed
      // Skill points = tiles above threshold + 1 (minimum 1)
      val skillPointsEarned = game.sailSkillPointReward
      // New threshold is current tile count + 1
      val newThreshold = game.sailNextThreshold

      // Reset to single starting island
      val startingIsland = Island.create(0)

      val newTotalSkillPoints = game.skillPoints + skillPointsEarned
      val newTotalEarned = game.totalSkillPointsEarned + skillPointsEarned

      Right(game.copy(
        islands = List(startingIsland),
        currentIslandIndex = 0,
        wheat = 50.0, // Reset to starting amount
        wood = 0.0,
        faith = 0.0,
        stone = 0.0,
        gold = StartingGold, // Reset to starting gold
        lastTickTime = currentTimeMillis,
        totalAbdications = 0, // Abdications reset on sail
        bureauMode = Map.empty,
        bureauDirection = Map.empty,
        townHallDirection = Map.empty,
        politicianRoster = List.empty,
        politicianGenerationProgress = 0.0,
        legacyPoints = newLegacyPoints,
        skillPoints = newTotalSkillPoints,
        hasSailed = true, // Mark that player has sailed at least once
        hasPlacedBuilding = false, // Fresh sail
        totalSkillPointsEarned = newTotalEarned,
        sailedCount = game.sailedCount + 1,
        sailTileThreshold = newThreshold
      ))

  // Get all coords that can be unlocked on current island (locked tiles adjacent to unlocked tiles)
  def unlockableCoords(game: TileKingdomGame): Set[Coord] =
    game.unlockableCoordsOnCurrentIsland

  // Unlock a specific tile on current island (uses tile point if available, otherwise gold)
  def unlockTile(game: TileKingdomGame, coord: Coord): Either[String, TileKingdomGame] =
    unlockTileOnCurrentIsland(game, coord)


  // Unlock a skill from the skill tree
  def unlockSkill(game: TileKingdomGame, skill: Skill): Either[String, TileKingdomGame] =
    if !game.hasSailed then
      Left("Sail at least once to unlock the skill tree")
    else if game.unlockedSkills.contains(skill) then
      Left("Skill already unlocked")
    else if game.skillPoints < Skill.cost(skill) then
      Left(s"Not enough skill points (need ${Skill.cost(skill)})")
    else if Skill.mutuallyExclusive(skill).exists(game.unlockedSkills.contains) then
      Left("Cannot unlock - mutually exclusive skill already chosen")
    else
      val prereqMet = Skill.prerequisite(skill).forall(game.unlockedSkills.contains)
      val altPrereqMet = Skill.alternativePrerequisites(skill) match
        case Some(alternatives) => alternatives.exists(game.unlockedSkills.contains)
        case None => true
      if !prereqMet || !altPrereqMet then
        Left(s"Prerequisites not met")
      else
        Right(game.copy(
          skillPoints = game.skillPoints - Skill.cost(skill),
          unlockedSkills = game.unlockedSkills + skill
        ))

  // Check if player can switch to a mutually exclusive skill (free, only on fresh abdication)
  def canSwitchSkill(game: TileKingdomGame, toSkill: Skill): Boolean =
    if !game.hasSailed then false
    else if !game.isFreshAbdication then false
    else if game.unlockedSkills.contains(toSkill) then false
    else
      // Must have the mutually exclusive skill already unlocked
      Skill.mutuallyExclusive(toSkill).exists(game.unlockedSkills.contains)

  // Switch from one skill to its mutually exclusive alternative (free, only on fresh abdication)
  def switchSkill(game: TileKingdomGame, toSkill: Skill): Either[String, TileKingdomGame] =
    if !game.hasSailed then
      Left("Sail at least once to unlock the skill tree")
    else if !game.isFreshAbdication then
      Left("Can only switch skills before placing any buildings")
    else if game.unlockedSkills.contains(toSkill) then
      Left("Skill already unlocked")
    else
      Skill.mutuallyExclusive(toSkill) match
        case Some(fromSkill) if game.unlockedSkills.contains(fromSkill) =>
          Right(game.copy(
            unlockedSkills = game.unlockedSkills - fromSkill + toSkill
          ))
        case _ =>
          Left("No mutually exclusive skill to switch from")

  // Refund a skill: costs 1000 gold per skill point, only on fresh abdication
  def refundSkill(game: TileKingdomGame, skill: Skill): Either[String, TileKingdomGame] =
    if !game.hasSailed then
      Left("Sail at least once to unlock the skill tree")
    else if !game.isFreshAbdication then
      Left("Can only refund skills before placing any buildings")
    else if !game.unlockedSkills.contains(skill) then
      Left("Skill is not unlocked")
    else
      val cost = Skill.cost(skill)
      val goldCost = cost * SkillRefundGoldCost
      if game.gold < goldCost then
        Left(s"Not enough gold (need ${goldCost} 💰)")
      else if !game.canRefundSkill(skill) then
        Left("Cannot refund — another skill depends on this one")
      else
        Right(game.copy(
          gold = game.gold - goldCost,
          skillPoints = game.skillPoints + cost,
          unlockedSkills = game.unlockedSkills - skill
        ))


# Island-Based Tile Kingdom Overhaul

## Goal

Transform Tile Kingdom from an infinite expanding grid into a focused island-hopping experience. Each island is a fixed 3×5 tile grid (3 wide, 5 high = 15 tiles). Players fill one island, then unlock another, and can switch between islands to manage their kingdom.

---

## Problem Statement

The current implementation allows players to unlock hundreds of tiles in an infinite grid. This leads to:
- **Unwieldy management**: Too many tiles to track and optimize
- **Performance concerns**: Large tile counts slow down rendering and calculations
- **Reduced fun**: The game loses focus when there's no constraint on expansion
- **Diluted strategy**: With unlimited space, placement decisions matter less

---

## Proposed Solution

### Core Concept: Islands

An **Island** is a fixed 3×5 tile grid (15 tiles total). Players:
1. Start with one island
2. Fill all 15 tiles with buildings
3. Unlock a new island when the current one is full
4. Navigate between islands using left/right arrows or keyboard shortcuts

### Key Design Decisions

| Aspect | Decision |
|--------|----------|
| Island size | 3 columns × 5 rows = 15 tiles |
| Unlock condition | Current island full OR paid gold cost |
| Tile unlocking | Tiles within islands start **locked**; unlock with gold |
| Starting gold | Enough to unlock 1 tile (e.g., 10 gold) |
| Production scope | **All islands** run production calculations |
| Rendering scope | **One island** visible at a time |
| Navigation | Left/Right arrows or ← / → keys |
| Politicians | Affect tiles on the **same island** only |
| Bureaus | Affect tiles on the **same island** only |

---

## Data Model Changes

### New Types

```scala
case class Island(
  id: Int,                      // Sequential island number (0, 1, 2, ...)
  tiles: Map[Coord, Tile]       // Local coords (0,0) to (2,4), tiles can be locked/unlocked
)

// Coord is now local to an island (0-2 for col, 0-4 for row)
// Tile.unlocked field is preserved - tiles within an island must be unlocked with gold
```

### Modified `TileKingdomGame`

```scala
case class TileKingdomGame(
  islands: List[Island],        // Replaces: tiles: Map[Coord, Tile]
  currentIslandIndex: Int,      // Which island is being viewed
  // ... rest unchanged: wheat, wood, faith, gold, stone, etc.
)
```

### Helper Methods

```scala
// Get the currently viewed island
def currentIsland: Island = islands(currentIslandIndex)

// Get all unlocked tiles across all islands (for production calculations)
def allUnlockedTiles: List[Tile] = islands.flatMap(_.tiles.values.filter(_.unlocked))

// Check if current island is full (all unlocked tiles have buildings)
def currentIslandFull: Boolean = 
  currentIsland.tiles.values.filter(_.unlocked).forall(_.isBuilding)

// Check if all tiles on current island are unlocked
def currentIslandAllTilesUnlocked: Boolean =
  currentIsland.tiles.values.forall(_.unlocked)

// Check if can unlock new island (all 15 tiles unlocked AND all have buildings)
def canUnlockIsland: Boolean = 
  currentIslandAllTilesUnlocked && currentIslandFull && gold >= nextIslandCost

// Get unlockable tile coords on current island (locked tiles adjacent to unlocked tiles)
def unlockableCoordsOnCurrentIsland: Set[Coord] =
  val unlockedCoords = currentIsland.tiles.values.filter(_.unlocked).map(_.coord).toSet
  val allAdjacentToUnlocked = unlockedCoords.flatMap(_.neighbors)
  allAdjacentToUnlocked.filter(c => 
    currentIsland.tiles.get(c).exists(!_.unlocked)  // Must be a locked tile on this island
  )
```

---

## Production System Changes

### All Islands Produce

The game loop ticks **all islands simultaneously**:

```scala
private def gameTick(): Unit =
  val currentTime = System.currentTimeMillis()
  val elapsedMs = (currentTime - currentGame.lastTickTime).toDouble

  // Harvest from ALL islands, not just the visible one
  val allProducingTiles = currentGame.allUnlockedTiles
  
  val totalWheat = harvestProducingTiles(allProducingTiles.filter(_.isWheatField), ...)
  val totalWood = harvestProducingTiles(allProducingTiles.filter(_.isWoodcutter), ...)
  // ... etc
```

### Politicians Are Island-Scoped

A Town Hall's politician effects only apply to tiles on the **same island**:

```scala
def politicianMultiplier(game: TileKingdomGame, island: Island, coord: Coord, resource: Resource): Double =
  val nearbyTownHalls = island.tiles.values.filter(t => t.isTownHall && t.tileType match
    case TileType.TownHall(pols) if pols.nonEmpty => true
    case _ => false
  )
  // Only considers Town Halls on this island
```

### Bureaus Are Island-Scoped

Bureau auto-upgrades only affect tiles on the same island:

```scala
def bureauAffectedCoords(island: Island, bureauCoord: Coord): Set[Coord] =
  // Only returns coords that exist within this island's bounds
  bureauCoord.neighborsWithinRadius(2)
    .filter(c => c.col >= 0 && c.col < 3 && c.row >= 0 && c.row < 5)
```

---

## UI Changes

### Render Only Current Island

The `TileGrid` component renders only the current island's tiles:

```scala
val visibleSlotsSignal = tilesSignal
  .combineWith(currentIslandSignal)
  .map { (_, island) =>
    // Only tiles from the current island
    island.tiles.values.toList.map(tile => TileSlot(tile.coord, isUnlockable = false))
  }
```

### Island Navigation UI

New component for switching islands:

```scala
object IslandNavigator:
  def apply(
    onPrevious: () => Unit,
    onNext: () => Unit,
    canGoPrevious: Signal[Boolean],
    canGoNext: Signal[Boolean],
    currentIndex: Signal[Int],
    totalIslands: Signal[Int]
  ): HtmlElement =
    div(cls := "island-navigator",
      button(
        cls := "nav-arrow nav-left",
        disabled <-- canGoPrevious.map(!_),
        onClick --> (_ => onPrevious()),
        "←"
      ),
      span(
        cls := "island-indicator",
        child.text <-- currentIndex.combineWith(totalIslands).map { (i, total) =>
          s"Island ${i + 1} / $total"
        }
      ),
      button(
        cls := "nav-arrow nav-right",
        disabled <-- canGoNext.map(!_),
        onClick --> (_ => onNext()),
        "→"
      )
    )
```

### Keyboard Navigation

```scala
dom.document.onkeydown = (e: KeyboardEvent) =>
  e.key match
    case "ArrowLeft" => handlePreviousIsland()
    case "ArrowRight" => handleNextIsland()
    case _ => ()
```

### New Island Unlock Button

When current island is full, show unlock button:

```scala
object UnlockIslandButton:
  def apply(canUnlock: Signal[Boolean], cost: Signal[Int], onUnlock: () => Unit): HtmlElement =
    button(
      cls := "unlock-island-btn",
      cls <-- canUnlock.map(if _ then "" else "disabled"),
      child.text <-- cost.map(c => s"🏝️ Unlock New Island ($c 💰)"),
      onClick --> (_ => onUnlock())
    )
```

---

## Removed Features

The island model **removes** the following:
- Infinite tile expansion (bounded by island grid)
- Pan/zoom navigation (no longer needed with fixed islands)
- Snap-back behavior

The island model **preserves**:
- Tile unlock costs (tiles within an island start locked)
- `unlockableCoords` calculation (now island-scoped)
- Progressive tile expansion within each island

---

## Abdication and Sail Mechanics

The two prestige systems work differently with islands:

### Abdication (Tier 1 Prestige)

**Trigger**: Can abdicate **at any time** (no restrictions).

**Effect**:
- All buildings on all islands are destroyed (become Empty tiles)
- All **unlocked tiles remain unlocked** (you keep your tile progress)
- All islands remain (you keep your island count)
- Gold reward based on total income rate (unchanged formula)
- Resources reset (wheat=50, wood=0, faith=0, stone=0)
- Politicians destroyed, generation progress reset

**Purpose**: Quick reset to rebuild with accumulated gold. Both islands and unlocked tiles are preserved.

```scala
def abdicate(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
  val goldReward = abdicationReward(game.totalIncomeRate)
  
  // Clear all buildings but keep all islands and tile unlock status
  val resetIslands = game.islands.map { island =>
    island.copy(tiles = island.tiles.map { case (coord, tile) =>
      coord -> tile.copy(tileType = TileType.Empty)  // Keep unlocked status
    })
  }
  
  Right(game.copy(
    islands = resetIslands,
    currentIslandIndex = 0,  // Go back to first island
    wheat = 50.0, wood = 0.0, faith = 0.0, stone = 0.0,
    gold = game.gold + goldReward,
    totalAbdications = game.totalAbdications + 1,
    // ... other resets unchanged
  ))
```

### Sail (Tier 2 Prestige)

**Trigger**: Must have at least **2 islands** unlocked (30+ tiles).

**Effect**:
- **All islands destroyed** except the first one
- Back to 1 island with 15 empty tiles
- Legacy points earned = total tile count across all islands
- Gold resets to 0
- Skill points awarded based on legacy points
- Abdication count resets

**Purpose**: Major reset for skill tree progression. This is a bigger commitment since you lose your islands.

```scala
def sail(game: TileKingdomGame, currentTimeMillis: Long): Either[String, TileKingdomGame] =
  if game.islands.size < SailMinIslands then  // SailMinIslands = 2
    Left(s"Must have at least $SailMinIslands islands to sail")
  else
    val totalTiles = game.allUnlockedTiles.size  // Count all tiles for legacy
    val totalLegacyPoints = game.legacyPoints + totalTiles
    val skillPointsEarned = totalLegacyPoints / LegacyPointsPerSkillPoint
    
    // Reset to single starting island
    val startingIsland = newIsland(id = 0)
    
    Right(game.copy(
      islands = List(startingIsland),
      currentIslandIndex = 0,
      wheat = 50.0, wood = 0.0, faith = 0.0, stone = 0.0,
      gold = 0,  // Gold resets on sail
      totalAbdications = 0,
      legacyPoints = totalLegacyPoints % LegacyPointsPerSkillPoint,
      skillPoints = game.skillPoints + skillPointsEarned,
      hasSailed = true,
      // ... other resets
    ))
```

### Summary Table

| Action | Trigger | Islands After | Tiles After | Gold | Legacy Points |
|--------|---------|---------------|-------------|------|---------------|
| **Abdicate** | Any time | Keep all | Keep unlocked (empty) | + reward | No change |
| **Sail** | 2+ islands | Reset to 1 | Reset to 1 locked | = 0 | + total tiles |

---

## Migration Strategy

### Save Compatibility

Old saves have `tiles: Map[Coord, Tile]`. Migration:

```scala
def migrateOldSave(old: OldTileKingdomGame): TileKingdomGame =
  // Group existing tiles into 3x5 islands
  val sortedTiles = old.tiles.values.toList.sortBy(t => (t.coord.row, t.coord.col))
  val islands = sortedTiles.grouped(15).zipWithIndex.map { (tiles, idx) =>
    // Remap coords to local 0-2, 0-4 space
    val localTiles = tiles.zipWithIndex.map { (tile, localIdx) =>
      val localCoord = Coord(localIdx / 3, localIdx % 3)
      tile.copy(coord = localCoord)
    }
    Island(id = idx, tiles = localTiles.map(t => t.coord -> t).toMap, unlocked = true)
  }.toList
  
  TileKingdomGame(islands = islands, currentIslandIndex = 0, ...)
```

---

## Implementation Checklist

### Phase 1: Data Model
- [x] Define `Island` case class in `TileKingdom.scala`
- [x] Add `islands: List[Island]` to `TileKingdomGame`
- [x] Add `currentIslandIndex: Int` to `TileKingdomGame`
- [x] Add helper methods: `currentIsland`, `allUnlockedTiles`, `currentIslandFull`
- [x] Update `TileKingdomGame` ReadWriter for JSON serialization
- [x] ~~Create migration function for old saves~~ (dropped - no migration)

### Phase 2: Island Generation
- [x] Implement `newIsland(id: Int): Island` in `TileKingdomLogic` (all 15 tiles locked)
- [x] Update `newGame()` to start with one island (15 locked tiles) + enough gold for 1 unlock
- [x] Implement `unlockNewIsland(game: TileKingdomGame): Either[String, TileKingdomGame]`
- [x] Define island unlock cost formula
- [x] Implement `unlockTileOnIsland(game, islandId, coord)` for tile unlocking within islands

### Phase 3: Production Logic
- [x] Update `tick()` to iterate over all islands
- [x] Update wheat/wood/stone/faith harvesting to use `allUnlockedTiles`
- [x] Update bureau logic to be island-scoped
- [x] Update politician influence to be island-scoped
- [x] Update farm boost calculations to be island-scoped

### Phase 4: UI - Island Navigator
- [x] Create `IslandNavigator.scala` component
- [x] Add island indicator (e.g., "Island 1 / 3")
- [x] Implement left/right navigation buttons
- [x] Implement keyboard shortcuts (← / →)
- [ ] Add visual transition animation between islands (optional)

### Phase 5: UI - Grid Simplification
- [x] Update `TileGrid.scala` to render current island only
- [ ] Remove pan/zoom functionality (kept for accessibility)
- [ ] Remove snap-back logic (kept for now)
- [x] Update `TileGridState.scala` to track current island
- [x] Fix influence indicators for island-local coords

### Phase 6: UI - Island Unlock
- [x] Create `UnlockIslandButton.scala` component (integrated into IslandNavigator)
- [x] Show when current island is full
- [x] Display unlock cost
- [x] Handle unlock action

### Phase 7: Build Menu Adjustments
- [x] Update build menu to use island-local coords
- [x] Update unlockable tile logic to be island-scoped
- [x] All tiles on a new island start **locked**
- [x] First tile on each island is free to unlock (or player has enough starting gold)

### Phase 8: State Signals
- [x] Add `currentIslandSignal` to `TileKingdomState`
- [x] Add `currentIslandIndexSignal`
- [x] Add `totalIslandsSignal`
- [x] Add `canUnlockIslandSignal`
- [x] Update derived signals for island-scoped data

### Phase 9: Sail/Abdication Adjustments
- [x] Update abdication to clear all buildings on all islands (islands remain)
- [x] Update sail to reset back to one starting island
- [x] Change sail requirement from "25 tiles" to "2 islands"

### Phase 10: Polish
- [ ] Update skill tree if any skills reference tile counts
- [ ] Update help popup with new island mechanics
- [x] Add notification when new island is unlocked
- [ ] Update resource panel to show total tiles across all islands
- [ ] Test save/load with multiple islands

### Phase 11: Testing
- [x] Test production runs on all islands
- [x] Test politician effects are island-scoped
- [x] Test bureau auto-upgrades are island-scoped
- [ ] ~~Test save migration from old format~~ (dropped)
- [x] Test navigation between islands
- [x] Test island unlock flow

---

## Open Questions

1. **Island unlock cost curve**: Linear? Exponential? Suggestions:
   - Island 2: 1,000 gold
   - Island 3: 5,000 gold
   - Island 4: 25,000 gold
   - Island 5+: 25,000 × 2^(n-4) gold

2. **Cross-island bonuses**: Should there be any global bonuses that span islands?

3. **Island specialization**: Should each island have a theme or bonus? (Future feature)

4. **Visual differentiation**: Should islands have unique backgrounds or names?

---

## Future Enhancements

- **Island names**: Let players name their islands
- **Island themes**: Each island could have a terrain type affecting production
- **Cross-island trade routes**: Unlock connections between islands
- **Island minimap**: Overview showing all islands at once
- **Island objectives**: Each island has a goal to complete for bonuses


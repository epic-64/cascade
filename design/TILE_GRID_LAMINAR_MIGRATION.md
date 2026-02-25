# Tile Grid Laminar Migration Plan

## Overview

This document outlines the plan to migrate the tile grid rendering from imperative DOM manipulation in `TileKingdomClient.scala` to reactive Laminar components. This is the final major piece of the TileKingdom UI that needs migration.

---

## Current State

### What Has Been Migrated to Laminar

The following components are already using Laminar with reactive state via `TileKingdomState`:

| Component | File | Description |
|-----------|------|-------------|
| ResourcePanel | `ResourcePanel.scala` | Displays wheat, wood, stone, faith, gold, etc. |
| AbdicationButton | `AbdicationButton.scala` | Abdicate action button |
| SailButton | `SailButton.scala` | Sail away action button |
| SkillsButton | `SkillsButton.scala` | Skills tree toggle button |
| PoliticianTimer | `PoliticianTimer.scala` | Timer for next politician generation |
| PoliticianRoster | `PoliticianRoster.scala` | List of available politicians with drag-drop |
| NotificationSystem | `NotificationSystem.scala` | Toast notifications |
| SkillTree | `SkillTree.scala` | Skill tree modal |
| HelpPopup | `HelpPopup.scala` | Help/instructions popup |
| DevToolsPopup | `DevToolsPopup.scala` | Developer tools popup |
| WelcomeBackModal | `WelcomeBackModal.scala` | Offline progress modal |

### What Remains in Imperative Code

The tile grid itself (~800 lines) in `TileKingdomClient.scala`:

1. **Grid rendering** (`renderTiles()`, `renderTile()`, `renderUnlockableTile()`)
2. **Tile type rendering** (WheatField, Farm, Woodcutter, Bureau, Temple, Quarry, TownHall, Academy, Tavern, Empty)
3. **Progress bar management** (`createProgressBar()`, `updateProgressBars()`)
4. **Pan/zoom handling** (`setupDragHandlers()`, viewport positioning)
5. **Influence indicators** (`renderInfluenceIndicator()`)
6. **Build menus** (category selection, submenus)
7. **Floating animations** (`showFloatingReward()`, `showFloatingLevel()`, `showBureauProjectile()`)
8. **Tile interactions** (click handlers, drag-drop for politicians)

---

## Benefits of Migration

### 1. **Declarative UI**
- Current: `tileDiv.appendChild(div(cls = "tile-content")(...))` with manual DOM construction
- After: Laminar elements that declare structure once, update reactively

### 2. **Automatic Updates**
- Current: Manually call `renderTiles()`, `updateSingleTile()`, `updateTownHallLifespan()`
- After: Signal changes automatically propagate to affected tiles only

### 3. **Type Safety**
- Current: String-based element IDs (`getElementById(s"tile-${coord.row}-${coord.col}")`)
- After: Direct element references with Scala types

### 4. **Better Performance**
- Current: Full grid re-render on many state changes
- After: Fine-grained reactive updates (only changed tiles update)

### 5. **Simplified Event Handling**
- Current: Manual `onclick`, `ondragstart`, etc. assignments with imperative callbacks
- After: Laminar's `onClick --> handler` with reactive state integration

### 6. **Easier Testing**
- Current: Tests need to manipulate DOM directly
- After: Components can be unit tested with signal inputs

### 7. **Reduced State Synchronization Bugs**
- Current: `currentGame` and `TileKingdomState` must be kept in sync manually
- After: Single source of truth with derived signals

---

## Architecture Design

### New Component Structure

```
client/components/laminar/tilekingdom/
├── TileGrid.scala          # Main grid container with viewport
├── TileRenderer.scala      # Individual tile rendering (dispatches by type)
├── tiles/
│   ├── EmptyTile.scala     # Empty tile with build menu
│   ├── WheatFieldTile.scala
│   ├── FarmTile.scala
│   ├── WoodcutterTile.scala
│   ├── BureauTile.scala
│   ├── TempleTile.scala
│   ├── QuarryTile.scala
│   ├── TownHallTile.scala
│   ├── AcademyTile.scala
│   ├── TavernTile.scala
│   └── UnlockableTile.scala
├── BuildMenu.scala         # Build selection overlay
├── InfluenceIndicator.scala
├── ProgressBar.scala       # Reusable progress bar component
├── FloatingEffects.scala   # Floating reward/level animations
└── TileGridState.scala     # Grid-specific reactive state (pan, zoom, selection)
```

### State Architecture

#### TileGridState.scala (New)

```scala
object TileGridState:
  // Pan/zoom state
  val panOffset: Var[(Double, Double)] = Var((0.0, 0.0))
  val zoomLevel: Var[Double] = Var(2.0)
  
  // Selection state
  val selectingTileCoord: Var[Option[Coord]] = Var(None)
  val activeSubmenu: Var[Option[String]] = Var(None)
  
  // Progress tracking (reactive per-tile)
  val tileProgress: Var[Map[Coord, Double]] = Var(Map.empty)
  
  // Derived signals
  val visibleBounds: Signal[(Int, Int, Int, Int)] = ...
  val zoomTier: Signal[ZoomTier] = ...
```

#### Enhanced TileKingdomState.scala

```scala
// Add tile-specific signals
val tilesSignal: Signal[Map[Coord, Tile]] = gameSignal.map(_.tiles)

// Per-tile signals for fine-grained updates
def tileSignal(coord: Coord): Signal[Option[Tile]] =
  tilesSignal.map(_.get(coord))
```

### Rendering Strategy

#### Virtual Scrolling
Only render tiles within the visible viewport bounds:

```scala
def visibleTiles: Signal[Seq[Coord]] =
  TileGridState.visibleBounds.combineWith(TileKingdomState.tilesSignal).map:
    case ((minRow, maxRow, minCol, maxCol), tiles) =>
      tiles.keys.filter(c => 
        c.row >= minRow && c.row <= maxRow &&
        c.col >= minCol && c.col <= maxCol
      ).toSeq
```

#### Tile Element Pooling (Optional Optimization)
For very large grids, consider recycling DOM elements as tiles scroll in/out of view.

---

## Incremental Migration Steps

### Phase 1: Foundation (Low Risk)

**Goal**: Set up the component structure without changing behavior

1. **Create `TileGridState.scala`**
   - Move pan/zoom vars to reactive state
   - Add derived signals for visible bounds
   - Add tile progress tracking

2. **Create `ProgressBar.scala`** component
   - Reusable progress bar with reactive width
   - Test in isolation

3. **Create `InfluenceIndicator.scala`** component
   - Render influence rectangles
   - Position based on signals

**Checkpoint**: Grid still renders imperatively, but state lives in Laminar Vars

---

### Phase 2: Simple Tiles (Medium Risk)

**Goal**: Migrate the simplest tile types first

4. **Create `TavernTile.scala`**
   - Simplest tile (no progress bar, no interactions beyond destroy)
   - Pure display component

5. **Create `AcademyTile.scala`**
   - Slightly more complex (has mode toggle)
   - Action handler passed as prop

6. **Create `FarmTile.scala`**
   - Has level, upgrade cost, x10 button
   - Needs level-up handler

**Checkpoint**: Three tile types render via Laminar, others still imperative

---

### Phase 3: Production Tiles (Medium Risk)

**Goal**: Migrate tiles with progress bars

7. **Create `WheatFieldTile.scala`**
   - Progress bar integration
   - Farm bonus/town hall bonus badges
   - Level-up and destroy handlers

8. **Create `WoodcutterTile.scala`**
   - Similar to WheatField
   - Forest group bonus badge

9. **Create `TempleTile.scala`** and **`QuarryTile.scala`**
   - Same pattern as WheatField

**Checkpoint**: All production tiles use Laminar

---

### Phase 4: Complex Tiles (Higher Risk)

**Goal**: Migrate tiles with complex interactions

10. **Create `BureauTile.scala`**
    - Mode toggle buttons (Slow/Turbo/Disabled)
    - Progress bar (hidden when disabled)
    - Faith cost calculation for turbo mode

11. **Create `TownHallTile.scala`**
    - Drag-drop target for politicians
    - Politician display with lifespan timer
    - Swap functionality between town halls

**Checkpoint**: All built tiles use Laminar

---

### Phase 5: Build System (Higher Risk)

**Goal**: Migrate empty tiles and build menus

12. **Create `BuildMenu.scala`**
    - Category selection (Resources/Management)
    - Submenu navigation
    - Cost validation with reactive signals

13. **Create `EmptyTile.scala`**
    - Integrates BuildMenu
    - Selection state management

14. **Create `UnlockableTile.scala`**
    - Gold cost display
    - Click to unlock

**Checkpoint**: All tile types use Laminar

---

### Phase 6: Grid Container (Highest Risk)

**Goal**: Replace the grid rendering loop

15. **Create `TileRenderer.scala`**
    - Dispatcher that selects correct tile component by type
    - Handles zoom tier classes

16. **Create `TileGrid.scala`**
    - Main viewport container
    - Pan/zoom event handlers (mouse + touch)
    - Virtual scrolling based on visible bounds
    - Mounts tile components dynamically

17. **Update `TileKingdomClient.scala`**
    - Remove imperative `renderTiles()`, `renderTile()`, etc.
    - Mount `TileGrid()` component
    - Keep game tick logic (can migrate later if desired)

**Checkpoint**: Full Laminar rendering

---

### Phase 7: Animations & Polish

**Goal**: Migrate floating effects

18. **Create `FloatingEffects.scala`**
    - Floating reward numbers
    - Level-up text
    - Bureau projectile animation

19. **Integrate with tile components**
    - Effects triggered by signal changes
    - CSS animations with Laminar lifecycle

**Checkpoint**: Feature parity complete

---

### Phase 8: Cleanup

20. **Remove dead code** from `TileKingdomClient.scala`
    - Delete `renderTile()`, `renderInfluenceIndicator()`, etc.
    - Remove DOM element caches (`progressBarCache`)

21. **Consolidate state**
    - Move remaining `currentGame` usages to `TileKingdomState`
    - Consider making game tick logic reactive

---

## Risk Mitigation

### Feature Flags
Use a simple flag to switch between old and new rendering:

```scala
val UseLaminarGrid = false // Flip to true when ready

if UseLaminarGrid then
  laminarRender(container, TileGrid())
else
  renderTilesImperative()
```

### Rollback Plan
Keep the imperative code until migration is stable:
- Don't delete old code until Phase 8
- Can revert `UseLaminarGrid` flag instantly

### Testing Strategy
1. **Visual regression**: Screenshot comparison before/after
2. **Performance benchmarks**: Measure frame rate during pan/zoom
3. **Interaction testing**: Verify all click handlers work
4. **Edge cases**: Large grids (100+ tiles), rapid zoom changes

---

## Estimated Effort

| Phase | Effort | Description | Status |
|-------|--------|-------------|--------|
| Phase 1 | 2-3 hours | Foundation setup | ✅ Complete |
| Phase 2 | 2-3 hours | Simple tiles | ✅ Complete |
| Phase 3 | 3-4 hours | Production tiles | ✅ Complete |
| Phase 4 | 4-5 hours | Complex tiles (Bureau, TownHall) | ✅ Complete |
| Phase 5 | 3-4 hours | Build system | ✅ Complete |
| Phase 6 | 4-6 hours | Grid container (most complex) | ✅ Complete |
| Phase 7 | 2-3 hours | Animations | ✅ Complete (integrated) |
| Phase 8 | 1-2 hours | Cleanup | 🔲 Pending |

**Total: ~21-30 hours**

---

## Current Status

**Migration Status: Phase 6 Complete** ✅

All component files have been created and the `TileGrid` Laminar component is now mounted
via the `UseLaminarGrid` feature flag in `TileKingdomClient.scala`.

### Feature Flag

The migration uses a feature flag to enable the new Laminar grid:

```scala
object TileKingdomClient:
  /** Feature flag: Set to true to use the new Laminar-based tile grid rendering. */
  private val UseLaminarGrid: Boolean = true
```

### Files Created

| Component | File |
|-----------|------|
| TileGridState | `tilekingdom/TileGridState.scala` |
| TileUtils | `tilekingdom/TileUtils.scala` |
| ProgressBar | `tilekingdom/ProgressBar.scala` |
| InfluenceIndicator | `tilekingdom/InfluenceIndicator.scala` |
| TileRenderer | `tilekingdom/TileRenderer.scala` |
| TileGrid | `tilekingdom/TileGrid.scala` |
| FloatingEffects | `tilekingdom/FloatingEffects.scala` |
| EmptyTile | `tilekingdom/tiles/EmptyTile.scala` |
| UnlockableTile | `tilekingdom/tiles/UnlockableTile.scala` |
| BuildMenu | `tilekingdom/tiles/BuildMenu.scala` |
| WheatFieldTile | `tilekingdom/tiles/WheatFieldTile.scala` |
| FarmTile | `tilekingdom/tiles/FarmTile.scala` |
| WoodcutterTile | `tilekingdom/tiles/WoodcutterTile.scala` |
| TempleTile | `tilekingdom/tiles/TempleTile.scala` |
| QuarryTile | `tilekingdom/tiles/QuarryTile.scala` |
| BureauTile | `tilekingdom/tiles/BureauTile.scala` |
| TownHallTile | `tilekingdom/tiles/TownHallTile.scala` |
| AcademyTile | `tilekingdom/tiles/AcademyTile.scala` |
| TavernTile | `tilekingdom/tiles/TavernTile.scala` |

### Remaining Work

1. **Validate functionality** - Test all tile interactions work correctly
2. **Phase 8: Cleanup** - Once validated, remove the dead imperative code

---

## Success Criteria

1. **Functional parity**: All existing features work identically
2. **Performance**: No frame drops during normal gameplay
3. **Code reduction**: At least 30% reduction in tile-related code
4. **Type safety**: No string-based element lookups for tiles
5. **Testability**: Tile components can be unit tested in isolation

---

## Open Questions

1. **Progress bar animations**: Should progress update every tick or use CSS transitions?
2. **Large grid performance**: At what tile count does virtual scrolling become necessary?
3. **Touch gestures**: Should pinch-to-zoom be added during this migration?
4. **Accessibility**: Should ARIA attributes be added to tiles?

---

## Appendix: Code Examples

### Example: WheatFieldTile Component

```scala
object WheatFieldTile:
  def apply(
    coord: Coord,
    onLevelUp: () => Unit,
    onBulkLevelUp: Int => Unit,
    onDestroy: () => Unit
  ): HtmlElement =
    val tileSignal = TileKingdomState.tileSignal(coord)
    val progressSignal = TileGridState.progressSignal(coord)
    
    div(
      cls := "tile-kingdom-tile unlocked wheat-field",
      cls <-- tileSignal.map(_.map(t => s"data-level-${t.level}").getOrElse("")),
      
      // Position from grid state
      styleAttr <-- TileGridState.tileStyle(coord),
      
      // Content
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🌾"),
        div(cls := "tile-label", child.text <-- tileSignal.map(t => s"Lv${t.map(_.level).getOrElse(1)}")),
        // ... badges, upgrade row, etc.
      ),
      
      // Progress bar
      ProgressBar(progressSignal),
      
      // Event handlers
      onClick --> { _ => onLevelUp() },
      onContextMenu --> { e => e.preventDefault(); onDestroy() }
    )
```

### Example: Reactive Visible Tiles

```scala
def visibleTileCoords: Signal[Set[Coord]] =
  TileGridState.panOffset
    .combineWith(TileGridState.zoomLevel)
    .combineWith(viewportSize)
    .map { case ((panX, panY), zoom, (width, height)) =>
      val tileSize = 74 * zoom
      val minCol = ((-panX - tileSize * 2) / tileSize).floor.toInt
      val maxCol = ((-panX + width + tileSize * 2) / tileSize).ceil.toInt
      val minRow = ((-panY - tileSize * 2) / tileSize).floor.toInt
      val maxRow = ((-panY + height + tileSize * 2) / tileSize).ceil.toInt
      
      (for
        r <- minRow to maxRow
        c <- minCol to maxCol
      yield Coord(r, c)).toSet
    }
```


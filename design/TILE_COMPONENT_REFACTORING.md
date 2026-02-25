# Tile Component Refactoring Plan

## Overview

After migrating the TileKingdom game to Laminar, we now have 11 individual tile components. While the migration improved reactivity and maintainability, significant code duplication has emerged across these components. This document outlines a plan to reduce duplication while preserving component clarity.

---

## Current State: Duplication Analysis

### Tile Component Inventory

| Component | Lines | Has ProgressBar | Has LevelUp | Has Badges | Has BulkLevelUp |
|-----------|-------|-----------------|-------------|------------|-----------------|
| WheatFieldTile | 126 | ✅ | ✅ | ✅ (4 types) | ✅ |
| WoodcutterTile | 114 | ✅ | ✅ | ✅ (3 types) | ✅ |
| QuarryTile | 114 | ✅ | ✅ | ✅ (3 types) | ✅ |
| TempleTile | 103 | ✅ | ✅ | ✅ (2 types) | ✅ |
| FarmTile | 70 | ❌ | ✅ | ❌ | ✅ |
| BureauTile | 150 | ✅ (conditional) | ❌ | ✅ | ❌ |
| TownHallTile | 168 | ❌ | ❌ | ❌ | ❌ |
| AcademyTile | 71 | ❌ | ❌ | ✅ | ❌ |
| TavernTile | 44 | ❌ | ❌ | ✅ | ❌ |
| EmptyTile | 47 | ❌ | ❌ | ❌ | ❌ |
| UnlockableTile | 47 | ❌ | ❌ | ❌ | ❌ |

**Total: ~1,054 lines across 11 files**

### Duplicated Patterns

#### 1. **Outer Tile Wrapper** (all 11 components, ~10-15 lines each)
Every tile has the same structural wrapper:

```scala
div(
  idAttr := TileUtils.tileId(coord),
  cls := "tile-kingdom-tile unlocked <type>",
  cls <-- TileGridState.zoomTierClass,
  dataAttr("level") := level.toString,  // when applicable
  styleAttr <-- TileGridState.tileStyle(coord),
  // ... content ...
  onContextMenu --> { e => e.preventDefault(); actions.onDestroy() }
)
```

#### 2. **Production Tile Pattern** (WheatField, Woodcutter, Quarry, Temple = 4 components, ~60 lines each)
All production tiles share:
- Progress bar from `TileGridState.tileProgress`
- Level display (`Lv$level`)
- Production amount display
- Modifier badges section
- Upgrade row with `x10` bulk button
- Click → level up, right-click → destroy

#### 3. **Modifier Badge Pattern** (~8-15 lines per badge type)
```scala
child.maybe <-- someSignal.map: value =>
  Option.when(value > threshold):
    span(cls := "tile-badge badge-<type>", s"<emoji>+$percent%")
```

Repeated for: farm bonus, town hall multiplier, speed boost, upgrade discount, forest group, wisdom skill, etc.

#### 4. **Upgrade Row Pattern** (5 components, ~10 lines each)
```scala
div(
  cls := "tile-upgrade-row",
  span(cls := "tile-upgrade", s"⬆${TileUtils.formatNumber(cost)}<emoji>"),
  button(
    cls := "btn-x10",
    "x10",
    onClick --> { e =>
      e.stopPropagation()
      actions.onBulkLevelUp(TileUtils.levelsToNextTen(level))
    }
  )
)
```

#### 5. **Actions Case Classes** (11 components)
Each component defines its own `Actions` case class with similar patterns:
- `onLevelUp: () => Unit`
- `onBulkLevelUp: Int => Unit`
- `onDestroy: () => Unit`

#### 6. **Click/Drag Handlers** (~8 lines repeated across tiles)
```scala
onClick --> { _ =>
  if !TileGridState.wasDragging then actions.onLevelUp()
},
onContextMenu --> { e =>
  e.preventDefault()
  actions.onDestroy()
}
```

---

## Proposed Architecture

## Approach: Shared Helpers + Composition

Create small composable helpers without introducing a full abstraction layer.

#### New File: `TileComponents.scala`

```scala
package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}

/** Shared tile component building blocks */
object TileComponents:

  /** Common actions for upgradeable tiles */
  case class UpgradeActions(
    onLevelUp: () => Unit,
    onBulkLevelUp: Int => Unit,
    onDestroy: () => Unit
  )

  /** Common actions for non-upgradeable tiles */
  case class BasicActions(
    onDestroy: () => Unit
  )

  /** Base tile wrapper with common structure */
  def tileWrapper(
    coord: Coord,
    tileType: String,
    level: Option[Int] = None,
    extraCls: Signal[String] = Val("")
  )(content: Modifier[HtmlElement]*): HtmlElement =
    div(
      idAttr := TileUtils.tileId(coord),
      cls := s"tile-kingdom-tile unlocked $tileType",
      cls <-- TileGridState.zoomTierClass.combineWith(extraCls).map:
        case (zoom, extra) => s"$zoom $extra".trim,
      level.map(l => dataAttr("level") := l.toString),
      styleAttr <-- TileGridState.tileStyle(coord),
      content
    )

  /** Standard click handlers for upgradeable tiles */
  def upgradeClickHandlers(actions: UpgradeActions): Seq[Modifier[HtmlElement]] = Seq(
    onClick --> { _ =>
      if !TileGridState.wasDragging then actions.onLevelUp()
    },
    onContextMenu --> { e =>
      e.preventDefault()
      actions.onDestroy()
    }
  )

  /** Standard right-click destroy handler */
  def destroyHandler(onDestroy: () => Unit): Modifier[HtmlElement] =
    onContextMenu --> { e =>
      e.preventDefault()
      onDestroy()
    }

  /** Upgrade row with x10 button */
  def upgradeRow(
    cost: Int,
    costEmoji: String,
    level: Int,
    onBulkLevelUp: Int => Unit
  ): HtmlElement =
    div(
      cls := "tile-upgrade-row",
      span(cls := "tile-upgrade", s"⬆${TileUtils.formatNumber(cost)}$costEmoji"),
      button(
        cls := "btn-x10",
        "x10",
        onClick --> { e =>
          e.stopPropagation()
          onBulkLevelUp(TileUtils.levelsToNextTen(level))
        }
      )
    )

  /** Reactive upgrade row (for tiles with dynamic costs) */
  def upgradeRowSignal(
    costSignal: Signal[Int],
    costEmoji: String,
    level: Int,
    onBulkLevelUp: Int => Unit
  ): HtmlElement =
    div(
      cls := "tile-upgrade-row",
      span(
        cls := "tile-upgrade",
        child.text <-- costSignal.map(c => s"⬆${TileUtils.formatNumber(c)}$costEmoji")
      ),
      button(
        cls := "btn-x10",
        "x10",
        onClick --> { e =>
          e.stopPropagation()
          onBulkLevelUp(TileUtils.levelsToNextTen(level))
        }
      )
    )

  /** Badge for multiplier bonuses (town hall, etc.) - tooltip required */
  def multiplierBadge(
    multiplierSignal: Signal[Double],
    emoji: String,
    badgeClass: String,
    tooltip: String
  ): Modifier[HtmlElement] =
    child.maybe <-- multiplierSignal.map: mult =>
      Option.when(mult > 1.0):
        val text = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
        span(cls := s"tile-badge $badgeClass", title := tooltip, s"$emoji$text")

  /** Badge for percentage bonuses (farm boost, etc.) - tooltip required */
  def percentBadge(
    bonusSignal: Signal[Double],
    emoji: String,
    badgeClass: String,
    tooltip: String
  ): Modifier[HtmlElement] =
    child.maybe <-- bonusSignal.map: bonus =>
      Option.when(bonus > 1.0):
        val percent = ((bonus - 1) * 100).toInt
        span(cls := s"tile-badge $badgeClass", title := tooltip, s"$emoji+$percent%")

  /** Badge for static bonuses (skills, etc.) */
  def staticBadge(
    showSignal: Signal[Boolean],
    text: String,
    badgeClass: String,
    titleText: String
  ): Modifier[HtmlElement] =
    child.maybe <-- showSignal.map: show =>
      Option.when(show):
        span(cls := s"tile-badge $badgeClass", title := titleText, text)
```

#### Updated Production Tile Example: `WheatFieldTile.scala`

```scala
object WheatFieldTile:

  def apply(
    coord: Coord,
    tile: Tile,
    actions: TileComponents.UpgradeActions
  ): HtmlElement =
    import TileComponents.*
    
    val level = tile.level
    val gameSignal = TileKingdomState.gameSignal
    val progressSignal = TileGridState.tileProgress.signal.map(_.getOrElse(coord, 0.0))

    // Computed signals
    val harvestAmountSignal = gameSignal.map(TileKingdomLogic.productionPerHarvest(_, tile))
    val farmBonusSignal = gameSignal.map(TileKingdomLogic.farmBonusMultiplier(_, coord))
    val townHallMultiplierSignal = gameSignal.map(TileKingdomLogic.townHallWheatMultiplier(_, coord))
    val hasSpeedBoostSignal = gameSignal.map(_.hasSkill(Skill.Agriculture1B))
    val hasUpgradeDiscountSignal = gameSignal.map(_.hasSkill(Skill.Agriculture3A))
    val upgradeCostSignal = gameSignal.map(g => 
      TileKingdomLogic.effectiveUpgradeCost(g, tile).map(_.amount).getOrElse(0))

    tileWrapper(coord, "wheat-field", Some(level))(
      div(
        cls := "tile-content",
        div(cls := "tile-icon", "🌾"),
        div(cls := "tile-label", s"Lv$level"),
        div(
          cls := "tile-production",
          child.text <-- harvestAmountSignal.map(h => s"+${TileUtils.formatNumber(h)}")
        ),
        div(
          cls := "tile-modifiers",
          staticBadge(hasUpgradeDiscountSignal, "💰-90%", "badge-discount",
            "Agriculture skill: 90% cheaper upgrades"),
          staticBadge(hasSpeedBoostSignal, "⚡+25%", "badge-speed",
            "Agriculture skill: 25% faster production"),
          percentBadge(farmBonusSignal, "🏠", "badge-farm",
            "Farm bonus: nearby farms boost wheat production"),
          multiplierBadge(townHallMultiplierSignal, "🏛️", "badge-townhall",
            "Town Hall bonus: politician multiplier")
        ),
        upgradeRowSignal(upgradeCostSignal, "🌾", level, actions.onBulkLevelUp)
      ),
      ProgressBar(progressSignal),
      upgradeClickHandlers(actions)*
    )
```

### Estimated Reduction

| Area | Current Lines | After Refactoring | Savings |
|------|---------------|-------------------|---------|
| WheatFieldTile | 126 | ~55 | -71 |
| WoodcutterTile | 114 | ~50 | -64 |
| QuarryTile | 114 | ~50 | -64 |
| TempleTile | 103 | ~45 | -58 |
| FarmTile | 70 | ~35 | -35 |
| TileComponents (new) | 0 | ~120 | +120 |
| **Total Production** | **527** | **~355** | **-172 (-33%)** |

---

## Implementation Plan

### Phase 1: Create Shared Helpers (Low Risk)

1. Create `TileComponents.scala` with:
   - `tileWrapper` - common div structure
   - `upgradeRow` / `upgradeRowSignal`
   - `upgradeClickHandlers` / `destroyHandler`
   - Badge helpers: `multiplierBadge`, `percentBadge`, `staticBadge`
   - `UpgradeActions` and `BasicActions` case classes

2. Update `TileRenderer.scala` to use shared action types

### Phase 2: Refactor Production Tiles

Migrate in this order (simplest to most complex):
1. TempleTile (2 badge types)
2. FarmTile (no badges, no progress)
3. QuarryTile (3 badge types)
4. WoodcutterTile (3 badge types)
5. WheatFieldTile (4 badge types, dynamic cost)

**While refactoring, fix badge inconsistencies:**
- Add missing tooltips to all badges
- Change wisdom badge emoji from 🌲 to 📚
- Change upgrade discount CSS class from `badge-speed` to `badge-discount`

### Phase 3: Refactor Remaining Tiles

1. TavernTile (simplest, static badge only)
2. AcademyTile (toggle logic)
3. BureauTile (mode switching, conditional progress)
4. TownHallTile (drag-drop, lifespan logic)
5. EmptyTile / UnlockableTile (minimal changes needed)

### Phase 4: Cleanup and Polish

1. Remove unused code from individual tiles
2. Consolidate CSS classes if possible
3. Update tests
4. Document shared components

---

## Success Criteria

- [ ] Reduce total tile component code by ~30% (~300 lines)
- [ ] All existing functionality preserved
- [ ] No performance regression
- [ ] Easier to add new tile types (demonstrate with a hypothetical new tile)
- [ ] Improved consistency across tiles

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Over-abstraction making code harder to follow | Medium | Keep helpers simple; don't force every tile into same pattern |
| Signal composition performance | Low | Use `.distinct` on derived signals; profile if needed |
| Breaking tile-specific behaviors | High | Comprehensive manual testing of each tile type |
| CSS class conflicts | Low | Keep existing class names; only change structure |

---

## Design Decisions

1. **Where to put `TileComponents`?** → Separate file in `tiles/` package. Simple and discoverable.

2. **Standardize all Actions?** → No. Keep tile-specific Actions where needed (TownHall, Bureau). Only share `UpgradeActions` for the 5 upgradeable tiles.

3. **Badge system flexibility?** → Current helpers are enough. Don't over-engineer.

4. **ProductionTile wrapper?** → No. The tiles are similar but not identical enough. Helpers > inheritance.

**Philosophy: Factor out the most blatant repetition. Don't force tiles into a rigid framework.**

---

## Appendix: Full Badge Inventory

| Badge Type | Used By | Signal Source |
|------------|---------|---------------|
| Farm boost (%) | WheatField, Woodcutter, Quarry | `farmBonusMultiplier`, `agriculture2BFarmBonusMultiplier`, `agriculture3BFarmBonusMultiplier` |
| Town hall (×) | WheatField, Woodcutter, Temple, Quarry | `townHallWheatMultiplier`, etc. |
| Speed boost | WheatField | `hasSkill(Agriculture1B)` |
| Upgrade discount | WheatField | `hasSkill(Agriculture3A)` |
| Forest group | Woodcutter | `forestGroupBonusMultiplier` |
| Wisdom | Temple, Quarry | `templeWisdom2Multiplier`, `quarryWisdom1Multiplier` |
| Bureau mode | Bureau | `getBureauMode` |
| Academy mode | Academy | `AcademyMode` |
| Tavern lifespan | Tavern | Static |

---

## Appendix: Badge Inconsistencies to Fix

### Current Inconsistencies

| Issue | Where | Problem |
|-------|-------|---------|
| Missing tooltip | WheatField farm badge | Has no `title`, but Woodcutter/Quarry farm badges do |
| Missing tooltip | WheatField town hall badge | None of the town hall badges have tooltips |
| Missing tooltip | Woodcutter forest group badge | No explanation of what triggers the bonus |
| Wrong emoji | Temple/Quarry wisdom badges | Uses 🌲 (tree) but should be 🧠 or 📚 for wisdom |
| Inconsistent CSS class | WheatField upgrade discount | Uses `badge-speed` but it's not a speed bonus |

### Standardized Badge Approach

All badges should follow this pattern:
- **Always have a tooltip** explaining the bonus source
- **Use consistent emoji** for the same bonus type across tiles
- **Use semantic CSS classes** that match the bonus type

#### Proposed Badge Standards

| Badge Type | Emoji | CSS Class | Tooltip Template |
|------------|-------|-----------|------------------|
| Farm boost | 🏠 | `badge-farm` | "Farm bonus: +X% from nearby farms" |
| Town hall | 🏛️ | `badge-townhall` | "Town Hall bonus: ×X multiplier" |
| Forest group | 🌲 | `badge-forest` | "Forest synergy: +X% from adjacent forests" |
| Wisdom skill | 📚 | `badge-wisdom` | "Wisdom skill: ×X multiplier from nearby forests" |
| Speed boost | ⚡ | `badge-speed` | "Agriculture skill: 25% faster production" |
| Upgrade discount | 💰 | `badge-discount` | "Agriculture skill: 90% cheaper upgrades" |

#### Updated Helper Signatures

```scala
/** Badge for percentage bonuses - always requires tooltip */
def percentBadge(
  bonusSignal: Signal[Double],
  emoji: String,
  badgeClass: String,
  tooltip: String  // Now required, not optional
): Modifier[HtmlElement]

/** Badge for multiplier bonuses - always requires tooltip */
def multiplierBadge(
  multiplierSignal: Signal[Double],
  emoji: String,
  badgeClass: String,
  tooltip: String  // Now required
): Modifier[HtmlElement]
```

This ensures badges are always self-documenting for players.


# Velor Idle - Cleanup Plan

## Overview

This document identifies issues in the Velor Idle codebase and proposes a step-by-step cleanup plan to create a solid foundation before adding more features.

---

## Identified Issues

### 1. **Duplicated Number Formatting Functions**

**Problem:** Three nearly identical formatting functions exist across the UI components:

- `SkillTrainingView.formatNumber(n: Long)` 
- `Header.formatGold(gold: Long)`
- `InventoryPanel.formatCount(count: Long)`

All three do essentially the same thing: format large numbers with `k` and `M` suffixes.

**Impact:** Code duplication, inconsistent behavior if one is changed but not others.

**Solution:** Create a shared `VelorUtils` object with a single `formatNumber` function.

---

### 2. **Massive Pattern-Matching Functions in VelorIdle.scala**

**Problem:** The `Item` object has three large `match` expressions with 35+ cases each:

- `Item.icon(item)` - 35 cases
- `Item.displayName(item)` - 35 cases  
- `Item.sellValue(item)` - 35 cases

Similarly, `Skill.icon` and `Skill.displayName` repeat all 10 skills.

**Impact:** 
- Difficult to add new items (must update 3 places)
- Easy to miss a case when adding items
- Verbose and hard to scan

**Solution:** Use a single data structure (case class or Map) that holds all item metadata together.

---

### 3. **VelorIdleLogic Uses Mutable Variables**

**Problem:** In `completeGatheringAction` and `completeProcessingAction`:

```scala
var events = Vector.empty[GameEvent]
var updatedGame = game
// ... mutations throughout
```

This is contrary to the functional style preferred in the project.

**Impact:** Harder to reason about, potential for subtle bugs, inconsistent with codebase style.

**Solution:** Refactor to use fold/foldLeft or chained operations that accumulate state immutably.

---

### 4. **Inconsistent Action Type Handling**

**Problem:** In `VelorIdleClient.handleStartAction`:

```scala
val result = currentGame.currentSkill match
  case Some(skill) if Skill.isGathering(skill) =>
    VelorIdleLogic.startGathering(currentGame, actionId)
  case Some(skill) if Skill.isProcessing(skill) =>
    VelorIdleLogic.startProcessing(currentGame, actionId)
  case _ =>
    Left("No skill selected")
```

The client must know about skill types to dispatch correctly. This logic is duplicated conceptually with `Skill.isGathering` / `Skill.isProcessing`.

**Impact:** Adding a new skill type requires changes in multiple places.

**Solution:** Create a single `VelorIdleLogic.startAction(game, actionId)` that handles dispatch internally based on the current skill.

---

### 5. **Header Has Duplicated Active Skill Blocks**

**Problem:** In `Header.activeSkillIndicator`:

```scala
case ActiveAction.Gathering(skill, _) =>
  div(
    cls := "velor-active-skill-content active clickable",
    onClick --> { _ => 
      VelorIdleState.requestSelectSkill(skill)
      VelorIdleState.goToSkillTraining()
    },
    span(cls := "velor-active-skill-icon spinning", Skill.icon(skill)),
    span(cls := "velor-active-skill-status", "Active")
  )
case ActiveAction.Processing(skill, _) =>
  // Exact same code block repeated
```

**Impact:** Duplication, if one is changed the other might be missed.

**Solution:** Extract the active skill div into a helper function that takes the skill.

---

### 6. **No Tests for Game Logic**

**Problem:** There are no tests for `VelorIdleLogic`. Other games in the project (Trader, TileKingdom, TugOfWar) have test suites.

**Impact:** 
- Risky to refactor without tests
- Bugs may go unnoticed
- Perk calculations (efficiency, yield, double chance, recycle) are untested

**Solution:** Add comprehensive tests before any refactoring.

---

### 7. **Perk Calculation Functions Are Repetitive**

**Problem:** Four perk calculation functions follow the same pattern:

```scala
def calculateEfficiencyBonus(level: Int): Double =
  val tier1 = if level >= 10 then 0.05 else 0.0
  val tier2 = if level >= 40 then 0.05 else 0.0
  val tier3 = if level >= 70 then 0.05 else 0.0
  tier1 + tier2 + tier3
```

Same structure repeated for `calculateYieldBonus`, `calculateDoubleChance`, `calculateRecycleChance`.

**Impact:** Hard to add new perks or adjust tier thresholds consistently.

**Solution:** Create a data-driven perk system with configurable tiers.

---

### 8. **VelorIdleState Uses Callback Registration Pattern**

**Problem:** The state module uses a callback pattern:

```scala
private var selectSkillCallback: Option[Skill => Unit] = None

def registerSelectSkillCallback(callback: Skill => Unit): Unit =
  selectSkillCallback = Some(callback)

def requestSelectSkill(skill: Skill): Unit =
  selectSkillCallback.foreach(_(skill))
```

This is a workaround for the split between `VelorIdleClient` (owns game state) and `VelorIdleState` (reactive signals).

**Impact:** Confusing indirection, easy to forget to register callback.

**Solution:** Consider a cleaner architecture where either:
- State fully owns the game and logic
- Or callbacks are unnecessary by using events/bus

---

### 9. **ActionSelector and ProcessingSelector Are Nearly Identical**

**Problem:** Both components follow the same structure:
- Get actions for skill
- Map to item elements
- Each item has locked/active signals
- Same click handling pattern

Only differences are:
- Action type (Gathering vs Processing)
- Processing checks ingredients

**Impact:** Adding features (tooltips, sorting) requires changes in two places.

**Solution:** Extract a generic `ActionList` component parameterized by action type.

---

### 10. **SkillTrainingView.renderActionProgress Has Mixed Concerns**

**Problem:** The function renders both gathering and processing actions identically, but they come from different pattern matches:

```scala
case ActiveAction.Gathering(skill, action) if skill == viewingSkill =>
  renderActionProgress(action.icon, action.name, ...)
case ActiveAction.Processing(skill, action) if skill == viewingSkill =>
  renderActionProgress(action.icon, action.name, ...)  // Same call
```

**Impact:** Minor, but shows that `GatheringAction` and `ProcessingAction` share enough interface that they could share a trait.

**Solution:** Create a common `Action` trait or use a union type.

---

## Cleanup Plan

### Phase 1: Add Tests (Critical Before Refactoring) ✅ COMPLETED

1. **Created `VelorIdleLogicSpec.scala`** with 47 tests covering:
   - XP calculations and level-up detection
   - Gathering action completion
   - Processing action completion (including burn chance)
   - Perk calculations at key thresholds
   - Inventory operations (add, remove, overflow)
   - Action start/stop logic
   - Unified `startAction` dispatch

---

### Phase 2: Consolidate Utilities ✅ COMPLETED

1. **Created `VelorUtils.scala`** in the veloride package with `formatNumber(Long/Double/Int)`

2. **Removed duplicate formatters** from:
   - `SkillTrainingView` (now delegates to VelorUtils)
   - `Header` (now delegates to VelorUtils)  
   - `InventoryPanel` (now delegates to VelorUtils)

---

### Phase 3: Unify Item/Skill Metadata ✅ COMPLETED

1. **Created `ItemData` case class** with icon, displayName, and sellValue

2. **Created single metadata Map** for all items - adding a new item now requires just one line

3. **Replaced three 35-case match expressions** with simple Map lookups

4. **Applied same pattern to Skill** with `SkillData` case class

---

### Phase 4: Simplify Action Handling ✅ COMPLETED

1. **Created unified `startAction` method** in `VelorIdleLogic`:
   ```scala
   def startAction(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
     game.currentSkill match
       case None => Left("No skill selected")
       case Some(skill) if Skill.isGathering(skill) => startGathering(game, actionId)
       case Some(skill) if Skill.isProcessing(skill) => startProcessing(game, actionId)
       case Some(skill) => Left(s"${Skill.displayName(skill)} actions not yet implemented")
   ```

2. **Simplified VelorIdleClient.handleStartAction** to use the unified method.

---

### Phase 5: Clean Up Header Duplication ✅ COMPLETED

1. **Extracted `activeSkillDiv` helper** in Header to eliminate duplicated Gathering/Processing blocks.

---

### Phase 6: Refactor Mutable Logic to Functional Style

1. **Refactor `completeGatheringAction`** to use pattern matching or fold:
   ```scala
   private def completeGatheringAction(...): (VelorIdleGame, Vector[GameEvent]) =
     val xpResult = grantXp(game, skill, action.xpGain)
     val itemResult = grantItems(xpResult.game, action.output, ...)
     val rareResult = checkRareDrop(itemResult.game, action.rareOutput, random)
     (rareResult.game, xpResult.events ++ itemResult.events ++ rareResult.events)
   ```

2. **Same approach for `completeProcessingAction`.**

---

### Phase 7: Data-Driven Perks (Optional Enhancement)

1. **Create perk configuration:**
   ```scala
   case class PerkTier(levelRequired: Int, bonus: Double)
   
   val efficiencyPerks = Vector(
     PerkTier(10, 0.05),
     PerkTier(40, 0.05),
     PerkTier(70, 0.05)
   )
   ```

2. **Generic calculation:**
   ```scala
   def calculatePerkBonus(level: Int, tiers: Vector[PerkTier]): Double =
     tiers.filter(_.levelRequired <= level).map(_.bonus).sum
   ```

---

### Phase 8: Consider UI Component Consolidation (Future)

1. **Evaluate merging ActionSelector and ProcessingSelector** into a generic component.

2. **Consider a shared `Action` trait** for `GatheringAction` and `ProcessingAction`.

---

## Priority Order

| Priority | Task | Effort | Status |
|----------|------|--------|--------|
| **P0** | Add tests | Medium | ✅ Done |
| **P1** | Consolidate formatters | Low | ✅ Done |
| **P1** | Fix Header duplication | Low | ✅ Done |
| **P2** | Simplify action handling | Low | ✅ Done |
| **P2** | Unify Item metadata | Medium | ✅ Done |
| **P3** | Refactor mutable logic | Medium | Pending |
| **P3** | Data-driven perks | Medium | Pending |
| **P4** | UI component consolidation | High | Pending |

---

## Notes

- All refactoring should maintain exact same behavior
- Run tests after each change
- Consider adding property-based tests for calculations
- The callback pattern in VelorIdleState is awkward but functional—fix only if it causes bugs


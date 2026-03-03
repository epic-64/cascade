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

### Phase 1: Add Tests (Critical Before Refactoring)

1. **Create `VelorIdleLogicSpec.scala`** with tests for:
   - XP calculations and level-up detection
   - Gathering action completion
   - Processing action completion (including burn chance)
   - Perk calculations at key thresholds
   - Inventory operations (add, remove, overflow)
   - Action start/stop logic

2. **Test edge cases:**
   - Level 99 cap behavior
   - Empty inventory handling
   - Missing ingredients for processing

---

### Phase 2: Consolidate Utilities

1. **Create `VelorUtils.scala`** in the veloride package:
   ```scala
   object VelorUtils:
     def formatNumber(n: Long): String = ...
   ```

2. **Remove duplicate formatters** from:
   - `SkillTrainingView`
   - `Header`
   - `InventoryPanel`

3. **Import and use `VelorUtils.formatNumber`** everywhere.

---

### Phase 3: Unify Item/Skill Metadata

1. **Create `ItemData` case class:**
   ```scala
   case class ItemData(
     icon: String,
     displayName: String,
     sellValue: Int
   )
   ```

2. **Create a single Map:**
   ```scala
   val itemMetadata: Map[Item, ItemData] = Map(
     Item.NormalLogs -> ItemData("🪵", "Normal Logs", 2),
     // ...
   )
   ```

3. **Replace the three match expressions** with Map lookups.

4. **Apply same pattern to Skill** with `SkillData`.

---

### Phase 4: Simplify Action Handling

1. **Create unified `startAction` method:**
   ```scala
   def startAction(game: VelorIdleGame, actionId: String): Either[String, VelorIdleGame] =
     game.currentSkill match
       case None => Left("No skill selected")
       case Some(skill) =>
         if Skill.isGathering(skill) then startGathering(game, actionId)
         else if Skill.isProcessing(skill) then startProcessing(game, actionId)
         else Left("Skill type not implemented")
   ```

2. **Simplify VelorIdleClient.handleStartAction:**
   ```scala
   private def handleStartAction(actionId: String): Unit =
     VelorIdleLogic.startAction(currentGame, actionId) match
       case Right(newGame) => ...
       case Left(error) => ...
   ```

---

### Phase 5: Clean Up Header Duplication

1. **Extract helper in Header:**
   ```scala
   private def activeSkillDiv(skill: Skill): HtmlElement = 
     div(
       cls := "velor-active-skill-content active clickable",
       onClick --> { _ => 
         VelorIdleState.requestSelectSkill(skill)
         VelorIdleState.goToSkillTraining()
       },
       span(cls := "velor-active-skill-icon spinning", Skill.icon(skill)),
       span(cls := "velor-active-skill-status", "Active")
     )
   ```

2. **Use in pattern match:**
   ```scala
   case ActiveAction.Gathering(skill, _) => activeSkillDiv(skill)
   case ActiveAction.Processing(skill, _) => activeSkillDiv(skill)
   ```

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

| Priority | Task | Effort | Risk if Skipped |
|----------|------|--------|-----------------|
| **P0** | Add tests | Medium | Cannot safely refactor |
| **P1** | Consolidate formatters | Low | Minor inconsistency |
| **P1** | Fix Header duplication | Low | Minor maintenance burden |
| **P2** | Unify Item metadata | Medium | Adding items is error-prone |
| **P2** | Simplify action handling | Low | Minor complexity |
| **P3** | Refactor mutable logic | Medium | Style inconsistency |
| **P3** | Data-driven perks | Medium | Adding perks is repetitive |
| **P4** | UI component consolidation | High | Code duplication |

---

## Notes

- All refactoring should maintain exact same behavior
- Run tests after each change
- Consider adding property-based tests for calculations
- The callback pattern in VelorIdleState is awkward but functional—fix only if it causes bugs


## Done 2026-02-23
- [x] when placing a politician into a townhall, there is a sub second time window for dropping it. The drop area keeps disappearing and reappearing,
  which is very frustrating. This is especially bad when trying to swap a politician, and may be related to the timer updates and too aggressive re-rendering.
- [x] the politician roster should be a bit wider to allow longer descriptions to remain as one line
- [x] the politican roster should be a bit taller to allow for more politicians without needing to scroll
- [x] the bureau should send a projectile to the tile it upgrades, for better visual feedback
- [x] the bureau should signify, the upgrade process costs 100 wood (and 100 faith, in turbo mode)
- [x] the quarry should cost 500 wood to build, and wood to upgrade
- [x] Any text in the game should not be selectable. Since there is a lot of clicking and dragging, having text be selectable leads to a lot of accidental text selection, which is frustrating.
- [x] The text is too small in a lot of places
- [x] create variables for standardized text sizes, and use them across the board to ensure consistency
- [x] Quarries are too dark
- [x] When zooming out, the text should become smaller. When zooming out sufficiently, the test should disappear entirely, and just be the tile colors.
- [x] When dragging the mouse to move around the map, when releasing the mouse button on a tile,
    it upgrades the tile. Releasing the mouse after dragging should not create a click event.
- [x] Rename Wood tile to Forest
- [x] allow dragging politicians from one town hall to another, to swap them

## Todos
- [ ] the 10x upgrade button on tiles should only upgrade to the next level divisible by 10. E.g. clicking 10x upgrade on a level 16 quarry should upgrade it to level 20 (or less if you don't have enough resources), not level 26.
- [ ] the town hall should cost 1000 stone to build
- [ ] if the player does not have enough of a resource to build a building, display the resource as red in the build menu
- [ ] the temple should cost 10k wood to build
- [ ] large numbers should be displayed in a more compact format (e.g. 1.2k, 3.4M, etc.)
- [ ] add a second tier prestige system, called "Sail". The button unlocks once you reach 25 tiles.
  - When sailing, all your resources including gold reset, and your tile count resets as well.
  - For every tile destroyed in this way, you collect a Legacy Point.
  - 25 Legacy points can be converted into one Skill Point
- [ ] add a skill tree that unlocks after the first Sail. It has multiple linear skill lines, that
  raise in cost (1, 2, 3, 4, 5, etc) and have to be unlocked sequentially (within a branch).
  - starter skills:
    - Agriculture: 
      - 1P: fields start at level 10
      - 2P: farms start at level 10
    - Management:
      - 1P: Your town halls are 10x cheaper to build
    - Wisdom:
      - 1P: Your quarries produce 25% more stone for each neighboring forest.
      - 2P: Each forest grants 50% increased faith production to neighboring temples.
    - Education:
      - 1P: Your academies are 10x cheaper
      - 2P: Your academies have both modes active at the same time

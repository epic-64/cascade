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
- [x] not all texts in the tiles are invisible on low zoom levels. Also, the text should disappear sooner (decrease zoom level threshold for text disappearance), since it becomes unreadable very quickly as you zoom out.
- [x] the 10x upgrade button on tiles should only upgrade to the next level divisible by 10. E.g. clicking 10x upgrade on a level 16 quarry should upgrade it to level 20 (or less if you don't have enough resources), not level 26.
- [x] the temple should cost 10k wood to build
- [x] if the player does not have enough of a resource to build a building, display the resource as red in the build menu
- [x] large numbers should be displayed in a more compact format (e.g. 1.2k, 3.4M, etc.)
- [x] on abdication, destroy all politicians
- [x] Politicians should only start being produced when there is at least one town hall. Before that, write "Build Town Hall" into the roster list.
- [x] bureau faith cost (when turbo enabled) should cost current level of target tile x 10.
- [x] The faith cost and wood cost floating texts (on upgrade) are overlapping.
- [x] add a second tier prestige system, called "Sail". The button unlocks once you reach 25 tiles.
  - When sailing, all your resources including gold reset, and your tile count resets as well.
  - For every tile destroyed in this way, you collect a Legacy Point.
  - 25 Legacy points can be converted into one Skill Point
- [x] add a skill tree that unlocks after the first Sail. It has multiple linear skill lines, that
  raise in cost (1, 2, 3, 4, 5, etc) and have to be unlocked sequentially (within a branch).
  - starter skills:
    - Agriculture:
      - 1P: fields start at level 10
      - 2P: farms start at level 10
    - Management:
      - 1P: Your politician roster can hold 2 additional politicians
      - 2P: Your town halls are 10x cheaper to build
    - Wisdom:
      - 1P: Your quarries produce 25% more stone for each neighboring forest.
      - 2P: Each forest grants 50% increased faith production to neighboring temples.
    - Education:
      - 1P: Your academies are 10x cheaper
      - 2P: Your academies have both modes active at the same time
- add more dev buttons: add Stone, add Faith, add Skill Point
- when building a town hall, and the roster is empty, automatically generate a politician immediately
- add some player choice in the skill tree. Each tree is a dual track. On each level, you can choose from one of two options
  - Agriculture:
    - 1P: fields start at level 10
    - OR: 1P: fields work 25% faster.
- add Agriculture2B: farms bonus applies to forests at half strength
- add an off-switch to the Bureau

## Done 2026-02-25
- game writes to localStorage every tick. This sounds a bit excessive, can we do something about it?
- use the nice badge-like styling for all tile modifiers (see the production speed multiplier for fields)
- simplify unlock requirements for tile types. After field, farm and forest, everything else should be unlocked. The build costs are enough to guide the player.
- allow switching between skills of the same tier for free
- Allow refunding skills. It costs 1000 gold per skill point and refunds skill points. Skills can only be refunded if they are not a prerequisite for another purchased skill. Skill switching/refunding only allowed on a fresh abdication (no buildings placed).
- add Logistics skill branch (dual-track): reduce bureau wood cost by 90% OR reduce bureau turbo faith cost by 90%
- add Agriculture tier 3 (dual-track): wheat field upgrade costs reduced by 90% OR farm bonus applies to quarries at 50%

## Done 2026-02-27
- Right clicking a tile converts it to an empty tile first. Only an empty tile is erased on right click.
- When releasing the mouse after dragging, the click is mostly suppressed, as intended. But when landing on an empty tile, the click event still goes through and upgrades the tile. This is especially frustrating when trying to drag a politician from one town hall to another, and accidentally upgrading a tile in the process. We should make sure that releasing the mouse after dragging does not trigger a click event at all, even on empty tiles.
- Update the Agriculture Tree:
  T1: Fields start at level 10 OR Farms affect neighboring forests at 50% effectiveness
  T2: Fields work 50% faster OR Farms affect neighboring quarries at 50% effectiveness
  T3: Fields cost 99% less to upgrade OR Farms affect neighboring temples at 50% effectiveness
- add a management skill that adds "direction" to the bureau. By default, it affects tiles in a 2 tile radius.
  With directions enabled, it can affect tiles in a 5x3 rectangle (3 wide, 5 long) to the top, bottom, left or right of the bureau.
  There is one button for each direction (including centered on bureau)
- elements in skill window are pulsing multiple times per second, it looks like the re-rendering is too coarse.
- Add a skill that allows town halls to hold 2 politicians at the same time.
- The cost reduction badge on the wheat field has white font. It is badly visible on the light background.
- improve the visibility of influence areas. On hover, draw lines to all tiles affected by the tile that is hovered.
  also add a toggle to always show the influence lines.
- by default, each academy should also grant 1 extra slot in the politician roster.

## Done 2026-02-28
- Currently, the game nukes the save completely if it fails to deserialize. Please change
  the save structure such that it keeps track of total skill points earned. Then, restore at least this number on failed deserialization.
  With this change we will still wipe saves on the next update, but at least afterwards it will be more stable.
- when fully zoomed in, the text on the tiles is a bit blurry. Please make sure the zoom level and font size are chosen such that the text is always crisp and clear.
- currently we visualize influence areas, as well as influence lines. The influence areas are sometimes shown on top of tiles which makes them harder to read.
  Normally, the influence area should always be behind tiles. However on hover, it should be shown on top of tiles.
- overhaul the management tree:
  - Management1: Bureau costs 0 wood to build and upgrades consume 0 wood. OR: Bureau can be directed to affect a 5x3 area in one of four directions (up, down, left, right) instead of a 2 tile radius around it.
  - Management2: Town halls cost 90% less stone to build. OR: Town halls can be directed.
  - Management3: Town halls can hold 2 politicians at the same time. OR: Normal politicians have 2 effects, rare politicians have 3 effects.
- there are currently influence indicator lines (lines between tiles). Please make them solid (not dotted), and brighter, but thinner.
- cache forest adjacency group bonus to fix performance sink
- it seems resources are now generated per second rather than waiting for the tile progress to complete

## Done 2026-03-03
- The Zen button changes its content to "TEXT" when active and also changes its emoji. It do not want that.
- Please create a manifest.json (PWA) specifically for the Tile Kingdom page. Make sure it is locked into portrait mode.

## Todos
- on mobile, the skill alternatives are displayed one per row, but I want them to be displayed next to each other.
- when re-opening the game after a while, we compute the offline progression and show it in a modal.
  but, when re-activating the game (re-focusing the tab), we do something slightly different. I want that the re-focusing uses exactly the same process of calculating progress and then displaying the modal.
- the offline progress modal should show how many tiles (of each type) have been upgraded
- Please change all skill unlock costs to 1. We already make it harder to gain skill points inherently.
- In Zen mode, I want to hide all contents from the tiles, including the progress bars and the emojis. Also, I want to hide most of the UI, except the Zen mode button itself.
- 
package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.tiles.*

/** Tile renderer component.
  *
  * Dispatches to the appropriate tile component based on tile type.
  */
object TileRenderer:

  /** All possible tile actions */
  case class Actions(
    // Build actions
    onBuildWheatField: Coord => Unit,
    onBuildFarm: Coord => Unit,
    onBuildWoodcutter: Coord => Unit,
    onBuildQuarry: Coord => Unit,
    onBuildBureau: Coord => Unit,
    onBuildTemple: Coord => Unit,
    onBuildTownHall: Coord => Unit,
    onBuildAcademy: Coord => Unit,
    onBuildTavern: Coord => Unit,
    // Upgrade/level actions
    onLevelUp: Coord => Unit,
    onBulkLevelUp: (Coord, Int) => Unit,
    // Destroy actions
    onDestroy: Coord => Unit,
    onDestroyTile: Coord => Unit, // Destroys the tile entirely (right-click)
    // Bureau mode
    onSetBureauMode: (Coord, BureauMode) => Unit,
    onSetBureauDirection: (Coord, BureauDirection) => Unit,
    // Academy mode
    onToggleAcademyMode: Coord => Unit,
    // Town hall actions
    onAssignPolitician: (Coord, String) => Unit,
    onRemovePolitician: Coord => Unit,
    onSwapPoliticians: (Coord, Coord) => Unit,
    // Unlock
    onUnlockTile: Coord => Unit
  )

  /** Render a tile at the given coordinate */
  def apply(coord: Coord, tile: Tile, actions: Actions): HtmlElement =
    tile.tileType match
      case TileType.Empty =>
        EmptyTile(
          coord,
          BuildMenu.Actions(
            onBuildWheatField = () => actions.onBuildWheatField(coord),
            onBuildFarm = () => actions.onBuildFarm(coord),
            onBuildWoodcutter = () => actions.onBuildWoodcutter(coord),
            onBuildQuarry = () => actions.onBuildQuarry(coord),
            onBuildBureau = () => actions.onBuildBureau(coord),
            onBuildTemple = () => actions.onBuildTemple(coord),
            onBuildTownHall = () => actions.onBuildTownHall(coord),
            onBuildAcademy = () => actions.onBuildAcademy(coord),
            onBuildTavern = () => actions.onBuildTavern(coord),
            onCancel = () => TileGridState.clearSelection()
          ),
          onDestroyTile = () => actions.onDestroyTile(coord)
        )

      case TileType.WheatField(_) =>
        WheatFieldTile(
          coord,
          tile,
          TileComponents.UpgradeActions(
            onLevelUp = () => actions.onLevelUp(coord),
            onBulkLevelUp = count => actions.onBulkLevelUp(coord, count),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Farm(level) =>
        FarmTile(
          coord,
          level,
          TileComponents.UpgradeActions(
            onLevelUp = () => actions.onLevelUp(coord),
            onBulkLevelUp = count => actions.onBulkLevelUp(coord, count),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Woodcutter(_) =>
        WoodcutterTile(
          coord,
          tile,
          TileComponents.UpgradeActions(
            onLevelUp = () => actions.onLevelUp(coord),
            onBulkLevelUp = count => actions.onBulkLevelUp(coord, count),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Temple(_) =>
        TempleTile(
          coord,
          tile,
          TileComponents.UpgradeActions(
            onLevelUp = () => actions.onLevelUp(coord),
            onBulkLevelUp = count => actions.onBulkLevelUp(coord, count),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Quarry(_) =>
        QuarryTile(
          coord,
          tile,
          TileComponents.UpgradeActions(
            onLevelUp = () => actions.onLevelUp(coord),
            onBulkLevelUp = count => actions.onBulkLevelUp(coord, count),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Bureau(level) =>
        BureauTile(
          coord,
          level,
          BureauTile.Actions(
            onSetMode = mode => actions.onSetBureauMode(coord, mode),
            onSetDirection = dir => actions.onSetBureauDirection(coord, dir),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.TownHall(politicians) =>
        TownHallTile(
          coord,
          politicians,
          TownHallTile.Actions(
            onAssignPolitician = id => actions.onAssignPolitician(coord, id),
            onRemovePolitician = () => actions.onRemovePolitician(coord),
            onSwapPoliticians = fromCoord => actions.onSwapPoliticians(fromCoord, coord),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Academy(mode) =>
        AcademyTile(
          coord,
          mode,
          AcademyTile.Actions(
            onToggleMode = () => actions.onToggleAcademyMode(coord),
            onDestroy = () => actions.onDestroy(coord)
          )
        )

      case TileType.Tavern =>
        TavernTile(
          coord,
          TileComponents.UpgradeActions(
            onLevelUp = () => (), // Tavern has no level up
            onBulkLevelUp = _ => (), // Tavern has no level up
            onDestroy = () => actions.onDestroy(coord)
          )
        )

  /** Render an unlockable tile */
  def renderUnlockable(coord: Coord, actions: Actions): HtmlElement =
    UnlockableTile(
      coord,
      UnlockableTile.Actions(
        onUnlock = () => actions.onUnlockTile(coord)
      )
    )


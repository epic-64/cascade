package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, BuildMenu}
import TileComponents.*

/** Empty tile component.
  *
  * Shows a build hammer icon. When clicked, shows the build menu.
  * Right-click destroys the tile entirely.
  */
object EmptyTile:

  def apply(
    coord: Coord,
    buildActions: BuildMenu.Actions,
    onDestroyTile: () => Unit
  ): HtmlElement =
    val isSelectingSignal = TileGridState.selectingTileCoord.signal.map(_.contains(coord))
    val selectingCls = isSelectingSignal.map(sel => if sel then "selecting" else "")

    tileWrapper(coord, "empty", extraCls = selectingCls)(
      // Build icon container (shown when not selecting)
      div(
        cls := "tile-build-icon-container",
        display <-- isSelectingSignal.map(sel => if sel then "none" else "flex"),
        i(cls := "fa-solid fa-hammer"),
        div(cls := "build-label", "Build"),
        onClick --> { e =>
          e.stopPropagation()
          TileGridState.selectTile(coord)
        }
      ),

      // Build menu (shown when selecting)
      div(
        display <-- isSelectingSignal.map(sel => if sel then "block" else "none"),
        BuildMenu(buildActions)
      ),

      // Right-click to destroy tile
      destroyTileHandler(onDestroyTile)
    )

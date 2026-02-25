package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, BuildMenu}

/** Empty tile component.
  *
  * Shows a build hammer icon. When clicked, shows the build menu.
  */
object EmptyTile:

  def apply(
    coord: Coord,
    buildActions: BuildMenu.Actions
  ): HtmlElement =
    val isSelectingSignal = TileGridState.selectingTileCoord.signal.map(_.contains(coord))

    div(
      cls := "tile-kingdom-tile unlocked empty",
      cls <-- TileGridState.zoomTierClass.combineWith(isSelectingSignal).map:
        case (zoomCls, true) => s"$zoomCls selecting".trim
        case (zoomCls, false) => zoomCls,
      styleAttr <-- TileGridState.tileStyle(coord),

      // Build icon container (shown when not selecting)
      div(
        cls := "tile-build-icon-container",
        display <-- isSelectingSignal.map(sel => if sel then "none" else "flex"),
        i(cls := "fa-solid fa-hammer"),
        div(cls := "build-label", "Build"),
        onClick --> { e =>
          e.stopPropagation()
          // Clear any other selecting tiles
          TileGridState.selectTile(coord)
        }
      ),

      // Build menu (shown when selecting)
      div(
        display <-- isSelectingSignal.map(sel => if sel then "block" else "none"),
        BuildMenu(buildActions)
      )
    )


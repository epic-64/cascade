package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Renders lines from an influence source tile to all tiles it affects.
  *
  * Shown on hover, or always when the influence lines toggle is active.
  * Uses absolutely-positioned HTML divs rotated to form lines.
  * Each tile type has a different colored line:
  *   - Farm: blue lines to neighboring tiles
  *   - Bureau: purple lines to affected tiles
  *   - Town Hall: amber lines to tiles in influence radius
  */
object InfluenceLines:

  /** Determine which tile coords are affected by the tile at `sourceCoord`.
    * Only returns coords that actually contain a tile in the game. */
  private def affectedCoords(game: TileKingdomGame, sourceCoord: Coord): (List[Coord], String) =
    game.tiles.get(sourceCoord).map(_.tileType) match
      case Some(TileType.Farm(_)) =>
        val affected = sourceCoord.neighbors.filter(game.tiles.contains).toList
        (affected, "farm")
      case Some(TileType.Bureau(_)) =>
        val affected = TileKingdomLogic.bureauAffectedCoords(game, sourceCoord)
          .filter(c => game.tiles.get(c).exists(_.isUpgradeable)).toList
        (affected, "bureau")
      case Some(TileType.TownHall(pols)) if pols.nonEmpty =>
        val affected = TileKingdomLogic.townHallAffectedCoords(game, sourceCoord)
          .filter(c => game.tiles.contains(c) && c != sourceCoord).toList
        (affected, "town-hall")
      case _ => (List.empty, "")

  /** Render influence lines for all visible influence sources (always-on mode) */
  private def renderAllInfluenceLines(game: TileKingdomGame, tileSize: Double): List[HtmlElement] =
    game.tiles.toList.flatMap: (coord, tile) =>
      tile.tileType match
        case TileType.Farm(_) | TileType.Bureau(_) =>
          val (targets, lineType) = affectedCoords(game, coord)
          targets.map(target => renderLine(coord, target, tileSize, lineType))
        case TileType.TownHall(pols) if pols.nonEmpty =>
          val (targets, lineType) = affectedCoords(game, coord)
          targets.map(target => renderLine(coord, target, tileSize, lineType))
        case _ => Nil

  /** Render a single line between two tile centers as a rotated div */
  private def renderLine(from: Coord, to: Coord, tileSize: Double, lineType: String): HtmlElement =
    val halfTile = tileSize / 2.0
    val x1 = from.col * tileSize + halfTile
    val y1 = from.row * tileSize + halfTile
    val x2 = to.col * tileSize + halfTile
    val y2 = to.row * tileSize + halfTile

    val dx = x2 - x1
    val dy = y2 - y1
    val length = math.sqrt(dx * dx + dy * dy)
    val angle = math.atan2(dy, dx) * 180.0 / math.Pi

    div(
      cls := s"influence-line influence-line-$lineType",
      styleAttr := s"position:absolute;left:${x1}px;top:${y1}px;width:${length}px;transform:rotate(${angle}deg);transform-origin:0 50%;"
    )

  /** The container for influence lines, placed inside the grid container */
  def apply(): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val alwaysShowSignal = TileGridState.showInfluenceLines.signal
    val tileSizeSignal = TileGridState.tileSizeSignal

    div(
      cls := "influence-lines-overlay",
      // Always-on lines — only shown when toggled on via button
      children <-- alwaysShowSignal.combineWith(gameSignal).combineWith(tileSizeSignal).map:
        case (show, game, tileSize) =>
          if show then renderAllInfluenceLines(game, tileSize)
          else Nil
    )


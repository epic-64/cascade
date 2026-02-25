package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*

/** Influence indicator component for showing tile effect ranges.
  *
  * Renders a semi-transparent rectangle showing the area of influence
  * for tiles like Farms, Bureaus, and Town Halls.
  */
object InfluenceIndicator:

  /** Create an influence indicator element
    *
    * @param center The center coordinate of the influence area
    * @param radius The radius of influence (in tiles)
    * @param cssClass CSS class for styling (e.g., "farm-influence", "bureau-influence")
    */
  def apply(
    center: Coord,
    radius: Int,
    cssClass: String
  ): HtmlElement =
    div(
      cls := s"influence-indicator $cssClass",
      styleAttr <-- TileGridState.tileSizeSignal.map: tileSize =>
        // Calculate the rectangle bounds
        val left = (center.col - radius) * tileSize
        val top = (center.row - radius) * tileSize
        val width = (radius * 2 + 1) * tileSize - 4 // -4 for gap
        val height = (radius * 2 + 1) * tileSize - 4

        s"position: absolute; left: ${left}px; top: ${top}px; width: ${width}px; height: ${height}px;"
    )

  /** Create influence indicators for all relevant tiles in the game */
  def renderAll(game: TileKingdomGame, bounds: (Int, Int, Int, Int)): List[HtmlElement] =
    val (minRow, maxRow, minCol, maxCol) = bounds
    val extendedBounds = (minRow - 3, maxRow + 3, minCol - 3, maxCol + 3) // Extend bounds for influence areas

    game.unlockedTiles.flatMap: tile =>
      val coord = tile.coord
      if coord.row >= extendedBounds._1 && coord.row <= extendedBounds._2 &&
         coord.col >= extendedBounds._3 && coord.col <= extendedBounds._4 then
        tile.tileType match
          case TileType.Farm(_) =>
            Some(apply(coord, 1, "farm-influence"))
          case TileType.Bureau(_) =>
            Some(apply(coord, TileKingdomLogic.BureauRadius, "bureau-influence"))
          case TileType.TownHall(Some(_)) =>
            Some(apply(coord, TileKingdomLogic.TownHallInfluenceRadius, "town-hall-influence"))
          case _ => None
      else None
    .toList

  /** Create influence indicators from a list of tiles (more efficient - avoids full game traversal) */
  def renderAllFromTiles(tiles: List[Tile], bounds: (Int, Int, Int, Int)): List[HtmlElement] =
    val (minRow, maxRow, minCol, maxCol) = bounds
    val extendedBounds = (minRow - 3, maxRow + 3, minCol - 3, maxCol + 3)

    tiles.flatMap: tile =>
      val coord = tile.coord
      if coord.row >= extendedBounds._1 && coord.row <= extendedBounds._2 &&
         coord.col >= extendedBounds._3 && coord.col <= extendedBounds._4 then
        tile.tileType match
          case TileType.Farm(_) =>
            Some(apply(coord, 1, "farm-influence"))
          case TileType.Bureau(_) =>
            Some(apply(coord, TileKingdomLogic.BureauRadius, "bureau-influence"))
          case TileType.TownHall(Some(_)) =>
            Some(apply(coord, TileKingdomLogic.TownHallInfluenceRadius, "town-hall-influence"))
          case _ => None
      else None


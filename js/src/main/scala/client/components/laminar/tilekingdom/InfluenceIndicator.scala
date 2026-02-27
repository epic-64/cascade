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
      cls <-- TileGridState.hoveredTileCoord.signal.map:
        case Some(coord) if coord == center => "influence-hovered"
        case _ => "",
      styleAttr <-- TileGridState.tileSizeSignal.map: tileSize =>
        // Calculate the rectangle bounds
        val left = (center.col - radius) * tileSize
        val top = (center.row - radius) * tileSize
        val width = (radius * 2 + 1) * tileSize - 4 // -4 for gap
        val height = (radius * 2 + 1) * tileSize - 4

        s"position: absolute; left: ${left}px; top: ${top}px; width: ${width}px; height: ${height}px;"
    )

  /** Create a directional influence indicator for a bureau with direction skill */
  def applyDirectional(
    center: Coord,
    direction: BureauDirection,
    cssClass: String
  ): HtmlElement =
    div(
      cls := s"influence-indicator $cssClass",
      cls <-- TileGridState.hoveredTileCoord.signal.map:
        case Some(coord) if coord == center => "influence-hovered"
        case _ => "",
      styleAttr <-- TileGridState.tileSizeSignal.map: tileSize =>
        val (left, top, width, height) = direction match
          case BureauDirection.Center =>
            val r = TileKingdomLogic.BureauRadius
            ((center.col - r) * tileSize, (center.row - r) * tileSize,
              (r * 2 + 1) * tileSize - 4, (r * 2 + 1) * tileSize - 4)
          case BureauDirection.Up =>
            ((center.col - TileKingdomLogic.BureauDirectionHalfWidth) * tileSize,
              (center.row - TileKingdomLogic.BureauDirectionLength) * tileSize,
              (TileKingdomLogic.BureauDirectionHalfWidth * 2 + 1) * tileSize - 4,
              TileKingdomLogic.BureauDirectionLength * tileSize - 4)
          case BureauDirection.Down =>
            ((center.col - TileKingdomLogic.BureauDirectionHalfWidth) * tileSize,
              (center.row + 1) * tileSize,
              (TileKingdomLogic.BureauDirectionHalfWidth * 2 + 1) * tileSize - 4,
              TileKingdomLogic.BureauDirectionLength * tileSize - 4)
          case BureauDirection.Left =>
            ((center.col - TileKingdomLogic.BureauDirectionLength) * tileSize,
              (center.row - TileKingdomLogic.BureauDirectionHalfWidth) * tileSize,
              TileKingdomLogic.BureauDirectionLength * tileSize - 4,
              (TileKingdomLogic.BureauDirectionHalfWidth * 2 + 1) * tileSize - 4)
          case BureauDirection.Right =>
            ((center.col + 1) * tileSize,
              (center.row - TileKingdomLogic.BureauDirectionHalfWidth) * tileSize,
              TileKingdomLogic.BureauDirectionLength * tileSize - 4,
              (TileKingdomLogic.BureauDirectionHalfWidth * 2 + 1) * tileSize - 4)

        s"position: absolute; left: ${left}px; top: ${top}px; width: ${width}px; height: ${height}px;"
    )

  /** Render bureau influence indicator based on game state (direction-aware) */
  private def renderBureauInfluence(game: TileKingdomGame, coord: Coord): HtmlElement =
    if game.hasSkill(Skill.Management3) then
      val direction = TileKingdomLogic.getBureauDirection(game, coord)
      applyDirectional(coord, direction, "bureau-influence")
    else
      apply(coord, TileKingdomLogic.BureauRadius, "bureau-influence")

  /** Render town hall influence indicator based on game state (direction-aware) */
  private def renderTownHallInfluence(game: TileKingdomGame, coord: Coord): HtmlElement =
    if game.hasSkill(Skill.Management5) then
      val direction = TileKingdomLogic.getTownHallDirection(game, coord)
      applyDirectional(coord, direction, "town-hall-influence")
    else
      apply(coord, TileKingdomLogic.TownHallInfluenceRadius, "town-hall-influence")

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
            Some(renderBureauInfluence(game, coord))
          case TileType.TownHall(pols) if pols.nonEmpty =>
            Some(renderTownHallInfluence(game, coord))
          case _ => None
      else None
    .toList

  /** Create influence indicators from a list of tiles (more efficient - avoids full game traversal) */
  def renderAllFromTiles(tiles: List[Tile], game: TileKingdomGame, bounds: (Int, Int, Int, Int)): List[HtmlElement] =
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
            Some(renderBureauInfluence(game, coord))
          case TileType.TownHall(pols) if pols.nonEmpty =>
            Some(renderTownHallInfluence(game, coord))
          case _ => None
      else None


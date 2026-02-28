package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Main tile grid component.
  *
  * Renders the current island's tiles in a fixed 3x5 grid.
  */
object TileGrid:

  /** Represents a tile slot that can be rendered */
  private case class TileSlot(coord: Coord, isUnlockable: Boolean, isLocked: Boolean)

  /** Compare tiles for structural equality, ignoring politician lifespan changes.
    * This prevents tile re-rendering just because the lifespan countdown changed.
    * TownHallTile handles lifespan updates internally via signals.
    */
  private def tileStructurallyEqual(a: Option[Tile], b: Option[Tile]): Boolean =
    (a, b) match
      case (None, None) => true
      case (Some(t1), Some(t2)) =>
        t1.coord == t2.coord && t1.unlocked == t2.unlocked && tileTypeStructurallyEqual(t1.tileType, t2.tileType)
      case _ => false

  private def tileTypeStructurallyEqual(a: TileType, b: TileType): Boolean =
    (a, b) match
      case (TileType.TownHall(p1), TileType.TownHall(p2)) =>
        // Compare politicians by IDs only, ignore lifespan changes
        p1.map(_.id) == p2.map(_.id)
      case _ => a == b

  /** Create the tile grid component */
  def apply(actions: TileRenderer.Actions, onNotification: String => Unit): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val tilesSignal = TileKingdomState.tilesSignal
    val unlockableCoordsSignal = TileKingdomState.unlockableCoordsSignal
    val canAffordUnlockSignal = TileKingdomState.canAffordUnlockSignal
    val visibleBoundsSignal = TileGridState.visibleBoundsSignal

    // Signal of visible tile slots - includes all island tiles
    // Unlocked tiles show their content, unlockable tiles show unlock UI, locked tiles show as dark
    val visibleSlotsSignal: Signal[List[TileSlot]] = tilesSignal
      .combineWith(unlockableCoordsSignal)
      .combineWith(canAffordUnlockSignal)
      .combineWith(visibleBoundsSignal)
      .map { (tiles, unlockableCoords, canAffordUnlock, minRow, maxRow, minCol, maxCol) =>
        // All tiles on the current island (unlocked and locked)
        val allIslandCoords = Island.AllCoords
        val unlockedCoords = tiles.filter(_._2.unlocked).keySet
        val unlockable = if canAffordUnlock then unlockableCoords else Set.empty[Coord]

        allIslandCoords.flatMap { coord =>
          if coord.row >= minRow && coord.row <= maxRow &&
             coord.col >= minCol && coord.col <= maxCol then
            val isUnlocked = unlockedCoords.contains(coord)
            val isUnlockable = !isUnlocked && unlockable.contains(coord)
            val isLocked = !isUnlocked && !isUnlockable
            Some(TileSlot(coord, isUnlockable, isLocked))
          else
            None
        }.toList.sortBy(s => (s.coord.row, s.coord.col))
      }
      .distinct // Only emit when the list of slots actually changes

    div(
      idAttr := "tile-kingdom-grid-viewport",
      cls := "tile-kingdom-grid-viewport",

      // Grid container (transforms with pan offset)
      div(
        idAttr := "tile-kingdom-grid",
        cls := "tile-kingdom-grid",
        styleAttr <-- TileGridState.panOffset.signal.map { case (panX, panY) =>
          s"transform: translate(${panX}px, ${panY}px);"
        },

        // Influence indicators (rendered behind tiles)
        // Only re-render when tiles with influence change, not every tick
        children <-- gameSignal.combineWith(tilesSignal).combineWith(visibleBoundsSignal).map { (game, tiles, minRow, maxRow, minCol, maxCol) =>
          // Extract only the info needed for influence indicators
          val influenceTiles = tiles.values.filter { tile =>
            tile.tileType match
              case TileType.Farm(_) | TileType.Bureau(_) => true
              case TileType.TownHall(pols) if pols.nonEmpty => true
              case _ => false
          }.toList
          // Include bureau directions in the distinct key so direction changes trigger re-render
          val bureauDirections = influenceTiles.collect {
            case tile if tile.isBureau => tile.coord -> TileKingdomLogic.getBureauDirection(game, tile.coord)
          }
          val townHallDirections = influenceTiles.collect {
            case tile if tile.isTownHall => tile.coord -> TileKingdomLogic.getTownHallDirection(game, tile.coord)
          }
          val hasDirectionSkill = game.hasSkill(Skill.Management1B) || game.hasSkill(Skill.Management2B)
          (influenceTiles, (minRow, maxRow, minCol, maxCol), bureauDirections, townHallDirections, hasDirectionSkill)
        }.distinct.map { (influenceTiles, bounds, _, _, _) =>
          val game = TileKingdomState.currentGame
          InfluenceIndicator.renderAllFromTiles(influenceTiles, game, bounds)
        },

        // Tiles - use split for efficient updates
        children <-- visibleSlotsSignal.split(_.coord) { (coord, initialSlot, slotSignal) =>
          // Each tile component reads its own data from the global signal
          // Use distinctByFn to ignore politician lifespan changes (handled by TownHallTile internally)
          val tileSignal = tilesSignal.map(_.get(coord)).distinctByFn(tileStructurallyEqual)
          val isUnlockableSignal = slotSignal.map(_.isUnlockable).distinct
          val isLockedSignal = slotSignal.map(_.isLocked).distinct

          div(
            // This wrapper div is keyed by coord and stays stable
            children <-- tileSignal.combineWith(isUnlockableSignal).combineWith(isLockedSignal).map { (tileOpt, isUnlockable, isLocked) =>
              tileOpt match
                case Some(tile) if tile.unlocked =>
                  List(TileRenderer(coord, tile, actions))
                case _ if isUnlockable =>
                  List(TileRenderer.renderUnlockable(coord, actions))
                case _ if isLocked =>
                  List(TileRenderer.renderLocked(coord))
                case _ =>
                  Nil
            }
          )
        },

        // Influence lines (rendered above tiles)
        InfluenceLines()
      ),

      // Center the grid on mount and handle resize
      onMountCallback { _ =>
        // Initial centering
        TileGridState.centerOnKingdom(TileKingdomState.currentGame, animated = false)
        
        // Re-center on window resize (handles orientation change on mobile)
        dom.window.addEventListener("resize", TileGrid.resizeHandler)
      },
      
      onUnmountCallback { _ =>
        dom.window.removeEventListener("resize", TileGrid.resizeHandler)
      }
    )

  /** Resize handler for recentering on viewport change */
  private val resizeHandler: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) =>
    TileGridState.centerOnKingdom(TileKingdomState.currentGame, animated = false)

  /** Check if element or any ancestor has draggable="true" */
  private def hasDraggableAncestor(elem: dom.Element): Boolean =
    var current: dom.Node = elem
    while current != null && current.isInstanceOf[dom.Element] do
      val el = current.asInstanceOf[dom.Element]
      if el.getAttribute("draggable") == "true" then return true
      current = el.parentNode
    false


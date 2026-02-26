package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, TouchEvent, WheelEvent, HTMLElement, window}
import shared.TileKingdom.*
import client.components.laminar.TileKingdomState

/** Main tile grid component.
  *
  * Manages the grid viewport with pan/zoom and renders all tiles.
  */
object TileGrid:

  /** Represents a tile slot that can be rendered */
  private case class TileSlot(coord: Coord, isUnlockable: Boolean)

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

    // Signal of visible tile slots - only changes when coords or tile types change
    // Use distinctBy to prevent re-renders when only resource amounts change
    val visibleSlotsSignal: Signal[List[TileSlot]] = tilesSignal
      .combineWith(unlockableCoordsSignal)
      .combineWith(canAffordUnlockSignal)
      .combineWith(visibleBoundsSignal)
      .map { (tiles, unlockableCoords, canAffordUnlock, minRow, maxRow, minCol, maxCol) =>
        val tileCoords = tiles.keySet
        val unlockable = if canAffordUnlock then unlockableCoords else Set.empty[Coord]
        val allCoords = tileCoords ++ unlockable

        allCoords.flatMap { coord =>
          if coord.row >= minRow && coord.row <= maxRow &&
             coord.col >= minCol && coord.col <= maxCol then
            val isUnlockable = !tileCoords.contains(coord) && unlockable.contains(coord)
            Some(TileSlot(coord, isUnlockable))
          else
            None
        }.toList.sortBy(s => (s.coord.row, s.coord.col))
      }
      .distinct // Only emit when the list of slots actually changes

    div(
      idAttr := "tile-kingdom-grid-viewport",
      cls := "tile-kingdom-grid-viewport",
      styleAttr := "cursor: grab;",

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
          val hasDirectionSkill = game.hasSkill(Skill.Management3)
          (influenceTiles, (minRow, maxRow, minCol, maxCol), bureauDirections, hasDirectionSkill)
        }.distinct.map { (influenceTiles, bounds, _, _) =>
          val game = TileKingdomState.currentGame
          InfluenceIndicator.renderAllFromTiles(influenceTiles, game, bounds)
        },

        // Tiles - use split for efficient updates
        children <-- visibleSlotsSignal.split(_.coord) { (coord, initialSlot, slotSignal) =>
          // Each tile component reads its own data from the global signal
          // Use distinctByFn to ignore politician lifespan changes (handled by TownHallTile internally)
          val tileSignal = tilesSignal.map(_.get(coord)).distinctByFn(tileStructurallyEqual)
          val isUnlockableSignal = slotSignal.map(_.isUnlockable).distinct

          div(
            // This wrapper div is keyed by coord and stays stable
            children <-- tileSignal.combineWith(isUnlockableSignal).map { (tileOpt, isUnlockable) =>
              tileOpt match
                case Some(tile) =>
                  List(TileRenderer(coord, tile, actions))
                case None if isUnlockable =>
                  List(TileRenderer.renderUnlockable(coord, actions))
                case _ =>
                  Nil
            }
          )
        }
      ),

      // Event handlers for pan/zoom
      onMountCallback { ctx =>
        val viewport = ctx.thisNode.ref

        // Mouse drag handlers
        viewport.onmousedown = (e: MouseEvent) =>
          if e.button == 0 then // Left mouse button
            val target = e.target.asInstanceOf[dom.Element]
            if !hasDraggableAncestor(target) then
              TileGridState.isDragging = true
              TileGridState.dragStartX = e.clientX
              TileGridState.dragStartY = e.clientY
              val (panX, panY) = TileGridState.panOffset.now()
              TileGridState.panStartX = panX
              TileGridState.panStartY = panY
              viewport.asInstanceOf[HTMLElement].style.cursor = "grabbing"

        dom.document.onmousemove = (e: MouseEvent) =>
          if TileGridState.isDragging then
            val dx = e.clientX - TileGridState.dragStartX
            val dy = e.clientY - TileGridState.dragStartY
            if math.abs(dx) > 5 || math.abs(dy) > 5 then
              TileGridState.wasDragging = true
            TileGridState.panOffset.set((TileGridState.panStartX + dx, TileGridState.panStartY + dy))

        dom.document.onmouseup = (_: MouseEvent) =>
          if TileGridState.isDragging then
            TileGridState.isDragging = false
            viewport.asInstanceOf[HTMLElement].style.cursor = "grab"
            TileGridState.snapBackIfNeeded(TileKingdomState.currentGame, () => onNotification("Snapped back to kingdom"))

        // Suppress click events that follow a drag - using capture phase to intercept before any handler
        dom.document.addEventListener("click", (e: dom.Event) =>
          if TileGridState.wasDragging then
            e.stopPropagation()
            e.preventDefault()
            TileGridState.wasDragging = false
        , true) // true = capture phase

        // Touch handlers
        viewport.addEventListener("touchstart", (e: dom.Event) =>
          val te = e.asInstanceOf[TouchEvent]
          if te.touches.length == 1 then
            val touch = te.touches(0)
            TileGridState.isDragging = true
            TileGridState.dragStartX = touch.clientX
            TileGridState.dragStartY = touch.clientY
            val (panX, panY) = TileGridState.panOffset.now()
            TileGridState.panStartX = panX
            TileGridState.panStartY = panY
        )

        viewport.addEventListener("touchmove", (e: dom.Event) =>
          val te = e.asInstanceOf[TouchEvent]
          if TileGridState.isDragging && te.touches.length == 1 then
            e.preventDefault()
            val touch = te.touches(0)
            val dx = touch.clientX - TileGridState.dragStartX
            val dy = touch.clientY - TileGridState.dragStartY
            TileGridState.panOffset.set((TileGridState.panStartX + dx, TileGridState.panStartY + dy))
        )

        viewport.addEventListener("touchend", (_: dom.Event) =>
          if TileGridState.isDragging then
            TileGridState.isDragging = false
            TileGridState.snapBackIfNeeded(TileKingdomState.currentGame, () => onNotification("Snapped back to kingdom"))
        )

        // Mouse wheel zoom
        viewport.addEventListener("wheel", (e: dom.Event) =>
          val we = e.asInstanceOf[WheelEvent]
          e.preventDefault()
          val delta = if we.deltaY < 0 then TileGridState.ZoomStep else -TileGridState.ZoomStep
          TileGridState.applyZoom(delta, we.clientX, we.clientY)
        )
      }
    )

  /** Check if element or any ancestor has draggable="true" */
  private def hasDraggableAncestor(elem: dom.Element): Boolean =
    var current: dom.Node = elem
    while current != null && current.isInstanceOf[dom.Element] do
      val el = current.asInstanceOf[dom.Element]
      if el.getAttribute("draggable") == "true" then return true
      current = el.parentNode
    false


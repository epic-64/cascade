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

  /** Create the tile grid component */
  def apply(actions: TileRenderer.Actions, onNotification: String => Unit): HtmlElement =
    val gameSignal = TileKingdomState.gameSignal
    val tilesSignal = TileKingdomState.tilesSignal
    val unlockableCoordsSignal = TileKingdomState.unlockableCoordsSignal
    val canAffordUnlockSignal = TileKingdomState.canAffordUnlockSignal
    val visibleBoundsSignal = TileGridState.visibleBoundsSignal

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
        children <-- gameSignal.combineWith(visibleBoundsSignal).map { (game: TileKingdomGame, minRow: Int, maxRow: Int, minCol: Int, maxCol: Int) =>
          InfluenceIndicator.renderAll(game, (minRow, maxRow, minCol, maxCol))
        },

        // Tiles
        children <-- tilesSignal
          .combineWith(unlockableCoordsSignal)
          .combineWith(canAffordUnlockSignal)
          .combineWith(visibleBoundsSignal)
          .map { (tiles: Map[Coord, Tile], unlockableCoords: Set[Coord], canAffordUnlock: Boolean, minRow: Int, maxRow: Int, minCol: Int, maxCol: Int) =>
            // Determine which coords to render
            val coordsToRender = tiles.keySet ++ (if canAffordUnlock then unlockableCoords else Set.empty)

            // Filter to visible range and render
            coordsToRender.flatMap { coord =>
              if coord.row >= minRow && coord.row <= maxRow &&
                 coord.col >= minCol && coord.col <= maxCol then
                tiles.get(coord) match
                  case Some(tile) =>
                    Some(TileRenderer(coord, tile, actions))
                  case None if canAffordUnlock && unlockableCoords.contains(coord) =>
                    Some(TileRenderer.renderUnlockable(coord, actions))
                  case _ =>
                    None
              else
                None
            }.toList
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
            if TileGridState.wasDragging then
              window.setTimeout(() => TileGridState.wasDragging = false, 10)

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


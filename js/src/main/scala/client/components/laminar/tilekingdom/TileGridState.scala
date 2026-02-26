package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import org.scalajs.dom.window
import shared.TileKingdom.*

/** Grid-specific reactive state for the tile grid.
  *
  * Manages pan/zoom, tile selection, and progress tracking.
  * Separate from TileKingdomState to keep grid concerns isolated.
  */
object TileGridState:

  // ============================================================================
  // Constants
  // ============================================================================

  val BaseTileSize: Int = 74 // 70px tile + 4px gap
  val MinZoom: Double = 0.3
  val MaxZoom: Double = 2.0
  val ZoomStep: Double = 0.1
  val VisiblePadding: Int = 2 // Extra tiles to render outside viewport

  // Zoom tier thresholds
  val ZoomTierIcons: Double = 1.4   // below this: icons only
  val ZoomTierMinimal: Double = 0.58  // below this: no content at all

  // ============================================================================
  // Pan/Zoom State
  // ============================================================================

  val panOffset: Var[(Double, Double)] = Var((0.0, 0.0))
  val zoomLevel: Var[Double] = Var(2.0)

  // Dragging state (not reactive - updated imperatively during drag)
  var isDragging: Boolean = false
  var wasDragging: Boolean = false
  var dragStartX: Double = 0.0
  var dragStartY: Double = 0.0
  var panStartX: Double = 0.0
  var panStartY: Double = 0.0

  // ============================================================================
  // Selection State
  // ============================================================================

  val selectingTileCoord: Var[Option[Coord]] = Var(None)
  val activeSubmenu: Var[Option[String]] = Var(None) // "resources" or "management"

  // ============================================================================
  // Progress Tracking
  // ============================================================================

  val tileProgress: Var[Map[Coord, Double]] = Var(Map.empty)

  /** Get or initialize progress for a tile (0.0 to 1.0) */
  def getOrInitProgress(coord: Coord): Double =
    tileProgress.now().getOrElse(coord, {
      val offset = scala.util.Random.nextDouble()
      tileProgress.update(_.updated(coord, offset))
      offset
    })

  /** Update progress for a tile */
  def updateProgress(coord: Coord, progress: Double): Unit =
    tileProgress.update(_.updated(coord, progress))

  /** Remove progress tracking for a tile */
  def removeProgress(coord: Coord): Unit =
    tileProgress.update(_.removed(coord))

  /** Clear all progress tracking */
  def clearProgress(): Unit =
    tileProgress.set(Map.empty)

  // ============================================================================
  // Derived Signals
  // ============================================================================

  /** Current tile size in pixels (base size * zoom) */
  val tileSizeSignal: Signal[Double] = zoomLevel.signal.map(_ * BaseTileSize)

  /** Current zoom tier for determining what to render */
  enum ZoomTier:
    case Full, Icons, Minimal

  val zoomTierSignal: Signal[ZoomTier] = zoomLevel.signal.map: zoom =>
    if zoom < ZoomTierMinimal then ZoomTier.Minimal
    else if zoom < ZoomTierIcons then ZoomTier.Icons
    else ZoomTier.Full

  /** Visible tile bounds based on viewport and pan offset */
  val visibleBoundsSignal: Signal[(Int, Int, Int, Int)] =
    panOffset.signal.combineWith(tileSizeSignal).map { (panX: Double, panY: Double, tileSize: Double) =>
      val viewportWidth = window.innerWidth
      val viewportHeight = window.innerHeight

      val minCol = ((-panX - tileSize * VisiblePadding) / tileSize).floor.toInt
      val maxCol = ((-panX + viewportWidth + tileSize * VisiblePadding) / tileSize).ceil.toInt
      val minRow = ((-panY - tileSize * VisiblePadding) / tileSize).floor.toInt
      val maxRow = ((-panY + viewportHeight + tileSize * VisiblePadding) / tileSize).ceil.toInt

      (minRow, maxRow, minCol, maxCol)
    }

  // ============================================================================
  // Style Helpers
  // ============================================================================

  /** Generate CSS style for positioning a tile at given coordinates */
  def tileStyle(coord: Coord): Signal[String] =
    zoomLevel.signal.map: zoom =>
      val tileSize = BaseTileSize * zoom
      val tilePixelSize = (70 * zoom).toInt
      val fontScale = math.max(0.3, math.min(1.0, zoom))
      s"position: absolute; left: ${coord.col * tileSize}px; top: ${coord.row * tileSize}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontScale}em;"

  /** Get zoom tier CSS class */
  def zoomTierClass: Signal[String] = zoomTierSignal.map:
    case ZoomTier.Minimal => "zoom-minimal"
    case ZoomTier.Icons => "zoom-icons"
    case ZoomTier.Full => ""

  // ============================================================================
  // Pan/Zoom Operations
  // ============================================================================

  /** Update grid position (called during drag) */
  def updateGridPosition(): Unit =
    () // Position is managed by the panOffset signal - components react automatically

  /** Center the view on the kingdom */
  def centerOnKingdom(game: TileKingdomGame, animated: Boolean): Unit =
    val target = calculateCenterOffset(game)
    if animated then
      animateTo(target)
    else
      panOffset.set(target)

  /** Calculate the offset needed to center on unlocked tiles */
  def calculateCenterOffset(game: TileKingdomGame): (Double, Double) =
    val unlockedCoords = game.unlockedTiles.map(_.coord)
    if unlockedCoords.nonEmpty then
      val centerRow = unlockedCoords.map(_.row).sum.toDouble / unlockedCoords.size
      val centerCol = unlockedCoords.map(_.col).sum.toDouble / unlockedCoords.size
      val viewportWidth = window.innerWidth
      val viewportHeight = window.innerHeight
      val tileSize = zoomLevel.now() * BaseTileSize
      val targetX = viewportWidth / 2 - (centerCol + 0.5) * tileSize
      val targetY = viewportHeight / 2 - (centerRow + 0.5) * tileSize
      (targetX, targetY)
    else
      panOffset.now()

  /** Animate pan offset to target position */
  def animateTo(target: (Double, Double)): Unit =
    val (targetX, targetY) = target
    val (startX, startY) = panOffset.now()
    val duration = 300.0 // milliseconds
    val startTime = System.currentTimeMillis().toDouble

    def animate(): Unit =
      val elapsed = System.currentTimeMillis().toDouble - startTime
      val progress = math.min(1.0, elapsed / duration)
      // Ease-out cubic for smooth deceleration
      val eased = 1.0 - math.pow(1.0 - progress, 3)

      val newX = startX + (targetX - startX) * eased
      val newY = startY + (targetY - startY) * eased
      panOffset.set((newX, newY))

      if progress < 1.0 then
        window.requestAnimationFrame((_: Double) => animate())

    animate()

  /** Check if any tile is visible and snap back if not */
  def snapBackIfNeeded(game: TileKingdomGame, onSnap: () => Unit): Unit =
    val viewportWidth = window.innerWidth
    val viewportHeight = window.innerHeight
    val tileSize = zoomLevel.now() * BaseTileSize
    val (panX, panY) = panOffset.now()

    val unlockedCoords = game.unlockedTiles.map(_.coord)
    val margin = tileSize * 0.5

    val anyVisible = unlockedCoords.exists: coord =>
      val tileScreenX = coord.col * tileSize + panX
      val tileScreenY = coord.row * tileSize + panY
      tileScreenX > -tileSize + margin && tileScreenX < viewportWidth - margin &&
      tileScreenY > -tileSize + margin && tileScreenY < viewportHeight - margin

    if !anyVisible then
      animateTo(calculateCenterOffset(game))
      onSnap()

  /** Apply zoom delta, keeping a world position fixed */
  def applyZoom(delta: Double, mouseX: Double, mouseY: Double): Unit =
    val currentZoom = zoomLevel.now()
    val (panX, panY) = panOffset.now()
    val currentTileSize = BaseTileSize * currentZoom

    // Calculate the world position under the mouse before zoom
    val worldXBefore = (mouseX - panX) / currentTileSize
    val worldYBefore = (mouseY - panY) / currentTileSize

    // Apply zoom
    val newZoom = math.max(MinZoom, math.min(MaxZoom, currentZoom + delta))
    zoomLevel.set(newZoom)

    // Adjust pan to keep the same world position under the mouse
    val newTileSize = BaseTileSize * newZoom
    val newPanX = mouseX - worldXBefore * newTileSize
    val newPanY = mouseY - worldYBefore * newTileSize
    panOffset.set((newPanX, newPanY))

  // ============================================================================
  // Selection Operations
  // ============================================================================

  /** Clear tile selection */
  def clearSelection(): Unit =
    selectingTileCoord.set(None)
    activeSubmenu.set(None)

  /** Select a tile for building */
  def selectTile(coord: Coord): Unit =
    selectingTileCoord.set(Some(coord))
    activeSubmenu.set(None)

  /** Open a submenu */
  def openSubmenu(menu: String): Unit =
    activeSubmenu.set(Some(menu))

  /** Close submenu (back to main menu) */
  def closeSubmenu(): Unit =
    activeSubmenu.set(None)


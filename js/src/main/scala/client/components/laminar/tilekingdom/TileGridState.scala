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
  // Influence Line State
  // ============================================================================

  /** Currently hovered tile coordinate (for influence line display) */
  val hoveredTileCoord: Var[Option[Coord]] = Var(None)

  /** Whether to always show influence lines (toggle) */
  val showInfluenceLines: Var[Boolean] = Var(false)

  def toggleInfluenceLines(): Unit =
    showInfluenceLines.update(!_)

  // ============================================================================
  // Zen Mode State
  // ============================================================================

  /** Whether Zen Mode is active (hides text, shows only emojis) */
  val zenMode: Var[Boolean] = Var(false)

  def toggleZenMode(): Unit =
    zenMode.update(!_)
    // Apply/remove zen-mode class on body
    if zenMode.now() then
      org.scalajs.dom.document.body.classList.add("zen-mode")
    else
      org.scalajs.dom.document.body.classList.remove("zen-mode")

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

  /** Generate CSS style for positioning a tile at given coordinates.
    * All values are rounded to whole pixels to prevent sub-pixel blurriness.
    */
  def tileStyle(coord: Coord): Signal[String] =
    zoomLevel.signal.map: zoom =>
      val tileSize = BaseTileSize * zoom
      val tilePixelSize = math.round(70 * zoom).toInt
      val left = math.round(coord.col * tileSize).toInt
      val top = math.round(coord.row * tileSize).toInt
      val fontScale = math.max(0.3, math.min(1.0, zoom))
      val fontPx = math.round(fontScale * 16).toInt // snap font to whole pixels (base 16px)
      s"position: absolute; left: ${left}px; top: ${top}px; width: ${tilePixelSize}px; height: ${tilePixelSize}px; font-size: ${fontPx}px;"

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

  /** Center the view on the kingdom and adjust zoom to fit */
  def centerOnKingdom(game: TileKingdomGame, animated: Boolean): Unit =
    // Calculate optimal zoom to fit the 3x5 grid with some padding
    val optimalZoom = calculateOptimalZoom()
    zoomLevel.set(optimalZoom)

    val target = calculateCenterOffset(game)
    if animated then
      animateTo(target)
    else
      panOffset.set(target)

  /** Calculate the optimal zoom level to fit the 3x5 island grid on screen */
  private def calculateOptimalZoom(): Double =
    val viewportWidth = window.innerWidth
    val viewportHeight = window.innerHeight

    // Grid dimensions: 3 columns, 5 rows
    // Each tile is BaseTileSize (74px = 70px tile + 4px gap)
    val gridWidth = Island.Width * BaseTileSize   // 3 * 74 = 222px at zoom 1.0
    val gridHeight = Island.Height * BaseTileSize // 5 * 74 = 370px at zoom 1.0

    // Check if mobile (viewport width <= 768px)
    val isMobile = viewportWidth <= 768

    // Leave padding for UI elements
    val horizontalPadding = 40  // 20px on each side
    // Mobile: top bar (48px) + island nav (44px) at top, action bar (56px) at bottom + margins
    // Desktop: island navigator at bottom + some margin
    val verticalPadding = if isMobile then 180 else 160

    val availableWidth = viewportWidth - horizontalPadding
    val availableHeight = viewportHeight - verticalPadding

    // Calculate zoom to fit width and height
    val zoomForWidth = availableWidth.toDouble / gridWidth
    val zoomForHeight = availableHeight.toDouble / gridHeight

    // Use the smaller zoom to ensure it fits in both dimensions
    // Clamp to reasonable bounds
    val optimalZoom = math.min(zoomForWidth, zoomForHeight)
    math.max(MinZoom, math.min(MaxZoom, optimalZoom))

  /** Calculate the offset needed to center on the current island.
    * For a 3x5 island grid, centers on the middle of the grid (row 2, col 1).
    * Returns pan offset in screen pixels.
    */
  def calculateCenterOffset(game: TileKingdomGame): (Double, Double) =
    // Center on the island grid center (middle of 3x5 grid)
    // Island.Height = 5, Island.Width = 3, so center is (2, 1)
    val centerRow = (Island.Height - 1) / 2.0  // 2.0
    val centerCol = (Island.Width - 1) / 2.0   // 1.0
    val viewportWidth = window.innerWidth
    val viewportHeight = window.innerHeight
    val zoom = zoomLevel.now()

    // Check if mobile (viewport width <= 768px)
    val isMobile = viewportWidth <= 768

    // On mobile, offset the center to account for top bar + nav at top and action bar at bottom
    // Top: 48px + 44px = 92px, Bottom: 56px
    // So shift the visual center down by (92 - 56) / 2 = 18px
    val verticalOffset = if isMobile then -18 else 0

    // World center in pixels, then multiply by zoom to get screen pixels
    val targetX = viewportWidth / 2 - (centerCol + 0.5) * BaseTileSize * zoom
    val targetY = (viewportHeight / 2 - verticalOffset) - (centerRow + 0.5) * BaseTileSize * zoom
    (math.round(targetX).toDouble, math.round(targetY).toDouble)

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
      panOffset.set((math.round(newX).toDouble, math.round(newY).toDouble))

      if progress < 1.0 then
        window.requestAnimationFrame((_: Double) => animate())

    animate()

  /** Check if the island grid is visible and snap back if not */
  def snapBackIfNeeded(game: TileKingdomGame, onSnap: () => Unit): Unit =
    val viewportWidth = window.innerWidth
    val viewportHeight = window.innerHeight
    val zoom = zoomLevel.now()
    val screenTileSize = BaseTileSize * zoom
    val (panX, panY) = panOffset.now()

    // Check if any part of the 3x5 island grid is visible
    val margin = screenTileSize * 0.5
    val gridRight = Island.Width * screenTileSize + panX
    val gridBottom = Island.Height * screenTileSize + panY

    val islandVisible =
      panX < viewportWidth - margin &&       // Left edge not too far right
      gridRight > margin &&                  // Right edge not too far left
      panY < viewportHeight - margin &&      // Top edge not too far down
      gridBottom > margin                    // Bottom edge not too far up

    if !islandVisible then
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
    panOffset.set((math.round(newPanX).toDouble, math.round(newPanY).toDouble))

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


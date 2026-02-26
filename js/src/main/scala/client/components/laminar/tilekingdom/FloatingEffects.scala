package client.components.laminar.tilekingdom

import org.scalajs.dom
import org.scalajs.dom.{window, HTMLElement}
import shared.TileKingdom.*

/** Floating effects for tile animations.
  *
  * Creates floating reward numbers, level-up text, and bureau projectiles.
  * These are imperative effects that need to be triggered from the game loop.
  */
object FloatingEffects:

  /** Calculate tile center position in pixels */
  private def tileCenterPosition(coord: Coord, yOffset: Double): (Double, Double) =
    val zoom = TileGridState.zoomLevel.now()
    val tileSize = TileGridState.BaseTileSize * zoom
    val tilePixelSize = 70 * zoom
    val centerX = coord.col * tileSize + tilePixelSize / 2
    val centerY = coord.row * tileSize + tilePixelSize / 2 + yOffset
    (centerX, centerY)

  /** Position an element at the given pixel coordinates */
  extension (element: HTMLElement)
    private def positionAt(x: Double, y: Double): Unit =
      element.style.left = s"${x}px"
      element.style.top = s"${y}px"

  /** Create and show a centered floating element at a tile position */
  private def showCenteredFloater(
    gridElement: dom.Element,
    coord: Coord,
    className: String,
    text: String,
    yOffset: Double
  ): Unit =
    val (centerX, centerY) = tileCenterPosition(coord, yOffset)
    val floater = dom.document.createElement("div").asInstanceOf[HTMLElement]
    floater.className = className
    floater.textContent = text
    floater.positionAt(centerX, centerY)
    floater.style.transform = "translate(-50%, -50%)"
    gridElement.appendChild(floater)
    window.setTimeout(() => floater.remove(), 1000)

  /** Show a floating reward/cost number above a tile
    *
    * @param gridElement The grid container element
    * @param coord The tile coordinate
    * @param amount The number to display
    * @param emoji The resource emoji
    * @param isSpend Whether this is a cost (red) or gain (green)
    * @param offsetIndex Vertical offset for stacking multiple effects
    */
  def showFloatingReward(
    gridElement: dom.Element,
    coord: Coord,
    amount: Int,
    emoji: String,
    isSpend: Boolean,
    offsetIndex: Int
  ): Unit =
    if TileGridState.isDragging then return // Skip during pan

    val className = if isSpend then "floating-reward floating-spend" else "floating-reward"
    val sign = if isSpend then "-" else "+"
    val text = s"$sign${TileUtils.formatNumber(amount)}$emoji"
    showCenteredFloater(gridElement, coord, className, text, offsetIndex * 18)

  /** Show a floating level number above a tile */
  def showFloatingLevel(
    gridElement: dom.Element,
    coord: Coord,
    level: Int
  ): Unit =
    showCenteredFloater(gridElement, coord, "floating-reward floating-level", s"Level $level", 0)

  /** Show a bureau projectile animation from one tile to another */
  def showBureauProjectile(
    gridElement: dom.Element,
    fromCoord: Coord,
    toCoord: Coord,
    onComplete: () => Unit
  ): Unit =
    if TileGridState.isDragging then
      onComplete()
      return

    val (fromX, fromY) = tileCenterPosition(fromCoord, 0)
    val (toX, toY) = tileCenterPosition(toCoord, 0)
    val offset = 12 // Center the projectile element

    val projectile = dom.document.createElement("div").asInstanceOf[HTMLElement]
    projectile.className = "bureau-projectile"
    projectile.textContent = "📜"

    projectile.positionAt(fromX - offset, fromY - offset)
    gridElement.appendChild(projectile)

    // Animate to target after brief delay
    window.setTimeout(() => projectile.positionAt(toX - offset, toY - offset), 20)

    // Complete animation
    window.setTimeout(() =>
      projectile.classList.add("arrived")
      onComplete()
      window.setTimeout(() => projectile.remove(), 200)
    , 420)


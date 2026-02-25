package client.components.laminar.tilekingdom

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{window, HTMLElement}
import shared.TileKingdom.*

/** Floating effects for tile animations.
  *
  * Creates floating reward numbers, level-up text, and bureau projectiles.
  * These are imperative effects that need to be triggered from the game loop.
  */
object FloatingEffects:

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
    emoji: String = "",
    isSpend: Boolean = false,
    offsetIndex: Int = 0
  ): Unit =
    if TileGridState.isDragging then return // Skip during pan

    // Calculate tile position in the grid
    val zoom = TileGridState.zoomLevel.now()
    val tileSize = TileGridState.BaseTileSize * zoom
    val tilePixelSize = 70 * zoom

    // Position at center of tile
    val centerX = coord.col * tileSize + tilePixelSize / 2
    val centerY = coord.row * tileSize + tilePixelSize / 2 + (offsetIndex * 18)

    val floater = dom.document.createElement("div").asInstanceOf[HTMLElement]
    floater.className = if isSpend then "floating-reward floating-spend" else "floating-reward"
    val sign = if isSpend then "-" else "+"
    floater.textContent = s"$sign${TileUtils.formatNumber(amount)}$emoji"

    // Position absolutely within the grid
    floater.style.left = s"${centerX}px"
    floater.style.top = s"${centerY}px"
    floater.style.transform = "translate(-50%, -50%)"

    gridElement.appendChild(floater)
    window.setTimeout(() => floater.remove(), 1000)

  /** Show a floating level number above a tile */
  def showFloatingLevel(
    gridElement: dom.Element,
    coord: Coord,
    level: Int
  ): Unit =
    // Calculate tile position in the grid
    val zoom = TileGridState.zoomLevel.now()
    val tileSize = TileGridState.BaseTileSize * zoom
    val tilePixelSize = 70 * zoom

    // Position at center of tile
    val centerX = coord.col * tileSize + tilePixelSize / 2
    val centerY = coord.row * tileSize + tilePixelSize / 2

    val floater = dom.document.createElement("div").asInstanceOf[HTMLElement]
    floater.className = "floating-reward floating-level"
    floater.textContent = s"Level $level"

    // Position absolutely within the grid
    floater.style.left = s"${centerX}px"
    floater.style.top = s"${centerY}px"
    floater.style.transform = "translate(-50%, -50%)"

    gridElement.appendChild(floater)
    window.setTimeout(() => floater.remove(), 1000)

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

    val zoom = TileGridState.zoomLevel.now()
    val tileSize = TileGridState.BaseTileSize * zoom
    val tilePixelSize = 70 * zoom

    // Calculate pixel positions (center of tiles)
    val fromX = fromCoord.col * tileSize + tilePixelSize / 2 - 12
    val fromY = fromCoord.row * tileSize + tilePixelSize / 2 - 12
    val toX = toCoord.col * tileSize + tilePixelSize / 2 - 12
    val toY = toCoord.row * tileSize + tilePixelSize / 2 - 12

    val projectile = dom.document.createElement("div").asInstanceOf[HTMLElement]
    projectile.className = "bureau-projectile"
    projectile.textContent = "📜"

    projectile.style.left = s"${fromX}px"
    projectile.style.top = s"${fromY}px"

    gridElement.appendChild(projectile)

    // Animate to target after brief delay
    window.setTimeout(() =>
      projectile.style.left = s"${toX}px"
      projectile.style.top = s"${toY}px"
    , 20)

    // Complete animation
    window.setTimeout(() =>
      projectile.classList.add("arrived")
      onComplete()
      window.setTimeout(() => projectile.remove(), 200)
    , 420)


package client.components.laminar.tilekingdom.tiles

import com.raquo.laminar.api.L.*
import shared.TileKingdom.*
import client.components.laminar.tilekingdom.{TileGridState, TileUtils, ProgressBar}

/** Shared tile component building blocks.
  *
  * Provides reusable helpers for tile wrappers, click handlers, upgrade rows, and badges.
  * Use these to reduce duplication across tile components.
  */
object TileComponents:

  /** Common actions for upgradeable tiles */
  case class UpgradeActions(
    onLevelUp: () => Unit,
    onBulkLevelUp: Int => Unit,
    onDestroy: () => Unit
  )

  /** Common actions for non-upgradeable tiles */
  case class BasicActions(
    onDestroy: () => Unit
  )

  /** Base tile wrapper with common structure */
  def tileWrapper(
    coord: Coord,
    tileType: String,
    level: Option[Int] = None,
    extraCls: Signal[String] = Val("")
  )(content: Modifier[HtmlElement]*): HtmlElement =
    div(
      idAttr := TileUtils.tileId(coord),
      cls := s"tile-kingdom-tile unlocked $tileType",
      cls <-- TileGridState.zoomTierClass.combineWith(extraCls).map:
        case (zoom, extra) => s"$zoom $extra".trim,
      level.map(l => dataAttr("level") := l.toString),
      styleAttr <-- TileGridState.tileStyle(coord),
      content
    )

  /** Standard click handler for upgradeable tiles (level up) */
  def clickToLevelUp(actions: UpgradeActions): Modifier[HtmlElement] =
    onClick --> { _ =>
      if !TileGridState.wasDragging then actions.onLevelUp()
    }

  /** Standard right-click destroy handler */
  def destroyHandler(onDestroy: () => Unit): Modifier[HtmlElement] =
    onContextMenu --> { e =>
      e.preventDefault()
      onDestroy()
    }

  /** Destroy handler from UpgradeActions */
  def destroyHandler(actions: UpgradeActions): Modifier[HtmlElement] =
    destroyHandler(actions.onDestroy)

  /** Upgrade row with x10 button */
  def upgradeRow(
    cost: Int,
    costEmoji: String,
    level: Int,
    onBulkLevelUp: Int => Unit
  ): HtmlElement =
    div(
      cls := "tile-upgrade-row",
      span(cls := "tile-upgrade", s"⬆${TileUtils.formatNumber(cost)}$costEmoji"),
      button(
        cls := "btn-x10",
        "x10",
        onClick --> { e =>
          e.stopPropagation()
          onBulkLevelUp(TileUtils.levelsToNextTen(level))
        }
      )
    )

  /** Reactive upgrade row (for tiles with dynamic costs) */
  def upgradeRowSignal(
    costSignal: Signal[Int],
    costEmoji: String,
    level: Int,
    onBulkLevelUp: Int => Unit
  ): HtmlElement =
    div(
      cls := "tile-upgrade-row",
      span(
        cls := "tile-upgrade",
        child.text <-- costSignal.map(c => s"⬆${TileUtils.formatNumber(c)}$costEmoji")
      ),
      button(
        cls := "btn-x10",
        "x10",
        onClick --> { e =>
          e.stopPropagation()
          onBulkLevelUp(TileUtils.levelsToNextTen(level))
        }
      )
    )

  /** Badge for multiplier bonuses (town hall, wisdom, etc.) - tooltip required */
  def multiplierBadge(
    multiplierSignal: Signal[Double],
    emoji: String,
    badgeClass: String,
    tooltip: String
  ): Modifier[HtmlElement] =
    child.maybe <-- multiplierSignal.map: mult =>
      Option.when(mult > 1.0):
        val text = if mult % 1.0 == 0 then s"x${mult.toInt}" else f"x$mult%.1f"
        span(cls := s"tile-badge $badgeClass", title := tooltip, s"$emoji$text")

  /** Badge for percentage bonuses (farm boost, forest group, etc.) - tooltip required */
  def percentBadge(
    bonusSignal: Signal[Double],
    emoji: String,
    badgeClass: String,
    tooltip: String
  ): Modifier[HtmlElement] =
    child.maybe <-- bonusSignal.map: bonus =>
      Option.when(bonus > 1.0):
        val percent = ((bonus - 1) * 100).toInt
        span(cls := s"tile-badge $badgeClass", title := tooltip, s"$emoji+$percent%")

  /** Badge for static/skill bonuses that are either shown or hidden */
  def staticBadge(
    showSignal: Signal[Boolean],
    text: String,
    badgeClass: String,
    tooltip: String
  ): Modifier[HtmlElement] =
    child.maybe <-- showSignal.map: show =>
      Option.when(show):
        span(cls := s"tile-badge $badgeClass", title := tooltip, text)

  /** Progress signal for a tile coordinate */
  def progressSignal(coord: Coord): Signal[Double] =
    TileGridState.tileProgress.signal.map(_.getOrElse(coord, 0.0))

  /** Level label element - inherits color from tile-specific CSS */
  def levelLabel(level: Int): HtmlElement =
    div(cls := "tile-label", s"Lv$level")


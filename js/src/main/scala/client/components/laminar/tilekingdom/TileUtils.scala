package client.components.laminar.tilekingdom

import shared.TileKingdom.Resource

/** Shared utility functions for tile grid components. */
object TileUtils:

  /** Format large numbers compactly (e.g. 1.2k, 3.4M, 5.6B) */
  def formatNumber(n: Double): String =
    val absN = math.abs(n)
    val sign = if n < 0 then "-" else ""
    if absN >= 1_000_000_000 then
      val v = absN / 1_000_000_000
      f"$sign$v%.1fB"
    else if absN >= 1_000_000 then
      val v = absN / 1_000_000
      f"$sign$v%.1fM"
    else if absN >= 10_000 then
      val v = absN / 1_000
      f"$sign$v%.1fk"
    else if absN >= 1 then s"$sign${absN.toInt}"
    else if absN > 0 then f"$sign$absN%.1f"
    else "0"

  /** Format number for integer values */
  def formatNumber(n: Int): String = formatNumber(n.toDouble)

  /** Get emoji for a resource type */
  def resourceEmoji(resource: Resource): String = resource match
    case Resource.Wheat => "🌾"
    case Resource.Wood  => "🪵"
    case Resource.Faith => "✨"
    case Resource.Gold  => "💰"
    case Resource.Stone => "🪨"

  /** Calculate levels needed to reach next multiple of 10 */
  def levelsToNextTen(currentLevel: Int): Int =
    val remainder = currentLevel % 10
    if remainder == 0 then 10 else 10 - remainder


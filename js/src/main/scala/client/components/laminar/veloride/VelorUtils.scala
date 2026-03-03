package client.components.laminar.veloride

/** Shared utility functions for Velor Idle UI components */
object VelorUtils:

  /** Format a number with k/M suffixes for display */
  def formatNumber(n: Long): String =
    if n >= 1_000_000 then f"${n / 1_000_000.0}%.1fM"
    else if n >= 1_000 then f"${n / 1_000.0}%.1fk"
    else n.toString

  /** Format a number with k/M suffixes (Double overload) */
  def formatNumber(n: Double): String = formatNumber(n.toLong)

  /** Format a number with k/M suffixes (Int overload) */
  def formatNumber(n: Int): String = formatNumber(n.toLong)


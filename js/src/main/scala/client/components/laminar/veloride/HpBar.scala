package client.components.laminar.veloride

import com.raquo.laminar.api.L.*

/** Reusable HP bar component with ghost bar for damage chunk effect */
object HpBar:

  /**
   * Create an HP bar with ghost bar effect
   * @param hpSignal Signal of current HP value
   * @param maxHpSignal Signal of max HP value
   * @param isPlayer true for green player bar, false for red enemy bar
   * @param showLabel optional label to show before HP numbers (e.g. "❤️")
   */
  def apply(
    hpSignal: Signal[Int],
    maxHpSignal: Signal[Int],
    isPlayer: Boolean,
    showLabel: Option[String] = None
  ): HtmlElement =
    val colorClass = if isPlayer then "player" else "enemy"
    val widthSignal = hpSignal.combineWith(maxHpSignal).map { case (hp, maxHp) =>
      if maxHp > 0 then s"${(hp.toDouble / maxHp * 100).max(0)}%" else "0%"
    }

    div(
      cls := "velor-hp-bar-container",
      // Ghost bar (delayed, shows damage chunks)
      div(
        cls := s"velor-hp-bar-ghost $colorClass",
        width <-- widthSignal
      ),
      // Main HP bar
      div(
        cls := s"velor-hp-bar $colorClass",
        width <-- widthSignal
      ),
      // HP text
      div(
        cls := "velor-hp-text",
        child.text <-- hpSignal.combineWith(maxHpSignal).map { case (hp, maxHp) =>
          showLabel.map(l => s"$l $hp / $maxHp").getOrElse(s"$hp / $maxHp")
        }
      )
    )

  /**
   * Simplified version that takes a single signal containing (currentHp, maxHp)
   */
  def fromTuple(
    hpTupleSignal: Signal[(Int, Int)],
    isPlayer: Boolean,
    showLabel: Option[String] = None
  ): HtmlElement =
    val hpSignal = hpTupleSignal.map(_._1)
    val maxHpSignal = hpTupleSignal.map(_._2)
    apply(hpSignal, maxHpSignal, isPlayer, showLabel)


package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import shared.VelorIdle.*

/** Floating reward indicators that appear when actions complete */
object FloatingRewards:

  private case class FloatingReward(id: Int, content: String, cssClass: String)

  private val rewardsVar: Var[Vector[FloatingReward]] = Var(Vector.empty)
  private var nextId = 0

  /** Show a floating XP reward */
  def showXp(amount: Int): Unit =
    show(s"+$amount XP", "xp")

  /** Show a floating item reward */
  def showItem(item: Item, count: Long): Unit =
    val text = if count > 1 then s"+$count ${Item.icon(item)}" else s"+${Item.icon(item)}"
    show(text, "item")

  /** Show a floating gold reward */
  def showGold(amount: Long): Unit =
    show(s"+$amount 💰", "gold")

  private def show(content: String, cssClass: String, durationMs: Int = 1500): Unit =
    val id = nextId
    nextId += 1
    val reward = FloatingReward(id, content, cssClass)
    rewardsVar.update(_ :+ reward)

    // Auto-remove after animation completes
    dom.window.setTimeout(
      () => rewardsVar.update(_.filterNot(_.id == id)),
      durationMs
    )

  /** Container element - place this inside the action progress area */
  def container(): HtmlElement =
    div(
      cls := "velor-floating-rewards",
      children <-- rewardsVar.signal.map(_.map(renderReward))
    )

  private def renderReward(reward: FloatingReward): HtmlElement =
    div(
      cls := s"velor-floating-reward ${reward.cssClass}",
      reward.content
    )


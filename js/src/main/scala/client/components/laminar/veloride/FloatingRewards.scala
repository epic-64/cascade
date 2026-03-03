package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import shared.VelorIdle.*

/** Floating reward indicators that appear when actions complete */
object FloatingRewards:
  
  private case class FloatingReward(id: Int, content: String, cssClass: String, curveDirection: Int, spawnTime: Long)
  
  private val rewardsVar: Var[Vector[FloatingReward]] = Var(Vector.empty)
  private var nextId = 0
  private val random = new scala.util.Random()
  
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
    // Random curve: -40 to +40 pixels horizontal movement
    val curveDirection = random.nextInt(81) - 40
    val spawnTime = System.currentTimeMillis()
    
    val reward = FloatingReward(id, content, cssClass, curveDirection, spawnTime)
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
      children <-- rewardsVar.signal.map { rewards =>
        rewards.zipWithIndex.map { case (reward, index) => 
          renderReward(reward, index)
        }
      }
    )
  
  private def renderReward(reward: FloatingReward, index: Int): HtmlElement =
    // Each reward gets a vertical offset based on its position in the list
    val yOffset = index * 24  // 24px spacing between rewards
    div(
      cls := s"velor-floating-reward ${reward.cssClass}",
      styleAttr := s"--curve-x: ${reward.curveDirection}px; --y-offset: ${yOffset}px",
      reward.content
    )


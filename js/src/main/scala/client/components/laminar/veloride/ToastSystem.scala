package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import shared.VelorIdle.*

/** Toast notification system for Velor Idle */
object ToastSystem:
  
  private val toastsVar: Var[Vector[Toast]] = Var(Vector.empty)
  private var nextId = 0
  
  case class Toast(id: Int, message: String, toastType: ToastType)
  
  enum ToastType:
    case Normal, LevelUp, RareDrop
  
  def show(message: String, toastType: ToastType = ToastType.Normal, durationMs: Int = 3000): Unit =
    val id = nextId
    nextId += 1
    val toast = Toast(id, message, toastType)
    toastsVar.update(_ :+ toast)
    
    // Auto-remove after duration
    dom.window.setTimeout(
      () => toastsVar.update(_.filterNot(_.id == id)),
      durationMs
    )
  
  def showLevelUp(skill: Skill, newLevel: Int): Unit =
    show(s"🎉 ${Skill.displayName(skill)} reached level $newLevel!", ToastType.LevelUp, 4000)
  
  def showRareDrop(item: Item): Unit =
    show(s"✨ Rare drop: ${Item.displayName(item)}!", ToastType.RareDrop, 4000)
  
  def container(): HtmlElement =
    div(
      cls := "velor-toast-container",
      children <-- toastsVar.signal.map(_.map(renderToast))
    )
  
  private def renderToast(toast: Toast): HtmlElement =
    val extraCls = toast.toastType match
      case ToastType.Normal => ""
      case ToastType.LevelUp => " level-up"
      case ToastType.RareDrop => " level-up"
    
    div(
      cls := s"velor-toast$extraCls",
      toast.message
    )


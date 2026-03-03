package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*
import VelorIdleState.ViewMode

/** Bottom navigation bar for mobile */
object BottomNav:

  def apply(): HtmlElement =
    div(
      cls := "velor-bottom-nav",
      navButton("⚔️", "Skills", VelorIdleState.ViewMode.SkillSelect),
      navButton("📦", "Items", VelorIdleState.ViewMode.Inventory),
      navButton("👤", "Char", VelorIdleState.ViewMode.Character),
      navButton("🏪", "Shop", VelorIdleState.ViewMode.Shop),
      navButton("⚙️", "More", VelorIdleState.ViewMode.Settings)
    )

  private def navButton(icon: String, label: String, mode: VelorIdleState.ViewMode): HtmlElement =
    val isActive = VelorIdleState.viewModeSignal.map:
      case m if m == mode => true
      case VelorIdleState.ViewMode.SkillTraining if mode == VelorIdleState.ViewMode.SkillSelect => true
      case _ => false
    
    button(
      cls <-- isActive.map(active => if active then "velor-nav-btn active" else "velor-nav-btn"),
      onClick --> { _ => VelorIdleState.setViewMode(mode) },
      div(cls := "velor-nav-btn-icon", icon),
      div(label)
    )


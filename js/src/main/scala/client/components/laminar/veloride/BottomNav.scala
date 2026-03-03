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
      navButton("🧪", "Potions", VelorIdleState.ViewMode.Potions),
      navButton("⚙️", "Settings", VelorIdleState.ViewMode.Settings)
    )

  private def navButton(icon: String, label: String, mode: VelorIdleState.ViewMode): HtmlElement =
    val isActive = VelorIdleState.viewModeSignal.map:
      case m if m == mode => true
      case VelorIdleState.ViewMode.SkillTraining if mode == VelorIdleState.ViewMode.SkillSelect => true
      case VelorIdleState.ViewMode.Shop if mode == VelorIdleState.ViewMode.Settings => true  // Shop redirects to settings
      case _ => false
    
    button(
      cls <-- isActive.map(active => if active then "velor-nav-btn active" else "velor-nav-btn"),
      onClick --> { _ => VelorIdleState.setViewMode(mode) },
      div(cls := "velor-nav-btn-icon", icon),
      div(label)
    )


package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*
import VelorIdleState.ViewMode

/** Bottom navigation bar for mobile */
object BottomNav:

  def apply(): HtmlElement =
    div(
      cls := "velor-bottom-nav",
      navButton("⚔️", "Skills", ViewMode.Skills),
      navButton("📦", "Inventory", ViewMode.Inventory),
      navButton("🏪", "Shop", ViewMode.Shop),
      navButton("⚙️", "Settings", ViewMode.Settings)
    )

  private def navButton(icon: String, label: String, mode: ViewMode): HtmlElement =
    val isActive = VelorIdleState.viewModeSignal.map(_ == mode)
    
    button(
      cls <-- isActive.map(active => if active then "velor-nav-btn active" else "velor-nav-btn"),
      onClick --> { _ => VelorIdleState.setViewMode(mode) },
      div(cls := "velor-nav-btn-icon", icon),
      div(label)
    )


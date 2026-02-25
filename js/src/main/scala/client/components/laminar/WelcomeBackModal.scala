package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based welcome back modal for TileKingdom.
  *
  * Shows offline progress when the player returns.
  */
object WelcomeBackModal:

  private val isVisibleVar: Var[Boolean] = Var(false)
  private val contentVar: Var[Option[WelcomeContent]] = Var(None)

  case class WelcomeContent(
    wheatGain: Int,
    woodGain: Int,
    faithGain: Int,
    timeAway: String
  )

  /** Show the welcome back modal with the given content */
  def show(wheatGain: Int, woodGain: Int, faithGain: Int, offlineSeconds: Double): Unit =
    val timeAway =
      if offlineSeconds >= 3600 then f"${offlineSeconds / 3600}%.1f hours"
      else if offlineSeconds >= 60 then f"${offlineSeconds / 60}%.0f minutes"
      else f"${offlineSeconds}%.0f seconds"

    contentVar.set(Some(WelcomeContent(wheatGain, woodGain, faithGain, timeAway)))
    isVisibleVar.set(true)

  /** Hide the welcome back modal */
  def hide(): Unit =
    isVisibleVar.set(false)

  /** The welcome back modal element */
  def apply(): HtmlElement =
    div(
      idAttr := "tile-kingdom-welcome-modal",
      cls := "welcome-modal",
      cls <-- isVisibleVar.signal.map(visible => if visible then "show" else ""),
      div(
        cls := "welcome-modal-content",
        div(
          cls := "welcome-modal-header",
          h3("👑 Welcome Back!")
        ),
        div(
          cls := "welcome-modal-body",
          children <-- contentVar.signal.map:
            case Some(content) => renderContent(content)
            case None => List.empty
        ),
        button(
          cls := "btn-primary welcome-modal-close",
          "Continue",
          onClick --> { _ => hide() }
        )
      )
    )

  private def renderContent(content: WelcomeContent): List[HtmlElement] =
    val resources = List(
      Option.when(content.wheatGain > 0)(p(cls := "welcome-resource", s"🌾 ${content.wheatGain} wheat")),
      Option.when(content.woodGain > 0)(p(cls := "welcome-resource", s"🪵 ${content.woodGain} wood")),
      Option.when(content.faithGain > 0)(p(cls := "welcome-resource", s"✨ ${content.faithGain} faith"))
    ).flatten
    
    List(
      p(cls := "welcome-time", s"You were away for ${content.timeAway}"),
      p(cls := "welcome-resources", "While you were gone, you earned:")
    ) ++ resources


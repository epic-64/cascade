package client.components.laminar

import com.raquo.laminar.api.L.*

/** Laminar-based welcome back modal for TileKingdom.
  *
  * Shows offline progress when the player returns.
  */
object WelcomeBackModal:

  private val isVisibleVar: Var[Boolean] = Var(false)
  private val contentVar: Var[Option[WelcomeContent]] = Var(None)

  case class IncomeChange(oldRate: Double, newRate: Double)
  
  case class TileBuilds(
    wheatFields: Int = 0,
    woodcutters: Int = 0,
    quarries: Int = 0,
    temples: Int = 0
  ):
    def nonEmpty: Boolean = wheatFields > 0 || woodcutters > 0 || quarries > 0 || temples > 0

  case class WelcomeContent(
    wheatGain: Int,
    woodGain: Int,
    faithGain: Int,
    stoneGain: Int,
    timeAway: String,
    tileBuilds: TileBuilds = TileBuilds(),
    wheatIncome: Option[IncomeChange] = None,
    woodIncome: Option[IncomeChange] = None,
    stoneIncome: Option[IncomeChange] = None,
    faithIncome: Option[IncomeChange] = None
  )

  /** Format a number for display */
  private def formatNumber(n: Double): String =
    if n >= 1_000_000 then f"${n / 1_000_000}%.1fM"
    else if n >= 1_000 then f"${n / 1_000}%.1fk"
    else if n == n.toInt then n.toInt.toString
    else f"$n%.1f"

  /** Show the welcome back modal with the given content */
  def show(
    wheatGain: Int,
    woodGain: Int,
    faithGain: Int,
    stoneGain: Int,
    offlineSeconds: Double,
    tileBuilds: TileBuilds = TileBuilds(),
    wheatIncome: Option[IncomeChange] = None,
    woodIncome: Option[IncomeChange] = None,
    stoneIncome: Option[IncomeChange] = None,
    faithIncome: Option[IncomeChange] = None
  ): Unit =
    val timeAway =
      if offlineSeconds >= 3600 then f"${offlineSeconds / 3600}%.1f hours"
      else if offlineSeconds >= 60 then f"${offlineSeconds / 60}%.0f minutes"
      else f"${offlineSeconds}%.0f seconds"

    contentVar.set(Some(WelcomeContent(
      wheatGain, woodGain, faithGain, stoneGain, timeAway,
      tileBuilds, wheatIncome, woodIncome, stoneIncome, faithIncome
    )))
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
      Option.when(content.wheatGain > 0)(p(cls := "welcome-resource", s"🌾 +${formatNumber(content.wheatGain)} wheat")),
      Option.when(content.woodGain > 0)(p(cls := "welcome-resource", s"🪵 +${formatNumber(content.woodGain)} wood")),
      Option.when(content.faithGain > 0)(p(cls := "welcome-resource", s"✨ +${formatNumber(content.faithGain)} faith")),
      Option.when(content.stoneGain > 0)(p(cls := "welcome-resource", s"🪨 +${formatNumber(content.stoneGain)} stone"))
    ).flatten

    val tileBuildsSection = if content.tileBuilds.nonEmpty then
      val builds = List(
        Option.when(content.tileBuilds.wheatFields > 0)(s"🌾 ${content.tileBuilds.wheatFields} wheat fields"),
        Option.when(content.tileBuilds.woodcutters > 0)(s"🌲 ${content.tileBuilds.woodcutters} woodcutters"),
        Option.when(content.tileBuilds.quarries > 0)(s"🪨 ${content.tileBuilds.quarries} quarries"),
        Option.when(content.tileBuilds.temples > 0)(s"⛪ ${content.tileBuilds.temples} temples")
      ).flatten
      List(
        p(cls := "welcome-section-label", "Buildings constructed:"),
        div(cls := "welcome-tile-builds", builds.map(b => p(cls := "welcome-tile-build", b)))
      )
    else List.empty

    val incomeChanges = List(
      content.wheatIncome.filter(i => i.oldRate > 0 || i.newRate > 0).map(i => ("🌾", i)),
      content.woodIncome.filter(i => i.oldRate > 0 || i.newRate > 0).map(i => ("🪵", i)),
      content.stoneIncome.filter(i => i.oldRate > 0 || i.newRate > 0).map(i => ("🪨", i)),
      content.faithIncome.filter(i => i.oldRate > 0 || i.newRate > 0).map(i => ("✨", i))
    ).flatten

    val incomeSection = if incomeChanges.nonEmpty then
      List(
        p(cls := "welcome-section-label", "Income rates:"),
        div(cls := "welcome-income-changes",
          incomeChanges.map: (emoji, change) =>
            p(cls := "welcome-income-change",
              s"$emoji ${formatNumber(change.oldRate)}/s → ${formatNumber(change.newRate)}/s"
            )
        )
      )
    else List.empty
    
    List(
      p(cls := "welcome-time", s"You were away for ${content.timeAway}"),
      p(cls := "welcome-subtitle", "Your kingdom progressed while you were gone:")
    ) ++ 
    (if resources.nonEmpty then List(p(cls := "welcome-section-label", "Resources earned:")) ++ resources else List.empty) ++
    tileBuildsSection ++
    incomeSection


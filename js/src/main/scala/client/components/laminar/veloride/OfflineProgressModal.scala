package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Modal that displays detailed offline progress information */
object OfflineProgressModal:

  private val isOpenVar: Var[Boolean] = Var(false)
  private val resultVar: Var[Option[OfflineProgress.OfflineResult]] = Var(None)
  private val hoursSkippedVar: Var[Int] = Var(0)

  /** Show the modal with offline progress results */
  def show(hours: Int, result: OfflineProgress.OfflineResult): Unit =
    hoursSkippedVar.set(hours)
    resultVar.set(Some(result))
    isOpenVar.set(true)

  /** Hide the modal */
  def hide(): Unit =
    isOpenVar.set(false)

  /** The modal element - should be rendered once at the app level */
  def apply(): HtmlElement =
    div(
      cls <-- isOpenVar.signal.map(open => if open then "velor-modal-overlay show" else "velor-modal-overlay"),
      onClick --> { e =>
        if e.target == e.currentTarget then hide()
      },
      div(
        cls := "velor-modal velor-offline-modal",
        div(
          cls := "velor-modal-header",
          child.text <-- hoursSkippedVar.signal.map(h => s"⏰ Offline Progress (${h}h)"),
          button(
            cls := "velor-modal-close",
            "✕",
            onClick --> { _ => hide() }
          )
        ),
        div(
          cls := "velor-modal-body",
          child <-- resultVar.signal.map:
            case Some(result) => renderContent(result)
            case None => div("No data")
        )
      )
    )

  private def renderContent(result: OfflineProgress.OfflineResult): HtmlElement =
    div(
      cls := "velor-offline-content",

      // Summary section
      renderSummarySection(result),

      // Skill Level Ups
      if result.skillLevelUps.nonEmpty then renderSkillLevelUpsSection(result.skillLevelUps) else emptyNode,

      // Action Level Ups
      if result.actionLevelUps.nonEmpty then renderActionLevelUpsSection(result.actionLevelUps) else emptyNode,

      // Items Gained
      if result.itemsGained.nonEmpty then renderItemsSection("📦 Items Gained", result.itemsGained, "gained") else emptyNode,

      // Equipment Crafted
      renderEquipmentCraftedSection(result),

      // Items Consumed
      if result.itemsConsumed.nonEmpty then renderItemsSection("🔥 Items Consumed", result.itemsConsumed, "consumed") else emptyNode,

      // Gold
      if result.goldGained > 0 then renderGoldSection(result.goldGained) else emptyNode,

      // Combat stats (if applicable)
      renderCombatSection(result)
    )

  private def renderSummarySection(result: OfflineProgress.OfflineResult): HtmlElement =
    val totalXp = result.xpGained.values.sum
    val totalActionXp = result.actionXpGained.values.sum

    div(
      cls := "velor-offline-section",
      h4("📊 Summary"),
      div(
        cls := "velor-offline-summary-grid",
        if totalXp > 0 then
          div(
            cls := "velor-offline-stat",
            span(cls := "stat-label", "Skill XP"),
            span(cls := "stat-value xp", s"+${formatNumber(totalXp)}")
          )
        else emptyNode,
        if totalActionXp > 0 then
          div(
            cls := "velor-offline-stat",
            span(cls := "stat-label", "Action XP"),
            span(cls := "stat-value xp", s"+${formatNumber(totalActionXp)}")
          )
        else emptyNode,
        if result.itemsGained.nonEmpty then
          div(
            cls := "velor-offline-stat",
            span(cls := "stat-label", "Items Gained"),
            span(cls := "stat-value gained", s"+${formatNumber(result.itemsGained.values.sum)}")
          )
        else emptyNode,
        if result.itemsConsumed.nonEmpty then
          div(
            cls := "velor-offline-stat",
            span(cls := "stat-label", "Items Used"),
            span(cls := "stat-value consumed", s"-${formatNumber(result.itemsConsumed.values.sum)}")
          )
        else emptyNode
      )
    )

  private def renderSkillLevelUpsSection(levelUps: Map[Skill, Int]): HtmlElement =
    div(
      cls := "velor-offline-section",
      h4("🎉 Skill Level Ups"),
      div(
        cls := "velor-offline-list",
        levelUps.toSeq.sortBy(-_._2).map { case (skill, levels) =>
          div(
            cls := "velor-offline-row level-up",
            span(cls := "row-icon", Skill.icon(skill)),
            span(cls := "row-name", Skill.displayName(skill)),
            span(cls := "row-value", s"+$levels levels")
          )
        }
      )
    )

  private def renderActionLevelUpsSection(levelUps: Map[String, Int]): HtmlElement =
    div(
      cls := "velor-offline-section",
      h4("⭐ Action Level Ups"),
      div(
        cls := "velor-offline-list",
        levelUps.toSeq.sortBy(-_._2).map { case (actionId, levels) =>
          val actionName = getActionName(actionId)
          div(
            cls := "velor-offline-row level-up",
            span(cls := "row-name", actionName),
            span(cls := "row-value", s"+$levels levels")
          )
        }
      )
    )

  private def renderItemsSection(title: String, items: Map[Item, Long], cssClass: String): HtmlElement =
    div(
      cls := "velor-offline-section",
      h4(title),
      div(
        cls := "velor-offline-list",
        items.toSeq.sortBy(-_._2).map { case (item, count) =>
          div(
            cls := s"velor-offline-row $cssClass",
            span(cls := "row-icon", Item.icon(item)),
            span(cls := "row-name", Item.displayName(item)),
            span(cls := "row-value", if cssClass == "consumed" then s"-${formatNumber(count)}" else s"+${formatNumber(count)}")
          )
        }
      )
    )

  private def renderGoldSection(gold: Long): HtmlElement =
    div(
      cls := "velor-offline-section",
      h4("💰 Gold"),
      div(
        cls := "velor-offline-row gold",
        span(cls := "row-icon", "💰"),
        span(cls := "row-name", "Gold earned"),
        span(cls := "row-value", s"+${formatNumber(gold)}")
      )
    )

  private def renderEquipmentCraftedSection(result: OfflineProgress.OfflineResult): HtmlElement =
    // Extract equipment crafted events
    val craftedEquipment = result.events.collect:
      case GameEvent.EquipmentCrafted(defId, quality, rarity, _) => (defId, quality, rarity)
    
    if craftedEquipment.isEmpty then return div()
    
    // Group by (defId, quality, rarity) and count
    val grouped = craftedEquipment.groupBy(identity).view.mapValues(_.size).toSeq
      .sortBy { case ((defId, _, _), count) => -count }
    
    div(
      cls := "velor-offline-section",
      h4("⚔️ Equipment Crafted"),
      div(
        cls := "velor-offline-list",
        grouped.map { case ((defId, quality, rarity), count) =>
          val equipDef = EquipmentDefs.byId.get(defId)
          val name = equipDef.map(_.name).getOrElse(defId)
          val icon = equipDef.map(_.icon).getOrElse("⚔️")
          val qualityStr = quality match
            case EquipmentQuality.Normal => ""
            case EquipmentQuality.Superior => "Superior "
          val rarityStr = rarity match
            case EquipmentRarity.Normal => ""
            case EquipmentRarity.Magical => "Magical "
          val displayName = s"$qualityStr$rarityStr$name"
          
          div(
            cls := s"velor-offline-row equipment rarity-${rarity.toString.toLowerCase}",
            span(cls := "row-icon", icon),
            span(cls := "row-name", displayName),
            span(cls := "row-value", s"×$count")
          )
        }
      )
    )

  private def renderCombatSection(result: OfflineProgress.OfflineResult): HtmlElement =
    val enemiesDefeated = result.events.count:
      case GameEvent.AdventureEnemyDefeated(_) => true
      case _ => false
    
    val playerDeaths = result.events.count:
      case GameEvent.AdventurePlayerDied => true
      case _ => false
    
    if enemiesDefeated > 0 || playerDeaths > 0 then
      div(
        cls := "velor-offline-section",
        h4("⚔️ Combat"),
        div(
          cls := "velor-offline-list",
          if enemiesDefeated > 0 then
            div(
              cls := "velor-offline-row combat",
              span(cls := "row-icon", "💀"),
              span(cls := "row-name", "Enemies defeated"),
              span(cls := "row-value", enemiesDefeated.toString)
            )
          else div(),
          if playerDeaths > 0 then
            div(
              cls := "velor-offline-row combat death",
              span(cls := "row-icon", "☠️"),
              span(cls := "row-name", "Times defeated"),
              span(cls := "row-value", playerDeaths.toString)
            )
          else div()
        )
      )
    else div()

  private def getActionName(actionId: String): String =
    // Try to find the action name from all action types
    GatheringActions.woodcutting.find(_.id == actionId).map(_.name)
      .orElse(GatheringActions.mining.find(_.id == actionId).map(_.name))
      .orElse(GatheringActions.fishing.find(_.id == actionId).map(_.name))
      .orElse(GatheringActions.herbalism.find(_.id == actionId).map(_.name))
      .orElse(ProcessingActions.cooking.find(_.id == actionId).map(_.name))
      .orElse(ProcessingActions.smithing.find(_.id == actionId).map(_.name))
      .orElse(ProcessingActions.alchemy.find(_.id == actionId).map(_.name))
      .orElse(ProcessingActions.summoning.find(_.id == actionId).map(_.name))
      .orElse(ThievingActions.targets.find(_.id == actionId).map(_.name))
      .orElse(EquipmentCraftingActions.all.find(_.id == actionId).map(_.name))
      .getOrElse(actionId)

  private def formatNumber(n: Long): String =
    if n >= 1_000_000 then f"${n / 1_000_000.0}%.1fM"
    else if n >= 1_000 then f"${n / 1_000.0}%.1fK"
    else n.toString


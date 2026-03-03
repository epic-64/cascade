package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Tablet equipment panel - equip summoning tablets for passive bonuses and synergies */
object TabletPanel:

  def apply(onEquipTablet: (Item, Int) => Unit, onUnequipTablet: Int => Unit): HtmlElement =
    div(
      cls := "velor-tablet-panel",

      // Header
      div(
        cls := "velor-skill-card",
        h3("📜 Summoning Tablets"),
        p(cls := "velor-text-muted", "Equip tablets for passive bonuses. Two tablets can create synergies!")
      ),

      // Equipped tablets and synergy display
      equippedTablets(onUnequipTablet),

      // Synergy display
      synergyDisplay(),

      // Available tablets from inventory
      availableTablets(onEquipTablet),

      // Synergy reference guide
      synergyReference()
    )

  private def equippedTablets(onUnequipTablet: Int => Unit): HtmlElement =
    div(
      cls := "velor-tablet-slots-section",
      h4("Equipped Tablets"),
      div(
        cls := "velor-tablet-slots-container",
        // Slot 1
        tabletSlot(1, onUnequipTablet),
        // Slot 2
        tabletSlot(2, onUnequipTablet)
      )
    )

  private def tabletSlot(slot: Int, onUnequipTablet: Int => Unit): HtmlElement =
    val slotSignal = VelorIdleState.tabletSlotsSignal.map: slots =>
      if slot == 1 then slots.slot1 else slots.slot2

    val isLockedSignal = VelorIdleState.gameSignal.map: game =>
      if slot == 2 then
        val summoningLevel = game.skills.getOrElse(Skill.Summoning, SkillState.initial).level
        !game.tabletSlots.isSlot2Unlocked(summoningLevel)
      else false

    div(
      cls := "velor-tablet-slot",
      div(
        cls := "velor-tablet-slot-header",
        span(s"Slot $slot"),
        child <-- isLockedSignal.map: locked =>
          if locked then span(cls := "velor-text-muted", " 🔒 Lv.25")
          else emptyNode
      ),
      child <-- slotSignal.combineWith(isLockedSignal).map:
        case (_, true) =>
          div(
            cls := "velor-tablet-slot-empty locked",
            span(cls := "velor-text-muted", "Unlock at Summoning Lv.25")
          )
        case (None, false) =>
          div(
            cls := "velor-tablet-slot-empty",
            span(cls := "velor-text-muted", "Empty")
          )
        case (Some(equipped), false) =>
          div(
            cls := "velor-tablet-slot-content",
            div(
              cls := "velor-tablet-slot-icon",
              Item.icon(equipped.item)
            ),
            div(
              cls := "velor-tablet-slot-info",
              div(cls := "velor-tablet-slot-name", Item.displayName(equipped.item)),
              div(cls := "velor-tablet-slot-effect", TabletType.description(equipped.tabletType)),
              div(
                cls := "velor-tablet-slot-charges",
                s"${equipped.actionsRemaining} actions left"
              )
            ),
            button(
              cls := "btn btn-danger btn-sm",
              "Remove",
              onClick --> { _ => onUnequipTablet(slot) }
            )
          )
    )

  private def synergyDisplay(): HtmlElement =
    div(
      cls := "velor-synergy-section",
      h4("Active Synergy"),
      child <-- VelorIdleState.tabletSlotsSignal.map: slots =>
        slots.activeSynergy match
          case None =>
            div(
              cls := "velor-no-synergy",
              span(cls := "velor-text-muted", "Equip two compatible tablets to activate a synergy")
            )
          case Some(synergy) =>
            div(
              cls := "velor-synergy-card",
              div(
                cls := "velor-synergy-header",
                span(cls := "velor-synergy-icon", "✨"),
                span(cls := "velor-synergy-name", SynergyEffect.displayName(synergy))
              ),
              div(
                cls := "velor-synergy-effect",
                SynergyEffect.description(synergy)
              )
            )
    )

  private def availableTablets(onEquipTablet: (Item, Int) => Unit): HtmlElement =
    div(
      cls := "velor-available-tablets-section",
      h4("Inventory Tablets"),
      children <-- VelorIdleState.inventorySignal.map: inventory =>
        val tabletItems = inventory.slots.flatten
          .filter(stack => TabletType.isTablet(stack.item))

        if tabletItems.isEmpty then
          Vector(
            div(
              cls := "velor-no-tablets",
              span(cls := "velor-text-muted", "No tablets in inventory. Create some with Summoning!")
            )
          )
        else
          tabletItems.map: stack =>
            tabletCard(stack.item, stack.count, onEquipTablet)
    )

  private def tabletCard(tablet: Item, count: Long, onEquipTablet: (Item, Int) => Unit): HtmlElement =
    val tabletType = TabletType.fromItem(tablet)

    div(
      cls := "velor-tablet-card",
      div(
        cls := "velor-tablet-card-header",
        span(cls := "velor-tablet-icon", Item.icon(tablet)),
        span(cls := "velor-tablet-name", Item.displayName(tablet)),
        span(cls := "velor-tablet-count", s"x$count")
      ),
      tabletType.map: tt =>
        div(
          cls := "velor-tablet-effect-desc",
          TabletType.description(tt),
          div(
            cls := "velor-tablet-duration",
            s"Lasts ${TabletType.consumptionRate(tt)} actions per charge"
          )
        )
      .getOrElse(emptyNode),
      div(
        cls := "velor-tablet-equip-buttons",
        button(
          cls := "btn btn-primary btn-sm",
          disabled <-- VelorIdleState.tabletSlotsSignal.map(_.slot1.isDefined),
          "Equip Slot 1",
          onClick --> { _ => onEquipTablet(tablet, 1) }
        ),
        button(
          cls := "btn btn-primary btn-sm",
          disabled <-- VelorIdleState.gameSignal.map: game =>
            val summoningLevel = game.skills.getOrElse(Skill.Summoning, SkillState.initial).level
            !game.tabletSlots.isSlot2Unlocked(summoningLevel) || game.tabletSlots.slot2.isDefined,
          "Equip Slot 2",
          onClick --> { _ => onEquipTablet(tablet, 2) }
        )
      )
    )

  private def synergyReference(): HtmlElement =
    val synergies = Vector(
      ("Gatherer", "Miner", SynergyEffect.EarthAffinity),
      ("Gatherer", "Fisher", SynergyEffect.NaturesBounty),
      ("Gatherer", "Lumberjack", SynergyEffect.ForestSpirit),
      ("Miner", "Artisan", SynergyEffect.Metalworker),
      ("Fisher", "Artisan", SynergyEffect.SeaChef),
      ("Herbalist", "Alchemist", SynergyEffect.PotionMaster),
      ("Artisan", "Alchemist", SynergyEffect.EfficientBrewer),
      ("Thief", "Stargazer", SynergyEffect.ShadowWalker),
      ("Lumberjack", "Herbalist", SynergyEffect.GroveKeeper)
    )
    
    div(
      cls := "velor-synergy-reference-section",
      h4("Synergy Guide"),
      div(
        cls := "velor-synergy-reference-list",
        synergies.map: (t1, t2, synergy) =>
          div(
            cls := "velor-synergy-reference-item",
            div(
              cls := "velor-synergy-reference-tablets",
              span(t1), span(" + "), span(t2)
            ),
            div(
              cls := "velor-synergy-reference-name",
              SynergyEffect.displayName(synergy)
            ),
            div(
              cls := "velor-synergy-reference-effect",
              SynergyEffect.description(synergy)
            )
          )
        ,
        div(
          cls := "velor-synergy-reference-item velor-synergy-master",
          div(
            cls := "velor-synergy-reference-tablets",
            span("Any"), span(" + "), span("Master")
          ),
          div(
            cls := "velor-synergy-reference-name",
            SynergyEffect.displayName(SynergyEffect.MasteryBoost)
          ),
          div(
            cls := "velor-synergy-reference-effect",
            SynergyEffect.description(SynergyEffect.MasteryBoost)
          )
        )
      )
    )


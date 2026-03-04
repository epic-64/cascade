package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Equipment panel for equipping/unequipping weapons and armor */
object EquipmentPanel:

  def apply(
    onEquipWeapon: Long => Unit,
    onUnequipWeapon: () => Unit,
    onEquipArmor: Long => Unit,
    onUnequipArmor: () => Unit,
    onSellEquipment: Long => Unit
  ): HtmlElement =
    div(
      cls := "velor-equipment-panel",

      // Currently equipped items section
      equippedSection(onUnequipWeapon, onUnequipArmor),

      // Equipment inventory section
      inventorySection(onEquipWeapon, onEquipArmor, onSellEquipment)
    )

  private def equippedSection(
    onUnequipWeapon: () => Unit,
    onUnequipArmor: () => Unit
  ): HtmlElement =
    val equipmentSignal = VelorIdleState.adventureStateSignal.map(_.equipment).distinct

    div(
      cls := "velor-equipped-section",
      h4("Equipped"),
      div(
        cls := "velor-equipped-slots",
        // Weapon slot
        child <-- equipmentSignal.map { eq =>
          equippedSlot("Weapon", "⚔️", eq.weapon, onUnequipWeapon)
        },
        // Armor slot
        child <-- equipmentSignal.map { eq =>
          equippedSlot("Armor", "🛡️", eq.armor, onUnequipArmor)
        }
      ),
      // Stats summary
      child <-- equipmentSignal.map(statsSummary)
    )

  private def equippedSlot(
    slotName: String,
    emptyIcon: String,
    equipment: Option[EquipmentInstance],
    onUnequip: () => Unit
  ): HtmlElement =
    equipment match
      case None =>
        div(
          cls := "velor-equipment-slot empty",
          div(cls := "velor-equipment-slot-icon", emptyIcon),
          div(cls := "velor-equipment-slot-name", s"No $slotName")
        )
      case Some(eq) =>
        val def_ = eq.definition
        div(
          cls := s"velor-equipment-slot ${EquipmentRarity.cssClass(eq.rarity)}",
          div(cls := "velor-equipment-slot-icon", def_.map(_.icon).getOrElse("❓")),
          div(
            cls := "velor-equipment-slot-info",
            div(cls := "velor-equipment-slot-name", eq.displayName),
            div(cls := "velor-equipment-slot-stats", formatStats(eq))
          ),
          button(
            cls := "velor-equipment-unequip-btn",
            "✕",
            onClick --> { _ => onUnequip() }
          )
        )

  private def formatStats(eq: EquipmentInstance): String =
    val parts = scala.collection.mutable.ArrayBuffer[String]()
    if eq.attackDamage > 0 then parts += s"+${eq.attackDamage} Atk"
    if eq.defense > 0 then parts += s"+${eq.defense} Def"
    if eq.maxHpBonus > 0 then parts += s"+${eq.maxHpBonus} HP"
    if eq.maxManaBonus > 0 then parts += s"+${eq.maxManaBonus} MP"
    parts.mkString(", ")

  private def statsSummary(equipment: EquipmentSlots): HtmlElement =
    div(
      cls := "velor-equipment-stats-summary",
      span(cls := "velor-stat", s"⚔️ +${equipment.totalAttackDamage}"),
      span(cls := "velor-stat", s"🛡️ +${equipment.totalDefense}"),
      span(cls := "velor-stat", s"❤️ +${equipment.totalMaxHpBonus}"),
      if equipment.totalMaxManaBonus > 0 then
        span(cls := "velor-stat", s"💧 +${equipment.totalMaxManaBonus}")
      else
        emptyMod
    )

  private def inventorySection(
    onEquipWeapon: Long => Unit,
    onEquipArmor: Long => Unit,
    onSellEquipment: Long => Unit
  ): HtmlElement =
    val equipmentInvSignal = VelorIdleState.gameSignal.map(_.equipmentInventory).distinct

    div(
      cls := "velor-equipment-inventory-section",
      h4("Equipment Inventory"),
      child <-- equipmentInvSignal.map { items =>
        if items.isEmpty then
          div(cls := "velor-no-equipment", "No equipment. Craft some in Smithing!")
        else
          div(
            cls := "velor-equipment-inventory-list",
            items.map { eq =>
              equipmentItem(eq, onEquipWeapon, onEquipArmor, onSellEquipment)
            }
          )
      }
    )

  private def equipmentItem(
    eq: EquipmentInstance,
    onEquipWeapon: Long => Unit,
    onEquipArmor: Long => Unit,
    onSellEquipment: Long => Unit
  ): HtmlElement =
    val def_ = eq.definition
    val slotType = def_.map(_.slot).getOrElse(EquipmentSlot.Weapon)
    val levelReq = def_.map(_.levelRequired).getOrElse(1)
    val adventureLevel = VelorIdleState.current.skills.getOrElse(Skill.Adventure, SkillState.initial).level
    val canEquip = adventureLevel >= levelReq

    div(
      cls := s"velor-equipment-item ${EquipmentRarity.cssClass(eq.rarity)}",
      div(cls := "velor-equipment-item-icon", def_.map(_.icon).getOrElse("❓")),
      div(
        cls := "velor-equipment-item-info",
        div(cls := "velor-equipment-item-name", eq.displayName),
        div(cls := "velor-equipment-item-stats", formatStats(eq)),
        // Show affixes if magical
        eq.affixes.headOption.map { _ =>
          div(
            cls := "velor-equipment-item-affixes",
            eq.affixes.map(aff => div(MagicalAffix.description(aff))).toSeq
          )
        }.getOrElse(emptyMod),
        if !canEquip then
          div(cls := "velor-equipment-level-req", s"Requires Adventure Lv.$levelReq")
        else
          emptyMod
      ),
      div(
        cls := "velor-equipment-item-actions",
        button(
          cls := "velor-equipment-equip-btn",
          disabled := !canEquip,
          "Equip",
          onClick --> { _ =>
            slotType match
              case EquipmentSlot.Weapon => onEquipWeapon(eq.instanceId)
              case EquipmentSlot.Armor => onEquipArmor(eq.instanceId)
          }
        ),
        button(
          cls := "velor-equipment-sell-btn",
          "Sell",
          onClick --> { _ => onSellEquipment(eq.instanceId) }
        )
      )
    )


package client.components.laminar.veloride

import com.raquo.laminar.api.L.*
import shared.VelorIdle.*

/** Equipment panel for equipping/unequipping weapons and armor */
object EquipmentPanel:

  /** Grouped non-magical equipment: (defId, quality) -> list of instances */
  case class EquipmentGroup(
    defId: String,
    quality: EquipmentQuality,
    items: Vector[EquipmentInstance]
  ):
    def count: Int = items.size
    def first: EquipmentInstance = items.head
    def definition: Option[EquipmentDef] = EquipmentDefs.byId.get(defId)

  /** Grouped magical equipment by defId (for sell all button) */
  case class MagicalEquipmentGroup(
    defId: String,
    items: Vector[EquipmentInstance]
  ):
    def count: Int = items.size
    def definition: Option[EquipmentDef] = EquipmentDefs.byId.get(defId)

  def apply(
    onEquipWeapon: Long => Unit,
    onUnequipWeapon: () => Unit,
    onEquipArmor: Long => Unit,
    onUnequipArmor: () => Unit,
    onSellEquipment: Long => Unit,
    onSellEquipmentBulk: (String, EquipmentQuality) => Unit,
    onSellEquipmentBulkMagical: String => Unit
  ): HtmlElement =
    div(
      cls := "velor-equipment-panel",

      // Currently equipped items section
      equippedSection(onUnequipWeapon, onUnequipArmor),

      // Equipment inventory section
      inventorySection(onEquipWeapon, onEquipArmor, onSellEquipment, onSellEquipmentBulk, onSellEquipmentBulkMagical)
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
          cls := s"velor-equipment-slot ${eq.cssClass}",
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
    onSellEquipment: Long => Unit,
    onSellEquipmentBulk: (String, EquipmentQuality) => Unit,
    onSellEquipmentBulkMagical: String => Unit
  ): HtmlElement =
    val equipmentInvSignal = VelorIdleState.gameSignal.map(_.equipmentInventory).distinct

    div(
      cls := "velor-equipment-inventory-section",
      h4("Equipment Inventory"),
      child <-- equipmentInvSignal.map { items =>
        if items.isEmpty then
          div(cls := "velor-no-equipment", "No equipment. Craft some in Smithing!")
        else
          // Separate by rarity and quality
          val (magicalSuperior, rest1) = items.partition(eq => eq.rarity == EquipmentRarity.Magical && eq.quality == EquipmentQuality.Superior)
          val (magicalNormal, nonMagical) = rest1.partition(_.rarity == EquipmentRarity.Magical)
          
          // Group non-magical by (defId, quality)
          val groupedNonMagical = nonMagical
            .groupBy(eq => (eq.defId, eq.quality))
            .map { case ((defId, quality), eqs) => EquipmentGroup(defId, quality, eqs) }
            .toVector
            .sortBy(g => (g.definition.map(_.tier).getOrElse(0), g.quality.ordinal))
          
          // Group magical (non-superior) by defId for sell-all button
          val groupedMagical = magicalNormal
            .groupBy(_.defId)
            .map { case (defId, eqs) => MagicalEquipmentGroup(defId, eqs) }
            .toVector
            .sortBy(g => g.definition.map(_.tier).getOrElse(0))
          
          div(
            cls := "velor-equipment-inventory-list",
            // Render grouped non-magical items
            groupedNonMagical.map { group =>
              groupedEquipmentItem(group, onEquipWeapon, onEquipArmor, onSellEquipmentBulk)
            },
            // Render grouped magical items (with sell all button per group)
            groupedMagical.map { group =>
              magicalEquipmentGroup(group, onEquipWeapon, onEquipArmor, onSellEquipment, onSellEquipmentBulkMagical)
            },
            // Render superior magical items individually (rare, keep separate)
            magicalSuperior.map { eq =>
              superiorMagicalEquipmentItem(eq, onEquipWeapon, onEquipArmor, onSellEquipment)
            }
          )
      }
    )

  /** Render a group of identical non-magical items with count and Sell All button */
  private def groupedEquipmentItem(
    group: EquipmentGroup,
    onEquipWeapon: Long => Unit,
    onEquipArmor: Long => Unit,
    onSellEquipmentBulk: (String, EquipmentQuality) => Unit
  ): HtmlElement =
    val eq = group.first
    val def_ = group.definition
    val slotType = def_.map(_.slot).getOrElse(EquipmentSlot.Weapon)
    val levelReq = def_.map(_.levelRequired).getOrElse(1)
    val adventureLevel = VelorIdleState.current.skills.getOrElse(Skill.Adventure, SkillState.initial).level
    val canEquip = adventureLevel >= levelReq

    div(
      cls := s"velor-equipment-item ${eq.cssClass}",
      div(cls := "velor-equipment-item-icon", def_.map(_.icon).getOrElse("❓")),
      div(
        cls := "velor-equipment-item-info",
        div(
          cls := "velor-equipment-item-name",
          eq.displayName,
          span(cls := "velor-equipment-count", s" ×${group.count}")
        ),
        div(cls := "velor-equipment-item-stats", formatStats(eq)),
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
          cls := "velor-equipment-sell-all-btn",
          s"Sell All (${group.count})",
          onClick --> { _ => onSellEquipmentBulk(group.defId, group.quality) }
        )
      )
    )

  /** Render a group of magical (non-superior) items with a header showing count and sell-all */
  private def magicalEquipmentGroup(
    group: MagicalEquipmentGroup,
    onEquipWeapon: Long => Unit,
    onEquipArmor: Long => Unit,
    onSellEquipment: Long => Unit,
    onSellEquipmentBulkMagical: String => Unit
  ): HtmlElement =
    val def_ = group.definition
    val defName = def_.map(_.name).getOrElse("Unknown")
    val defIcon = def_.map(_.icon).getOrElse("❓")
    
    div(
      cls := "velor-equipment-group magical",
      // Group header with sell all button
      div(
        cls := "velor-equipment-group-header",
        span(cls := "velor-equipment-group-icon", defIcon),
        span(cls := "velor-equipment-group-name", s"🔮 $defName"),
        span(cls := "velor-equipment-group-count", s"×${group.count}"),
        button(
          cls := "velor-equipment-sell-all-btn",
          s"Sell All (${group.count})",
          onClick --> { _ => onSellEquipmentBulkMagical(group.defId) }
        )
      ),
      // Individual items
      div(
        cls := "velor-equipment-group-items",
        group.items.map { eq =>
          magicalEquipmentItem(eq, onEquipWeapon, onEquipArmor, onSellEquipment)
        }
      )
    )

  /** Render a magical item individually (has unique affixes) */
  private def magicalEquipmentItem(
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
      cls := s"velor-equipment-item ${eq.cssClass}",
      div(cls := "velor-equipment-item-icon", def_.map(_.icon).getOrElse("❓")),
      div(
        cls := "velor-equipment-item-info",
        div(cls := "velor-equipment-item-stats", formatStats(eq)),
        // Show affixes
        div(
          cls := "velor-equipment-item-affixes",
          eq.affixes.map(aff => div(MagicalAffix.description(aff))).toSeq
        ),
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

  /** Render a superior magical item individually (rare, no bulk sell) */
  private def superiorMagicalEquipmentItem(
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
      cls := s"velor-equipment-item ${eq.cssClass}",
      div(cls := "velor-equipment-item-icon", def_.map(_.icon).getOrElse("❓")),
      div(
        cls := "velor-equipment-item-info",
        div(cls := "velor-equipment-item-name", eq.displayName),
        div(cls := "velor-equipment-item-stats", formatStats(eq)),
        // Show affixes
        div(
          cls := "velor-equipment-item-affixes",
          eq.affixes.map(aff => div(MagicalAffix.description(aff))).toSeq
        ),
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


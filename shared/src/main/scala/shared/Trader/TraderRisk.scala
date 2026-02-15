package shared.Trader

import scala.util.Random

/** Risk system for Trader game.
  * 
  * High-value cargo attracts bandits. The risk scales with value per kg:
  * - Gems (40 g/kg) = very high risk
  * - Bulk goods (<1 g/kg) = essentially safe
  * - Mixed cargo = proportionally reduced risk
  */
object TraderRisk:

  // Risk formula constants (can be tuned for balance)
  private val BaseDivisor: Double = 10.0       // Higher = lower risk overall
  private val RiskExponent: Double = 1.5       // Higher = steeper penalty for high-value cargo
  private val EncounterMultiplier: Double = 0.1 // Higher = more frequent encounters
  private val EncounterCap: Double = 0.8       // Maximum encounter chance (80%)

  /** Calculate the risk assessment for current cargo.
    * Uses item base prices (not market prices) for consistent risk calculation.
    */
  def assessRisk(inventory: Inventory): RiskAssessment =
    val totalWeight = inventory.totalWeight
    
    if totalWeight == 0 then
      RiskAssessment.safe
    else
      val totalValue = inventory.items.map { (item, qty) =>
        Item.basePrice(item) * qty
      }.sum
      
      val valuePerKg = totalValue.toDouble / totalWeight
      val riskScore = math.pow(valuePerKg / BaseDivisor, RiskExponent)
      val encounterChance = math.min(riskScore * EncounterMultiplier, EncounterCap)
      
      RiskAssessment(totalValue, totalWeight, valuePerKg, riskScore, encounterChance)

  /** Roll for a bandit encounter during travel.
    * Returns None if no encounter, Some(outcome) if bandits strike.
    */
  def rollEncounter(risk: RiskAssessment, rng: Random): Option[EncounterOutcome] =
    if risk.encounterChance <= 0.0 then None
    else if rng.nextDouble() >= risk.encounterChance then None
    else Some(resolveEncounter(risk, rng))

  /** Determine the outcome of a bandit encounter.
    * 
    * Probability distribution:
    * - 20% Escaped (no loss)
    * - 40% Toll (10-20% cargo value in gold)
    * - 25% Robbery (30-50% of highest-value items)
    * - 15% Devastating Loss (50-80% of all cargo)
    */
  private def resolveEncounter(risk: RiskAssessment, rng: Random): EncounterOutcome =
    val roll = rng.nextInt(100)
    if roll < 20 then
      EncounterOutcome.Escaped
    else if roll < 60 then
      val lossPercent = 0.10 + rng.nextDouble() * 0.10 // 10-20%
      val goldLost = math.max(1, (risk.cargoValue * lossPercent).toInt)
      EncounterOutcome.Toll(goldLost)
    else if roll < 85 then
      EncounterOutcome.Robbery(Map.empty) // Placeholder - calculated in applyEncounter
    else
      EncounterOutcome.DevastatingLoss(Map.empty, 0) // Placeholder - calculated in applyEncounter

  /** Apply encounter outcome to game state.
    * Returns updated game with losses applied and log entry added.
    */
  def applyEncounter(
      game: TraderGame,
      outcome: EncounterOutcome,
      fromCity: CityId,
      toCity: CityId,
      rng: Random
  ): TraderGame =
    outcome match
      case EncounterOutcome.Escaped =>
        val logEntry = "⚔️ Bandits attacked but you escaped unharmed!"
        game.copy(log = (logEntry :: game.log).take(TraderGame.MaxLogEntries))

      case EncounterOutcome.Toll(goldLost) =>
        val actualLoss = math.min(goldLost, game.player.gold)
        val newPlayer = game.player.copy(gold = game.player.gold - actualLoss)
        val logEntry = s"⚔️ Bandits demanded a toll! Lost ${actualLoss}g"
        game.copy(
          player = newPlayer,
          log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
        )

      case EncounterOutcome.Robbery(_) =>
        // Calculate actual robbery: 30-50% of highest-value items
        val lossPercent = 0.30 + rng.nextDouble() * 0.20
        val itemsLost = calculateRobbery(game.player.inventory, lossPercent, rng)
        val newInventory = removeItems(game.player.inventory, itemsLost)
        val newPlayer = game.player.copy(inventory = newInventory)
        val lostItemsDesc = formatItemsLost(itemsLost)
        val logEntry = s"⚔️ Bandits robbed your cargo! Lost $lostItemsDesc"
        game.copy(
          player = newPlayer,
          log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
        )

      case EncounterOutcome.DevastatingLoss(_, _) =>
        // Calculate devastating loss: 50-80% of all cargo + some gold
        val lossPercent = 0.50 + rng.nextDouble() * 0.30
        val itemsLost = calculateDevastatingLoss(game.player.inventory, lossPercent, rng)
        val goldLost = math.min((game.player.gold * 0.2).toInt, game.player.gold)
        val newInventory = removeItems(game.player.inventory, itemsLost)
        val newPlayer = game.player.copy(
          gold = game.player.gold - goldLost,
          inventory = newInventory
        )
        val lostItemsDesc = formatItemsLost(itemsLost)
        val goldDesc = if goldLost > 0 then s" and ${goldLost}g" else ""
        val logEntry = s"⚔️ Devastating bandit attack! Lost $lostItemsDesc$goldDesc"
        game.copy(
          player = newPlayer,
          log = (logEntry :: game.log).take(TraderGame.MaxLogEntries)
        )

  /** Calculate which items are lost in a robbery.
    * Targets highest value-per-kg items first.
    */
  private def calculateRobbery(
      inventory: Inventory,
      lossPercent: Double,
      rng: Random
  ): Map[Item, Int] =
    val itemsByValue = inventory.items.toList
      .sortBy((item, _) => -Item.basePrice(item).toDouble / Item.weight(item))
    
    val totalValue = inventory.items.map((item, qty) => Item.basePrice(item) * qty).sum
    val targetLossValue = (totalValue * lossPercent).toInt
    
    var lostValue = 0
    var result = Map.empty[Item, Int]
    
    itemsByValue.foreach { (item, qty) =>
      if lostValue < targetLossValue && qty > 0 then
        val itemValue = Item.basePrice(item)
        val maxToLose = math.ceil((targetLossValue - lostValue).toDouble / itemValue).toInt
        val qtyToLose = math.min(maxToLose, qty)
        if qtyToLose > 0 then
          result = result.updated(item, qtyToLose)
          lostValue += qtyToLose * itemValue
    }
    
    result

  /** Calculate which items are lost in a devastating attack.
    * Loses a percentage of ALL items.
    */
  private def calculateDevastatingLoss(
      inventory: Inventory,
      lossPercent: Double,
      rng: Random
  ): Map[Item, Int] =
    inventory.items.map { (item, qty) =>
      val qtyToLose = math.max(1, (qty * lossPercent).toInt)
      item -> math.min(qtyToLose, qty)
    }.filter((_, qty) => qty > 0)

  /** Remove items from inventory */
  private def removeItems(inventory: Inventory, itemsToRemove: Map[Item, Int]): Inventory =
    itemsToRemove.foldLeft(inventory) { case (inv, (item, qty)) =>
      inv.remove(item, qty).getOrElse(inv)
    }

  /** Format lost items for log display */
  private def formatItemsLost(items: Map[Item, Int]): String =
    if items.isEmpty then "nothing"
    else items.map((item, qty) => s"$qty ${item.toString}").mkString(", ")

  /** Get risk level description for UI */
  def getRiskLevel(risk: RiskAssessment): String =
    if risk.encounterChance < 0.01 then "Safe"
    else if risk.encounterChance < 0.20 then "Low"
    else if risk.encounterChance < 0.50 then "Moderate"
    else if risk.encounterChance < 0.70 then "High"
    else "Extreme"

  /** Get risk color class for UI styling */
  def getRiskColor(risk: RiskAssessment): String =
    if risk.encounterChance < 0.20 then "risk-safe"
    else if risk.encounterChance < 0.50 then "risk-moderate"
    else "risk-dangerous"


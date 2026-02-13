package client

import org.scalajs.dom
import org.scalajs.dom.*
import shared.Trader.*
import client.*

import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

// LocalStorage key for game persistence
private val TraderStorageKey = "traderGame"

// Current game state
private var traderGame: Option[TraderGame] = None

def initializeTrader(): Unit =
  println("[Trader] Starting Trader game...")

  // Try to load existing game from localStorage
  loadTraderGame() match
    case Some(game) =>
      println("[Trader] Loaded saved game")
      traderGame = Some(game)
    case None =>
      println("[Trader] Starting new game")
      traderGame = Some(TraderLogic.newGame())
      saveTraderGame()

  renderTraderUI()

// ============================================================================
// Persistence

private def saveTraderGame(): Unit =
  traderGame.foreach { game =>
    Try {
      val json = upickle.default.write(game)
      dom.window.localStorage.setItem(TraderStorageKey, json)
    }.recover { case e =>
      println(s"[Trader] Failed to save game: ${e.getMessage}")
    }
  }

private def loadTraderGame(): Option[TraderGame] =
  Try {
    Option(dom.window.localStorage.getItem(TraderStorageKey))
      .filter(_.nonEmpty)
      .map(upickle.default.read[TraderGame](_))
  }.toOption.flatten

private def clearTraderGame(): Unit =
  dom.window.localStorage.removeItem(TraderStorageKey)

// ============================================================================
// UI Rendering

private def renderTraderUI(): Unit =
  val body = document.body
  body.innerHTML = ""

  traderGame match
    case Some(game) =>
      val container = div(cls = "trader-container")(
        renderHeader(game),
        renderMap(game),
        div(cls = "trader-main")(
          renderCarriage(game),
          renderMarket(game)
        ),
        renderTravel(game),
        renderLog(game),
        renderNewGameButton()
      )
      body.appendChild(container)
    case None =>
      body.appendChild(div(content = "Loading..."))

private def renderHeader(game: TraderGame): HTMLElement =
  val seasonClass = s"season-${game.season.toString.toLowerCase}"
  div(cls = "trader-header")(
    span(cls = "trader-title", content = "🏪 TRADER"),
    div(cls = "trader-status")(
      div(cls = "trader-turn")(
        span(content = "Turn: "),
        span(content = game.turn.toString)
      ),
      div(cls = s"trader-season $seasonClass")(
        span(content = "Season: "),
        span(content = game.season.toString)
      )
    )
  )

private def renderMap(game: TraderGame): HTMLElement =
  val travelOptions = TraderLogic.getTravelOptions(game).toMap

  div(cls = "trader-map")(
    h2(content = "City Map"),
    div(cls = "city-grid")(
      // Row 0
      renderCityNode(game, CityId.Northport, travelOptions.get(CityId.Northport)),
      renderCityNode(game, CityId.Ironforge, travelOptions.get(CityId.Ironforge)),
      renderCityNode(game, CityId.Crystalpeak, travelOptions.get(CityId.Crystalpeak)),
      // Row 1
      renderCityNode(game, CityId.Wheatholm, travelOptions.get(CityId.Wheatholm)),
      renderCityNode(game, CityId.Riverdale, travelOptions.get(CityId.Riverdale)),
      renderCityNode(game, CityId.Silkwood, travelOptions.get(CityId.Silkwood)),
      // Row 2
      renderCityNode(game, CityId.Saltmarsh, travelOptions.get(CityId.Saltmarsh)),
      renderCityNode(game, CityId.Vineyard, travelOptions.get(CityId.Vineyard)),
      renderCityNode(game, CityId.Timberfall, travelOptions.get(CityId.Timberfall))
    )
  )

private def renderCityNode(game: TraderGame, cityId: CityId, travelCost: Option[Int]): HTMLElement =
  val city = game.cities(cityId)
  val isCurrent = cityId == game.player.currentCity
  val cls = if isCurrent then "city-node current" else "city-node"

  val node = div(cls = cls)(
    div(cls = "city-name", content = city.name),
    travelCost.map(cost => div(cls = "city-travel-cost", content = s"${cost}g")).getOrElse(div())
  )

  if !isCurrent then
    node.with_click { _ =>
      handleTravel(cityId)
    }

  node

private def renderCarriage(game: TraderGame): HTMLElement =
  val player = game.player
  val capacity = player.carriageCapacity
  val used = player.inventory.totalWeight
  val percentage = (used.toDouble / capacity * 100).toInt
  val fillClass =
    if percentage >= 90 then "capacity-fill full"
    else if percentage >= 70 then "capacity-fill warning"
    else "capacity-fill"

  div(cls = "trader-carriage")(
    h2(content = "🚗 Your Carriage"),
    div(cls = "trader-gold")(
      span(cls = "trader-gold-icon", content = "💰"),
      span(content = s"${player.gold}g")
    ),
    div(cls = "trader-capacity")(
      div(cls = "capacity-text", content = s"Capacity: ${used}kg / ${capacity}kg"),
      div(cls = "capacity-bar")(
        div(cls = fillClass).tap(_.style.width = s"$percentage%")
      )
    ),
    div(cls = "trader-inventory")(
      h3(content = "Inventory"),
      renderInventory(player.inventory)
    ),
    renderUpgradeButton(game)
  )

private def renderInventory(inventory: Inventory): HTMLElement =
  if inventory.items.isEmpty then
    div(cls = "inventory-empty", content = "(empty)")
  else
    div()(
      inventory.items.toSeq.sortBy(_._1.toString).map { case (item, qty) =>
        div(cls = "inventory-item")(
          span(cls = "inventory-item-name", content = item.toString),
          span(cls = "inventory-item-qty", content = s"×$qty (${Item.weight(item) * qty}kg)")
        )
      }*
    )

private def renderUpgradeButton(game: TraderGame): HTMLElement =
  val player = game.player
  if player.carriageLevel >= 8 then
    button(cls = "upgrade-btn", content = "Carriage Maxed Out").tap { btn =>
      btn.disabled = true
    }
  else
    val cost = player.upgradesCost
    val canAfford = player.gold >= cost
    val newCapacity = 200 + player.carriageLevel * 50
    button(cls = "upgrade-btn", content = s"Upgrade to ${newCapacity}kg (${cost}g)").tap { btn =>
      btn.disabled = !canAfford
      if canAfford then btn.with_click(_ => handleUpgrade())
    }

private def renderMarket(game: TraderGame): HTMLElement =
  val city = game.currentCity

  div(cls = "trader-market")(
    h2()(
      span(content = "Market - "),
      span(cls = "market-city-name", content = city.name)
    ),
    el("table", cls = "market-table")(
      el("thead")(
        el("tr")(
          el("th", content = "Item"),
          el("th", content = "Base"),
          el("th", content = "Price"),
          el("th", content = "Factors"),
          el("th", content = "Stock"),
          el("th", content = "Actions")
        )
      ),
      el("tbody")(
        Item.all.map(item => renderMarketRow(game, item))*
      )
    )
  )

private def renderMarketRow(game: TraderGame, item: Item): HTMLElement =
  val city = game.currentCity
  val basePrice = Item.basePrice(item)
  val currentPrice = TraderLogic.calculatePrice(city, item, game.season)
  val stock = game.player.inventory.getQuantity(item)
  val weight = Item.weight(item)

  val supplyLevel = city.market.supply.get(item)
  val demandLevel = city.market.demand.get(item)
  val seasonMod = Season.modifier(game.season, item)

  // Collect price factors
  val factors = List(
    supplyLevel.collect {
      case SupplyLevel.Abundant => ("supply-abundant", "Supply ↑")
      case SupplyLevel.Scarce => ("supply-scarce", "Supply ↓")
    },
    demandLevel.collect {
      case DemandLevel.High => ("demand-high", "Demand ↑")
      case DemandLevel.Low => ("demand-low", "Demand ↓")
    },
    Option.when(seasonMod != 1.0) {
      if seasonMod > 1.0 then ("season-high", s"${game.season} ↑")
      else ("season-low", s"${game.season} ↓")
    }
  ).flatten

  // Determine if price is good for buying (low = good) or selling (high = good)
  // Compare to base price to determine color
  val priceRatio = currentPrice.toDouble / basePrice
  val priceClass = 
    if priceRatio <= 0.75 then "price-very-low"      // Great buy, bad sell
    else if priceRatio <= 0.95 then "price-low"      // Good buy, okay sell  
    else if priceRatio >= 1.3 then "price-very-high" // Bad buy, great sell
    else if priceRatio >= 1.1 then "price-high"      // Okay buy, good sell
    else "price-normal"

  el("tr")(
    el("td")(
      span(cls = "item-name", content = item.toString),
      span(cls = "item-weight", content = s"(${weight}kg)")
    ),
    el("td", cls = "price-base", content = s"${basePrice}g"),
    el("td", cls = s"price-current $priceClass", content = s"${currentPrice}g"),
    el("td", cls = "price-factors")(
      if factors.isEmpty then span(cls = "factor-none", content = "—")
      else div(cls = "factors-list")(
        factors.map { case (cls, label) => 
          span(cls = s"factor-tag $cls", content = label)
        }*
      )
    ),
    el("td", content = if stock > 0 then stock.toString else "-"),
    el("td")(
      div(cls = "trade-actions")(
        button(cls = "trade-btn buy", content = "Buy").tap { btn =>
          val canBuy = game.player.gold >= currentPrice && game.player.availableCapacity >= weight
          btn.disabled = !canBuy
          if canBuy then btn.with_click(_ => handleBuy(item, 1))
        },
        button(cls = "trade-btn sell", content = "Sell").tap { btn =>
          btn.disabled = stock <= 0
          if stock > 0 then btn.with_click(_ => handleSell(item, 1))
        }
      )
    )
  )

private def renderTravel(game: TraderGame): HTMLElement =
  val travelOptions = TraderLogic.getTravelOptions(game)

  div(cls = "trader-travel")(
    h2(content = "Travel To"),
    div(cls = "travel-options")(
      travelOptions.map { case (cityId, cost) =>
        val city = game.cities(cityId)
        val canAfford = game.player.gold >= cost
        button(cls = "travel-btn").tap { btn =>
          btn.innerHTML = s"${city.name} - <span class='cost'>${cost}g</span>"
          btn.disabled = !canAfford
          if canAfford then btn.with_click(_ => handleTravel(cityId))
        }
      }*
    )
  )

private def renderLog(game: TraderGame): HTMLElement =
  div(cls = "trader-log")(
    h2(content = "Log"),
    div(cls = "log-entries")(
      game.log.map(entry => div(cls = "log-entry", content = entry))*
    )
  )

private def renderNewGameButton(): HTMLElement =
  button(cls = "new-game-btn", content = "New Game").with_click { _ =>
    if dom.window.confirm("Start a new game? Your current progress will be lost.") then
      clearTraderGame()
      traderGame = Some(TraderLogic.newGame())
      saveTraderGame()
      renderTraderUI()
  }

// ============================================================================
// Event Handlers

private def handleBuy(item: Item, qty: Int): Unit =
  traderGame.foreach { game =>
    TraderLogic.buyItem(game, item, qty) match
      case Right(newGame) =>
        traderGame = Some(newGame)
        saveTraderGame()
        renderTraderUI()
      case Left(error) =>
        dom.window.alert(error)
  }

private def handleSell(item: Item, qty: Int): Unit =
  traderGame.foreach { game =>
    TraderLogic.sellItem(game, item, qty) match
      case Right(newGame) =>
        traderGame = Some(newGame)
        saveTraderGame()
        renderTraderUI()
      case Left(error) =>
        dom.window.alert(error)
  }

private def handleTravel(destination: CityId): Unit =
  traderGame.foreach { game =>
    TraderLogic.travel(game, destination) match
      case Right(newGame) =>
        traderGame = Some(newGame)
        saveTraderGame()
        renderTraderUI()
      case Left(error) =>
        dom.window.alert(error)
  }

private def handleUpgrade(): Unit =
  traderGame.foreach { game =>
    TraderLogic.upgradeCarriage(game) match
      case Right(newGame) =>
        traderGame = Some(newGame)
        saveTraderGame()
        renderTraderUI()
      case Left(error) =>
        dom.window.alert(error)
  }


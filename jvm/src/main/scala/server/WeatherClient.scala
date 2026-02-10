package server

import scala.util.Try

// Trait to allow mocking in tests
trait WeatherClient:
  def getWeather(city: String): Try[ujson.Value]

// Real implementation that calls an external API
class RealWeatherClient extends WeatherClient:
  def getWeather(city: String): Try[ujson.Value] =
    Try:
      // Using wttr.in - a free weather API that doesn't require API keys
      val response = requests.get(
        s"https://wttr.in/$city?format=j1",
        headers = Map("User-Agent" -> "Cascade/1.0")
      )
      ujson.read(response.text())

// Global instance - can be swapped out in tests
object WeatherClient:
  @volatile private var instance: WeatherClient = new RealWeatherClient()
  
  def getWeather(city: String): Try[ujson.Value] =
    instance.getWeather(city)
  
  // For tests only - swap the implementation
  def setInstance(client: WeatherClient): Unit =
    instance = client
  
  def resetInstance(): Unit =
    instance = new RealWeatherClient()


package server

import scala.util.Try

// Trait to allow mocking in tests
trait WeatherClient:
  def getWeather(city: String): Try[ujson.Value]

// Real implementation that calls an external API
class RealWeatherClient extends WeatherClient:
  // Simple city to coordinates mapping - in production, use a geocoding API
  private val cityCoordinates = Map(
    "london" -> (51.5074, -0.1278),
    "paris" -> (48.8566, 2.3522),
    "newyork" -> (40.7128, -74.0060),
    "tokyo" -> (35.6762, 139.6503),
    "sydney" -> (-33.8688, 151.2093),
    "berlin" -> (52.5200, 13.4050),
    "rome" -> (41.9028, 12.4964),
    "madrid" -> (40.4168, -3.7038),
    "toronto" -> (43.6532, -79.3832),
    "vancouver" -> (49.2827, -123.1207),
    "chicago" -> (41.8781, -87.6298),
    "losangeles" -> (34.0522, -118.2437),
    "sanfrancisco" -> (37.7749, -122.4194),
    "seattle" -> (47.6062, -122.3321),
    "miami" -> (25.7617, -80.1918),
    "boston" -> (42.3601, -71.0589)
  )

  def getWeather(city: String): Try[ujson.Value] =
    Try:
      val normalizedCity = city.toLowerCase.replaceAll("[^a-z]", "")
      val (lat, lon) = cityCoordinates.getOrElse(
        normalizedCity,
        throw new IllegalArgumentException(s"City '$city' not found. Try: ${cityCoordinates.keys.mkString(", ")}")
      )
      
      // Using Open-Meteo API - free weather API that doesn't require API keys
      val response = requests.get(
        s"https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&temperature_unit=celsius",
        headers = Map("User-Agent" -> "Cascade/1.0")
      )
      ujson.read(response.text())

// Provides a given instance that can be swapped for testing
object WeatherClient:
  @volatile private var _instance: WeatherClient = RealWeatherClient()

  // The given delegates to the swappable instance
  given default: WeatherClient with
    def getWeather(city: String): Try[ujson.Value] = _instance.getWeather(city)

  // For tests only - swap the backing implementation
  def setInstance(client: WeatherClient): Unit =
    _instance = client

  def resetInstance(): Unit =
    _instance = RealWeatherClient()


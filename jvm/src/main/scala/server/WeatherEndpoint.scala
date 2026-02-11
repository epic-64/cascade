package server

import org.slf4j.LoggerFactory
import scala.util.{Try, Success, Failure}

object WeatherEndpoint:
  private val logger = LoggerFactory.getLogger(getClass)

  // WMO Weather interpretation codes (WW)
  // https://open-meteo.com/en/docs
  private def weatherCodeToDescription(code: Int): String = code match
    case 0            => "Clear sky"
    case 1 | 2 | 3    => "Partly cloudy"
    case 45 | 48      => "Foggy"
    case 51 | 53 | 55 => "Drizzle"
    case 61 | 63 | 65 => "Rain"
    case 71 | 73 | 75 => "Snow"
    case 77           => "Snow grains"
    case 80 | 81 | 82 => "Rain showers"
    case 85 | 86      => "Snow showers"
    case 95           => "Thunderstorm"
    case 96 | 99      => "Thunderstorm with hail"
    case _            => "Unknown"

  def getWeather(city: String)(using client: WeatherClient): ujson.Value =
    client.getWeather(city) match
      case Success(data) =>
        // Extract relevant info from the Open-Meteo API response
        val current = data("current")
        val weatherCode = current("weather_code").num.toInt
        val tempC = current("temperature_2m").num
        val tempF = (tempC * 9 / 5) + 32

        ujson.Obj(
          "city" -> city,
          "temperature_c" -> tempC.toString,
          "temperature_f" -> f"$tempF%.1f",
          "condition" -> weatherCodeToDescription(weatherCode),
          "humidity" -> current("relative_humidity_2m").num.toString,
          "feelsLike_c" -> current("apparent_temperature").num.toString
        )

      case Failure(ex) =>
        logger.error(s"Failed to fetch weather for $city: ${ex.getMessage}", ex)
        ujson.Obj(
          "error" -> "Failed to fetch weather data",
          "message" -> ex.getMessage
        )

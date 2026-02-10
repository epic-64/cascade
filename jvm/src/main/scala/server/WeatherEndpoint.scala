package server

import org.slf4j.LoggerFactory
import scala.util.{Try, Success, Failure}

object WeatherEndpoint:
  private val logger = LoggerFactory.getLogger(getClass)

  def getWeather(city: String): ujson.Value =
    WeatherClient.getWeather(city) match
      case Success(data) =>
        // Extract relevant info from the API response
        val current = data("current_condition")(0)
        ujson.Obj(
          "city" -> city,
          "temperature_c" -> current("temp_C").str,
          "temperature_f" -> current("temp_F").str,
          "condition" -> current("weatherDesc")(0)("value").str,
          "humidity" -> current("humidity").str,
          "feelsLike_c" -> current("FeelsLikeC").str
        )

      case Failure(ex) =>
        logger.error(s"Failed to fetch weather for $city: ${ex.getMessage}", ex)
        ujson.Obj(
          "error" -> "Failed to fetch weather data",
          "message" -> ex.getMessage
        )


import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import server.{WeatherClient, WeatherEndpoint}
import scala.util.{Success, Failure}

class WeatherEndpointSpec extends AnyFunSuite with TestServerHelper with BeforeAndAfterEach:
  
  override def afterEach(): Unit =
    // Restore the original client after each test
    WeatherClient.resetInstance()
    super.afterEach()

  test("weather endpoint returns weather data when API succeeds"):
    // Given: A mock weather client that returns fake data
    val mockClient = new WeatherClient:
      def getWeather(city: String): scala.util.Try[ujson.Value] =
        Success(ujson.Obj(
          "current_condition" -> ujson.Arr(
            ujson.Obj(
              "temp_C" -> "15",
              "temp_F" -> "59",
              "weatherDesc" -> ujson.Arr(ujson.Obj("value" -> "Partly cloudy")),
              "humidity" -> "65",
              "FeelsLikeC" -> "13"
            )
          )
        ))
    
    // Inject the mock
    WeatherClient.setInstance(mockClient)

    // When: Making a request to the weather endpoint
    val response = requests.get(s"$baseUrl/api/weather/London")
    val json = ujson.read(response.text())

    // Then: The response should contain the mocked weather data
    assert(response.statusCode == 200)
    assert(json("city").str == "London")
    assert(json("temperature_c").str == "15")
    assert(json("temperature_f").str == "59")
    assert(json("condition").str == "Partly cloudy")
    assert(json("humidity").str == "65")
    assert(json("feelsLike_c").str == "13")

  test("weather endpoint returns error when API fails"):
    // Given: A mock weather client that fails
    val mockClient = new WeatherClient:
      def getWeather(city: String): scala.util.Try[ujson.Value] =
        Failure(new RuntimeException("API unavailable"))
    
    // Inject the mock
    WeatherClient.setInstance(mockClient)

    // When: Making a request to the weather endpoint
    val response = requests.get(s"$baseUrl/api/weather/InvalidCity")
    val json = ujson.read(response.text())

    // Then: The response should contain an error message
    assert(response.statusCode == 200) // Still 200, but with error in JSON
    assert(json.obj.contains("error"))
    assert(json("message").str == "API unavailable")

  test("weather endpoint handles different cities"):
    // Given: A mock that echoes the city name in temp
    val mockClient = new WeatherClient:
      def getWeather(city: String): scala.util.Try[ujson.Value] =
        Success(ujson.Obj(
          "current_condition" -> ujson.Arr(
            ujson.Obj(
              "temp_C" -> s"${city.length}",
              "temp_F" -> "0",
              "weatherDesc" -> ujson.Arr(ujson.Obj("value" -> "Sunny")),
              "humidity" -> "50",
              "FeelsLikeC" -> "0"
            )
          )
        ))

    WeatherClient.setInstance(mockClient)

    // When: Making requests for different cities
    val parisResponse = requests.get(s"$baseUrl/api/weather/Paris")
    val tokyoResponse = requests.get(s"$baseUrl/api/weather/Tokyo")

    // Then: Each response should be customized
    val parisJson = ujson.read(parisResponse.text())
    val tokyoJson = ujson.read(tokyoResponse.text())

    assert(parisJson("city").str == "Paris")
    assert(parisJson("temperature_c").str == "5") // "Paris".length

    assert(tokyoJson("city").str == "Tokyo")
    assert(tokyoJson("temperature_c").str == "5") // "Tokyo".length


import org.scalatest.funsuite.AnyFunSuite

class IndexEndpointSpec extends AnyFunSuite with TestServerHelper:

  test("index page returns 200 OK"):
    val response = requests.get(s"$baseUrl/")
    assert(response.statusCode == 200)

  test("index page returns HTML content type"):
    val response = requests.get(s"$baseUrl/")
    assert(response.contentType.exists(_.contains("text/html")))

  test("index page contains Cascade title"):
    val response = requests.get(s"$baseUrl/")
    assert(response.text().contains("<title>Cascade"))

  test("index page contains link to Color Rush"):
    val response = requests.get(s"$baseUrl/")
    assert(response.text().contains("""href="/color-rush""""))

  test("index page contains link to Counter"):
    val response = requests.get(s"$baseUrl/")
    assert(response.text().contains("""href="/counter""""))

  test("index page contains link to Velor Idle"):
    val response = requests.get(s"$baseUrl/")
    assert(response.text().contains("""href="/velor-idle""""))

  test("counter page returns 200 OK"):
    val response = requests.get(s"$baseUrl/counter")
    assert(response.statusCode == 200)

  test("color-rush page returns 200 OK"):
    val response = requests.get(s"$baseUrl/color-rush")
    assert(response.statusCode == 200)


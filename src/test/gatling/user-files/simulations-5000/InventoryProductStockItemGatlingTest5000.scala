import _root_.io.gatling.core.scenario.Simulation
import ch.qos.logback.classic.{Level, LoggerContext}
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Performance test for the Inventory entity.
 */
class InventoryProductStockItemGatlingTest5000 extends Simulation {

    val context: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    // Log all HTTP requests
    //context.getLogger("io.gatling.http").setLevel(Level.valueOf("TRACE"))
    // Log failed HTTP requests
    //context.getLogger("io.gatling.http").setLevel(Level.valueOf("DEBUG"))

    val baseURL = Option(System.getProperty("baseURL")) getOrElse """http://localhost:8080"""

    val httpConf = http
        .baseURL(baseURL)
        .inferHtmlResources()
        .acceptHeader("*/*")
        .acceptEncodingHeader("gzip, deflate")
        .acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
        .connectionHeader("keep-alive")
        .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:33.0) Gecko/20100101 Firefox/33.0")

    val headers_http = Map(
        "Accept" -> """application/json"""
    )

    val headers_http_authenticated = Map(
        "Accept" -> """application/json""",
        "X-XSRF-TOKEN" -> "${xsrf_token}"
    )

    val keycloakHeaders = Map(
        "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Upgrade-Insecure-Requests" -> "1"
    )

		val feeder = csv("barcodes.csv").queue 
		
    val scn = scenario("Test the Inventory entity")
        .exec(http("First unauthenticated request")
        .get("/api/account")
        .headers(headers_http)
        .check(status.is(401))
        .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(10)
				.feed(feeder)
        .exec(http("Authentication")
        .post("/api/authentication")
        .headers(headers_http_authenticated)
        .formParam("j_username", "admin")
        .formParam("j_password", "admin")
        .formParam("remember-me", "true")
        .formParam("submit", "Login")
        .check(headerRegex("Set-Cookie", "XSRF-TOKEN=(.*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(1)
        .exec(http("Authenticated request")
        .get("/api/account")
        .headers(headers_http_authenticated)
        .check(status.is(200)))
        .pause(10)
        .repeat(2) {
            exec(http("Get all inventories")
            .get("/api/inventories")
            .headers(headers_http_authenticated)
            .check(status.is(200)))
            .pause(10 seconds, 20 seconds)
            .exec(http("Get all products")
            .get("/api/products")
            .headers(headers_http_authenticated)
            .check(status.is(200)))
            .pause(10 seconds, 20 seconds)						
            .exec(http("Create new inventory")
            .post("/api/inventories")
            .headers(headers_http_authenticated)
            .body(StringBody("""{"id":null}""")).asJSON
            .check(status.is(201))
            .check(headerRegex("Location", "(.*)").saveAs("new_inventory_url"))
						.check(jsonPath("$..id").find.saveAs("inventory_id"))
						).exitHereIfFailed
            .exec(http("Create new product")
            .post("/api/products")
            .headers(headers_http_authenticated)
            .body(StringBody("""{"id":null, "name":"SAMPLE_TEXT", "barCode":"${barcode}", "price":"0"}""")).asJSON
            .check(status.is(201))
            .check(headerRegex("Location", "(.*)").saveAs("new_product_url"))
						.check(jsonPath("$..id").find.saveAs("product_id"))
						).exitHereIfFailed	
            .exec(http("Create new stockItem")
            .post("/api/stock-items")
            .headers(headers_http_authenticated)
            .body(StringBody("""{"id":null, "amount":"10", "minStock":"10", "maxStock":"10","product": {"id":"${product_id}", "name":"SAMPLE_TEXT", "barCode":"${barcode}", "price":"0"}, "inventory": {"id":"${inventory_id}"}}""")).asJSON
            .check(status.is(201))
            .check(headerRegex("Location", "(.*)").saveAs("new_stockItem_url"))).exitHereIfFailed
            .pause(10)
            .repeat(5) {
                exec(http("Get created stockItem")
                .get("${new_stockItem_url}")
                .headers(headers_http_authenticated))
                .pause(10)
            }
            .exec(http("Delete created stockItem")
            .delete("${new_stockItem_url}")
            .headers(headers_http_authenticated))
            .pause(10)
            .repeat(5) {
                exec(http("Get created inventory")
                .get("${new_inventory_url}")
                .headers(headers_http_authenticated))
                .pause(10)
            }
            .repeat(5) {
                exec(http("Get created product")
                .get("${new_product_url}")
                .headers(headers_http_authenticated))
                .pause(10)
            }						
            .exec(http("Delete created inventory")
            .delete("${new_inventory_url}")
            .headers(headers_http_authenticated))
            .pause(10)
            .exec(http("Delete created product")
            .delete("${new_product_url}")
            .headers(headers_http_authenticated))
            .pause(10)						
        }

    val users = scenario("Users").exec(scn)

    setUp(
        users.inject(rampUsers(Integer.getInteger("users", 5000)) over (Integer.getInteger("ramp", 2) minutes))
    ).protocols(httpConf)
}

package examples.authentication.controllers

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import kotlin.String

public interface DefaultController {
  /**
   * Route is expected to respond with status 200.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param testString 
   * @param call The Ktor application call
   */
  public suspend fun testPath(testString: String, call: ApplicationCall)
}

/**
 * Mounts all routes for the Default resource
 *
 * - GET /default 
 */
public fun Route.defaultRoutes(controller: DefaultController) {
  authenticate("basicAuth", optional = false) {
    `get`("/default") {
      val testString = call.request.queryParameters.getTypedOrFail<kotlin.String>("testString")
      controller.testPath(testString, call)
    }
  }
}

public interface NoneController {
  /**
   * Route is expected to respond with status 200.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param testString 
   * @param call The Ktor application call
   */
  public suspend fun testPath(testString: String, call: ApplicationCall)
}

/**
 * Mounts all routes for the None resource
 *
 * - GET /none 
 */
public fun Route.noneRoutes(controller: NoneController) {
  `get`("/none") {
    val testString = call.request.queryParameters.getTypedOrFail<kotlin.String>("testString")
    controller.testPath(testString, call)
  }
}

public interface OptionalController {
  /**
   * Route is expected to respond with status 200.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param testString 
   * @param call The Ktor application call
   */
  public suspend fun testPath(testString: String, call: ApplicationCall)
}

/**
 * Mounts all routes for the Optional resource
 *
 * - GET /optional 
 */
public fun Route.optionalRoutes(controller: OptionalController) {
  authenticate("BasicAuth", optional = true) {
    `get`("/optional") {
      val testString = call.request.queryParameters.getTypedOrFail<kotlin.String>("testString")
      controller.testPath(testString, call)
    }
  }
}

public interface ProhibitedController {
  /**
   * Route is expected to respond with status 200.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param testString 
   * @param call The Ktor application call
   */
  public suspend fun testPath(testString: String, call: ApplicationCall)
}

/**
 * Mounts all routes for the Prohibited resource
 *
 * - GET /prohibited 
 */
public fun Route.prohibitedRoutes(controller: ProhibitedController) {
  `get`("/prohibited") {
    val testString = call.request.queryParameters.getTypedOrFail<kotlin.String>("testString")
    controller.testPath(testString, call)
  }
}

public interface RequiredController {
  /**
   * Route is expected to respond with status 200.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param testString 
   * @param call The Ktor application call
   */
  public suspend fun testPath(testString: String, call: ApplicationCall)
}

/**
 * Mounts all routes for the Required resource
 *
 * - GET /required 
 */
public fun Route.requiredRoutes(controller: RequiredController) {
  authenticate("BasicAuth", "BearerAuth", optional = false) {
    `get`("/required") {
      val testString = call.request.queryParameters.getTypedOrFail<kotlin.String>("testString")
      controller.testPath(testString, call)
    }
  }
}

package examples.parameterNameClash.controllers

import examples.parameterNameClash.models.SomeObject
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.server.routing.post
import kotlin.String

public interface ExampleController {
  /**
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param pathB 
   * @param queryB 
   * @param call The Ktor application call
   */
  public suspend fun getById(
    pathB: String,
    queryB: String,
    call: ApplicationCall,
  )

  /**
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param bodySomeObject example
   * @param querySomeObject 
   * @param call The Ktor application call
   */
  public suspend fun post(
    querySomeObject: String,
    bodySomeObject: SomeObject?,
    call: ApplicationCall,
  )
}

/**
 * Mounts all routes for the Example resource
 *
 * - GET /example/{b} 
 * - POST /example 
 */
public fun Route.exampleRoutes(controller: ExampleController) {
  `get`("/example/{b}") {
    val pathB = call.parameters.getTypedOrFail<kotlin.String>("b")
    val queryB = call.request.queryParameters.getTypedOrFail<kotlin.String>("b")
    controller.getById(pathB, queryB, call)
  }
  post("/example") {
    val querySomeObject = call.request.queryParameters.getTypedOrFail<kotlin.String>("someObject")
    val bodySomeObject = call.receive<SomeObject>()
    controller.post(querySomeObject, bodySomeObject, call)
  }
}

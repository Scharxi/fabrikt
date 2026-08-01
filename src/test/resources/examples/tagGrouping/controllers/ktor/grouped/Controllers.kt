package examples.tagGrouping.controllers

import examples.tagGrouping.models.Owner
import examples.tagGrouping.models.Pet
import examples.tagGrouping.models.Vehicle
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.dataconversion.conversionService
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import java.util.UUID
import kotlin.Int
import kotlin.collections.List

public interface OwnerController {
  /**
   * List all owners
   *
   * Route is expected to respond with [kotlin.collections.List<examples.tagGrouping.models.Owner>].
   * Use [examples.tagGrouping.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listOwners(call: TypedApplicationCall<List<Owner>>)

  /**
   * Create an owner
   *
   * Route is expected to respond with status 201.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param owner 
   * @param call The Ktor application call
   */
  public suspend fun createOwner(owner: Owner, call: ApplicationCall)

  /**
   * List pets belonging to an owner
   *
   * Route is expected to respond with [kotlin.collections.List<examples.tagGrouping.models.Pet>].
   * Use [examples.tagGrouping.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param ownerId 
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listPetsByOwner(ownerId: UUID, call: TypedApplicationCall<List<Pet>>)
}

/**
 * Mounts all routes for the Owner resource
 *
 * - GET /owners List all owners
 * - POST /owners Create an owner
 * - GET /owners/{ownerId}/pets List pets belonging to an owner
 */
public fun Route.ownerRoutes(controller: OwnerController) {
  `get`("/owners") {
    controller.listOwners(TypedApplicationCall(call))
  }
  post("/owners") {
    val owner = call.receive<Owner>()
    controller.createOwner(owner, call)
  }
  `get`("/owners/{ownerId}/pets") {
    val ownerId = call.parameters.getTypedOrFail<java.util.UUID>("ownerId",
        call.application.conversionService)
    controller.listPetsByOwner(ownerId, TypedApplicationCall(call))
  }
}

public interface PetController {
  /**
   * List all pets
   *
   * Route is expected to respond with [kotlin.collections.List<examples.tagGrouping.models.Pet>].
   * Use [examples.tagGrouping.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param limit 
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listPets(limit: Int?, call: TypedApplicationCall<List<Pet>>)

  /**
   * Create a pet
   *
   * Route is expected to respond with status 201.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param pet 
   * @param call The Ktor application call
   */
  public suspend fun createPet(pet: Pet, call: ApplicationCall)

  /**
   * Get a pet by ID
   *
   * Route is expected to respond with [examples.tagGrouping.models.Pet].
   * Use [examples.tagGrouping.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param petId 
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getPetById(petId: UUID, call: TypedApplicationCall<Pet>)

  /**
   * Delete a pet
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param petId 
   * @param call The Ktor application call
   */
  public suspend fun deletePet(petId: UUID, call: ApplicationCall)
}

/**
 * Mounts all routes for the Pet resource
 *
 * - GET /pets List all pets
 * - POST /pets Create a pet
 * - GET /pets/{petId} Get a pet by ID
 * - DELETE /pets/{petId} Delete a pet
 */
public fun Route.petRoutes(controller: PetController) {
  `get`("/pets") {
    val limit = call.request.queryParameters.getTyped<kotlin.Int>("limit")
    controller.listPets(limit, TypedApplicationCall(call))
  }
  post("/pets") {
    val pet = call.receive<Pet>()
    controller.createPet(pet, call)
  }
  `get`("/pets/{petId}") {
    val petId = call.parameters.getTypedOrFail<java.util.UUID>("petId",
        call.application.conversionService)
    controller.getPetById(petId, TypedApplicationCall(call))
  }
  delete("/pets/{petId}") {
    val petId = call.parameters.getTypedOrFail<java.util.UUID>("petId",
        call.application.conversionService)
    controller.deletePet(petId, call)
  }
}

public interface VehicleController {
  /**
   * List all vehicles (tagged vehicle, alphabetically first verb=get wins)
   *
   * Route is expected to respond with
   * [kotlin.collections.List<examples.tagGrouping.models.Vehicle>].
   * Use [examples.tagGrouping.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listVehicles(call: TypedApplicationCall<List<Vehicle>>)

  /**
   * Create a vehicle (tagged owner, but post > get alphabetically so owner tag does NOT win)
   *
   * Route is expected to respond with status 201.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param vehicle 
   * @param call The Ktor application call
   */
  public suspend fun createVehicle(vehicle: Vehicle, call: ApplicationCall)
}

/**
 * Mounts all routes for the Vehicle resource
 *
 * - GET /vehicles List all vehicles (tagged vehicle, alphabetically first verb=get wins)
 * - POST /vehicles Create a vehicle (tagged owner, but post > get alphabetically so owner tag does
 * NOT win)
 */
public fun Route.vehicleRoutes(controller: VehicleController) {
  `get`("/vehicles") {
    controller.listVehicles(TypedApplicationCall(call))
  }
  post("/vehicles") {
    val vehicle = call.receive<Vehicle>()
    controller.createVehicle(vehicle, call)
  }
}

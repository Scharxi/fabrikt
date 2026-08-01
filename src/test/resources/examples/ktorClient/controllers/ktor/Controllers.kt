package examples.ktorClient.controllers

import examples.ktorClient.models.Item
import examples.ktorClient.models.SortOrder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.dataconversion.conversionService
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.collections.List

public interface CatalogsItemsAvailabilityController {
  /**
   * Check item availability
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param catalogId The ID of the catalog
   * @param itemId The ID of the item
   * @param call The Ktor application call
   */
  public suspend fun `get`(
    catalogId: String,
    itemId: String,
    call: ApplicationCall,
  )

  /**
   * Update item availability
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param catalogId The ID of the catalog
   * @param itemId The ID of the item
   * @param call The Ktor application call
   */
  public suspend fun put(
    catalogId: String,
    itemId: String,
    call: ApplicationCall,
  )
}

/**
 * Mounts all routes for the CatalogsItemsAvailability resource
 *
 * - GET /catalogs/{catalogId}/items/{itemId}/availability Check item availability
 * - PUT /catalogs/{catalogId}/items/{itemId}/availability Update item availability
 */
public fun Route.catalogsItemsAvailabilityRoutes(controller: CatalogsItemsAvailabilityController) {
  `get`("/catalogs/{catalogId}/items/{itemId}/availability") {
    val catalogId = call.parameters.getTypedOrFail<kotlin.String>("catalogId")
    val itemId = call.parameters.getTypedOrFail<kotlin.String>("itemId")
    controller.get(catalogId, itemId, call)
  }
  put("/catalogs/{catalogId}/items/{itemId}/availability") {
    val catalogId = call.parameters.getTypedOrFail<kotlin.String>("catalogId")
    val itemId = call.parameters.getTypedOrFail<kotlin.String>("itemId")
    controller.put(catalogId, itemId, call)
  }
}

public interface CatalogsItemsController {
  /**
   * Create a new item
   *
   * Route is expected to respond with [examples.ktorClient.models.Item].
   * Use [examples.ktorClient.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param item The item to create
   * @param catalogId The ID of the catalog
   * @param xRequestID Unique identifier for the request
   * @param randomNumber Just a test query param
   * @param xTracingID Unique identifier for the tracing
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun createItem(
    xRequestID: String,
    xTracingID: String?,
    catalogId: String,
    randomNumber: Int,
    item: Item,
    call: TypedApplicationCall<Item>,
  )
}

/**
 * Mounts all routes for the CatalogsItems resource
 *
 * - POST /catalogs/{catalogId}/items Create a new item
 */
public fun Route.catalogsItemsRoutes(controller: CatalogsItemsController) {
  post("/catalogs/{catalogId}/items") {
    val catalogId = call.parameters.getTypedOrFail<kotlin.String>("catalogId")
    val xRequestID = call.request.headers.getOrFail("X-Request-ID")
    val xTracingID = call.request.headers["X-Tracing-ID"]
    val randomNumber = call.request.queryParameters.getTypedOrFail<kotlin.Int>("randomNumber")
    val item = call.receive<Item>()
    controller.createItem(xRequestID, xTracingID, catalogId, randomNumber, item,
        TypedApplicationCall(call))
  }
}

public interface CatalogsSearchController {
  /**
   * Search for items
   *
   * Route is expected to respond with [kotlin.collections.List<examples.ktorClient.models.Item>].
   * Use [examples.ktorClient.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param catalogId The ID of the catalog
   * @param query The search query
   * @param page Page number
   * @param sort Sort order
   * @param xTracingID Unique identifier for the tracing
   * @param listParam A list parameter
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun searchCatalogItems(
    xTracingID: String?,
    catalogId: String,
    query: String,
    page: Int?,
    sort: SortOrder?,
    listParam: List<String>?,
    call: TypedApplicationCall<List<Item>>,
  )
}

/**
 * Mounts all routes for the CatalogsSearch resource
 *
 * - GET /catalogs/{catalogId}/search Search for items
 */
public fun Route.catalogsSearchRoutes(controller: CatalogsSearchController) {
  `get`("/catalogs/{catalogId}/search") {
    val catalogId = call.parameters.getTypedOrFail<kotlin.String>("catalogId")
    val xTracingID = call.request.headers["X-Tracing-ID"]
    val query = call.request.queryParameters.getTypedOrFail<kotlin.String>("query")
    val page = call.request.queryParameters.getTyped<kotlin.Int>("page")
    val sort = call.request.queryParameters.getTyped<examples.ktorClient.models.SortOrder>("sort",
        call.application.conversionService)
    val listParam =
        call.request.queryParameters.getTyped<kotlin.collections.List<kotlin.String>>("listParam")
    controller.searchCatalogItems(xTracingID, catalogId, query, page, sort, listParam,
        TypedApplicationCall(call))
  }
}

public interface ItemsController {
  /**
   * Retrieve a list of items
   *
   * Route is expected to respond with [kotlin.collections.List<examples.ktorClient.models.Item>].
   * Use [examples.ktorClient.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param limit Maximum number of items to return
   * @param category Filter items by category
   * @param priceLimit Maximum price of items to return
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getItems(
    limit: Int?,
    category: String?,
    priceLimit: Double?,
    call: TypedApplicationCall<List<Item>>,
  )
}

/**
 * Mounts all routes for the Items resource
 *
 * - GET /items Retrieve a list of items
 */
public fun Route.itemsRoutes(controller: ItemsController) {
  `get`("/items") {
    val limit = call.request.queryParameters.getTyped<kotlin.Int>("limit")
    val category = call.request.queryParameters.getTyped<kotlin.String>("category")
    val priceLimit = call.request.queryParameters.getTyped<kotlin.Double>("priceLimit")
    controller.getItems(limit, category, priceLimit, TypedApplicationCall(call))
  }
}

public interface ItemsSubitemsController {
  /**
   * Retrieve a specific subitem of an item
   *
   * Route is expected to respond with [examples.ktorClient.models.Item].
   * Use [examples.ktorClient.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param itemId The ID of the item
   * @param subItemId The ID of the subitem
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getSubItem(
    itemId: String,
    subItemId: String,
    call: TypedApplicationCall<Item>,
  )
}

/**
 * Mounts all routes for the ItemsSubitems resource
 *
 * - GET /items/{itemId}/subitems/{subItemId} Retrieve a specific subitem of an item
 */
public fun Route.itemsSubitemsRoutes(controller: ItemsSubitemsController) {
  `get`("/items/{itemId}/subitems/{subItemId}") {
    val itemId = call.parameters.getTypedOrFail<kotlin.String>("itemId")
    val subItemId = call.parameters.getTypedOrFail<kotlin.String>("subItemId")
    controller.getSubItem(itemId, subItemId, TypedApplicationCall(call))
  }
}

public interface NoContentController {
  /**
   * Endpoint with no content response
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param call The Ktor application call
   */
  public suspend fun getNoContent(call: ApplicationCall)
}

/**
 * Mounts all routes for the NoContent resource
 *
 * - GET /no-content Endpoint with no content response
 */
public fun Route.noContentRoutes(controller: NoContentController) {
  `get`("/no-content") {
    controller.getNoContent(call)
  }
}

public interface ReservedInvalidWordsController {
  /**
   * Endpoint with reserved or invalid words as query parameter names
   *
   * Route is expected to respond with status 204.
   * Use [io.ktor.server.response.respond] to send the response.
   *
   * @param for what this is for
   * @param if if this should exist
   * @param 1234 is this 1234?
   * @param fun whether this is fun
   * @param when when this is
   * @param call The Ktor application call
   */
  public suspend fun getReservedInvalidWords(
    `for`: String,
    `if`: List<Item>,
    `1234`: Boolean,
    `fun`: Boolean?,
    `when`: List<Item>?,
    call: ApplicationCall,
  )
}

/**
 * Mounts all routes for the ReservedInvalidWords resource
 *
 * - GET /reserved-invalid-words Endpoint with reserved or invalid words as query parameter names
 */
public fun Route.reservedInvalidWordsRoutes(controller: ReservedInvalidWordsController) {
  `get`("/reserved-invalid-words") {
    val for = call.request.queryParameters.getTypedOrFail<kotlin.String>("for")
    val if =
        call.request.queryParameters.getTypedOrFail<kotlin.collections.List<examples.ktorClient.models.Item>>("if",
        call.application.conversionService)
    val 1234 = call.request.queryParameters.getTypedOrFail<kotlin.Boolean>("1234")
    val fun = call.request.queryParameters.getTyped<kotlin.Boolean>("fun")
    val when =
        call.request.queryParameters.getTyped<kotlin.collections.List<examples.ktorClient.models.Item>>("when",
        call.application.conversionService)
    controller.getReservedInvalidWords(for, if, 1234, fun, when, call)
  }
}

public interface UptimeController {
  /**
   * Get the uptime of the system
   *
   * Route is expected to respond with [kotlin.String].
   * Use [examples.ktorClient.controllers.TypedApplicationCall.respondTyped] to send the response.
   *
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun getSystemUptime(call: TypedApplicationCall<String>)
}

/**
 * Mounts all routes for the Uptime resource
 *
 * - GET /uptime Get the uptime of the system
 */
public fun Route.uptimeRoutes(controller: UptimeController) {
  `get`("/uptime") {
    controller.getSystemUptime(TypedApplicationCall(call))
  }
}

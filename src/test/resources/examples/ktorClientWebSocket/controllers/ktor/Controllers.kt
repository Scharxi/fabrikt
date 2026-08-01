package examples.ktorClientWebSocket.controllers

import examples.ktorClientWebSocket.models.ChatCommand
import examples.ktorClientWebSocket.models.ChatEvent
import examples.ktorClientWebSocket.models.Room
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.`get`
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.flow.Flow

public interface RoomsCommandsController {
  /**
   * Push commands to a room without reading anything back
   *
   * Handles the typed WebSocket session for `/rooms/{roomId}/commands`.
   */
  public suspend fun pushRoomCommands(roomId: String, session: PushRoomCommandsSession)

  /**
   * Type safe server session for the `/rooms/{roomId}/commands` WebSocket endpoint.
   */
  public class PushRoomCommandsSession(
    private val session: DefaultWebSocketServerSession,
  ) {
    /**
     * Messages sent by the client, until the session is closed.
     */
    public val incoming: Flow<ChatCommand> = session.incomingMessages()

    /**
     * Closes the session, completing any active message flow.
     */
    public suspend fun close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
      session.close(reason)
    }
  }
}

/**
 * Mounts all routes for the RoomsCommands resource
 *
 * - WEBSOCKET /rooms/{roomId}/commands Push commands to a room without reading anything back
 */
public fun Route.roomsCommandsRoutes(controller: RoomsCommandsController) {
  webSocket("/rooms/{roomId}/commands") {
    val roomId = call.parameters.getTypedOrFail<kotlin.String>("roomId")
    controller.pushRoomCommands(roomId, RoomsCommandsController.PushRoomCommandsSession(this))
  }
}

public interface RoomsController {
  /**
   * List the available chat rooms
   *
   * Route is expected to respond with
   * [kotlin.collections.List<examples.ktorClientWebSocket.models.Room>].
   * Use [examples.ktorClientWebSocket.controllers.TypedApplicationCall.respondTyped] to send the
   * response.
   *
   * @param call Decorated ApplicationCall with additional typed respond methods
   */
  public suspend fun listRooms(call: TypedApplicationCall<List<Room>>)
}

/**
 * Mounts all routes for the Rooms resource
 *
 * - GET /rooms List the available chat rooms
 */
public fun Route.roomsRoutes(controller: RoomsController) {
  `get`("/rooms") {
    controller.listRooms(TypedApplicationCall(call))
  }
}

public interface RoomsEventsController {
  /**
   * Subscribe to the events of a room without sending anything
   *
   * Handles the typed WebSocket session for `/rooms/{roomId}/events`.
   */
  public suspend fun subscribeRoomEvents(roomId: String, session: SubscribeRoomEventsSession)

  /**
   * Type safe server session for the `/rooms/{roomId}/events` WebSocket endpoint.
   */
  public class SubscribeRoomEventsSession(
    private val session: DefaultWebSocketServerSession,
  ) {
    /**
     * Sends a message to the client.
     */
    public suspend fun send(message: ChatEvent) {
      session.sendSerialized(message)
    }

    /**
     * Closes the session, completing any active message flow.
     */
    public suspend fun close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
      session.close(reason)
    }
  }
}

/**
 * Mounts all routes for the RoomsEvents resource
 *
 * - WEBSOCKET /rooms/{roomId}/events Subscribe to the events of a room without sending anything
 */
public fun Route.roomsEventsRoutes(controller: RoomsEventsController) {
  webSocket("/rooms/{roomId}/events") {
    val roomId = call.parameters.getTypedOrFail<kotlin.String>("roomId")
    controller.subscribeRoomEvents(roomId, RoomsEventsController.SubscribeRoomEventsSession(this))
  }
}

public interface RoomsStreamController {
  /**
   * Open a bidirectional stream of chat messages
   *
   * Handles the typed WebSocket session for `/rooms/{roomId}/stream`.
   */
  public suspend fun streamRoom(
    xClientId: String,
    roomId: String,
    since: String?,
    session: StreamRoomSession,
  )

  /**
   * Type safe server session for the `/rooms/{roomId}/stream` WebSocket endpoint.
   */
  public class StreamRoomSession(
    private val session: DefaultWebSocketServerSession,
  ) {
    /**
     * Messages sent by the client, until the session is closed.
     */
    public val incoming: Flow<ChatCommand> = session.incomingMessages()

    /**
     * Sends a message to the client.
     */
    public suspend fun send(message: ChatEvent) {
      session.sendSerialized(message)
    }

    /**
     * Closes the session, completing any active message flow.
     */
    public suspend fun close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
      session.close(reason)
    }
  }
}

/**
 * Mounts all routes for the RoomsStream resource
 *
 * - WEBSOCKET /rooms/{roomId}/stream Open a bidirectional stream of chat messages
 */
public fun Route.roomsStreamRoutes(controller: RoomsStreamController) {
  webSocket("/rooms/{roomId}/stream") {
    val roomId = call.parameters.getTypedOrFail<kotlin.String>("roomId")
    val xClientId = call.request.headers.getOrFail("X-Client-Id")
    val since = call.request.queryParameters.getTyped<kotlin.String>("since")
    controller.streamRoom(xClientId, roomId, since, RoomsStreamController.StreamRoomSession(this))
  }
}

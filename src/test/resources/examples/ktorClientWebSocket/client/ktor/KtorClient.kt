package examples.ktorClientWebSocket.client

import examples.ktorClientWebSocket.models.ChatCommand
import examples.ktorClientWebSocket.models.ChatEvent
import examples.ktorClientWebSocket.models.Room
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.`get`
import io.ktor.client.request.`header`
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.io.IOException
import kotlin.String
import kotlin.Unit
import kotlin.collections.List

public class RoomsClient(
    private val httpClient: HttpClient,
) {
    /**
     * List the available chat rooms
     *
     *
     * Returns:
     * 	[NetworkResult.Success] with
     * [kotlin.collections.List<examples.ktorClientWebSocket.models.Room>] if the request was successful.
     * 	[NetworkResult.Failure] with a [NetworkError] if the request failed.
     */
    public suspend fun listRooms(apiConfiguration: ApiConfiguration = ApiConfiguration()): NetworkResult<List<Room>> {
        val basePath = apiConfiguration.basePath.trimEnd('/')
        val url = basePath + """/rooms"""

        return try {
            val response =
                httpClient.`get`(url) {
                    `header`("Accept", "application/json")
                    headers {
                        apiConfiguration.customHeaders.forEach { (name, value) ->
                            remove(name)
                            append(name, value)
                        }
                    }
                }

            if (response.status.isSuccess()) {
                NetworkResult.Success(response.body())
            } else {
                val errorBody = response.bodyAsText().ifBlank { null }
                NetworkResult.Failure(
                    NetworkError.Http(
                        statusCode = response.status.value,
                        statusDescription = response.status.description,
                        body = errorBody,
                    ),
                )
            }
        } catch (e: ResponseException) {
            val status = e.response.status
            val body = runCatching { e.response.bodyAsText() }.getOrNull()?.ifBlank { null }
            NetworkResult.Failure(NetworkError.Http(status.value, status.description, body))
        } catch (e: IOException) {
            NetworkResult.Failure(NetworkError.Network(e))
        } catch (e: ContentConvertException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: NoTransformationFoundException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        }
    }
}

public class RoomsStreamClient(
    private val httpClient: HttpClient,
) {
    /**
     * Open a bidirectional stream of chat messages
     *
     * Parameters:
     * 	 @param roomId The room to join
     * 	 @param since Only replay messages newer than this cursor
     * 	 @param xClientId Identifies the connecting client
     * 	 @param block Runs against the open session, and suspends until it returns.
     *
     * Returns:
     * 	[NetworkResult.Success] once the session completes, either because [StreamRoomSession]
     * returned or because the server closed it.
     * 	[NetworkResult.Failure] with a [NetworkError] if the handshake or the session failed.
     */
    public suspend fun streamRoom(
        roomId: String,
        since: String? = null,
        xClientId: String,
        apiConfiguration: ApiConfiguration = ApiConfiguration(),
        block: suspend StreamRoomSession.() -> Unit,
    ): NetworkResult<Unit> {
        val basePath = apiConfiguration.basePath.trimEnd('/')
        val url =
            buildString {
                append(basePath)
                append("""/rooms/$roomId/stream""")
                val params =
                    buildList {
                        since?.let { add("since=$it") }
                    }
                if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
            }.toWebSocketUrl()

        return try {
            httpClient.webSocket(url, request = {
                `header`("X-Client-Id", xClientId)
                headers {
                    apiConfiguration.customHeaders.forEach { (name, value) ->
                        remove(name)
                        append(name, value)
                    }
                }
            }) {
                StreamRoomSession(this).block()
            }
            NetworkResult.Success(Unit)
        } catch (e: ClosedReceiveChannelException) {
            NetworkResult.Success(Unit)
        } catch (e: ResponseException) {
            val status = e.response.status
            val body = runCatching { e.response.bodyAsText() }.getOrNull()?.ifBlank { null }
            NetworkResult.Failure(NetworkError.Http(status.value, status.description, body))
        } catch (e: WebSocketException) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        } catch (e: IOException) {
            NetworkResult.Failure(NetworkError.Network(e))
        } catch (e: ContentConvertException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: NoTransformationFoundException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        }
    }

    /**
     * Type safe session for the `/rooms/{roomId}/stream` WebSocket endpoint.
     */
    public class StreamRoomSession(
        private val session: DefaultClientWebSocketSession,
    ) {
        /**
         * Messages sent by the server, until the session is closed.
         */
        public val incoming: Flow<ChatEvent> = session.incomingMessages()

        /**
         * Sends a message to the server.
         */
        public suspend fun send(message: ChatCommand) {
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

public class RoomsEventsClient(
    private val httpClient: HttpClient,
) {
    /**
     * Subscribe to the events of a room without sending anything
     *
     * Parameters:
     * 	 @param roomId The room to observe
     * 	 @param block Runs against the open session, and suspends until it returns.
     *
     * Returns:
     * 	[NetworkResult.Success] once the session completes, either because
     * [SubscribeRoomEventsSession] returned or because the server closed it.
     * 	[NetworkResult.Failure] with a [NetworkError] if the handshake or the session failed.
     */
    public suspend fun subscribeRoomEvents(
        roomId: String,
        apiConfiguration: ApiConfiguration = ApiConfiguration(),
        block: suspend SubscribeRoomEventsSession.() -> Unit,
    ): NetworkResult<Unit> {
        val basePath = apiConfiguration.basePath.trimEnd('/')
        val url = (basePath + """/rooms/$roomId/events""").toWebSocketUrl()

        return try {
            httpClient.webSocket(url, request = {
                headers {
                    apiConfiguration.customHeaders.forEach { (name, value) ->
                        remove(name)
                        append(name, value)
                    }
                }
            }) {
                SubscribeRoomEventsSession(this).block()
            }
            NetworkResult.Success(Unit)
        } catch (e: ClosedReceiveChannelException) {
            NetworkResult.Success(Unit)
        } catch (e: ResponseException) {
            val status = e.response.status
            val body = runCatching { e.response.bodyAsText() }.getOrNull()?.ifBlank { null }
            NetworkResult.Failure(NetworkError.Http(status.value, status.description, body))
        } catch (e: WebSocketException) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        } catch (e: IOException) {
            NetworkResult.Failure(NetworkError.Network(e))
        } catch (e: ContentConvertException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: NoTransformationFoundException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        }
    }

    /**
     * Type safe session for the `/rooms/{roomId}/events` WebSocket endpoint.
     */
    public class SubscribeRoomEventsSession(
        private val session: DefaultClientWebSocketSession,
    ) {
        /**
         * Messages sent by the server, until the session is closed.
         */
        public val incoming: Flow<ChatEvent> = session.incomingMessages()

        /**
         * Closes the session, completing any active message flow.
         */
        public suspend fun close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
            session.close(reason)
        }
    }
}

public class RoomsCommandsClient(
    private val httpClient: HttpClient,
) {
    /**
     * Push commands to a room without reading anything back
     *
     * Parameters:
     * 	 @param roomId The room to command
     * 	 @param block Runs against the open session, and suspends until it returns.
     *
     * Returns:
     * 	[NetworkResult.Success] once the session completes, either because [PushRoomCommandsSession]
     * returned or because the server closed it.
     * 	[NetworkResult.Failure] with a [NetworkError] if the handshake or the session failed.
     */
    public suspend fun pushRoomCommands(
        roomId: String,
        apiConfiguration: ApiConfiguration = ApiConfiguration(),
        block: suspend PushRoomCommandsSession.() -> Unit,
    ): NetworkResult<Unit> {
        val basePath = apiConfiguration.basePath.trimEnd('/')
        val url = (basePath + """/rooms/$roomId/commands""").toWebSocketUrl()

        return try {
            httpClient.webSocket(url, request = {
                headers {
                    apiConfiguration.customHeaders.forEach { (name, value) ->
                        remove(name)
                        append(name, value)
                    }
                }
            }) {
                PushRoomCommandsSession(this).block()
            }
            NetworkResult.Success(Unit)
        } catch (e: ClosedReceiveChannelException) {
            NetworkResult.Success(Unit)
        } catch (e: ResponseException) {
            val status = e.response.status
            val body = runCatching { e.response.bodyAsText() }.getOrNull()?.ifBlank { null }
            NetworkResult.Failure(NetworkError.Http(status.value, status.description, body))
        } catch (e: WebSocketException) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        } catch (e: IOException) {
            NetworkResult.Failure(NetworkError.Network(e))
        } catch (e: ContentConvertException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: NoTransformationFoundException) {
            NetworkResult.Failure(NetworkError.Serialization(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Failure(NetworkError.Unknown(e))
        }
    }

    /**
     * Type safe session for the `/rooms/{roomId}/commands` WebSocket endpoint.
     */
    public class PushRoomCommandsSession(
        private val session: DefaultClientWebSocketSession,
    ) {
        /**
         * Sends a message to the server.
         */
        public suspend fun send(message: ChatCommand) {
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

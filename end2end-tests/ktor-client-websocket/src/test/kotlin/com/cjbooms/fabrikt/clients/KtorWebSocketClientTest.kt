package com.cjbooms.fabrikt.clients

import com.example.client.ApiConfiguration
import com.example.client.NetworkError
import com.example.client.NetworkResult
import com.example.client.RoomsClient
import com.example.client.RoomsCommandsClient
import com.example.client.RoomsEventsClient
import com.example.client.RoomsStreamClient
import com.example.models.ChatCommand
import com.example.models.ChatEvent
import com.example.models.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.receiveDeserialized as serverReceiveDeserialized
import io.ktor.server.websocket.sendSerialized as serverSendSerialized
import io.ktor.server.websocket.webSocket as serverWebSocket

/**
 * Exercises the generated Ktor WebSocket client against a real Netty server, covering the
 * duplex, receive-only and send-only session shapes as well as plain HTTP alongside them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorWebSocketClientTest {

    private val port: Int = ServerSocket(0).use { it.localPort }

    private val pushedCommands = Channel<ChatCommand>(Channel.UNLIMITED)

    private val server = embeddedServer(Netty, port = port) {
        install(ServerWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        install(ServerContentNegotiation) {
            json()
        }
        routing {
            get("/rooms") {
                call.respond(listOf(Room(id = "general", name = "General")))
            }

            serverWebSocket("/rooms/{roomId}/stream") {
                val author = call.request.headers["X-Client-Id"] ?: "unknown"
                try {
                    while (true) {
                        val command = serverReceiveDeserialized<ChatCommand>()
                        serverSendSerialized(ChatEvent(author = author, text = "echo: ${command.text}"))
                    }
                } catch (_: ClosedReceiveChannelException) {
                    // The client finished its session.
                }
            }

            serverWebSocket("/rooms/{roomId}/events") {
                repeat(3) { index ->
                    serverSendSerialized(ChatEvent(author = "server", text = "event-$index"))
                }
                close(CloseReason(CloseReason.Codes.NORMAL, "done"))
            }

            serverWebSocket("/rooms/{roomId}/commands") {
                try {
                    while (true) {
                        pushedCommands.send(serverReceiveDeserialized<ChatCommand>())
                    }
                } catch (_: ClosedReceiveChannelException) {
                    // The client finished its session.
                }
            }
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json()
        }
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }

    private fun apiConfig() = ApiConfiguration(basePath = "http://localhost:$port")

    @BeforeAll
    fun startServer() {
        server.start(wait = false)
    }

    @AfterAll
    fun stopServer() {
        httpClient.close()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
    }

    @Test
    fun `a duplex session sends commands and receives events`() {
        runBlocking {
            lateinit var received: ChatEvent

            val result = RoomsStreamClient(httpClient).streamRoom(
                roomId = "general",
                xClientId = "client-1",
                apiConfiguration = apiConfig(),
            ) {
                send(ChatCommand(text = "hello"))
                received = incoming.first()
            }

            assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
            assertThat(received).isEqualTo(ChatEvent(author = "client-1", text = "echo: hello"))
        }
    }

    @Test
    fun `a receive-only session streams every server message`() {
        runBlocking {
            val events = mutableListOf<ChatEvent>()

            val result = RoomsEventsClient(httpClient).subscribeRoomEvents(
                roomId = "general",
                apiConfiguration = apiConfig(),
            ) {
                events += incoming.take(3).toList()
            }

            assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
            assertThat(events.map { it.text }).containsExactly("event-0", "event-1", "event-2")
        }
    }

    @Test
    fun `a send-only session delivers every message to the server`() {
        runBlocking {
            val result = RoomsCommandsClient(httpClient).pushRoomCommands(
                roomId = "general",
                apiConfiguration = apiConfig(),
            ) {
                send(ChatCommand(text = "first"))
                send(ChatCommand(text = "second"))
            }

            assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
            val delivered = withTimeout(5_000) { listOf(pushedCommands.receive(), pushedCommands.receive()) }
            assertThat(delivered.map { it.text }).containsExactly("first", "second")
        }
    }

    @Test
    fun `plain HTTP operations are unaffected by websocket generation`() {
        runBlocking {
            val result = RoomsClient(httpClient).listRooms(apiConfiguration = apiConfig())

            assertThat(result).isEqualTo(NetworkResult.Success(listOf(Room(id = "general", name = "General"))))
        }
    }

    @Test
    fun `an unreachable server is reported as a failure`() {
        runBlocking {
            val deadPort = ServerSocket(0).use { it.localPort }

            val result = RoomsEventsClient(httpClient).subscribeRoomEvents(
                roomId = "general",
                apiConfiguration = ApiConfiguration(basePath = "http://localhost:$deadPort"),
            ) {
                incoming.first()
            }

            assertThat(result).isInstanceOf(NetworkResult.Failure::class.java)
            assertThat((result as NetworkResult.Failure).error)
                .isInstanceOfAny(NetworkError.Network::class.java, NetworkError.Unknown::class.java)
        }
    }
}

package com.cjbooms.fabrikt.servers

import com.example.controllers.RoomsController
import com.example.controllers.RoomsStreamController
import com.example.controllers.TypedApplicationCall
import com.example.controllers.roomsRoutes
import com.example.controllers.roomsStreamRoutes
import com.example.models.ChatCommand
import com.example.models.ChatEvent
import com.example.models.Room
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing

class KtorServerStubsTest {

    @Test
    fun `generated HTTP route mounts and responds with typed models`() = testApplication {
        application {
            install(ServerContentNegotiation) { json() }
            routing {
                roomsRoutes(object : RoomsController {
                    override suspend fun listRooms(call: TypedApplicationCall<List<Room>>) {
                        call.respondTyped(listOf(Room(id = "general", name = "General")))
                    }
                })
            }
        }

        val client = createClient {
            install(ClientContentNegotiation) { json() }
        }

        val rooms = client.get("/rooms").body<List<Room>>()
        assertThat(rooms).containsExactly(Room(id = "general", name = "General"))
    }

    @Test
    fun `generated websocket route mounts a typed duplex session`() = testApplication {
        application {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            install(ServerContentNegotiation) { json() }
            routing {
                roomsStreamRoutes(object : RoomsStreamController {
                    override suspend fun streamRoom(
                        xClientId: String,
                        roomId: String,
                        since: String?,
                        session: RoomsStreamController.StreamRoomSession,
                    ) {
                        assertThat(roomId).isEqualTo("general")
                        assertThat(xClientId).isEqualTo("test-client")
                        val command = session.incoming.first()
                        session.send(ChatEvent(author = xClientId, text = "echo: ${command.text}"))
                        session.close()
                    }
                })
            }
        }

        val client = createClient {
            install(ClientWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        var echoed: ChatEvent? = null
        client.webSocket(
            urlString = "/rooms/general/stream",
            request = { header("X-Client-Id", "test-client") },
        ) {
            sendSerialized(ChatCommand(text = "hello"))
            echoed = receiveDeserialized()
        }

        assertThat(echoed).isEqualTo(ChatEvent(author = "test-client", text = "echo: hello"))
    }
}

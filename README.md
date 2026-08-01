<p align="center">
  <img src=".github/assets/fabrikt-horizontal-final.png" alt="fabrikt" height="80">
</p>

# Fabrikt `/ˈfa-brikt/` — Ktor client/server + kotlinx-serialization from OpenAPI 3

This fork generates a **Ktor HTTP client**, **Ktor server stubs**, and **kotlinx-serialization** models from OpenAPI 3 (including WebSockets via `x-websocket`). OkHttp/Feign/Spring clients, Spring/Micronaut controllers, Jackson-as-model-library, Quarkus options, and the playground are not supported.

Jackson remains on the **tool** classpath only, for YAML/OpenAPI parsing. Generated models use kotlinx-serialization exclusively and do not depend on Jackson.

* [Features](#features)
* [Examples](#examples)
* [Usage Instructions](#usage-instructions)
* [Getting the Most from Fabrikt](#getting-the-most-from-fabrikt)
* [Configuration Options](#configuration-options)
* [Ktor server stubs](#ktor-server-stubs)
* [WebSockets via `x-websocket`](#websockets-via-x-websocket)
* [Building Locally](#building-locally)

## Features

* **Models** — kotlinx-serialization annotated data classes (sealed oneOf, enums, maps, type overrides, …)
* **Client** — Ktor client for HTTP operations
* **Server** — thin controller interfaces + top-level `Route` mount helpers + shared support
* **WebSockets** — type-safe session classes for `get` operations marked with `x-websocket` (client and server)

## Examples

Unit-test goldens live under [`src/test/resources/examples`](src/test/resources/examples). End-to-end modules:

* [`end2end-tests/ktor-client-kotlinx`](end2end-tests/ktor-client-kotlinx)
* [`end2end-tests/ktor-client-websocket`](end2end-tests/ktor-client-websocket)
* [`end2end-tests/ktor-server`](end2end-tests/ktor-server)
* [`end2end-tests/models-kotlinx`](end2end-tests/models-kotlinx)

## Usage Instructions

### Command Line

```
java -jar fabrikt.jar \
    --output-directory '/tmp' \
    --base-package 'com.example' \
    --api-file '/path-to-api/open-api.yaml' \
    --targets 'client'
```

`CLIENT` always co-generates models. Use `--targets http_models` alone when you only need models.

Build the fat jar locally with `./gradlew :shadowJar` (prefer `:shadowJar` over `:jar` so the executable archive is not overwritten by a thin jar).

### Gradle w/ custom task

```kotlin
val fabrikt: Configuration by configurations.creating

val generationDir = "$buildDir/generated"
val apiFile = "$projectDir/openapi/api.yaml"

sourceSets {
    main { java.srcDirs("$generationDir/src/main/kotlin") }
}

tasks {
    val generateCode by creating(JavaExec::class) {
        inputs.files(apiFile)
        outputs.dir(generationDir)
        classpath(fabrikt)
        mainClass.set("io.fabrikt.cli.CodeGen")
        args = listOf(
            "--output-directory", generationDir,
            "--base-package", "com.example",
            "--api-file", apiFile,
            "--targets", "client",
        )
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generateCode)
    }
}

dependencies {
    fabrikt(files("path/to/fabrikt.jar")) // or a Maven coordinate when published
    implementation("io.ktor:ktor-client-core:…")
    implementation("io.ktor:ktor-client-content-negotiation:…")
    implementation("io.ktor:ktor-serialization-kotlinx-json:…")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:…")
}
```

For WebSocket APIs, also depend on `ktor-client-websockets` and install Ktor's `WebSockets` plugin (see below).

### Docker

Upstream images may still expose the wider CLI. Prefer a local shadow jar for this fork until an image is rebuilt from it.

## Getting the Most from Fabrikt

### Prefer components to inline schemas

Inline schemas are not supported in all circumstances (especially request bodies and non-trivial parameters). Prefer `components.parameters` and `components.requestBodies`.

### Use `oneOf` with a discriminator for polymorphism

Discriminated `oneOf` generates sealed interfaces / polymorphic kotlinx models by default. Use `--http-model-opts DISABLE_SEALED_INTERFACES_FOR_ONE_OF` to turn that off.

## Configuration Options

| Parameter | Description |
| --- | --- |
| `--api-file` | OpenAPI v3 spec used as the primary input |
| `--api-fragment` | Partial OpenAPI fragment merged with the primary API |
| * `--base-package` | Base package for generated code |
| `--external-ref-resolution` | How external `$ref` schemas are included. Default: `TARGETED`. Choices: `TARGETED`, `AGGRESSIVE` |
| `--http-client-opts` | Client options. Choices: `GROUP_BY_TAG` |
| `--http-server-opts` | Server options. Choices: `GROUP_BY_TAG`, `AUTHENTICATION` |
| `--http-model-opts` | Model options. Choices: `X_EXTENSIBLE_ENUMS`, `JAVA_SERIALIZATION`, `INCLUDE_COMPANION_OBJECT`, `DISABLE_SEALED_INTERFACES_FOR_ONE_OF`, `NON_NULL_MAP_VALUES`, `FAULT_TOLERANT_ENUMS`, `FAULT_TOLERANT_OPEN_ENUMS` |
| `--http-model-suffix` | Custom suffix for generated model class names |
| `--instant-library` | Instant library when generating Instant types. Default: `KOTLINX_INSTANT`. Choices: `KOTLINX_INSTANT`, `KOTLIN_TIME_INSTANT` |
| `--output-directory` | Output root. Defaults to current directory |
| `--output-opts` | Output options. Choices: `ADD_FILE_DISCLAIMER` |
| `--resources-path` | Path for generated resources. Default: `src/main/resources` |
| `--src-path` | Path for generated sources. Default: `src/main/kotlin` |
| `--targets` | What to generate. Default: `CLIENT`. Choices: `CLIENT`, `SERVER`, `HTTP_MODELS` |
| `--type-overrides` | Non-default Kotlin types for certain OAS formats (`DATETIME_AS_INSTANT`, `ANY_AS_JSONELEMENT`, `*_AS_STRING`, …) |

Print live help with `./gradlew printCodeGenUsage`.

## Ktor server stubs

`--targets server` co-generates models and emits:

* one controller interface per resource (always `suspend`)
* a top-level `fun Route.<resource>Routes(controller)` mount helper
* shared `KtorServerSupport.kt` (`TypedApplicationCall`, typed parameter helpers)
* for `x-websocket` ops: nested session types + `webSocket` mounts, plus `KtorServerWebSocketSupport.kt`

```kotlin
routing {
    roomsRoutes(object : RoomsController {
        override suspend fun listRooms(call: TypedApplicationCall<List<Room>>) {
            call.respondTyped(listOf(Room(id = "general", name = "General")))
        }
    })
}
```

With `--http-server-opts AUTHENTICATION`, secured operations are wrapped in Ktor `authenticate(...)`. Install `Authentication` in your application for that to work. Typed path/query conversion uses Ktor's data conversion plugin for non-primitive types.

## WebSockets via `x-websocket`

OpenAPI has no native WebSocket shape, so Fabrikt reads an `x-websocket` extension on a `get` operation (HTTP upgrade handshake). Path, query, and header parameters are declared and generated like any other operation.

```yaml
paths:
  /rooms/{roomId}/stream:
    get:
      operationId: streamRoom
      parameters:
        - name: roomId
          in: path
          required: true
          schema:
            type: string
      x-websocket:
        send:
          $ref: '#/components/schemas/ChatCommand'
        receive:
          $ref: '#/components/schemas/ChatEvent'
      responses:
        '101':
          description: Switching Protocols
```

Both directions are optional. Message payloads must be `$ref`s into `#/components/schemas/` so model generation can emit them.

**Client** usage:

```kotlin
val result = RoomsStreamClient(httpClient).streamRoom(roomId = "general") {
    send(ChatCommand(text = "hello"))
    incoming.collect { event -> println(event.text) }
}
```

**Server** usage (generated mount + session; OpenAPI `send`/`receive` are client-centric, so the server session flips them):

```kotlin
routing {
    roomsStreamRoutes(object : RoomsStreamController {
        override suspend fun streamRoom(..., session: StreamRoomSession) {
            val command = session.incoming.first() // ChatCommand from client
            session.send(ChatEvent(...))           // to client
        }
    })
}
```

Install Ktor WebSockets with a kotlinx content converter on both sides:

```kotlin
val httpClient = HttpClient(CIO) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
}

// server
install(WebSockets) {
    contentConverter = KotlinxWebsocketSerializationConverter(Json)
}
```

## Building Locally

```
git clone <this-repo>
cd fabrikt/
./gradlew clean :test
./gradlew :end2end-tests:ktor-client-kotlinx:test \
          :end2end-tests:ktor-client-websocket:test \
          :end2end-tests:ktor-server:test \
          :end2end-tests:models-kotlinx:test
```

### Adjusting Test Examples

[`GeneratedCodeAsserter.kt`](src/test/kotlin/com/cjbooms/fabrikt/util/GeneratedCodeAsserter.kt) can mass-update golden files under test resources when generation output changes globally.

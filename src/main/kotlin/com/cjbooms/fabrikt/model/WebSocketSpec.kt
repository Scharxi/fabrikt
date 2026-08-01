package com.cjbooms.fabrikt.model

import com.cjbooms.fabrikt.generators.model.ModelGenerator.Companion.toModelType
import com.cjbooms.fabrikt.util.KaizenParserExtensions.X_WEBSOCKET
import com.cjbooms.fabrikt.util.KaizenParserExtensions.isWebSocket
import com.cjbooms.fabrikt.util.KaizenParserExtensions.webSocketExtension
import com.cjbooms.fabrikt.validation.ValidationError
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.TypeName

/**
 * The message types of a WebSocket endpoint declared through the `x-websocket` operation extension.
 * At least one direction is always present. A `null` [sendType] describes a receive-only socket,
 * a `null` [receiveType] a send-only one.
 */
data class WebSocketSpec(val sendType: TypeName?, val receiveType: TypeName?)

/**
 * Parses the `x-websocket` operation extension:
 *
 * ```yaml
 * x-websocket:
 *   send:    { $ref: '#/components/schemas/ClientCommand' }
 *   receive: { $ref: '#/components/schemas/ServerEvent' }
 * ```
 *
 * Kaizen exposes extensions as raw Jackson values, so a `$ref` nested inside one is never resolved
 * into a [Schema] overlay. Message payloads must therefore be local references that we look up by
 * hand against `components/schemas`, which also guarantees the referenced type is emitted by model
 * generation.
 */
object WebSocketExtension {

    private const val SEND = "send"
    private const val RECEIVE = "receive"
    private const val REF = "\$ref"
    private const val SCHEMA_REF_PREFIX = "#/components/schemas/"

    fun validate(api: OpenApi3): List<ValidationError> =
        api.paths.entries.flatMap { (route, path) ->
            path.operations.entries.flatMap { (verb, operation) ->
                when (val result = operation.parseMessages(api, verb)) {
                    is ParseResult.Invalid -> result.reasons.map {
                        ValidationError("Operation '${verb.uppercase()} $route': $it")
                    }

                    else -> emptyList()
                }
            }
        }

    /**
     * Resolves the message types of a WebSocket operation, or returns `null` when the operation is
     * a plain HTTP one. Assumes [validate] has already run, and so silently ignores malformed
     * declarations rather than failing generation twice over.
     */
    fun Operation.toWebSocketSpec(api: OpenApi3, basePackage: String, verb: String): WebSocketSpec? =
        when (val result = parseMessages(api, verb)) {
            is ParseResult.Valid -> WebSocketSpec(
                sendType = result.send?.toMessageType(basePackage),
                receiveType = result.receive?.toMessageType(basePackage),
            )

            else -> null
        }

    private fun Operation.parseMessages(api: OpenApi3, verb: String): ParseResult {
        if (!isWebSocket()) return ParseResult.NotWebSocket

        val reasons = mutableListOf<String>()

        if (!verb.equals("get", ignoreCase = true)) {
            reasons += "`$X_WEBSOCKET` is only supported on `get` operations, " +
                "because the WebSocket handshake is an HTTP GET upgrade"
        }

        val extension = webSocketExtension()
        if (extension !is Map<*, *>) {
            reasons += "`$X_WEBSOCKET` must be an object with optional `$SEND` and `$RECEIVE` entries"
            return ParseResult.Invalid(reasons)
        }

        val unsupportedKeys = extension.keys.map { it.toString() }.filterNot { it == SEND || it == RECEIVE }
        if (unsupportedKeys.isNotEmpty()) {
            reasons += "`$X_WEBSOCKET` declares unsupported ${unsupportedKeys.joinToString { "`$it`" }}. " +
                "Only `$SEND` and `$RECEIVE` are supported"
        }

        if (!extension.containsKey(SEND) && !extension.containsKey(RECEIVE)) {
            reasons += "`$X_WEBSOCKET` must declare at least one of `$SEND` or `$RECEIVE`"
        }

        val send = extension[SEND]?.let { resolveMessage(it, SEND, api, reasons) }
        val receive = extension[RECEIVE]?.let { resolveMessage(it, RECEIVE, api, reasons) }

        return if (reasons.isEmpty()) ParseResult.Valid(send, receive) else ParseResult.Invalid(reasons)
    }

    private fun resolveMessage(
        declaration: Any,
        direction: String,
        api: OpenApi3,
        reasons: MutableList<String>,
    ): ResolvedMessage? {
        val ref = (declaration as? Map<*, *>)?.get(REF) as? String
        if (ref == null) {
            reasons += "`$direction` must be a `$REF` pointing at `$SCHEMA_REF_PREFIX`. Inline schemas are not " +
                "supported, because schemas nested inside an extension are invisible to model generation"
            return null
        }

        if (!ref.startsWith(SCHEMA_REF_PREFIX)) {
            reasons += "`$direction` references `$ref`, but only local references under `$SCHEMA_REF_PREFIX` " +
                "are supported"
            return null
        }

        val name = ref.removePrefix(SCHEMA_REF_PREFIX)
        val schema = api.schemas[name]
        if (schema == null) {
            reasons += "`$direction` references `$ref`, which is not defined in the API"
            return null
        }

        return ResolvedMessage(name, schema)
    }

    private fun ResolvedMessage.toMessageType(basePackage: String): TypeName =
        toModelType(basePackage, KotlinTypeInfo.from(schema, name))

    private data class ResolvedMessage(val name: String, val schema: Schema)

    private sealed interface ParseResult {
        object NotWebSocket : ParseResult
        data class Invalid(val reasons: List<String>) : ParseResult
        data class Valid(val send: ResolvedMessage?, val receive: ResolvedMessage?) : ParseResult
    }
}

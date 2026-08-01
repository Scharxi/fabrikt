package com.cjbooms.fabrikt.model

import com.beust.jcommander.ParameterException
import com.cjbooms.fabrikt.cli.CodeGenerationType
import com.cjbooms.fabrikt.generators.MutableSettings
import com.cjbooms.fabrikt.util.ModelNameRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketExtensionTest {

    @BeforeEach
    fun init() {
        MutableSettings.updateSettings(
            genTypes = setOf(CodeGenerationType.CLIENT),
        )
        ModelNameRegistry.clear()
    }

    @Test
    fun `a socket declaring both directions is accepted`() {
        assertThatCode {
            SourceApi(apiWith("{ send: { $REF: '$SCHEMAS/ChatCommand' }, receive: { $REF: '$SCHEMAS/ChatEvent' } }"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a socket declaring a single direction is accepted`() {
        assertThatCode {
            SourceApi(apiWith("{ receive: { $REF: '$SCHEMAS/ChatEvent' } }"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a socket declaring neither direction is rejected`() {
        assertThat(errorsFor(apiWith("{}")))
            .contains("must declare at least one of `send` or `receive`")
    }

    @Test
    fun `an inline message schema is rejected`() {
        assertThat(errorsFor(apiWith("{ receive: { type: object } }")))
            .contains("`receive` must be a `\$ref` pointing at `$SCHEMAS/`")
            .contains("invisible to model generation")
    }

    @Test
    fun `a message schema outside components schemas is rejected`() {
        assertThat(errorsFor(apiWith("{ receive: { $REF: 'shared.yaml#/ChatEvent' } }")))
            .contains("only local references under `$SCHEMAS/` are supported")
    }

    @Test
    fun `an unresolvable message schema is rejected`() {
        assertThat(errorsFor(apiWith("{ receive: { $REF: '$SCHEMAS/DoesNotExist' } }")))
            .contains("which is not defined in the API")
    }

    @Test
    fun `a socket on a non-GET operation is rejected`() {
        val errors = errorsFor(apiWith("{ receive: { $REF: '$SCHEMAS/ChatEvent' } }", verb = "post"))

        assertThat(errors).contains("only supported on `get` operations")
        assertThat(errors).contains("Operation 'POST /rooms/{roomId}/stream'")
    }

    @Test
    fun `an unsupported direction is rejected`() {
        assertThat(errorsFor(apiWith("{ broadcast: { $REF: '$SCHEMAS/ChatEvent' } }")))
            .contains("declares unsupported `broadcast`")
    }

    @Test
    fun `a non-object extension value is rejected`() {
        assertThat(errorsFor(apiWith("true")))
            .contains("must be an object with optional `send` and `receive` entries")
    }

    private fun errorsFor(api: String): String {
        val thrown = catchThrowable { SourceApi(api) }
        assertThat(thrown).isInstanceOf(ParameterException::class.java)
        return thrown.message.orEmpty()
    }

    private fun apiWith(webSocketDeclaration: String, verb: String = "get"): String =
        """
        openapi: "3.0.2"
        info:
          title: Chat API
          version: "1.0"
        paths:
          /rooms/{roomId}/stream:
            $verb:
              operationId: streamRoom
              parameters:
                - name: roomId
                  in: path
                  required: true
                  schema:
                    type: string
              x-websocket: $webSocketDeclaration
              responses:
                '101':
                  description: Switching Protocols
        components:
          schemas:
            ChatCommand:
              type: object
              properties:
                text:
                  type: string
            ChatEvent:
              type: object
              properties:
                text:
                  type: string
        """.trimIndent()

    private companion object {
        const val REF = "\$ref"
        const val SCHEMAS = "#/components/schemas"
    }
}

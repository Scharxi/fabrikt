package com.cjbooms.fabrikt.generators

import com.cjbooms.fabrikt.cli.CodeGenerationType
import com.cjbooms.fabrikt.cli.ServerCodeGenOptionType
import com.cjbooms.fabrikt.configurations.Packages
import com.cjbooms.fabrikt.generators.server.KtorServerGenerator
import com.cjbooms.fabrikt.model.Controllers
import com.cjbooms.fabrikt.model.KotlinSourceSet
import com.cjbooms.fabrikt.model.SimpleFile
import com.cjbooms.fabrikt.model.SourceApi
import com.cjbooms.fabrikt.util.GeneratedCodeAsserter.Companion.assertThatGenerated
import com.cjbooms.fabrikt.util.Linter
import com.cjbooms.fabrikt.util.ModelNameRegistry
import com.squareup.kotlinpoet.FileSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Paths
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorServerGeneratorTest {

    @Suppress("unused")
    private fun httpCases(): Stream<String> = Stream.of(
        "ktorClient",
        "parameterNameClash",
    )

    @BeforeEach
    fun init() {
        MutableSettings.updateSettings(
            genTypes = setOf(CodeGenerationType.SERVER),
        )
        ModelNameRegistry.clear()
    }

    @ParameterizedTest
    @MethodSource("httpCases")
    fun `correct Ktor server stubs are generated`(testCaseName: String) {
        val packages = Packages("examples.$testCaseName")
        val apiLocation = javaClass.getResource("/examples/$testCaseName/api.yaml")!!
        val sourceApi = SourceApi(apiLocation.readText(), baseDir = Paths.get(apiLocation.toURI()))

        val generator = KtorServerGenerator(packages, sourceApi)
        val controllers = generator.generate(emptySet())
        val support = generator.generateLibrary(emptySet())
            .filterIsInstance<KotlinSourceSet>()
            .flatMap { it.files }

        assertThatGenerated(controllers.toSingleFile()).isEqualTo("/examples/$testCaseName/controllers/ktor/Controllers.kt")
        assertThatGenerated(Linter.lintString(support.first().toString()))
            .isEqualTo("/examples/$testCaseName/controllers/ktor/KtorServerSupport.kt")
    }

    @Test
    fun `GROUP_BY_TAG produces tagged route mounts`() {
        val packages = Packages("examples.tagGrouping")
        val apiLocation = javaClass.getResource("/examples/tagGrouping/api.yaml")!!
        val sourceApi = SourceApi(apiLocation.readText(), baseDir = Paths.get(apiLocation.toURI()))

        val controllers = KtorServerGenerator(packages, sourceApi)
            .generate(setOf(ServerCodeGenOptionType.GROUP_BY_TAG))

        assertThatGenerated(controllers.toSingleFile())
            .isEqualTo("/examples/tagGrouping/controllers/ktor/grouped/Controllers.kt")
    }

    @Test
    fun `AUTHENTICATION wraps secured routes`() {
        val packages = Packages("examples.authentication")
        val apiLocation = javaClass.getResource("/examples/authentication/api.yaml")!!
        val sourceApi = SourceApi(apiLocation.readText(), baseDir = Paths.get(apiLocation.toURI()))

        val generator = KtorServerGenerator(packages, sourceApi)
        val options = setOf(ServerCodeGenOptionType.AUTHENTICATION)
        val controllers = generator.generate(options)

        assertThatGenerated(controllers.toSingleFile())
            .isEqualTo("/examples/authentication/controllers/ktor/Controllers.kt")
    }

    @Test
    fun `websocket operations emit typed server sessions and support library`() {
        val packages = Packages("examples.ktorClientWebSocket")
        val apiLocation = javaClass.getResource("/examples/ktorClientWebSocket/api.yaml")!!
        val sourceApi = SourceApi(apiLocation.readText(), baseDir = Paths.get(apiLocation.toURI()))

        val generator = KtorServerGenerator(packages, sourceApi)
        val controllers = generator.generate(emptySet())
        val library = generator.generateLibrary(emptySet())

        assertThatGenerated(controllers.toSingleFile())
            .isEqualTo("/examples/ktorClientWebSocket/controllers/ktor/Controllers.kt")

        val wsSupport = library.filterIsInstance<SimpleFile>()
            .first { it.path.fileName.toString() == "KtorServerWebSocketSupport.kt" }
        assertThatGenerated(wsSupport.content)
            .isEqualTo("/examples/ktorClientWebSocket/controllers/ktor/KtorServerWebSocketSupport.kt")

        assertThat(controllers.toSingleFile()).contains("StreamRoomSession")
        assertThat(controllers.toSingleFile()).contains("webSocket")
    }

    private fun Controllers.toSingleFile(): String {
        val destPackage = if (resources.isNotEmpty()) resources.first().controller.destinationPackage else ""
        val builder = FileSpec.builder(destPackage, "Controllers")
        resources
            .sortedBy { it.controller.className.simpleName }
            .forEach { resource ->
                builder.addType(resource.controller.spec)
                builder.addFunction(resource.routeFunction)
            }
        // Combined multi-type FileSpecs can confuse ktlint's parser; golden comparison uses the raw poet output.
        return builder.build().toString()
    }

}

package com.cjbooms.fabrikt.generators.controller

import com.cjbooms.fabrikt.cli.ClientCodeGenOptionType
import com.cjbooms.fabrikt.configurations.Packages
import com.cjbooms.fabrikt.generators.GeneratorUtils.splitByType
import com.cjbooms.fabrikt.generators.GeneratorUtils.toIncomingParameters
import com.cjbooms.fabrikt.generators.GeneratorUtils.toKCodeName
import com.cjbooms.fabrikt.generators.client.ClientGenerator
import com.cjbooms.fabrikt.generators.client.ClientGeneratorUtils.groupedClientPaths
import com.cjbooms.fabrikt.util.NormalisedString.camelCase
import com.cjbooms.fabrikt.generators.controller.ControllerGeneratorUtils.toSuccessResponseType
import com.cjbooms.fabrikt.model.ClientType
import com.cjbooms.fabrikt.model.Clients
import com.cjbooms.fabrikt.model.Destinations
import com.cjbooms.fabrikt.model.GeneratedFile
import com.cjbooms.fabrikt.model.HandlebarsTemplates
import com.cjbooms.fabrikt.model.IncomingParameter
import com.cjbooms.fabrikt.model.KotlinTypeInfo
import com.cjbooms.fabrikt.model.RequestParameter
import com.cjbooms.fabrikt.model.SourceApi
import com.cjbooms.fabrikt.model.WebSocketExtension.toWebSocketSpec
import com.cjbooms.fabrikt.model.WebSocketSpec
import com.cjbooms.fabrikt.util.KaizenParserExtensions.isWebSocket
import com.github.javaparser.utils.CodeGenerationUtils
import com.reprezen.kaizen.oasparser.model3.Operation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import java.nio.file.Path

class KtorClientGenerator(
    private val packages: Packages,
    private val api: SourceApi,
    private val srcPath: Path = Destinations.MAIN_KT_SOURCE,
) : ClientGenerator {

    private val networkResultClassName = ClassName(packages.client, "NetworkResult")
    private val networkErrorClassName = ClassName(packages.client, "NetworkError")
    private val apiConfigurationClassName = ClassName(packages.client, "ApiConfiguration")

    private val toWebSocketUrl = MemberName(packages.client, "toWebSocketUrl", isExtension = true)
    private val incomingMessages = MemberName(packages.client, "incomingMessages", isExtension = true)

    private val hasWebSockets: Boolean =
        api.openApi3.paths.values.any { path -> path.operations.values.any { it.isWebSocket() } }

    override fun generate(options: Set<ClientCodeGenOptionType>): Clients {
        val resources: List<TypeSpec> = api.groupedClientPaths(options).flatMap { (resourceName, paths) ->
            val clientClassName = ClassName(packages.client, resourceName + "Client")
            val clientClassBuilder = TypeSpec.classBuilder(clientClassName)
                .addProperty(
                    PropertySpec.builder("httpClient", ClassName("io.ktor.client", "HttpClient"))
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("httpClient")
                        .build()
                )
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("httpClient", ClassName("io.ktor.client", "HttpClient"))
                        .build()
                )

            paths.forEach { path ->
                path.value.operations.forEach { (verb, operation) ->
                    val params = operation.toIncomingParameters(
                        packages.base, path.value.parameters, emptyList()
                    )

                    if (operation.isWebSocket()) {
                        operation.toWebSocketSpec(api.openApi3, packages.base, verb)?.let { webSocketSpec ->
                            addWebSocketOperation(
                                clientClassBuilder,
                                clientClassName,
                                path.value.pathString,
                                verb,
                                operation,
                                params,
                                webSocketSpec,
                            )
                        }
                    } else {
                        addHttpOperation(clientClassBuilder, path.value.pathString, verb, operation, params)
                    }
                }
            }

            listOf(clientClassBuilder.build())
        }

        return Clients(resources.map { ClientType(it, packages.base) }.toSet())
    }

    private fun addHttpOperation(
        clientClassBuilder: TypeSpec.Builder,
        pathString: String,
        verb: String,
        operation: Operation,
        params: List<IncomingParameter>,
    ) {
        val (pathParams, queryParams, headerParams, bodyParams) = params.splitByType()

        val responseType = operation.toSuccessResponseType(packages.base)
        val returnType = networkResultClassName.parameterizedBy(responseType)

        // build client function with NetworkResult<T> return type
        val clientFunctionBuilder = FunSpec.builder(clientRequestFunctionName(operation, verb, pathParams))
            .addModifiers(KModifier.SUSPEND)
            .returns(returnType)
            .addCode(
                CodeBlock.builder()
                    .apply { addUrlStatement(pathString, pathParams, queryParams) }
                    .addStatement("")
                    // Start try block
                    .beginControlFlow("return try")
                    .addStatement(
                        "val response = httpClient.%M(url) {",
                        MemberName("io.ktor.client.request", verb, isExtension = true)
                    )
                    .indent()
                    .apply {
                        addStatement(
                            "%M(\"Accept\", \"application/json\")",
                            MemberName("io.ktor.client.request", "header")
                        )
                        if (bodyParams.isNotEmpty()) {
                            addStatement(
                                "%M(\"Content-Type\", \"application/json\")",
                                MemberName("io.ktor.client.request", "header")
                            )
                            addStatement(
                                "%M(%L)",
                                MemberName("io.ktor.client.request", "setBody"),
                                bodyParams.first().name
                            )
                        }
                        addHeaderStatements(headerParams)
                    }
                    .unindent()
                    .addStatement("}")
                    .addStatement("")
                    .beginControlFlow(
                        "if (response.status.%M())",
                        MemberName("io.ktor.http", "isSuccess")
                    )
                    .addStatement(
                        "%T.Success(response.%M())",
                        networkResultClassName,
                        MemberName("io.ktor.client.call", "body"),
                    )
                    .nextControlFlow("else")
                    .addStatement(
                        "val errorBody = response.%M().ifBlank { null }",
                        MemberName("io.ktor.client.statement", "bodyAsText")
                    )
                    .addStatement(
                        "%T.Failure(%T.Http(statusCode = response.status.value, statusDescription = response.status.description, body = errorBody))",
                        networkResultClassName,
                        networkErrorClassName
                    )
                    .endControlFlow()
                    // Catch ResponseException
                    .nextControlFlow(
                        "catch (e: %T)",
                        ClassName("io.ktor.client.plugins", "ResponseException")
                    )
                    .addStatement("val status = e.response.status")
                    .addStatement(
                        "val body = runCatching { e.response.%M() }.getOrNull()?.ifBlank { null }",
                        MemberName("io.ktor.client.statement", "bodyAsText")
                    )
                    .addStatement(
                        "%T.Failure(%T.Http(status.value, status.description, body))",
                        networkResultClassName,
                        networkErrorClassName
                    )
                    .apply { addSharedFailureBranches() }
                    .endControlFlow()
                    .build()
            )
        if (bodyParams.isNotEmpty()) {
            clientFunctionBuilder.addParameter(
                ParameterSpec.builder(bodyParams.first().name, bodyParams.first().type)
                    .build()
            )
        }
        clientFunctionBuilder.addRequestParameters(pathParams + queryParams + headerParams)
        clientFunctionBuilder.addApiConfigurationParameter()

        clientFunctionBuilder.addKdoc(buildFunKdoc(operation, params))

        clientClassBuilder.addFunction(clientFunctionBuilder.build())
    }

    /**
     * Emits a nested session type exposing the socket's message types, plus a suspending function
     * that performs the handshake and runs [block] against that session.
     */
    private fun addWebSocketOperation(
        clientClassBuilder: TypeSpec.Builder,
        clientClassName: ClassName,
        pathString: String,
        verb: String,
        operation: Operation,
        params: List<IncomingParameter>,
        webSocketSpec: WebSocketSpec,
    ) {
        val (pathParams, queryParams, headerParams, _) = params.splitByType()

        val functionName = clientRequestFunctionName(operation, verb, pathParams)
        val sessionClassName = clientClassName.nestedClass(
            functionName.replaceFirstChar { it.uppercase() } + SESSION_SUFFIX
        )

        clientClassBuilder.addType(buildSessionType(sessionClassName, pathString, webSocketSpec))

        val connectFunctionBuilder = FunSpec.builder(functionName)
            .addModifiers(KModifier.SUSPEND)
            .returns(networkResultClassName.parameterizedBy(UNIT))
            .addCode(
                CodeBlock.builder()
                    .apply { addUrlStatement(pathString, pathParams, queryParams, toWebSocketUrl) }
                    .addStatement("")
                    .beginControlFlow("return try")
                    .add("httpClient.%M(url, request = {\n", MemberName("io.ktor.client.plugins.websocket", "webSocket"))
                    .indent()
                    .apply { addHeaderStatements(headerParams) }
                    .unindent()
                    .add("}) {\n")
                    .indent()
                    .addStatement("%T(this).block()", sessionClassName)
                    .unindent()
                    .add("}\n")
                    .addStatement("%T.Success(Unit)", networkResultClassName)
                    // The peer closing the socket ends the session normally.
                    .nextControlFlow(
                        "catch (e: %T)",
                        ClassName("kotlinx.coroutines.channels", "ClosedReceiveChannelException")
                    )
                    .addStatement("%T.Success(Unit)", networkResultClassName)
                    .nextControlFlow(
                        "catch (e: %T)",
                        ClassName("io.ktor.client.plugins", "ResponseException")
                    )
                    .addStatement("val status = e.response.status")
                    .addStatement(
                        "val body = runCatching { e.response.%M() }.getOrNull()?.ifBlank { null }",
                        MemberName("io.ktor.client.statement", "bodyAsText")
                    )
                    .addStatement(
                        "%T.Failure(%T.Http(status.value, status.description, body))",
                        networkResultClassName,
                        networkErrorClassName
                    )
                    .nextControlFlow(
                        "catch (e: %T)",
                        ClassName("io.ktor.client.plugins.websocket", "WebSocketException")
                    )
                    .addStatement(
                        "%T.Failure(%T.Unknown(e))",
                        networkResultClassName,
                        networkErrorClassName
                    )
                    .apply { addSharedFailureBranches() }
                    .endControlFlow()
                    .build()
            )

        connectFunctionBuilder.addRequestParameters(pathParams + queryParams + headerParams)
        connectFunctionBuilder.addApiConfigurationParameter()
        connectFunctionBuilder.addParameter(
            ParameterSpec.builder(
                "block",
                LambdaTypeName.get(receiver = sessionClassName, returnType = UNIT).copy(suspending = true),
            ).build()
        )

        connectFunctionBuilder.addKdoc(buildWebSocketFunKdoc(operation, params, sessionClassName))

        clientClassBuilder.addFunction(connectFunctionBuilder.build())
    }

    private fun buildSessionType(
        sessionClassName: ClassName,
        pathString: String,
        webSocketSpec: WebSocketSpec,
    ): TypeSpec {
        val sessionType = ClassName("io.ktor.client.plugins.websocket", "DefaultClientWebSocketSession")

        val builder = TypeSpec.classBuilder(sessionClassName)
            .addKdoc("Type safe session for the `%L` WebSocket endpoint.\n", pathString)
            .addProperty(
                PropertySpec.builder("session", sessionType)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("session")
                    .build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("session", sessionType)
                    .build()
            )

        webSocketSpec.receiveType?.let { receiveType ->
            builder.addProperty(
                PropertySpec
                    .builder("incoming", ClassName("kotlinx.coroutines.flow", "Flow").parameterizedBy(receiveType))
                    .addKdoc("Messages sent by the server, until the session is closed.\n")
                    .initializer("session.%M()", incomingMessages)
                    .build()
            )
        }

        webSocketSpec.sendType?.let { sendType ->
            builder.addFunction(
                FunSpec.builder("send")
                    .addKdoc("Sends a message to the server.\n")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("message", sendType)
                    .addStatement(
                        "session.%M(message)",
                        MemberName("io.ktor.client.plugins.websocket", "sendSerialized", isExtension = true)
                    )
                    .build()
            )
        }

        val closeReason = ClassName("io.ktor.websocket", "CloseReason")
        builder.addFunction(
            FunSpec.builder("close")
                .addKdoc("Closes the session, completing any active message flow.\n")
                .addModifiers(KModifier.SUSPEND)
                .addParameter(
                    ParameterSpec.builder("reason", closeReason)
                        .defaultValue("%T(%T.Codes.NORMAL, %S)", closeReason, closeReason, "")
                        .build()
                )
                .addStatement("session.%M(reason)", MemberName("io.ktor.websocket", "close", isExtension = true))
                .build()
        )

        return builder.build()
    }

    /**
     * Builds `basePath` and `url` locals from the operation's path template and query parameters.
     * [urlTransform], when given, is applied to the assembled URL.
     */
    private fun CodeBlock.Builder.addUrlStatement(
        pathString: String,
        pathParams: List<RequestParameter>,
        queryParams: List<RequestParameter>,
        urlTransform: MemberName? = null,
    ) {
        val urlBuilder = buildString {
            append(pathString)
            pathParams.forEach { param ->
                val placeholder = "{${param.originalName}}"
                val index = indexOf(placeholder)
                if (index >= 0) {
                    replace(index, index + placeholder.length, "\${${param.name}}")
                }
            }
        }

        addStatement("val basePath = apiConfiguration.basePath.trimEnd('/')")
        if (queryParams.isEmpty()) {
            if (urlTransform == null) {
                addStatement("val url = basePath + %P", urlBuilder)
            } else {
                addStatement("val url = (basePath + %P).%M()", urlBuilder, urlTransform)
            }
        } else {
            add("val url = buildString {\n")
            indent()
            addStatement("append(basePath)")
            addStatement("append(%P)", urlBuilder)
            addStatement("val params = buildList {")
            indent()
            queryParams.forEach { param ->
                val isArrayType = param.typeInfo is KotlinTypeInfo.Array
                if (isArrayType) {
                    if (param.isRequired) {
                        addStatement("%N.forEach { add(\"%L=\${it}\") }", param.name, param.originalName)
                    } else {
                        addStatement("%N?.forEach { add(\"%L=\${it}\") }", param.name, param.originalName)
                    }
                } else {
                    if (param.isRequired) {
                        addStatement("add(\"%L=\${%N}\")", param.originalName, param.name)
                    } else {
                        addStatement("%N?.let { add(\"%L=\${it}\") }", param.name, param.originalName)
                    }
                }
            }
            unindent()
            addStatement("}")
            addStatement("if (params.isNotEmpty()) append(\"?\").append(params.joinToString(\"&\"))")
            unindent()
            if (urlTransform == null) {
                addStatement("}")
            } else {
                addStatement("}.%M()", urlTransform)
            }
        }
    }

    /** Applies the operation's own header parameters, then the configured custom headers. */
    private fun CodeBlock.Builder.addHeaderStatements(headerParams: List<RequestParameter>) {
        headerParams.forEach {
            addStatement(
                "%M(%S, %L)",
                MemberName("io.ktor.client.request", "header"),
                it.originalName,
                it.name
            )
        }

        addStatement("%M {", MemberName("io.ktor.client.request", "headers"))
        indent()
        addStatement("apiConfiguration.customHeaders.forEach { (name, value) ->")
        indent()
        addStatement("remove(name)")
        addStatement("append(name, value)")
        unindent()
        addStatement("}")
        unindent()
        addStatement("}")
    }

    /** The failure branches shared by HTTP requests and WebSocket handshakes. */
    private fun CodeBlock.Builder.addSharedFailureBranches() {
        // Catch IOException
        nextControlFlow(
            "catch (e: %T)",
            ClassName("kotlinx.io", "IOException")
        )
        addStatement(
            "%T.Failure(%T.Network(e))",
            networkResultClassName,
            networkErrorClassName
        )
        // Catch ContentConvertException (thrown by Ktor's ContentNegotiation)
        nextControlFlow(
            "catch (e: %T)",
            ClassName("io.ktor.serialization", "ContentConvertException")
        )
        addStatement(
            "%T.Failure(%T.Serialization(e))",
            networkResultClassName,
            networkErrorClassName
        )
        // Catch NoTransformationFoundException (wrong content type)
        nextControlFlow(
            "catch (e: %T)",
            ClassName("io.ktor.client.call", "NoTransformationFoundException")
        )
        addStatement(
            "%T.Failure(%T.Serialization(e))",
            networkResultClassName,
            networkErrorClassName
        )
        // Catch CancellationException - rethrow
        nextControlFlow(
            "catch (e: %T)",
            ClassName("kotlinx.coroutines", "CancellationException")
        )
        addStatement("throw e")
        // Catch all other exceptions
        nextControlFlow("catch (e: Exception)")
        addStatement(
            "%T.Failure(%T.Unknown(e))",
            networkResultClassName,
            networkErrorClassName
        )
    }

    private fun FunSpec.Builder.addRequestParameters(params: List<RequestParameter>) = apply {
        params.forEach { param ->
            val defaultValue = if (!param.isRequired) "null" else null
            addParameter(
                ParameterSpec.builder(param.name, param.type.copy(nullable = !param.isRequired))
                    .apply { if (defaultValue != null) defaultValue(defaultValue) }
                    .build()
            )
        }
    }

    private fun FunSpec.Builder.addApiConfigurationParameter() = apply {
        addParameter(
            ParameterSpec.builder("apiConfiguration", apiConfigurationClassName)
                .defaultValue("%T()", apiConfigurationClassName)
                .build()
        )
    }

    override fun generateLibrary(options: Set<ClientCodeGenOptionType>): Collection<GeneratedFile> {
        val codeDir = srcPath.resolve(CodeGenerationUtils.packageToPath(packages.base))
        val clientDir = codeDir.resolve("client")

        val templateInput: Map<String, Any?> = mapOf(
            "base" to packages.base,
            "client" to packages.client,
            "models" to packages.models,
            "controllers" to packages.controllers,

            "basePath" to (api.openApi3.servers.firstOrNull()?.url ?: ""),
        )

        val libraryFiles = mutableSetOf(
            HandlebarsTemplates.applyTemplate(
                template = HandlebarsTemplates.ktorClientApiModels,
                input = templateInput,
                path = clientDir,
                fileName = "KtorApiModels.kt"
            ),
            HandlebarsTemplates.applyTemplate(
                template = HandlebarsTemplates.ktorClientApiConfiguration,
                input = templateInput,
                path = clientDir,
                fileName = "KtorApiConfiguration.kt"
            )
        )

        if (hasWebSockets) {
            libraryFiles += HandlebarsTemplates.applyTemplate(
                template = HandlebarsTemplates.ktorClientWebSocketSupport,
                input = templateInput,
                path = clientDir,
                fileName = "KtorWebSocketSupport.kt"
            )
        }

        return libraryFiles
    }

    private fun clientRequestFunctionName(op: Operation, verb: String, params: List<RequestParameter>) =
        if (op.operationId != null) {
            op.operationId.camelCase()
        } else {
            buildString {
                append(verb.lowercase())
                append(if (params.isNotEmpty()) "By" + params.joinToString("And") { it -> it.name.replaceFirstChar { it.uppercase() } } else "")
            }
        }

    private fun buildFunKdoc(operation: Operation, parameters: List<IncomingParameter>): CodeBlock {
        val kDoc = operation.descriptionAndParametersKdoc(parameters)

        // document response
        val toSuccessResponseType = operation.toSuccessResponseType(packages.base)
        kDoc.add("\nReturns:\n")
        kDoc.add("\t[NetworkResult.Success] with [%L] if the request was successful.\n", toSuccessResponseType.toString())
        kDoc.add("\t[NetworkResult.Failure] with a [NetworkError] if the request failed.\n")

        return kDoc.build()
    }

    private fun buildWebSocketFunKdoc(
        operation: Operation,
        parameters: List<IncomingParameter>,
        sessionClassName: ClassName,
    ): CodeBlock {
        val kDoc = operation.descriptionAndParametersKdoc(parameters)

        kDoc.add("\t @param block Runs against the open session, and suspends until it returns.\n")
        kDoc.add("\nReturns:\n")
        kDoc.add(
            "\t[NetworkResult.Success] once the session completes, either because [%L] returned or because the server closed it.\n",
            sessionClassName.simpleName,
        )
        kDoc.add("\t[NetworkResult.Failure] with a [NetworkError] if the handshake or the session failed.\n")

        return kDoc.build()
    }

    private fun Operation.descriptionAndParametersKdoc(parameters: List<IncomingParameter>): CodeBlock.Builder {
        val (pathParams, queryParams, headerParams, bodyParams) = parameters.splitByType()
        val kDoc = CodeBlock.builder()

        // add summary and description
        val methodDesc = listOf(summary.orEmpty(), description.orEmpty()).filter { it.isNotEmpty() }
        if (methodDesc.isNotEmpty()) {
            methodDesc.forEach { kDoc.add("%L\n", it) }
            kDoc.add("\n")
        }

        // document parameters
        if (parameters.isNotEmpty()) {
            kDoc.add("Parameters:\n")
            (bodyParams + pathParams + queryParams + headerParams).forEach {
                kDoc.add("\t @param %L %L\n", it.name.toKCodeName(), it.description?.trimIndent().orEmpty()).build()
            }
        }

        return kDoc
    }

    private companion object {
        const val SESSION_SUFFIX = "Session"
    }
}

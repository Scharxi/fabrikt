package com.cjbooms.fabrikt.generators.server

import com.cjbooms.fabrikt.cli.ServerCodeGenOptionType
import com.cjbooms.fabrikt.configurations.Packages
import com.cjbooms.fabrikt.generators.GeneratorUtils.isUnit
import com.cjbooms.fabrikt.generators.GeneratorUtils.splitByType
import com.cjbooms.fabrikt.generators.GeneratorUtils.toIncomingParameters
import com.cjbooms.fabrikt.generators.GeneratorUtils.toKCodeName
import com.cjbooms.fabrikt.generators.client.ClientResponseTypes.toSuccessResponseType
import com.cjbooms.fabrikt.generators.server.ServerGeneratorUtils.groupedServerPaths
import com.cjbooms.fabrikt.generators.server.ServerSecurity.SecuritySupport
import com.cjbooms.fabrikt.generators.server.ServerSecurity.controllerName
import com.cjbooms.fabrikt.generators.server.ServerSecurity.methodName
import com.cjbooms.fabrikt.generators.server.ServerSecurity.securitySupport
import com.cjbooms.fabrikt.model.ControllerLibraryType
import com.cjbooms.fabrikt.model.ControllerResource
import com.cjbooms.fabrikt.model.ControllerType
import com.cjbooms.fabrikt.model.Controllers
import com.cjbooms.fabrikt.model.Destinations
import com.cjbooms.fabrikt.model.GeneratedFile
import com.cjbooms.fabrikt.model.HandlebarsTemplates
import com.cjbooms.fabrikt.model.IncomingParameter
import com.cjbooms.fabrikt.model.KotlinTypeInfo
import com.cjbooms.fabrikt.model.RequestParameter
import com.cjbooms.fabrikt.model.SourceApi
import com.cjbooms.fabrikt.model.WebSocketExtension.toWebSocketSpec
import com.cjbooms.fabrikt.model.WebSocketSpec
import com.cjbooms.fabrikt.util.KaizenParserExtensions.isSingleResource
import com.cjbooms.fabrikt.util.KaizenParserExtensions.isWebSocket
import com.cjbooms.fabrikt.util.NormalisedString.camelCase
import com.cjbooms.fabrikt.util.toUpperCase
import com.github.javaparser.utils.CodeGenerationUtils
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Path
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import java.nio.file.Path as FilePath
import kotlin.reflect.KClass

private const val TYPED_APPLICATION_CALL_CLASS_NAME = "TypedApplicationCall"
private const val SESSION_SUFFIX = "Session"

/**
 * Generates thin Ktor server stubs: a controller interface per resource, a top-level
 * `Route` mount function, shared parameter/response helpers, and typed WebSocket sessions
 * for operations marked with `x-websocket`.
 */
class KtorServerGenerator(
    private val packages: Packages,
    private val api: SourceApi,
    private val srcPath: FilePath = Destinations.MAIN_KT_SOURCE,
) {
    private val typedApplicationCall = ClassName(packages.controllers, TYPED_APPLICATION_CALL_CLASS_NAME)
    private val incomingMessages = MemberName(packages.controllers, "incomingMessages", isExtension = true)
    private val globalSecurity = api.openApi3.securityRequirements.securitySupport()

    private val hasWebSockets: Boolean =
        api.openApi3.paths.values.any { path -> path.operations.values.any { it.isWebSocket() } }

    fun generate(options: Set<ServerCodeGenOptionType>): Controllers {
        val resources = api.groupedServerPaths(options).map { (resourceName, paths) ->
            buildResource(resourceName, paths, options)
        }
        return Controllers(resources)
    }

    fun generateLibrary(@Suppress("UNUSED_PARAMETER") options: Set<ServerCodeGenOptionType>): Collection<GeneratedFile> {
        val codeDir = srcPath.resolve(CodeGenerationUtils.packageToPath(packages.base))
        val controllersDir = codeDir.resolve("controllers")
        val files = mutableListOf<GeneratedFile>(
            com.cjbooms.fabrikt.model.KotlinSourceSet(setOf(buildKtorServerSupportFile()), srcPath)
        )

        if (hasWebSockets) {
            files += HandlebarsTemplates.applyTemplate(
                template = HandlebarsTemplates.ktorServerWebSocketSupport,
                input = mapOf(
                    "controllers" to packages.controllers,
                    "base" to packages.base,
                ),
                path = controllersDir,
                fileName = "KtorServerWebSocketSupport.kt",
            )
        }
        return files
    }

    private fun buildResource(
        resourceName: String,
        paths: Map<String, Path>,
        options: Set<ServerCodeGenOptionType>,
    ): ControllerResource {
        val controllerClassName = ClassName(packages.controllers, controllerName(resourceName))
        val controllerBuilder = TypeSpec.interfaceBuilder(controllerClassName.simpleName)

        val routeFunBuilder = FunSpec.builder("${resourceName.camelCase()}Routes")
            .receiver(ClassName("io.ktor.server.routing", "Route"))
            .addParameter("controller", controllerClassName)
            .addKdoc("Mounts all routes for the $resourceName resource\n\n")

        paths.forEach { pathEntry ->
            pathEntry.value.operations
                .filter { (verb, _) -> verb.toUpperCase() != "HEAD" }
                .forEach { (verb, operation) ->
                    if (operation.isWebSocket()) {
                        operation.toWebSocketSpec(api.openApi3, packages.base, verb)?.let { webSocketSpec ->
                            addWebSocketOperation(
                                controllerBuilder,
                                controllerClassName,
                                routeFunBuilder,
                                pathEntry,
                                verb,
                                operation,
                                webSocketSpec,
                            )
                        }
                    } else {
                        routeFunBuilder.addCode(buildRouteCode(operation, verb, pathEntry, options))
                        routeFunBuilder.addKdoc(
                            "- ${verb.toUpperCase()} ${pathEntry.key} ${(operation.summary ?: operation.description).orEmpty()}\n"
                        )
                        controllerBuilder.addFunction(buildControllerFun(operation, verb, pathEntry))
                    }
                }
        }

        return ControllerResource(
            controller = ControllerType(controllerBuilder.build(), packages.base),
            routeFunction = routeFunBuilder.build(),
        )
    }

    private fun buildControllerFun(operation: Operation, verb: String, path: Map.Entry<String, Path>): FunSpec {
        val methodName = getMethodName(operation, verb, path)
        val builder = FunSpec.builder(methodName)
            .addModifiers(setOf(KModifier.SUSPEND, KModifier.ABSTRACT))

        val params = operation.toIncomingParameters(packages.base, path.value.parameters, emptyList())
        val (pathParams, queryParams, headerParams, bodyParams) = params.splitByType()

        headerParams.forEach { param ->
            if (param.isRequired) {
                builder.addParameter(ParameterSpec.builder(param.name, String::class).build())
            } else {
                builder.addParameter(
                    ParameterSpec.builder(param.name, String::class.asTypeName().copy(nullable = true)).build()
                )
            }
        }

        (pathParams + queryParams).forEach { param ->
            if (param.isRequired) {
                builder.addParameter(param.toParameterSpecBuilder().build())
            } else {
                builder.addParameter(
                    ParameterSpec.builder(param.name, param.type.copy(nullable = true)).build()
                )
            }
        }

        bodyParams.forEach { param ->
            builder.addParameter(param.toParameterSpecBuilder().build())
        }

        builder.addKdoc(buildControllerFunKdoc(operation, params))

        if (operation.toSuccessResponseType(packages.base).isUnit()) {
            builder.addParameter("call", ClassName("io.ktor.server.application", "ApplicationCall"))
        } else {
            builder.addParameter(
                "call",
                typedApplicationCall.parameterizedBy(operation.toSuccessResponseType(packages.base))
            )
        }

        return builder.build()
    }

    private fun buildRouteCode(
        operation: Operation,
        verb: String,
        path: Map.Entry<String, Path>,
        options: Set<ServerCodeGenOptionType>,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        val securityOption = operation.securitySupport(globalSecurity)

        val addAuth =
            securityOption.allowsAuthenticated && ServerCodeGenOptionType.AUTHENTICATION in options
        if (addAuth) {
            val authNames = if (operation.hasSecurityRequirements()) {
                operation.securityRequirements
                    .filter { it.requirements.isNotEmpty() }
                    .map { it.requirements.keys.first() }
            } else {
                listOf(api.openApi3.securityRequirements.first().requirements.keys.first())
            }.joinToString(", ") { "\"$it\"" }

            builder
                .addStatement(
                    "%M($authNames, optional = %L) {",
                    MemberName("io.ktor.server.auth", "authenticate"),
                    securityOption == SecuritySupport.AUTHENTICATION_OPTIONAL,
                )
                .indent()
        }

        val params = operation.toIncomingParameters(packages.base, path.value.parameters, emptyList())
        val (pathParams, queryParams, headerParams, bodyParams) = params.splitByType()
        val methodName = getMethodName(operation, verb, path)

        builder
            .addStatement(
                "%M(%S) {",
                MemberName("io.ktor.server.routing", verb),
                path.key,
            )
            .indent()

        pathParams.forEach { param ->
            addTypedParameterRead(builder, param, required = true, fromPath = true)
        }

        headerParams.forEach { param ->
            if (param.isRequired) {
                builder.addStatement(
                    "val ${param.name} = %M.request.headers.getOrFail(\"${param.originalName}\")",
                    MemberName("io.ktor.server.application", "call"),
                )
            } else {
                builder.addStatement(
                    "val ${param.name} = %M.request.headers[\"${param.originalName}\"]",
                    MemberName("io.ktor.server.application", "call"),
                )
            }
        }

        queryParams.forEach { param ->
            addTypedParameterRead(builder, param, required = param.isRequired, fromPath = false)
        }

        bodyParams.forEach { param ->
            builder.addStatement(
                "val ${param.name} = %M.%M<%T>()",
                MemberName("io.ktor.server.application", "call"),
                MemberName("io.ktor.server.request", "receive"),
                param.type,
            )
        }

        val methodParameters =
            listOf(headerParams, pathParams, queryParams, bodyParams).asSequence().flatten().map { it.name }
                .joinToString(", ")

        if (operation.toSuccessResponseType(packages.base).isUnit()) {
            builder.addStatement(
                "controller.%L(%L%M)",
                methodName,
                methodParameters.let { if (it.isNotEmpty()) "$it, " else "" },
                MemberName("io.ktor.server.application", "call"),
            )
        } else {
            builder.addStatement(
                "controller.%L(%L%T(%M))",
                methodName,
                methodParameters.let { if (it.isNotEmpty()) "$it, " else "" },
                typedApplicationCall,
                MemberName("io.ktor.server.application", "call"),
            )
        }

        builder.unindent().addStatement("}")

        if (addAuth) {
            builder.unindent().addStatement("}")
        }

        return builder.build()
    }

    private fun addTypedParameterRead(
        builder: CodeBlock.Builder,
        param: RequestParameter,
        required: Boolean,
        fromPath: Boolean,
    ) {
        val typeName = param.type.copy(nullable = false)
        val queryMethodName = if (required) "getTypedOrFail" else "getTyped"
        val receiver = if (fromPath) {
            "%M.parameters"
        } else {
            "%M.request.queryParameters"
        }

        if (param.requiresKtorDataConversionPlugin()) {
            builder.addStatement(
                "val ${param.name} = $receiver.%M<$typeName>(\"${param.originalName}\", call.application.%M)",
                MemberName("io.ktor.server.application", "call"),
                MemberName(packages.controllers, queryMethodName),
                MemberName("io.ktor.server.plugins.dataconversion", "conversionService"),
            )
        } else {
            builder.addStatement(
                "val ${param.name} = $receiver.%M<$typeName>(\"${param.originalName}\")",
                MemberName("io.ktor.server.application", "call"),
                MemberName(packages.controllers, queryMethodName),
            )
        }
    }

    private fun addWebSocketOperation(
        controllerBuilder: TypeSpec.Builder,
        controllerClassName: ClassName,
        routeFunBuilder: FunSpec.Builder,
        path: Map.Entry<String, Path>,
        verb: String,
        operation: Operation,
        webSocketSpec: WebSocketSpec,
    ) {
        val params = operation.toIncomingParameters(packages.base, path.value.parameters, emptyList())
        val (pathParams, queryParams, headerParams, _) = params.splitByType()
        val methodName = getMethodName(operation, verb, path)
        val sessionSimpleName = methodName.replaceFirstChar { it.uppercase() } + SESSION_SUFFIX
        val sessionClassName = controllerClassName.nestedClass(sessionSimpleName)

        controllerBuilder.addType(buildSessionType(sessionSimpleName, path.key, webSocketSpec))

        val controllerFun = FunSpec.builder(methodName)
            .addModifiers(setOf(KModifier.SUSPEND, KModifier.ABSTRACT))
            .apply {
                headerParams.forEach { param ->
                    if (param.isRequired) {
                        addParameter(ParameterSpec.builder(param.name, String::class).build())
                    } else {
                        addParameter(
                            ParameterSpec.builder(param.name, String::class.asTypeName().copy(nullable = true)).build()
                        )
                    }
                }
                (pathParams + queryParams).forEach { param ->
                    if (param.isRequired) {
                        addParameter(param.toParameterSpecBuilder().build())
                    } else {
                        addParameter(
                            ParameterSpec.builder(param.name, param.type.copy(nullable = true)).build()
                        )
                    }
                }
                addParameter("session", sessionClassName)
                addKdoc(
                    CodeBlock.builder()
                        .add("%L\n\n", operation.summary ?: operation.description ?: "WebSocket endpoint")
                        .add("Handles the typed WebSocket session for `%L`.\n", path.key)
                        .build()
                )
            }
            .build()
        controllerBuilder.addFunction(controllerFun)

        val routeCode = CodeBlock.builder()
            .addStatement(
                "%M(%S) {",
                MemberName("io.ktor.server.websocket", "webSocket"),
                path.key,
            )
            .indent()

        pathParams.forEach { param ->
            addTypedParameterRead(routeCode, param, required = true, fromPath = true)
        }
        headerParams.forEach { param ->
            if (param.isRequired) {
                routeCode.addStatement(
                    "val ${param.name} = %M.request.headers.getOrFail(\"${param.originalName}\")",
                    MemberName("io.ktor.server.application", "call"),
                )
            } else {
                routeCode.addStatement(
                    "val ${param.name} = %M.request.headers[\"${param.originalName}\"]",
                    MemberName("io.ktor.server.application", "call"),
                )
            }
        }
        queryParams.forEach { param ->
            addTypedParameterRead(routeCode, param, required = param.isRequired, fromPath = false)
        }

        val methodParameters =
            listOf(headerParams, pathParams, queryParams).asSequence().flatten().map { it.name }
                .joinToString(", ")

        routeCode.addStatement(
            "controller.%L(%L%T(this))",
            methodName,
            methodParameters.let { if (it.isNotEmpty()) "$it, " else "" },
            sessionClassName,
        )
        routeCode.unindent().addStatement("}")

        routeFunBuilder.addCode(routeCode.build())
        routeFunBuilder.addKdoc("- WEBSOCKET ${path.key} ${(operation.summary ?: operation.description).orEmpty()}\n")
    }

    private fun buildSessionType(
        sessionSimpleName: String,
        pathString: String,
        webSocketSpec: WebSocketSpec,
    ): TypeSpec {
        val sessionType = ClassName("io.ktor.server.websocket", "DefaultWebSocketServerSession")

        val builder = TypeSpec.classBuilder(sessionSimpleName)
            .addKdoc("Type safe server session for the `%L` WebSocket endpoint.\n", pathString)
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

        // Client-centric IR: send = client→server, receive = server→client.
        // Server session flips that: incoming is client's sendType; send uses client's receiveType.
        webSocketSpec.sendType?.let { clientSendType ->
            builder.addProperty(
                PropertySpec
                    .builder("incoming", ClassName("kotlinx.coroutines.flow", "Flow").parameterizedBy(clientSendType))
                    .addKdoc("Messages sent by the client, until the session is closed.\n")
                    .initializer("session.%M()", incomingMessages)
                    .build()
            )
        }

        webSocketSpec.receiveType?.let { clientReceiveType ->
            builder.addFunction(
                FunSpec.builder("send")
                    .addKdoc("Sends a message to the client.\n")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("message", clientReceiveType)
                    .addStatement(
                        "session.%M(message)",
                        MemberName("io.ktor.server.websocket", "sendSerialized", isExtension = true)
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

    private fun buildControllerFunKdoc(operation: Operation, parameters: List<IncomingParameter>): CodeBlock {
        val kDoc = CodeBlock.builder()
        val methodDesc = listOf(operation.summary.orEmpty(), operation.description.orEmpty()).filter { it.isNotEmpty() }
        if (methodDesc.isNotEmpty()) {
            methodDesc.forEach { kDoc.add("%L\n", it) }
            kDoc.add("\n")
        }

        val successType = operation.toSuccessResponseType(packages.base)
        if (successType.isUnit()) {
            kDoc.add("Route is expected to respond with status ${operation.responses.keys.first()}.\n")
            kDoc.add(
                "Use [%M] to send the response.\n\n",
                MemberName("io.ktor.server.response", "respond", isExtension = true)
            )
        } else {
            kDoc.add(
                "Route is expected to respond with [%L].\nUse [%M] to send the response.\n\n",
                successType.toString(),
                MemberName(typedApplicationCall, "respondTyped")
            )
        }

        parameters.forEach {
            kDoc.add("@param %L %L\n", it.name.toKCodeName(), it.description?.trimIndent().orEmpty())
        }
        if (successType.isUnit()) {
            kDoc.add("@param call The Ktor application call\n")
        } else {
            kDoc.add("@param call Decorated ApplicationCall with additional typed respond methods\n")
        }

        return kDoc.build()
    }

    private fun buildKtorServerSupportFile(): FileSpec {
        val builder = FileSpec.builder(packages.controllers, "KtorServerSupport")
        builder.addType(buildTypedApplicationCall().spec)
        builder.addFunction(getTypedFun)
        builder.addFunction(getTypedOrFailFun)
        builder.addFunction(getOrFailFun)
        return builder.build()
    }

    private fun buildTypedApplicationCall(): ControllerLibraryType {
        val returnType = TypeVariableName("R", Any::class)
        val messageType = TypeVariableName("T", returnType).copy(reified = true)

        val spec = TypeSpec.classBuilder(TYPED_APPLICATION_CALL_CLASS_NAME)
            .addTypeVariable(returnType)
            .addSuperinterface(
                ClassName("io.ktor.server.application", "ApplicationCall"),
                delegate = CodeBlock.of("applicationCall")
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("applicationCall", ClassName("io.ktor.server.application", "ApplicationCall"))
                    .build()
            )
            .addProperty(
                PropertySpec.builder("applicationCall", ClassName("io.ktor.server.application", "ApplicationCall"))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("applicationCall")
                    .build()
            )
            .addKdoc(
                """
                Decorator for Ktor's ApplicationCall that exposes type-safe respond helpers.
                Use as a drop-in replacement for [io.ktor.server.application.ApplicationCall].
                """.trimIndent()
            )
            .addFunction(
                FunSpec.builder("respondTyped")
                    .addModifiers(KModifier.INLINE, KModifier.SUSPEND)
                    .addTypeVariable(messageType)
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "unused")
                            .build()
                    )
                    .addParameter("message", messageType)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "%M(message)",
                                MemberName("io.ktor.server.response", "respond", isExtension = true),
                            )
                            .build()
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("respondTyped")
                    .addModifiers(KModifier.INLINE, KModifier.SUSPEND)
                    .addTypeVariable(messageType)
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "unused")
                            .build()
                    )
                    .addParameter("status", ClassName("io.ktor.http", "HttpStatusCode"))
                    .addParameter("message", messageType)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "%M(status, message)",
                                MemberName("io.ktor.server.response", "respond", isExtension = true),
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return ControllerLibraryType(spec, packages.base)
    }

    private val getTypedFun = run {
        val returnType = TypeVariableName("R", Any::class).copy(nullable = true, reified = true)
        val conversionServiceParameter = ParameterSpec.builder(
            "conversionService",
            ClassName("io.ktor.util.converters", "ConversionService")
        )
            .defaultValue("%T", ClassName("io.ktor.util.converters", "DefaultConversionService"))
            .build()

        FunSpec.builder("getTyped")
            .addModifiers(KModifier.INLINE, KModifier.INTERNAL)
            .receiver(ClassName("io.ktor.http", "Parameters"))
            .addParameter("name", String::class)
            .addParameter(conversionServiceParameter)
            .addTypeVariable(returnType)
            .returns(returnType)
            .addCode(
                """
                val values = getAll(name) ?: return null
                val typeInfo = %M<R>()
                return try {
                    @Suppress("UNCHECKED_CAST")
                    conversionService.fromValues(values, typeInfo) as R
                } catch (cause: Exception) {
                    throw %M(name, typeInfo.type.simpleName ?: typeInfo.type.toString(), cause)
                }
                """.trimIndent(),
                MemberName("io.ktor.util.reflect", "typeInfo"),
                MemberName("io.ktor.server.plugins", "ParameterConversionException")
            )
            .addKdoc(
                """
                Gets parameter value associated with this name or null if the name is not present.
                Converting to type R using ConversionService.
                """.trimIndent()
            )
            .build()
    }

    private val getTypedOrFailFun = run {
        val returnType = TypeVariableName("R", Any::class).copy(nullable = false, reified = true)
        val conversionServiceParameter = ParameterSpec.builder(
            "conversionService",
            ClassName("io.ktor.util.converters", "ConversionService")
        )
            .defaultValue("%T", ClassName("io.ktor.util.converters", "DefaultConversionService"))
            .build()

        FunSpec.builder("getTypedOrFail")
            .addModifiers(KModifier.INLINE, KModifier.INTERNAL)
            .receiver(ClassName("io.ktor.http", "Parameters"))
            .addParameter("name", String::class)
            .addParameter(conversionServiceParameter)
            .addTypeVariable(returnType)
            .returns(returnType)
            .addCode(
                """
                val values = getAll(name) ?: throw %M(name)
                val typeInfo = %M<R>()
                return try {
                    @Suppress("UNCHECKED_CAST")
                    conversionService.fromValues(values, typeInfo) as R
                } catch (cause: Exception) {
                    throw %M(name, typeInfo.type.simpleName ?: typeInfo.type.toString(), cause)
                }
                """.trimIndent(),
                MemberName("io.ktor.server.plugins", "MissingRequestParameterException"),
                MemberName("io.ktor.util.reflect", "typeInfo"),
                MemberName("io.ktor.server.plugins", "ParameterConversionException")
            )
            .addKdoc(
                """
                Gets parameter value associated with this name or throws if the name is not present.
                Converting to type R using ConversionService.
                """.trimIndent()
            )
            .build()
    }

    private val getOrFailFun =
        FunSpec.builder("getOrFail")
            .addModifiers(KModifier.INTERNAL)
            .receiver(ClassName("io.ktor.http", "Headers"))
            .returns(String::class)
            .addParameter("name", String::class)
            .addCode(
                """
                return this[name] ?: throw %M("Header " + name + " is required")
                """.trimIndent(),
                MemberName("io.ktor.server.plugins", "BadRequestException")
            )
            .addKdoc(
                """
                Gets first value from the list of values associated with a name.
                """.trimIndent()
            )
            .build()

    private fun getMethodName(operation: Operation, verb: String, path: Map.Entry<String, Path>) =
        methodName(operation, verb, path.value.pathString.isSingleResource())
}

private fun RequestParameter.requiresKtorDataConversionPlugin(): Boolean {
    return when (val type = this.typeInfo) {
        is KotlinTypeInfo.Array -> !isPrimitiveType(type.parameterizedType.modelKClass)
        else -> !isPrimitiveType(this.typeInfo.modelKClass)
    }
}

private fun isPrimitiveType(klass: KClass<*>): Boolean {
    return when (klass) {
        Int::class,
        Float::class,
        Double::class,
        Long::class,
        Short::class,
        Char::class,
        Boolean::class,
        String::class -> true
        else -> false
    }
}

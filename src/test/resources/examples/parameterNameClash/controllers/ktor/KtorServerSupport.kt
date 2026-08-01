package examples.parameterNameClash.controllers

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.response.respond
import io.ktor.util.converters.ConversionService
import io.ktor.util.converters.DefaultConversionService
import io.ktor.util.reflect.typeInfo
import kotlin.Any
import kotlin.String
import kotlin.Suppress

/**
 * Decorator for Ktor's ApplicationCall that exposes type-safe respond helpers.
 * Use as a drop-in replacement for [io.ktor.server.application.ApplicationCall].
 */
public class TypedApplicationCall<R : Any>(
    private val applicationCall: ApplicationCall,
) : ApplicationCall by applicationCall {
    @Suppress("unused")
    public suspend inline fun <reified T : R> respondTyped(message: T) {
        respond(message)
    }

    @Suppress("unused")
    public suspend inline fun <reified T : R> respondTyped(
        status: HttpStatusCode,
        message: T,
    ) {
        respond(status, message)
    }
}

/**
 * Gets parameter value associated with this name or null if the name is not present.
 * Converting to type R using ConversionService.
 */
internal inline fun <reified R : Any> Parameters.getTyped(
    name: String,
    conversionService: ConversionService = DefaultConversionService,
): R? {
    val values = getAll(name) ?: return null
    val typeInfo = typeInfo<R>()
    return try {
        @Suppress("UNCHECKED_CAST")
        conversionService.fromValues(values, typeInfo) as R
    } catch (cause: Exception) {
        throw ParameterConversionException(
            name,
            typeInfo.type.simpleName ?: typeInfo.type.toString(),
            cause,
        )
    }
}

/**
 * Gets parameter value associated with this name or throws if the name is not present.
 * Converting to type R using ConversionService.
 */
internal inline fun <reified R : Any> Parameters.getTypedOrFail(
    name: String,
    conversionService: ConversionService = DefaultConversionService,
): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    val typeInfo = typeInfo<R>()
    return try {
        @Suppress("UNCHECKED_CAST")
        conversionService.fromValues(values, typeInfo) as R
    } catch (cause: Exception) {
        throw ParameterConversionException(
            name,
            typeInfo.type.simpleName ?: typeInfo.type.toString(),
            cause,
        )
    }
}

/**
 * Gets first value from the list of values associated with a name.
 */
internal fun Headers.getOrFail(name: String): String =
    this[name] ?: throw
        BadRequestException("Header " + name + " is required")

package com.cjbooms.fabrikt.generators.client

import com.cjbooms.fabrikt.generators.GeneratorUtils.hasMultipleSuccessResponseSchemas
import com.cjbooms.fabrikt.generators.GeneratorUtils.hasOnlyJsonSuccessResponses
import com.cjbooms.fabrikt.generators.model.ModelGenerator.Companion.toModelType
import com.cjbooms.fabrikt.model.KotlinTypeInfo
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Response
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.json.JsonElement

object ClientResponseTypes {
    fun Operation.toSuccessResponseType(basePackage: String): TypeName =
        when {
            hasMultipleSuccessResponseSchemas() -> multiSchemaResponseType()
            else -> singleSchemaResponseType(basePackage)
        }

    private fun Operation.multiSchemaResponseType(): TypeName =
        if (hasOnlyJsonSuccessResponses()) {
            JsonElement::class.asTypeName()
        } else {
            Any::class.asTypeName()
        }

    private fun Operation.singleSchemaResponseType(basePackage: String): TypeName =
        primarySuccessResponse()
            ?.contentMediaTypes
            ?.mapNotNull { it.value?.schema }
            ?.firstOrNull()
            ?.let { toModelType(basePackage, KotlinTypeInfo.from(it), it.isNullable) }
            ?: Unit::class.asTypeName()

    private fun Operation.primarySuccessResponse(): Response? =
        responses
            .filterNot { it.key == "default" }
            .mapNotNull { (code, response) -> code.replace('X', '0').toIntOrNull()?.let { it to response } }
            .toMap()
            .minByOrNull { it.key }
            ?.value
}

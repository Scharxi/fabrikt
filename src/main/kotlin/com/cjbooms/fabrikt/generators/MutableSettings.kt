package com.cjbooms.fabrikt.generators

import com.cjbooms.fabrikt.cli.ClientCodeGenOptionType
import com.cjbooms.fabrikt.cli.CodeGenTypeOverride
import com.cjbooms.fabrikt.cli.CodeGenerationType
import com.cjbooms.fabrikt.cli.ExternalReferencesResolutionMode
import com.cjbooms.fabrikt.cli.InstantLibrary
import com.cjbooms.fabrikt.cli.ModelCodeGenOptionType
import com.cjbooms.fabrikt.cli.OutputOptionType
import com.cjbooms.fabrikt.model.KotlinxSerializationAnnotations
import com.cjbooms.fabrikt.model.SerializationAnnotations

object MutableSettings {
    var generationTypes: Set<CodeGenerationType> = mutableSetOf()
        private set
    var modelOptions: Set<ModelCodeGenOptionType> = mutableSetOf()
        private set
    var modelSuffix: String = ""
        private set
    var clientOptions: Set<ClientCodeGenOptionType> = mutableSetOf()
        private set
    var typeOverrides: Set<CodeGenTypeOverride> = mutableSetOf()
        private set
    var externalRefResolutionMode: ExternalReferencesResolutionMode = ExternalReferencesResolutionMode.default
        private set
    var instantLibrary: InstantLibrary = InstantLibrary.default
        private set
    var outputOptions: Set<OutputOptionType> = mutableSetOf()
        private set

    val serializationAnnotations: SerializationAnnotations
        get() = KotlinxSerializationAnnotations

    val validationAnnotations: ValidationAnnotations
        get() = NoValidationAnnotations

    fun updateSettings(
        genTypes: Set<CodeGenerationType> = emptySet(),
        modelOptions: Set<ModelCodeGenOptionType> = emptySet(),
        modelSuffix: String = "",
        clientOptions: Set<ClientCodeGenOptionType> = emptySet(),
        typeOverrides: Set<CodeGenTypeOverride> = emptySet(),
        externalRefResolutionMode: ExternalReferencesResolutionMode = ExternalReferencesResolutionMode.default,
        instantLibrary: InstantLibrary = InstantLibrary.default,
        outputOptions: Set<OutputOptionType> = emptySet()
    ) {
        this.generationTypes = genTypes
        this.modelOptions = modelOptions - ModelCodeGenOptionType.SEALED_INTERFACES_FOR_ONE_OF
        this.modelSuffix = modelSuffix
        this.clientOptions = clientOptions
        this.typeOverrides = typeOverrides
        this.externalRefResolutionMode = externalRefResolutionMode
        this.instantLibrary = instantLibrary
        this.outputOptions = outputOptions
    }

    fun addOption(option: ModelCodeGenOptionType) {
        modelOptions += option
    }

    fun addOption(override: CodeGenTypeOverride) {
        typeOverrides += override
    }

    fun isSealedInterfacesForOneOfEnabled(): Boolean =
        ModelCodeGenOptionType.DISABLE_SEALED_INTERFACES_FOR_ONE_OF !in modelOptions
}

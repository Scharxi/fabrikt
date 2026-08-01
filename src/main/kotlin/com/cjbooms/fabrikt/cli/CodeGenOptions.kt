package com.cjbooms.fabrikt.cli

enum class CodeGenerationType(val description: String) {
    HTTP_MODELS(
        "Kotlinx-serialization data classes for the schema objects defined in the input."
    ),
    CLIENT(
        "Ktor HTTP client for the endpoints defined in the input, including WebSocket sessions marked with x-websocket."
    );

    override fun toString() = "`${super.toString()}` - $description"
}

enum class ClientCodeGenOptionType(private val description: String) {
    GROUP_BY_TAG("This option groups clients based on the first tag rather than paths");

    override fun toString() = "`${super.toString()}` - $description"
}

enum class ModelCodeGenOptionType(val description: String) {
    X_EXTENSIBLE_ENUMS("This option treats x-extensible-enums as enums"),
    JAVA_SERIALIZATION("This option adds Java Serializable interface to the generated models"),
    INCLUDE_COMPANION_OBJECT("This option adds a companion object to the generated models."),
    @Deprecated("Sealed interfaces are enabled by default in v26+. Use DISABLE_SEALED_INTERFACES_FOR_ONE_OF to disable.")
    SEALED_INTERFACES_FOR_ONE_OF("This option is deprecated. Sealed interfaces are enabled by default in v26+. Use DISABLE_SEALED_INTERFACES_FOR_ONE_OF to disable."),
    DISABLE_SEALED_INTERFACES_FOR_ONE_OF("This option disables the default sealed interfaces for oneOf behavior in v26+"),
    NON_NULL_MAP_VALUES("This option makes map values non-null. The default (since v15) and most spec compliant is make map values nullable"),
    FAULT_TOLERANT_ENUMS("This option adds an UNRECOGNIZED enum entry as a fallback for unmapped values, preventing deserialization exceptions"),
    FAULT_TOLERANT_OPEN_ENUMS("This option converts the \"open enum\" pattern (an `anyOf` combining a string enum with an open `type: string`) into a fault-tolerant enum, i.e. an enum carrying the declared values plus an UNRECOGNIZED fallback, instead of collapsing the type to a plain `String`. Behaves like FAULT_TOLERANT_ENUMS for the affected enums"),
    ;

    override fun toString() = "`${super.toString()}` - $description"
}

enum class CodeGenTypeOverride(val description: String) {
    DATETIME_AS_INSTANT("Use `Instant` as the datetime type. Defaults to `OffsetDateTime`"),
    DATETIME_AS_LOCALDATETIME("Use `LocalDateTime` as the datetime type. Defaults to `OffsetDateTime`"),
    BYTE_AS_STRING("Ignore string format `byte` and use `String` as the type"),
    BINARY_AS_STRING("Ignore string format `binary` and use `String` as the type"),
    URI_AS_STRING("Ignore string format `uri` and use `String` as the type"),
    UUID_AS_STRING("Ignore string format `uuid` and use `String` as the type"),
    DATE_AS_STRING("Ignore string format `date` and use `String` as the type"),
    DATETIME_AS_STRING("Ignore string format `date-time` and use `String` as the type"),
    BYTEARRAY_AS_INPUTSTREAM("Use `InputStream` as ByteArray type. Defaults to `ByteArray`"),
    ANY_AS_JSONELEMENT("Use `kotlinx.serialization.json.JsonElement` for untyped (any) schemas and `JsonObject` for untyped objects. Defaults to `Any`");

    override fun toString() = "`${super.toString()}` - $description"
}

enum class OutputOptionType(val description: String) {
    ADD_FILE_DISCLAIMER("This option adds a disclaimer to the generated files.");

    override fun toString() = "`${super.toString()}` - $description"
}

enum class InstantLibrary(val description: String) {
    KOTLINX_INSTANT("Use `kotlinx.datetime` Instant in generated classes (default)"),
    KOTLIN_TIME_INSTANT("Use `kotlin.time` Instant in generated classes");

    override fun toString() = "`${super.toString()}` - $description"

    companion object {
        val default = KOTLINX_INSTANT
    }
}

enum class ExternalReferencesResolutionMode(val description: String) {
    TARGETED("Generate models only for directly referenced schemas in external API files."),
    AGGRESSIVE("Referencing any schema in an external API file triggers generation of every external schema in that file.");

    override fun toString() = "`${super.toString()}` - $description"

    companion object {
        val default = TARGETED
    }
}

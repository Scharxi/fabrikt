import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val generationDir = "$buildDir/generated"

sourceSets {
    main { java.srcDirs("$generationDir/src/main/kotlin") }
    test { java.srcDirs("$generationDir/src/test/kotlin") }
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.0.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // ktor client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    // ktor server, used to exercise the generated client against a real socket
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty.jvm)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.content.negotiation.jvm)

    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.assertj.core)
}

tasks {
    val generateCode = createGenerateCodeTask(
        "generateKtorWebSocketCode",
        "src/test/resources/examples/ktorClientWebSocket/api.yaml"
    )

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
            optIn.add("kotlin.time.ExperimentalTime")
            jvmTarget.set(JvmTarget.JVM_17)
        }
        dependsOn(generateCode)
    }

    withType<Test> {
        useJUnitPlatform()
        jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED")
        dependsOn(generateCode)
    }
}

fun createGenerateCodeTask(name: String, apiFilePath: String, additionalArgs: List<String> = emptyList()) =
    tasks.create(name, JavaExec::class) {
    val apiFile = "${rootProject.projectDir}/$apiFilePath"
    inputs.files(apiFile)
    outputs.dir(generationDir)
    outputs.cacheIf { true }
    classpath = rootProject.files("./build/libs/fabrikt-${rootProject.version}.jar")
    mainClass.set("io.fabrikt.cli.CodeGen")
    args = listOf(
        "--output-directory", generationDir,
        "--base-package", "com.example",
        "--api-file", apiFile,
        "--targets", "http_models",
        "--targets", "client",
        "--http-model-opts", "DISABLE_SEALED_INTERFACES_FOR_ONE_OF"
    ).plus(additionalArgs)
    dependsOn(":shadowJar")
}

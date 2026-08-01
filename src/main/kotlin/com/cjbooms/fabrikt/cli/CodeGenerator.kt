package com.cjbooms.fabrikt.cli

import com.cjbooms.fabrikt.cli.CodeGenerationType.CLIENT
import com.cjbooms.fabrikt.cli.CodeGenerationType.HTTP_MODELS
import com.cjbooms.fabrikt.cli.CodeGenerationType.SERVER
import com.cjbooms.fabrikt.configurations.Packages
import com.cjbooms.fabrikt.generators.MutableSettings
import com.cjbooms.fabrikt.generators.client.KtorClientGenerator
import com.cjbooms.fabrikt.generators.model.ModelGenerator
import com.cjbooms.fabrikt.generators.server.KtorServerGenerator
import com.cjbooms.fabrikt.model.GeneratedFile
import com.cjbooms.fabrikt.model.KotlinSourceSet
import com.cjbooms.fabrikt.model.Models
import com.cjbooms.fabrikt.model.SourceApi
import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

class CodeGenerator(
    private val packages: Packages,
    private val sourceApi: SourceApi,
    private val srcPath: Path,
    @Suppress("UNUSED_PARAMETER") private val resourcesPath: Path,
) {

    fun generate(): Collection<GeneratedFile> = MutableSettings.generationTypes.map(::generateCode).flatten()

    private fun generateCode(generationType: CodeGenerationType): Collection<GeneratedFile> =
        when (generationType) {
            CLIENT -> generateClient()
            SERVER -> generateServer()
            HTTP_MODELS -> generateModels()
        }

    private fun generateModels(): Collection<GeneratedFile> = sourceSet(models().files)

    private fun generateClient(): Collection<GeneratedFile> {
        val clientGenerator = KtorClientGenerator(packages, sourceApi, srcPath)
        val options = MutableSettings.clientOptions
        val clientFiles = clientGenerator.generate(options).files
        val libFiles = clientGenerator.generateLibrary(options)
        return sourceSet(clientFiles).plus(libFiles).plus(sourceSet(models().files))
    }

    private fun generateServer(): Collection<GeneratedFile> {
        val serverGenerator = KtorServerGenerator(packages, sourceApi, srcPath)
        val options = MutableSettings.serverOptions
        val serverFiles = serverGenerator.generate(options).files
        val libFiles = serverGenerator.generateLibrary(options)
        return sourceSet(serverFiles).plus(libFiles).plus(sourceSet(models().files))
    }

    private fun sourceSet(fileSpec: Collection<FileSpec>) = setOf(KotlinSourceSet(fileSpec, srcPath))

    private fun models(): Models =
        ModelGenerator(packages, sourceApi).generate()
}

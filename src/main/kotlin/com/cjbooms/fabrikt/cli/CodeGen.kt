package com.cjbooms.fabrikt.cli

import com.beust.jcommander.ParameterException
import com.cjbooms.fabrikt.configurations.Packages
import com.cjbooms.fabrikt.generators.MutableSettings
import com.cjbooms.fabrikt.model.SourceApi
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

object CodeGen {

    private val logger = Logger.getGlobal()

    @JvmStatic
    fun main(args: Array<String>) {
        val codeGenArgs = CodeGenArgs.parse(args)

        if (Files.notExists(codeGenArgs.apiFile))
            throw ParameterException("Could not find api file '${codeGenArgs.apiFile}', Specify its location with the -a option. Use --help for further information.")

        MutableSettings.updateSettings(
            genTypes = codeGenArgs.targets,
            modelOptions = codeGenArgs.modelOptions,
            modelSuffix = codeGenArgs.modelSuffix,
            clientOptions = codeGenArgs.clientOptions,
            typeOverrides = codeGenArgs.typeOverrides,
            externalRefResolutionMode = codeGenArgs.externalRefResolutionMode,
            instantLibrary = codeGenArgs.instantLibrary,
            outputOptions = codeGenArgs.outputOptions,
        )
        generate(
            basePackage = codeGenArgs.basePackage,
            pathToApi = codeGenArgs.apiFile.toAbsolutePath(),
            outputDir = codeGenArgs.outputDirectory,
            apiFragments = codeGenArgs.apiFragments.map { it.toFile().readText() },
            srcPath = codeGenArgs.srcPath,
            resourcesPath = codeGenArgs.resourcesPath,
        )
    }

    private fun generate(
        basePackage: String,
        pathToApi: Path,
        outputDir: Path,
        apiFragments: List<String> = emptyList(),
        srcPath: Path,
        resourcesPath: Path,
    ) {

        val suppliedApi = pathToApi.toFile().readText()
        val baseDir = pathToApi.parent

        logger.info("Generating code and dumping to $outputDir/")

        val packages = Packages(basePackage)
        val sourceApi = SourceApi.create(suppliedApi, apiFragments, baseDir)
        val generator = CodeGenerator(packages, sourceApi, srcPath, resourcesPath)
        generator.generate().forEach { it.writeFileTo(outputDir.toFile()) }
    }
}

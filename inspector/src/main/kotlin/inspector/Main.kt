package inspector

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import inspector.util.AnimeExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ConfigKodeinModule
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.streams.asSequence
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}
private val androidCompat by lazy { AndroidCompat() }

suspend fun main(args: Array<String>) {
    relaunchWithNoVerifyIfNeeded(args)

    if (args.size < 3) {
        throw RuntimeException("Inspector must be given the path of apks directory, output json, and a tmp dir")
    }

    val (apksPath, outputPath, tmpDirPath) = args

    initApplication()

    val tmpDir = File(tmpDirPath, "tmp").also(File::mkdir)
    val extensions =
        Files
            .find(Paths.get(apksPath), 2, { _, fileAttributes -> fileAttributes.isRegularFile })
            .asSequence()
            .filter { it.extension == "apk" }
            .toList()

    logger.info { "Found ${extensions.size} extensions" }

    val extensionsInfo =
        coroutineScope {
            extensions
                .map {
                    async {
                        logger.debug { "Installing $it" }
                        val (pkgName, sources) = AnimeExtension.installApk(tmpDir) { it.toFile() }
                        pkgName to sources.map(::SourceJson)
                    }
                }.awaitAll()
        }.toMap()

    File(outputPath).writeText(Json.encodeToString(extensionsInfo))
}

private fun relaunchWithNoVerifyIfNeeded(args: Array<String>) {
    if (System.getProperty("inspector.noverify.reexec") == "true") {
        return
    }

    val javaExecutable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
    val javaBin =
        Paths
            .get(System.getProperty("java.home"), "bin", javaExecutable)
            .absolutePathString()
    val classPath = System.getProperty("java.class.path")
    val command =
        mutableListOf(
            javaBin,
            "-noverify",
            "-Dinspector.noverify.reexec=true",
        )

    if (classPath.endsWith(".jar") && File(classPath).isFile) {
        command += listOf("-jar", classPath)
    } else {
        command += listOf("-cp", classPath, "inspector.MainKt")
    }
    command += args

    val exitCode =
        ProcessBuilder(command)
            .inheritIO()
            .start()
            .waitFor()
    exitProcess(exitCode)
}

private fun initApplication() {
    logger.info { "Running Inspector ${BuildConfig.VERSION} revision ${BuildConfig.REVISION}" }

    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())
}

@Serializable
private data class SourceJson(
    val name: String,
    val lang: String,
    val id: String,
    val baseUrl: String,
    val versionId: Int,
) {
    constructor(source: AnimeHttpSource) :
        this(
            source.name,
            source.lang,
            source.id.toString(),
            source.baseUrl,
            source.versionId,
        )
}

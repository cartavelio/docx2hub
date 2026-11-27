package org.cartavelio.docx2hub

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Execute public CLI boundaries with bounded children and retain exact commands/results. */
data class CommandResult(
    val command: List<String>,
    val exit: Int,
    val log: String,
)

object Evidence {
    private val json = ObjectMapper()
    private const val PROCESS_SECONDS = 45L

    fun writeJson(
        path: Path,
        value: Any,
    ) {
        path.writeText(json.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n")
    }

    fun command(
        command: List<String>,
        log: Path,
    ): CommandResult {
        log.parent.createDirectories()
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            check(process.waitFor(PROCESS_SECONDS, TimeUnit.SECONDS)) { "Process timed out: $command; log=$log" }
            return CommandResult(command, process.exitValue(), log.readText())
        } finally {
            if (process.isAlive) {
                process.destroyForcibly().waitFor()
            }
        }
    }

    fun archive(
        repo: Path,
        revision: String,
        target: Path,
    ) {
        val result =
            command(
                listOf("git", "-C", repo.toString(), "archive", "--format=zip", "--output=$target", revision),
                target.resolveSibling("$revision-archive.log"),
            )
        check(result.exit == 0) { result.log }
        Archives.zip(target.readBytes(), target.resolveSibling("source"))
    }
}

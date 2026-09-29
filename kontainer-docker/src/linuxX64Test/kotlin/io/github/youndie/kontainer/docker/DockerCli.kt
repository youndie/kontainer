@file:OptIn(ExperimentalForeignApi::class)

package io.github.youndie.kontainer.docker

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import kotlin.random.Random

/**
 * The `docker` CLI, for setting a test up and for asking a second witness. Never the subject: the
 * client under test must not be the thing that creates what it is then checked against.
 */
internal object DockerCli {
    fun run(vararg arguments: String): String {
        val command = (listOf("docker") + arguments).joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        val pipe = popen("$command 2>&1", "r") ?: error("cannot start: $command")
        val output = StringBuilder()
        val buffer = ByteArray(4096)
        while (fgets(buffer.refTo(0), buffer.size, pipe) != null) output.append(buffer.toKString())
        val status = pclose(pipe)
        check(status == 0) { "$command exited with $status: $output" }
        return output.toString().trim()
    }

    /**
     * A Postgres container with one port published on an ephemeral loopback port and a label unique to
     * this call, so that a filter can only ever match it. Removed by [remove].
     */
    fun startPostgres(): ScratchContainer {
        val mark = "b02-" + Random.nextLong().toULong().toString(36)
        val id =
            run(
                "run",
                "-d",
                "-e",
                "POSTGRES_PASSWORD=kontainer",
                "-p",
                "127.0.0.1::5432",
                "--label",
                "kontainer.test=$mark",
                "postgres:18-alpine",
            )
        return ScratchContainer(id, mark)
    }

    fun remove(container: ScratchContainer) {
        run("rm", "-f", "-v", container.id)
    }
}

internal data class ScratchContainer(
    val id: String,
    val mark: String,
)

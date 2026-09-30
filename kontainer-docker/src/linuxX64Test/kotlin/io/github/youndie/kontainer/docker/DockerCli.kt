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
     * `docker run -d`, and the id of the container it started. The id is the last line: on a machine
     * without the image the pull progress comes first on the same output, which a hosted runner showed
     * and the build box, whose cache was warm, never did (B-13).
     */
    fun runDetached(vararg arguments: String): String {
        val output = run("run", "-d", *arguments)
        val id = output.lines().last().trim()
        check(CONTAINER_ID.matches(id)) { "docker run -d printed no container id last: $output" }
        return id
    }

    private val CONTAINER_ID = Regex("[0-9a-f]{64}")

    /**
     * A Postgres container with one port published on an ephemeral loopback port and a label unique to
     * this call, so that a filter can only ever match it. Removed by [remove].
     */
    fun startPostgres(): ScratchContainer {
        val mark = "b02-" + Random.nextLong().toULong().toString(36)
        val id =
            runDetached(
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

    /**
     * A container that runs [script] with `sh -c` and then sleeps, labelled like [startPostgres]. With
     * [tty] it gets a terminal, which changes how its log is written.
     */
    fun startShell(
        script: String,
        tty: Boolean = false,
    ): ScratchContainer {
        val mark = "b03-" + Random.nextLong().toULong().toString(36)
        val terminal = if (tty) listOf("-t") else emptyList()
        val arguments =
            terminal + listOf("--label", "kontainer.test=$mark", "postgres:18-alpine", "sh", "-c", "$script; sleep 300")
        return ScratchContainer(runDetached(*arguments.toTypedArray()), mark)
    }

    fun remove(container: ScratchContainer) {
        run("rm", "-f", "-v", container.id)
    }
}

internal data class ScratchContainer(
    val id: String,
    val mark: String,
)

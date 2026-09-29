package io.github.youndie.kontainer.docker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Exec and logs against the engine of the machine the tests run on. */
class StreamsTest {
    @Test
    fun exec_keeps_stdout_and_stderr_apart_and_returns_the_exit_code() =
        withContainer("true") { engine, container ->
            val result = engine.exec(container.id, listOf("sh", "-c", "echo out; echo err >&2; exit 3"))
            assertEquals(ExecResult(exitCode = 3, stdout = "out\n", stderr = "err\n"), result)
        }

    @Test
    fun an_output_of_more_than_a_mebibyte_arrives_whole_and_in_order() =
        withContainer("true") { engine, container ->
            // 200 000 lines, 1 288 895 bytes: many frames, and any lost, repeated or reordered one shows.
            val result = engine.exec(container.id, listOf("seq", "1", "200000"))
            assertEquals(0, result.exitCode)
            assertTrue(result.stdout.length > 1_048_576, "only ${result.stdout.length} bytes")
            val lines = result.stdout.trimEnd('\n').split('\n')
            assertEquals(200_000, lines.size)
            lines.forEachIndexed { index, line -> assertEquals((index + 1).toString(), line) }
            assertEquals("", result.stderr)
        }

    @Test
    fun exec_in_a_stopped_container_is_a_conflict() =
        withContainer("true") { engine, container ->
            engine.kill(container.id)
            val conflict = assertFailsWith<DockerError.Conflict> { engine.exec(container.id, listOf("true")) }
            assertTrue("is not running" in conflict.engineMessage)
        }

    @Test
    fun logs_keep_the_two_streams_apart() =
        withContainer("echo first; echo to-out; echo to-err >&2") { engine, container ->
            val logs =
                eventually { engine.logs(container.id).takeIf { "to-err" in it.stderr && "to-out" in it.stdout } }
            assertEquals("first\nto-out\n", logs.stdout)
            assertEquals("to-err\n", logs.stderr)
        }

    @Test
    fun tail_returns_the_last_lines() =
        // One stream only: across two, the stored order is the order the lines arrived in, which B-03
        // saw differ from the order they were written.
        withContainer("echo one; echo two; echo three") { engine, container ->
            eventually { engine.logs(container.id).takeIf { "three" in it.stdout } }
            assertEquals("two\nthree\n", engine.logs(container.id, tail = 2).stdout)
        }

    @Test
    fun a_container_with_a_tty_logs_one_unframed_stream() =
        withContainer("echo tty-out", tty = true) { engine, container ->
            val logs = eventually { engine.logs(container.id).takeIf { "tty-out" in it.stdout } }
            // A terminal ends lines with CR LF, and there is no second stream to split out.
            assertEquals("tty-out\r\n", logs.stdout)
            assertEquals("", logs.stderr)
        }

    private suspend fun <T : Any> eventually(probe: suspend () -> T?): T {
        repeat(100) {
            probe()?.let { return it }
            withContext(Dispatchers.Default) { delay(100.milliseconds) }
        }
        return probe() ?: error("not observed within 10 s")
    }

    private fun withContainer(
        script: String,
        tty: Boolean = false,
        block: suspend (DockerEngine, ScratchContainer) -> Unit,
    ) = runTest(timeout = 2.minutes) {
        val container = DockerCli.startShell(script, tty)
        try {
            DockerEngine.fromEnvironment().use { block(it, container) }
        } finally {
            DockerCli.remove(container)
        }
    }
}

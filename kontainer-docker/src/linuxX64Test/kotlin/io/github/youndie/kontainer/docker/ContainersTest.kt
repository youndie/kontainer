package io.github.youndie.kontainer.docker

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The container calls against the engine of the machine the tests run on, with the `docker` CLI as the
 * second witness for what the client reports.
 */
class ContainersTest {
    @Test
    fun a_second_pause_is_a_conflict_and_an_unknown_id_is_named() =
        withPostgres { engine, container ->
            engine.pause(container.id)
            val conflict = assertFailsWith<DockerError.Conflict> { engine.pause(container.id) }
            assertEquals(container.id, conflict.id)
            engine.unpause(container.id)

            val missing = "kontainer-no-such-container"
            val unknown = assertFailsWith<DockerError.NoSuchContainer> { engine.pause(missing) }
            assertEquals(missing, unknown.id)
            assertTrue(missing in unknown.message.orEmpty())
        }

    @Test
    fun inspect_reports_the_host_port_docker_port_prints() =
        withPostgres { engine, container ->
            // `docker port` prints "127.0.0.1:37810": the witness the client is held against.
            val printed = DockerCli.run("port", container.id, "5432/tcp").lines().first()
            val published = engine.inspect(container.id).ports.single { it.containerPort == 5432 }
            assertEquals(printed, "${published.hostIp}:${published.hostPort}")
            assertEquals("tcp", published.protocol)
        }

    @Test
    fun containers_are_found_by_label_and_only_by_label() =
        withPostgres { engine, container ->
            val found = engine.containers(mapOf("kontainer.test" to container.mark))
            assertEquals(listOf(container.id), found.map { it.id })
            assertEquals("running", found.single().state)
            assertTrue(engine.containers(mapOf("kontainer.test" to container.mark + "-nobody")).isEmpty())
        }

    @Test
    fun stop_then_start_leaves_it_running() =
        withPostgres { engine, container ->
            engine.stop(container.id, timeout = 1.seconds)
            val stopped = engine.inspect(container.id)
            assertFalse(stopped.running)
            assertEquals("exited", stopped.status)
            // Stopping a stopped container is the state that was asked for (the engine answers 304).
            engine.stop(container.id, timeout = 1.seconds)

            engine.start(container.id)
            val started = engine.inspect(container.id)
            assertTrue(started.running)
            assertEquals("running", started.status)
        }

    @Test
    fun kill_of_a_stopped_container_is_a_conflict() =
        withPostgres { engine, container ->
            engine.kill(container.id)
            assertFalse(engine.inspect(container.id).running)
            assertFailsWith<DockerError.Conflict> { engine.kill(container.id) }
        }

    private fun withPostgres(block: suspend (DockerEngine, ScratchContainer) -> Unit) =
        runTest(timeout = 2.minutes) {
            val container = DockerCli.startPostgres()
            try {
                DockerEngine.fromEnvironment().use { block(it, container) }
            } finally {
                DockerCli.remove(container)
            }
        }
}

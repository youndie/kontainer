package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import kotlinx.coroutines.test.runTest
import platform.posix.getppid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Fixtures left by dead processes, against the engine and compose of the machine the tests run on. */
class ReapTest {
    @Test
    fun an_owned_fixture_of_a_dead_process_is_removed_by_the_next_up() =
        withLeftover(owner = "${deadPid()}@${hostName()}", kind = "owned") { project, fixture ->
            assertTrue(project in fixture.reaped, "reaped: ${fixture.reaped}")
            assertTrue(containersOf(project).isEmpty(), "the abandoned fixture's containers are still there")
            assertEquals("", docker("volume", "ls", "-q", "--filter", "name=${project}_data"))
        }

    @Test
    fun a_fixture_whose_owner_is_alive_is_left_alone() =
        withLeftover(owner = "${getppid()}@${hostName()}", kind = "owned") { project, fixture ->
            assertFalse(project in fixture.reaped)
            assertTrue(containersOf(project).isNotEmpty())
        }

    @Test
    fun a_fixture_of_another_host_is_left_alone() =
        withLeftover(owner = "${deadPid()}@some-other-host", kind = "owned") { project, fixture ->
            assertFalse(project in fixture.reaped)
            assertTrue(containersOf(project).isNotEmpty())
        }

    @Test
    fun a_shared_fixture_outlives_its_owner() =
        withLeftover(owner = "${deadPid()}@${hostName()}", kind = "shared") { project, fixture ->
            assertFalse(project in fixture.reaped)
            assertTrue(containersOf(project).isNotEmpty())
        }

    /**
     * A fixture brought up by the compose CLI directly, labelled as if [owner] had made it, then an
     * ordinary fixture's `up`. The leftover is removed afterwards whatever the test found.
     */
    private fun withLeftover(
        owner: String,
        kind: String,
        block: suspend (project: String, fixture: Fixture) -> Unit,
    ) = runTest(timeout = 4.minutes) {
        val project = "kontainer-test-leftover-" + Random.nextLong().toULong().toString(36)
        withComposeFile(LEFTOVER) { leftover ->
            withComposeFile(
                """
                services:
                  pg:
                    labels:
                      "${Fixture.OWNER_LABEL}": "$owner"
                      "${Fixture.KIND_LABEL}": "$kind"
                """,
            ) { labels ->
                try {
                    docker("compose", "-p", project, "-f", leftover, "-f", labels, "up", "-d")
                    withComposeFile(LEFTOVER) { file ->
                        val fixture = Fixture.owned(listOf(file))
                        try {
                            fixture.up()
                            block(project, fixture)
                        } finally {
                            fixture.down()
                        }
                    }
                } finally {
                    docker("compose", "-p", project, "down", "-v", "--remove-orphans")
                }
            }
        }
    }

    private suspend fun containersOf(project: String) =
        DockerEngine.fromEnvironment().use { it.containers(mapOf(Fixture.PROJECT_LABEL to project)) }

    /** The pid of a shell that has already exited: a process that certainly does not exist now. */
    private fun deadPid(): Int = runCommand(listOf("sh", "-c", "echo \$\$"), emptyMap()).output.trim().toInt()

    private companion object {
        const val LEFTOVER = """
            services:
              pg:
                image: postgres:18-alpine
                environment: { POSTGRES_PASSWORD: kontainer }
                volumes: [ "data:/var/lib/postgresql" ]
            volumes:
              data: {}
            """
    }
}

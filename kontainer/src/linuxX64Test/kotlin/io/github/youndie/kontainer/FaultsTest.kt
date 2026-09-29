package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Faults on a fixture, against Postgres on the machine the tests run on. */
class FaultsTest {
    @Test
    fun an_exception_inside_paused_reaches_the_caller_and_leaves_the_service_ready() =
        withPostgres(owned = true) { fixture ->
            val boom = IllegalStateException("x")
            val caught =
                assertFailsWith<IllegalStateException> {
                    fixture.paused("pg", 5432, Probe.postgres()) {
                        assertTrue(inspect(fixture).paused, "the block ran with the service not paused")
                        val silent =
                            assertFailsWith<FixtureError.NotReady> {
                                fixture.awaitReady(
                                    "pg",
                                    5432,
                                    Probe.postgres(),
                                    timeout = 3.seconds,
                                )
                            }
                        assertTrue("no answer" in silent.lastCause, silent.lastCause)
                        throw boom
                    }
                }
            assertSame(boom, caught)
            assertFalse(inspect(fixture).paused)
            assertEquals(0, pgIsReady(fixture.port("pg", 5432)), "the service was not ready when paused { } returned")
        }

    @Test
    fun paused_returns_the_block_result_with_the_service_ready() =
        withPostgres(owned = true) { fixture ->
            val result = fixture.paused("pg", 5432, Probe.postgres()) { 42 }
            assertEquals(42, result)
            assertEquals(0, pgIsReady(fixture.port("pg", 5432)))
        }

    @Test
    fun a_stopped_service_refuses_connections_and_comes_back_on_the_same_port() =
        withPostgres(owned = true) { fixture ->
            val port = fixture.port("pg", 5432)
            fixture.stopped("pg", 5432, Probe.postgres(), grace = 1.seconds) {
                assertFalse(inspect(fixture).running)
                assertFalse(tcpConnects(port), "a connection to a stopped service's port was accepted")
            }
            assertEquals(port, fixture.port("pg", 5432))
            assertEquals(0, pgIsReady(port))
        }

    @Test
    fun unpause_of_a_running_service_is_not_an_error() =
        withPostgres(owned = true) { fixture ->
            fixture.unpause("pg")
            assertTrue(inspect(fixture).running)
        }

    @Test
    fun faults_on_a_shared_fixture_are_refused_and_touch_nothing() =
        withPostgres(owned = false) { fixture ->
            val refused = assertFailsWith<FixtureError.SharedFixture> { fixture.pause("pg") }
            assertEquals("pg", refused.service)
            assertFailsWith<FixtureError.SharedFixture> {
                fixture.stopped(
                    "pg",
                    5432,
                    Probe.postgres(),
                ) { error("never runs") }
            }
            val details = inspect(fixture)
            assertTrue(details.running)
            assertFalse(details.paused)
        }

    private fun withPostgres(
        owned: Boolean,
        block: suspend (Fixture) -> Unit,
    ) = runTest(timeout = 4.minutes) {
        withComposeFile(
            """
            services:
              pg:
                image: postgres:18-alpine
                environment: { POSTGRES_PASSWORD: kontainer }
                ports: [ "127.0.0.1:${'$'}{KONTAINER_PORT_PG_5432}:5432" ]
            """,
        ) { file ->
            val ports = mapOf("pg" to listOf(5432))
            val fixture =
                if (owned) {
                    Fixture.owned(listOf(file), ports = ports)
                } else {
                    Fixture.shared("kontainer-test-shared-" + Random.nextInt(1_000_000), listOf(file), ports = ports)
                }
            try {
                fixture.up()
                fixture.awaitReady("pg", 5432, Probe.postgres())
                block(fixture)
            } finally {
                fixture.down()
            }
        }
    }

    private suspend fun inspect(fixture: Fixture) =
        DockerEngine.fromEnvironment().use {
            it.inspect(fixture.containerId("pg"))
        }

    private fun pgIsReady(port: Int): Int =
        runCommand(
            listOf(
                "docker",
                "run",
                "--rm",
                "--network",
                "host",
                "postgres:18-alpine",
                "pg_isready",
                "-h",
                "127.0.0.1",
                "-p",
                "$port",
            ),
            emptyMap(),
        ).exitCode

    private fun tcpConnects(port: Int): Boolean =
        runCommand(listOf("bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/$port"), emptyMap()).exitCode == 0
}

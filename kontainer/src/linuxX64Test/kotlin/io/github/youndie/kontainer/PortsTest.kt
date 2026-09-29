package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Host ports chosen before `up`, against the engine and compose of the machine the tests run on. */
class PortsTest {
    @Test
    fun the_chosen_port_survives_stop_and_start_and_postgres_answers_on_it() =
        runTest(timeout = 4.minutes) {
            withComposeFile(POSTGRES_ON_CHOSEN_PORT) { file ->
                val fixture = Fixture.owned(listOf(file), ports = mapOf("pg" to listOf(5432)))
                try {
                    fixture.up()
                    val port = fixture.port("pg", 5432)
                    awaitPgIsReady(port)
                    DockerEngine.fromEnvironment().use { engine ->
                        val id = fixture.containerId("pg")
                        engine.stop(id, timeout = 1.seconds)
                        engine.start(id)
                        assertEquals(
                            listOf(port),
                            engine
                                .inspect(id)
                                .ports
                                .filter { it.containerPort == 5432 }
                                .map { it.hostPort },
                        )
                    }
                    assertEquals(port, fixture.port("pg", 5432))
                    awaitPgIsReady(port)
                } finally {
                    fixture.down()
                }
            }
        }

    @Test
    fun a_fixed_port_in_the_compose_file_fails_up_naming_the_service_and_the_port() =
        runTest(timeout = 3.minutes) {
            val fixed = freeLoopbackPort()
            withComposeFile(
                """
                services:
                  pg:
                    image: postgres:18-alpine
                    environment: { POSTGRES_PASSWORD: kontainer }
                    ports: [ "127.0.0.1:$fixed:5432" ]
                """,
            ) { file ->
                val fixture = Fixture.owned(listOf(file), ports = mapOf("pg" to listOf(5432)))
                try {
                    val mismatch = assertFailsWith<FixtureError.PortMismatch> { fixture.up() }
                    assertEquals("pg", mismatch.service)
                    assertEquals(5432, mismatch.containerPort)
                    assertEquals(fixed, mismatch.published)
                    assertTrue("KONTAINER_PORT_PG_5432" in mismatch.message.orEmpty())
                } finally {
                    fixture.down()
                }
            }
        }

    @Test
    fun a_port_the_compose_file_does_not_publish_fails_up() =
        runTest(timeout = 3.minutes) {
            withComposeFile(
                """
                services:
                  pg:
                    image: postgres:18-alpine
                    environment: { POSTGRES_PASSWORD: kontainer }
                """,
            ) { file ->
                val fixture = Fixture.owned(listOf(file), ports = mapOf("pg" to listOf(5432)))
                try {
                    val missing = assertFailsWith<FixtureError.PortNotPublished> { fixture.up() }
                    assertEquals("pg", missing.service)
                    assertEquals(5432, missing.containerPort)
                } finally {
                    fixture.down()
                }
            }
        }

    @Test
    fun the_port_variable_is_named_after_the_service_and_the_port() {
        assertEquals("KONTAINER_PORT_PG_5432", portVariable("pg", 5432))
        assertEquals("KONTAINER_PORT_KAFKA_BROKER_9092", portVariable("kafka-broker", 9092))
    }

    @Test
    fun a_free_loopback_port_is_a_port() {
        val port = freeLoopbackPort()
        assertTrue(port in 1024..65535, "port $port")
        assertNotEquals(port, freeLoopbackPort(), "the kernel handed out the same ephemeral port twice in a row")
    }

    /** `pg_isready` from the host, through the published port (research D6), until it answers. */
    private suspend fun awaitPgIsReady(port: Int) {
        val command =
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
            )
        repeat(60) {
            if (runCommand(command, emptyMap()).exitCode == 0) return
            withContext(Dispatchers.Default) { delay(1.seconds) }
        }
        error("Postgres did not answer on 127.0.0.1:$port")
    }

    private companion object {
        const val POSTGRES_ON_CHOSEN_PORT = """
            services:
              pg:
                image: postgres:18-alpine
                environment: { POSTGRES_PASSWORD: kontainer }
                ports: [ "127.0.0.1:${'$'}{KONTAINER_PORT_PG_5432}:5432" ]
            """
    }
}

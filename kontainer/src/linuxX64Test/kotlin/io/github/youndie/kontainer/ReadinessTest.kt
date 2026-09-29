package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Readiness by protocol, against real services on the machine the tests run on. */
class ReadinessTest {
    @Test
    fun postgres_is_ready_only_when_pg_isready_agrees_and_not_while_paused() =
        runTest(timeout = 4.minutes) {
            withFixture(POSTGRES, mapOf("pg" to listOf(5432))) { fixture ->
                fixture.awaitReady("pg", 5432, Probe.postgres())
                val port = fixture.port("pg", 5432)
                // The second witness, asked right after: from the host, through the same port.
                assertEquals(0, pgIsReady(port), "awaitReady returned before pg_isready answered on $port")

                DockerEngine.fromEnvironment().use { engine ->
                    val id = fixture.containerId("pg")
                    engine.pause(id)
                    try {
                        assertTrue(tcpConnects(port), "the published port of a paused container stopped accepting TCP")
                        val refused =
                            assertFailsWith<FixtureError.NotReady> {
                                fixture.awaitReady(
                                    "pg",
                                    5432,
                                    Probe.postgres(),
                                    timeout = 3.seconds,
                                )
                            }
                        assertTrue("no answer" in refused.lastCause, refused.lastCause)
                    } finally {
                        engine.unpause(id)
                    }
                }
            }
        }

    @Test
    fun the_kafka_probe_says_no_to_postgres() =
        runTest(timeout = 4.minutes) {
            withFixture(POSTGRES, mapOf("pg" to listOf(5432))) { fixture ->
                fixture.awaitReady("pg", 5432, Probe.postgres())
                val refused =
                    assertFailsWith<FixtureError.NotReady> {
                        fixture.awaitReady(
                            "pg",
                            5432,
                            Probe.kafka(),
                            timeout = 3.seconds,
                        )
                    }
                assertTrue("not Kafka" in refused.lastCause, refused.lastCause)
            }
        }

    @Test
    fun the_kafka_probe_says_yes_to_a_broker_and_the_broker_agrees() =
        runTest(timeout = 4.minutes) {
            withFixture(kafka(controllerListenerNames = true), mapOf("broker" to listOf(9092))) { fixture ->
                fixture.awaitReady("broker", 9092, Probe.kafka(), timeout = 90.seconds)
                val witness =
                    DockerEngine.fromEnvironment().use {
                        it.exec(
                            fixture.containerId("broker"),
                            listOf(
                                "/opt/kafka/bin/kafka-broker-api-versions.sh",
                                "--bootstrap-server",
                                "127.0.0.1:9092",
                            ),
                        )
                    }
                assertEquals(0, witness.exitCode, witness.stderr)
            }
        }

    @Test
    fun a_broker_that_never_started_is_not_ready_and_its_log_says_why() =
        runTest(timeout = 4.minutes) {
            withComposeFile(kafka(controllerListenerNames = false)) { file ->
                val fixture = Fixture.owned(listOf(file), ports = mapOf("broker" to listOf(9092)))
                try {
                    val started = TimeSource.Monotonic.markNow()
                    val refused =
                        assertFailsWith<FixtureError.NotReady> {
                            fixture.up()
                            fixture.awaitReady("broker", 9092, Probe.kafka(), timeout = 60.seconds)
                        }
                    assertTrue("controller.listener.names" in refused.logTail, refused.logTail)
                    // An exited container fails at once, not at the end of the timeout: nothing will answer.
                    assertTrue(started.elapsedNow() < 30.seconds, "took ${started.elapsedNow()}")
                } finally {
                    fixture.down()
                }
            }
        }

    @Test
    fun the_http_probe_wants_the_status_it_was_told() =
        runTest(timeout = 3.minutes) {
            withFixture(NGINX, mapOf("web" to listOf(80))) { fixture ->
                fixture.awaitReady("web", 80, Probe.http("/", 200))
                val refused =
                    assertFailsWith<FixtureError.NotReady> {
                        fixture.awaitReady("web", 80, Probe.http("/nowhere", 200), timeout = 2.seconds)
                    }
                assertTrue("answered 404" in refused.lastCause, refused.lastCause)
            }
        }

    private suspend fun withFixture(
        yaml: String,
        ports: Map<String, List<Int>>,
        block: suspend (Fixture) -> Unit,
    ) = withComposeFile(yaml) { file ->
        val fixture = Fixture.owned(listOf(file), ports = ports)
        try {
            fixture.up()
            block(fixture)
        } finally {
            fixture.down()
        }
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

    private companion object {
        val POSTGRES =
            """
            services:
              pg:
                image: postgres:18-alpine
                environment: { POSTGRES_PASSWORD: kontainer }
                ports: [ "127.0.0.1:${'$'}{KONTAINER_PORT_PG_5432}:5432" ]
            """

        val NGINX =
            """
            services:
              web:
                image: nginx:alpine
                ports: [ "127.0.0.1:${'$'}{KONTAINER_PORT_WEB_80}:80" ]
            """

        /** A single KRaft node, as kafkakn's fixture, advertising the port kontainer chose (research D5). */
        fun kafka(controllerListenerNames: Boolean): String =
            listOfNotNull(
                "services:",
                "  broker:",
                "    image: apache/kafka:4.3.1",
                "    ports: [ \"127.0.0.1:${'$'}{KONTAINER_PORT_BROKER_9092}:9092\" ]",
                "    environment:",
                "      KAFKA_NODE_ID: \"1\"",
                "      KAFKA_PROCESS_ROLES: \"broker,controller\"",
                "      KAFKA_LISTENERS: \"PLAINTEXT://:9092,CONTROLLER://:9093\"",
                "      KAFKA_ADVERTISED_LISTENERS: \"PLAINTEXT://127.0.0.1:${'$'}{KONTAINER_PORT_BROKER_9092}\"",
                "      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: \"CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT\"",
                if (controllerListenerNames) "      KAFKA_CONTROLLER_LISTENER_NAMES: \"CONTROLLER\"" else null,
                "      KAFKA_CONTROLLER_QUORUM_VOTERS: \"1@127.0.0.1:9093\"",
                "      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: \"1\"",
                "      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: \"1\"",
                "      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: \"1\"",
            ).joinToString("\n")
    }
}

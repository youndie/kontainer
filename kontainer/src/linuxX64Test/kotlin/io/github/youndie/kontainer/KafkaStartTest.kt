package io.github.youndie.kontainer

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * What an owned Kafka broker costs a fault test (B-09): a new project each run, the image already on
 * the machine. The numbers go to the log as `KAFKA-START`; the bound is a regression guard, far above
 * what was measured, not the measurement.
 */
class KafkaStartTest {
    @Test
    fun an_owned_broker_answers_within_a_minute() =
        runTest(timeout = 3.minutes) {
            withComposeFile(ReadinessTest.kafka()) { file ->
                val fixture = Fixture.owned(listOf(file), ports = mapOf("broker" to listOf(9092)))
                try {
                    val started = TimeSource.Monotonic.markNow()
                    fixture.up()
                    val up = started.elapsedNow()
                    fixture.awaitReady("broker", 9092, Probe.kafka(), timeout = 90.seconds, attempt = 1.seconds)
                    val ready = started.elapsedNow()
                    println("KAFKA-START up=${up.inWholeMilliseconds}ms ready=${ready.inWholeMilliseconds}ms")
                    assertTrue(ready < 60.seconds, "an owned broker took $ready to answer")
                } finally {
                    fixture.down()
                }
            }
        }
}

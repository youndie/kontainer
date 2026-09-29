package io.github.youndie.kontainer.docker

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Against the real engine of the machine the tests run on. There is no fake: a test that passes
 * because Docker was not there would be the defect this library exists to remove, so an absent
 * engine fails the first test here, loudly.
 */
class PingTest {
    @Test
    fun ping_reports_an_engine_that_speaks_the_pinned_api() =
        runTest {
            val version = DockerEngine.fromEnvironment().use { it.ping() }
            println("Docker Engine ${version.version}, API ${version.apiVersion} (min ${version.minApiVersion})")
            assertTrue(
                compareApiVersions(version.apiVersion, DockerEngine.API_VERSION) >= 0,
                "API ${version.apiVersion} is older than ${DockerEngine.API_VERSION}",
            )
        }

    @Test
    fun a_missing_socket_is_named_rather_than_reported_as_io() =
        runTest {
            val error =
                assertFailsWith<DockerError.SocketNotFound> {
                    DockerEngine(DockerEngine.socketPathOf("unix:///nonexistent.sock")).use { it.ping() }
                }
            assertEquals("/nonexistent.sock", error.path)
            assertTrue("/nonexistent.sock" in error.message.orEmpty())
        }

    @Test
    fun docker_host_names_the_socket_or_is_refused() {
        assertEquals(DockerEngine.DEFAULT_SOCKET, DockerEngine.socketPathOf(null))
        assertEquals("/run/user/1000/docker.sock", DockerEngine.socketPathOf("unix:///run/user/1000/docker.sock"))
        assertFailsWith<DockerError.UnsupportedHost> { DockerEngine.socketPathOf("tcp://127.0.0.1:2375") }
    }

    @Test
    fun api_versions_compare_as_numbers() {
        assertTrue(compareApiVersions("1.52", "1.44") > 0)
        assertTrue(compareApiVersions("1.6", "1.52") < 0)
        assertEquals(0, compareApiVersions("1.44", "1.44"))
    }
}

package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import io.github.youndie.kontainer.docker.DockerError
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Fixtures against the Docker Engine and the compose CLI of the machine the tests run on. */
class FixtureTest {
    @Test
    fun an_owned_fixture_comes_up_under_its_own_name_and_down_with_its_volumes() =
        runTest(timeout = 3.minutes) {
            // The file names a project of its own; the fixture's name must win (kafkakn B-97).
            withComposeFile(
                """
                name: not-the-fixture
                services:
                  pg:
                    image: postgres:18-alpine
                    environment: { POSTGRES_PASSWORD: kontainer }
                    volumes: [ "data:/var/lib/postgresql" ]
                volumes:
                  data: {}
                """,
            ) { file ->
                val fixture = Fixture.owned(listOf(file))
                try {
                    fixture.up()
                    val details = DockerEngine.fromEnvironment().use { it.inspect(fixture.containerId("pg")) }
                    assertTrue(details.running)
                    assertEquals(fixture.project, details.labels[Fixture.PROJECT_LABEL])
                    assertEquals("owned", details.labels[Fixture.KIND_LABEL])
                    assertTrue(details.labels[Fixture.OWNER_LABEL].orEmpty().startsWith("${processId()}@"))
                    assertEquals("${fixture.project}_data", volumesNamed("${fixture.project}_data"))
                } finally {
                    fixture.down()
                }
                assertFailsWith<FixtureError.NoSuchService> { fixture.containerId("pg") }
                assertEquals("", volumesNamed("${fixture.project}_data"))
            }
        }

    @Test
    fun a_container_of_another_project_with_the_same_name_is_left_alone() =
        runTest(timeout = 3.minutes) {
            val name = "kontainer-test-" + Random.nextLong().toULong().toString(36)
            docker(
                "run",
                "-d",
                "--name",
                name,
                "--label",
                "${Fixture.PROJECT_LABEL}=other",
                "-e",
                "POSTGRES_PASSWORD=kontainer",
                "postgres:18-alpine",
            )
            try {
                withComposeFile(
                    """
                    services:
                      broker:
                        image: postgres:18-alpine
                        container_name: $name
                        environment: { POSTGRES_PASSWORD: kontainer }
                    """,
                ) { file ->
                    val fixture = Fixture.shared("kontainer-test-shared", listOf(file))
                    val refused = assertFailsWith<FixtureError.ForeignContainer> { fixture.up() }
                    assertEquals(name, refused.containerName)
                    assertEquals("other", refused.project)
                    assertTrue(DockerEngine.fromEnvironment().use { it.inspect(name) }.running)
                }
            } finally {
                docker("rm", "-f", "-v", name)
            }
        }

    @Test
    fun a_compose_failure_carries_what_compose_said() =
        runTest(timeout = 1.minutes) {
            withComposeFile("services:\n  broken:\n    image: \"\"\n") { file ->
                val fixture = Fixture.owned(listOf(file))
                try {
                    val failed = assertFailsWith<FixtureError.ComposeFailed> { fixture.up() }
                    assertNotEquals(0, failed.exitCode)
                    assertTrue(failed.output.isNotBlank())
                } finally {
                    fixture.down()
                }
            }
        }

    @Test
    fun owned_fixtures_never_share_a_project_name() {
        val first = Fixture.owned(listOf("unused.yml"))
        val second = Fixture.owned(listOf("unused.yml"))
        assertNotEquals(first.project, second.project)
        assertTrue(first.project.startsWith("kontainer-${processId()}-"))
    }

    @Test
    fun a_fixture_that_was_never_up_has_no_services() =
        runTest {
            val fixture = Fixture.shared("kontainer-test-nothing-" + Random.nextInt(1_000_000), listOf("unused.yml"))
            assertFailsWith<FixtureError.NoSuchService> { fixture.containerId("pg") }
            assertFailsWith<DockerError.NoSuchContainer> {
                DockerEngine.fromEnvironment().use { it.inspect("${fixture.project}-pg-1") }
            }
        }

    private fun volumesNamed(name: String): String = docker("volume", "ls", "-q", "--filter", "name=$name")
}

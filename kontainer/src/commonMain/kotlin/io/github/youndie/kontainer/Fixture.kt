package io.github.youndie.kontainer

import io.github.youndie.kontainer.docker.DockerEngine
import io.github.youndie.kontainer.docker.DockerError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Containers a test runs against, described by compose files the repository already has (research D4).
 *
 * `up` and `down` run the `docker compose` CLI under the project name this fixture chooses; after that,
 * containers are found by their compose labels through [DockerEngine]. `up` is not readiness.
 *
 * A **shared** fixture keeps a fixed project name and is reused between runs. An **owned** one is named
 * `kontainer-<pid>-<n>` and belongs to the test that made it.
 */
public class Fixture private constructor(
    /** The compose project name, passed as `-p`; a `name:` in the files does not change it. */
    public val project: String,
    public val owned: Boolean,
    private val composeFiles: List<String>,
    private val environment: Map<String, String>,
    /** Service → the container ports whose host port this fixture chooses. */
    private val ports: Map<String, List<Int>>,
    private val engine: DockerEngine,
) {
    private var overrideDirectory: String? = null
    private var chosenPorts: Map<Pair<String, Int>, Int> = emptyMap()

    /**
     * Brings every service up (`docker compose up -d`) with this fixture's labels on each container.
     *
     * @throws FixtureError.ForeignContainer when a service's `container_name` is taken by another project.
     * @throws FixtureError.ComposeFailed when compose itself fails.
     */
    public suspend fun up() {
        val services = services()
        for ((_, containerName) in services) {
            if (containerName == null) continue
            val existing =
                try {
                    engine.inspect(containerName)
                } catch (_: DockerError.NoSuchContainer) {
                    continue
                }
            val owner = existing.labels[PROJECT_LABEL]
            if (owner != project) throw FixtureError.ForeignContainer(containerName, owner)
        }
        val directory = overrideDirectory ?: createTemporaryDirectory("kontainer-").also { overrideDirectory = it }
        val override = "$directory/labels.compose.yml"
        writeTextFile(override, labelsOverride(services.keys))
        // A port picked free can be taken before compose binds it (research Risk 1): the engine's
        // "port is already allocated" picks new ones, a bounded number of times.
        var attempt = 0
        while (true) {
            chosenPorts =
                ports
                    .flatMap { (service, containerPorts) ->
                        containerPorts.map { (service to it) to freeLoopbackPort() }
                    }.toMap()
            try {
                compose(composeFiles + override, listOf("up", "-d"), portEnvironment())
                break
            } catch (failure: FixtureError.ComposeFailed) {
                if (++attempt >= PORT_ATTEMPTS || !failure.output.contains("port is already allocated")) throw failure
            }
        }
        checkPublishedPorts()
    }

    /**
     * The host port [service] publishes [containerPort] on: the one this fixture chose before `up` and
     * checked after it, so it stays the same across stop and start.
     *
     * @throws FixtureError.PortNotPublished when the port was not asked for when the fixture was made.
     */
    public fun port(
        service: String,
        containerPort: Int,
    ): Int = chosenPorts[service to containerPort] ?: throw FixtureError.PortNotPublished(service, containerPort)

    /** Every chosen port against what the container really publishes, before anything is asked of it. */
    private suspend fun checkPublishedPorts() {
        for ((key, chosen) in chosenPorts) {
            val (service, containerPort) = key
            val published =
                engine
                    .inspect(containerId(service))
                    .ports
                    .filter { it.containerPort == containerPort }
                    .map { it.hostPort }
                    .distinct()
            when {
                published.isEmpty() -> throw FixtureError.PortNotPublished(service, containerPort)

                published != listOf(chosen) -> throw FixtureError.PortMismatch(
                    service,
                    containerPort,
                    chosen,
                    published.first {
                        it !=
                            chosen
                    },
                )
            }
        }
    }

    private fun portEnvironment(): Map<String, String> =
        chosenPorts.map { (key, port) -> portVariable(key.first, key.second) to port.toString() }.toMap()

    /** Removes the containers, networks and volumes of this fixture (`docker compose down -v`). */
    public suspend fun down() {
        compose(emptyList(), listOf("down", "-v", "--remove-orphans"), portEnvironment())
        overrideDirectory?.let(::removeDirectory)
        overrideDirectory = null
    }

    /**
     * The id of [service]'s container, running or not.
     *
     * @throws FixtureError.NoSuchService when the fixture has no container for it.
     */
    public suspend fun containerId(service: String): String =
        engine
            .containers(mapOf(PROJECT_LABEL to project, SERVICE_LABEL to service))
            .singleOrNull()
            ?.id ?: throw FixtureError.NoSuchService(service, project)

    /** Service name → its `container_name`, if it sets one, from compose's own normalised model. */
    private fun services(): Map<String, String?> {
        // With placeholder ports: a file that publishes `${KONTAINER_PORT_…}` does not parse without them.
        val placeholders =
            ports
                .flatMap { (service, containerPorts) ->
                    containerPorts.map {
                        portVariable(service, it) to
                            "0"
                    }
                }.toMap()
        val model =
            Json
                .parseToJsonElement(
                    compose(composeFiles, listOf("config", "--format", "json"), placeholders),
                ).jsonObject
        val services = model["services"] as? JsonObject ?: return emptyMap()
        return services.mapValues { (_, definition) ->
            definition.jsonObject["container_name"]?.jsonPrimitive?.content
        }
    }

    private fun labelsOverride(services: Set<String>): String =
        buildString {
            appendLine("services:")
            for (service in services) {
                appendLine("  \"$service\":")
                appendLine("    labels:")
                appendLine("      \"$OWNER_LABEL\": \"${processId()}@${hostName()}\"")
                appendLine("      \"$KIND_LABEL\": \"${if (owned) "owned" else "shared"}\"")
            }
        }

    private fun compose(
        files: List<String>,
        arguments: List<String>,
        ports: Map<String, String> = emptyMap(),
    ): String {
        val command = listOf("docker", "compose", "-p", project) + files.flatMap { listOf("-f", it) } + arguments
        val result = runCommand(command, environment + ports)
        if (result.exitCode !=
            0
        ) {
            throw FixtureError.ComposeFailed(command.joinToString(" "), result.exitCode, result.output)
        }
        return result.output
    }

    public companion object {
        /** Compose's own labels, by which a fixture finds its containers. */
        public const val PROJECT_LABEL: String = "com.docker.compose.project"
        public const val SERVICE_LABEL: String = "com.docker.compose.service"

        /** `<pid>@<host>` of the process that brought the container up. */
        public const val OWNER_LABEL: String = "kontainer.owner"

        /** `owned` or `shared`. */
        public const val KIND_LABEL: String = "kontainer.fixture"

        /** A fixture under a fixed project name, reused between runs; faults are refused on it (B-07). */
        public fun shared(
            project: String,
            composeFiles: List<String>,
            environment: Map<String, String> = emptyMap(),
            ports: Map<String, List<Int>> = emptyMap(),
            engine: DockerEngine = DockerEngine.fromEnvironment(),
        ): Fixture = Fixture(project, owned = false, composeFiles, environment, ports, engine)

        /**
         * A fixture that belongs to this test, under a project name no other process uses.
         *
         * [ports] names, per service, the container ports whose host port the fixture chooses; the compose
         * file publishes each as `"127.0.0.1:${KONTAINER_PORT_<SERVICE>_<PORT>}:<PORT>"` ([portVariable]).
         */
        public fun owned(
            composeFiles: List<String>,
            environment: Map<String, String> = emptyMap(),
            ports: Map<String, List<Int>> = emptyMap(),
            engine: DockerEngine = DockerEngine.fromEnvironment(),
        ): Fixture = Fixture(ownedProjectName(), owned = true, composeFiles, environment, ports, engine)

        private const val PORT_ATTEMPTS = 3

        @OptIn(ExperimentalAtomicApi::class)
        private val counter = AtomicInt(0)

        @OptIn(ExperimentalAtomicApi::class)
        internal fun ownedProjectName(): String = "kontainer-${processId()}-${counter.incrementAndFetch()}"
    }
}

/**
 * The environment variable a compose file publishes a chosen port through:
 * `("pg", 5432)` → `KONTAINER_PORT_PG_5432`, anything but letters and digits in the service name as `_`.
 */
public fun portVariable(
    service: String,
    containerPort: Int,
): String = "KONTAINER_PORT_" + service.uppercase().replace(Regex("[^A-Z0-9]"), "_") + "_" + containerPort

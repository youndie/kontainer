package io.github.youndie.kontainer.docker

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A container port the engine published on the host. */
public data class PublishedPort(
    val containerPort: Int,
    val protocol: String,
    val hostIp: String,
    val hostPort: Int,
)

/** One row of `GET /containers/json`. */
public data class ContainerSummary(
    val id: String,
    val names: List<String>,
    /** `created`, `running`, `paused`, `restarting`, `exited`, `removing` or `dead`, as the engine says. */
    val state: String,
    val labels: Map<String, String>,
    val ports: List<PublishedPort>,
)

/** What `GET /containers/{id}/json` says about a container. */
public data class ContainerDetails(
    val id: String,
    val name: String,
    val status: String,
    val running: Boolean,
    val paused: Boolean,
    val labels: Map<String, String>,
    /** Empty while the container is not running: the engine publishes ports only for a live one. */
    val ports: List<PublishedPort>,
)

internal fun JsonObject.toSummary(): ContainerSummary =
    ContainerSummary(
        id = text("Id"),
        names = (this["Names"] as? JsonArray)?.map { it.jsonPrimitive.content.removePrefix("/") }.orEmpty(),
        state = text("State"),
        labels = labels(this["Labels"]),
        ports =
            (this["Ports"] as? JsonArray).orEmpty().mapNotNull { element ->
                val port = element.jsonObject
                val hostPort = port["PublicPort"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                PublishedPort(
                    containerPort = port.text("PrivatePort").toInt(),
                    protocol = port.text("Type"),
                    hostIp = port["IP"]?.jsonPrimitive?.content.orEmpty(),
                    hostPort = hostPort,
                )
            },
    )

internal fun JsonObject.toDetails(): ContainerDetails {
    val state = getValue("State").jsonObject
    val config = this["Config"] as? JsonObject
    val published = (this["NetworkSettings"] as? JsonObject)?.get("Ports") as? JsonObject
    return ContainerDetails(
        id = text("Id"),
        name = text("Name").removePrefix("/"),
        status = state.text("Status"),
        running = state["Running"]?.jsonPrimitive?.booleanOrNull == true,
        paused = state["Paused"]?.jsonPrimitive?.booleanOrNull == true,
        labels = labels(config?.get("Labels")),
        ports =
            published.orEmpty().flatMap { (key, bindings) ->
                // "5432/tcp" → [{"HostIp": "127.0.0.1", "HostPort": "37810"}], or null when not published.
                val (port, protocol) = key.split('/', limit = 2).let { it[0].toInt() to it.getOrElse(1) { "tcp" } }
                if (bindings is JsonNull) return@flatMap emptyList()
                bindings.jsonArray.map { binding ->
                    val hostBinding = binding.jsonObject
                    PublishedPort(
                        containerPort = port,
                        protocol = protocol,
                        hostIp = hostBinding["HostIp"]?.jsonPrimitive?.content.orEmpty(),
                        hostPort = hostBinding.text("HostPort").toInt(),
                    )
                }
            },
    )
}

private fun labels(element: Any?): Map<String, String> =
    (element as? JsonObject).orEmpty().mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() }

internal fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.content ?: throw DockerError.EngineError(200, "no '$key' in the engine's answer")

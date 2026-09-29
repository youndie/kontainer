package io.github.youndie.kontainer.docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the engine says about itself: `GET /version`. */
public data class EngineVersion(
    val version: String,
    val apiVersion: String,
    val minApiVersion: String,
)

/**
 * A Docker Engine reached over its unix socket.
 *
 * Every versioned request carries [API_VERSION] in its path and nothing is negotiated (research D8):
 * a newer field is then a visible change to this constant rather than a difference between machines.
 *
 * No call has a timeout of its own. `stop` waits as long as the grace period it is given, so the
 * client's per-request limit is off, and a caller that needs a bound puts `withTimeout` around the call.
 */
public class DockerEngine(
    public val socketPath: String,
) : AutoCloseable {
    private val client = HttpClient(CIO) { engine { requestTimeout = 0 } }

    /**
     * Asks the engine whether it is there and what it speaks.
     *
     * @throws DockerError.SocketNotFound when nothing exists at [socketPath].
     * @throws DockerError.SocketPermissionDenied when this process may not use it.
     * @throws DockerError.ApiTooOld when the engine's API is older than [API_VERSION].
     * @throws DockerError.EngineError when the engine answers outside 2xx.
     */
    public suspend fun ping(): EngineVersion {
        val pong = send(HttpMethod.Get, "/_ping", versioned = false).bodyAsText()
        if (pong != "OK") throw DockerError.EngineError(200, "/_ping answered '$pong'")
        val fields = Json.parseToJsonElement(send(HttpMethod.Get, "/version").bodyAsText()).jsonObject
        val version =
            EngineVersion(
                version = fields.text("Version"),
                apiVersion = fields.text("ApiVersion"),
                minApiVersion = fields.text("MinAPIVersion"),
            )
        if (compareApiVersions(version.apiVersion, API_VERSION) < 0) {
            throw DockerError.ApiTooOld(version.apiVersion, API_VERSION)
        }
        return version
    }

    /**
     * Every container — running or not — that carries all of [labels]. Compose labels its containers
     * `com.docker.compose.project` and `com.docker.compose.service`, which is how a fixture finds its
     * own without trusting a container name (kafkakn B-97).
     */
    public suspend fun containers(labels: Map<String, String>): List<ContainerSummary> {
        val filters =
            buildJsonObject {
                put("label", JsonArray(labels.map { (key, value) -> JsonPrimitive("$key=$value") }))
            }
        val body =
            send(
                HttpMethod.Get,
                "/containers/json",
                query =
                    mapOf(
                        "all" to "true",
                        "filters" to filters.toString(),
                    ),
            )
        return Json.parseToJsonElement(body.bodyAsText()).jsonArray.map { it.jsonObject.toSummary() }
    }

    /** @throws DockerError.NoSuchContainer when the engine knows no container by [id]. */
    public suspend fun inspect(id: String): ContainerDetails =
        Json
            .parseToJsonElement(send(HttpMethod.Get, "/containers/$id/json", container = id).bodyAsText())
            .jsonObject
            .toDetails()

    /** @throws DockerError.Conflict when the container is already paused or not running. */
    public suspend fun pause(id: String) {
        send(HttpMethod.Post, "/containers/$id/pause", container = id)
    }

    /** @throws DockerError.EngineError `500 … is not paused` when the container is not paused (Docker 29.1). */
    public suspend fun unpause(id: String) {
        send(HttpMethod.Post, "/containers/$id/unpause", container = id)
    }

    /**
     * Stops the container, killing it after [timeout]. Stopping one that is already stopped succeeds:
     * the engine answers `304`, and the container is in the state that was asked for.
     */
    public suspend fun stop(
        id: String,
        timeout: Duration = 10.seconds,
    ) {
        send(
            HttpMethod.Post,
            "/containers/$id/stop",
            container = id,
            query =
                mapOf("t" to timeout.inWholeSeconds.toString()),
        )
    }

    /** Starts the container. Starting one that is running succeeds, as `stop` does (`304`). */
    public suspend fun start(id: String) {
        send(HttpMethod.Post, "/containers/$id/start", container = id)
    }

    /** @throws DockerError.Conflict when the container is not running. */
    public suspend fun kill(id: String) {
        send(HttpMethod.Post, "/containers/$id/kill", container = id)
    }

    override fun close() {
        client.close()
    }

    /**
     * One request, with the engine's failures mapped. [container] is the id the path names, so that a
     * `404` or a `409` can say which container it was about.
     */
    private suspend fun send(
        method: HttpMethod,
        path: String,
        versioned: Boolean = true,
        container: String? = null,
        query: Map<String, String> = emptyMap(),
    ): HttpResponse {
        socketProblem(socketPath)?.let { throw it }
        // The host is a placeholder the engine ignores; the socket decides where the request goes.
        val response =
            client.request("http://docker${if (versioned) "/v$API_VERSION" else ""}$path") {
                this.method = method
                unixSocket(socketPath)
                url { query.forEach { (key, value) -> parameters.append(key, value) } }
            }
        val status = response.status
        if (status.isSuccess() || status == HttpStatusCode.NotModified) return response
        val message = engineMessage(response.bodyAsText())
        throw when {
            container != null && status == HttpStatusCode.NotFound -> DockerError.NoSuchContainer(container, message)
            container != null && status == HttpStatusCode.Conflict -> DockerError.Conflict(container, message)
            else -> DockerError.EngineError(status.value, message)
        }
    }

    public companion object {
        /** The API version every request is pinned to: the minimum the build box's engine accepts. */
        public const val API_VERSION: String = "1.44"

        /** Where the engine listens when `DOCKER_HOST` is not set. */
        public const val DEFAULT_SOCKET: String = "/var/run/docker.sock"

        /** The engine named by `DOCKER_HOST`, or the default socket. */
        public fun fromEnvironment(): DockerEngine = DockerEngine(socketPathOf(environment("DOCKER_HOST")))

        /**
         * The socket path a `DOCKER_HOST` value names: `unix:///run/docker.sock` → `/run/docker.sock`,
         * and `null` or blank → [DEFAULT_SOCKET].
         *
         * @throws DockerError.UnsupportedHost for any other scheme.
         */
        public fun socketPathOf(dockerHost: String?): String =
            when {
                dockerHost.isNullOrBlank() -> DEFAULT_SOCKET
                dockerHost.startsWith("unix://") -> dockerHost.removePrefix("unix://")
                else -> throw DockerError.UnsupportedHost(dockerHost)
            }
    }
}

/** Compares `major.minor` API versions numerically, so that `1.52` is newer than `1.6`. */
internal fun compareApiVersions(
    left: String,
    right: String,
): Int {
    val a = left.split('.').map { it.toIntOrNull() ?: 0 }
    val b = right.split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(a.size, b.size)) {
        val difference = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
        if (difference != 0) return difference
    }
    return 0
}

/** The engine's error body is `{"message": "…"}`; anything else is passed on as it came. */
private fun engineMessage(body: String): String =
    runCatching { Json.parseToJsonElement(body).jsonObject.text("message") }.getOrDefault(body)

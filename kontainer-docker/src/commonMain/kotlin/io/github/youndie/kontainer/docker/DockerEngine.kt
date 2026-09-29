package io.github.youndie.kontainer.docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 */
public class DockerEngine(
    public val socketPath: String,
) : AutoCloseable {
    private val client = HttpClient(CIO)

    /**
     * Asks the engine whether it is there and what it speaks.
     *
     * @throws DockerError.SocketNotFound when nothing exists at [socketPath].
     * @throws DockerError.SocketPermissionDenied when this process may not use it.
     * @throws DockerError.ApiTooOld when the engine's API is older than [API_VERSION].
     * @throws DockerError.EngineError when the engine answers outside 2xx.
     */
    public suspend fun ping(): EngineVersion {
        socketProblem(socketPath)?.let { throw it }
        val pong = request("/_ping")
        val ok = pong.bodyAsText()
        if (ok != "OK") throw DockerError.EngineError(pong.status.value, "/_ping answered '$ok'")
        val fields = Json.parseToJsonElement(request("/v$API_VERSION/version").bodyAsText()).jsonObject
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

    override fun close() {
        client.close()
    }

    private suspend fun request(path: String): HttpResponse {
        // The host is a placeholder the engine ignores; the socket decides where the request goes.
        val response = client.get("http://docker$path") { unixSocket(socketPath) }
        if (!response.status.isSuccess()) {
            throw DockerError.EngineError(response.status.value, engineMessage(response.bodyAsText()))
        }
        return response
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

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.content ?: throw DockerError.EngineError(200, "no '$key' in the engine's answer")

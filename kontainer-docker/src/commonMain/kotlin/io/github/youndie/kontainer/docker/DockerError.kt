package io.github.youndie.kontainer.docker

/**
 * Every way a call to the Docker Engine fails, as a type a test can act on.
 *
 * The socket failures are told apart before any request is made. Left to itself, the CIO client
 * reports a missing socket as `kotlinx.io.IOException: Failed to connect to UnixSocketAddress(<path>)`
 * — the path, but not the reason — and "Docker is not running" and "this user may not use Docker"
 * call for different fixes.
 */
public sealed class DockerError(
    message: String,
) : Exception(message) {
    /** Nothing exists at [path]: Docker is not running there, or `DOCKER_HOST` points elsewhere. */
    public class SocketNotFound(
        public val path: String,
    ) : DockerError("no Docker socket at $path")

    /** The socket exists and this process may not use it; usually a missing `docker` group membership. */
    public class SocketPermissionDenied(
        public val path: String,
    ) : DockerError("no permission to use the Docker socket at $path")

    /** `DOCKER_HOST` names a transport this client does not speak; only `unix://` is supported. */
    public class UnsupportedHost(
        public val dockerHost: String,
    ) : DockerError("DOCKER_HOST=$dockerHost is not a unix:// socket, the only transport kontainer speaks")

    /** The engine is older than the API version every request is pinned to ([DockerEngine.API_VERSION]). */
    public class ApiTooOld(
        public val server: String,
        public val required: String,
    ) : DockerError("the Docker Engine speaks API $server, and kontainer needs at least $required")

    /** The engine knows no container by [id] (`404`). */
    public class NoSuchContainer(
        public val id: String,
        public val engineMessage: String,
    ) : DockerError("no container $id: $engineMessage")

    /** The container is not in a state that allows the call (`409`) — a second `pause`, `kill` of a stopped one. */
    public class Conflict(
        public val id: String,
        public val engineMessage: String,
    ) : DockerError("container $id refused the call: $engineMessage")

    /**
     * Any other answer outside 2xx, with the engine's own message. Includes `unpause` of a container
     * that is not paused, which Docker 29.1 answers with `500 … is not paused` rather than `409`.
     */
    public class EngineError(
        public val status: Int,
        public val engineMessage: String,
    ) : DockerError("the Docker Engine answered $status: $engineMessage")
}

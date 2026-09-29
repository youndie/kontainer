package io.github.youndie.kontainer.docker

/**
 * Why [path] cannot be used as the engine's socket, or `null` when it can. Asked before a request so
 * that a missing socket and a forbidden one are told apart; the client's own I/O error names the path
 * and not the reason.
 */
internal expect fun socketProblem(path: String): DockerError?

/** One environment variable of this process. */
internal expect fun environment(name: String): String?

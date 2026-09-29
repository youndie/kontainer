@file:OptIn(ExperimentalForeignApi::class)

package io.github.youndie.kontainer.docker

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.R_OK
import platform.posix.W_OK
import platform.posix.access
import platform.posix.getenv

// Connecting to a unix socket needs write permission on it; reading alone is not enough.
internal actual fun socketProblem(path: String): DockerError? =
    when {
        access(path, F_OK) != 0 -> DockerError.SocketNotFound(path)
        access(path, R_OK or W_OK) != 0 -> DockerError.SocketPermissionDenied(path)
        else -> null
    }

internal actual fun environment(name: String): String? = getenv(name)?.toKString()

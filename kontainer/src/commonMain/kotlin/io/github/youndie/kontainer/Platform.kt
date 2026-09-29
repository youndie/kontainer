package io.github.youndie.kontainer

/** What a command printed (both streams, in order) and how it exited. */
internal class CommandResult(
    val exitCode: Int,
    val output: String,
)

/** Runs [arguments] with [environment] added to this process's own, and waits for it. */
internal expect fun runCommand(
    arguments: List<String>,
    environment: Map<String, String>,
): CommandResult

internal expect fun processId(): Int

internal expect fun hostName(): String

/** A new private directory under the system's temporary one. */
internal expect fun createTemporaryDirectory(prefix: String): String

internal expect fun writeTextFile(
    path: String,
    text: String,
)

internal expect fun removeDirectory(path: String)

/** A TCP port on the loopback interface that nothing is bound to at the moment of asking. */
internal expect fun freeLoopbackPort(): Int

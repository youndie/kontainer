@file:OptIn(ExperimentalForeignApi::class)

package io.github.youndie.kontainer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.gethostname
import platform.posix.getpid
import platform.posix.mkdtemp
import platform.posix.pclose
import platform.posix.popen

// Through the shell, with every argument single-quoted: popen is the one process API every Linux libc
// has, and quoting is simpler to get right than posix_spawn's argument and environment arrays.
internal actual fun runCommand(
    arguments: List<String>,
    environment: Map<String, String>,
): CommandResult {
    val assignments = environment.map { (key, value) -> quote("$key=$value") }
    val command = (listOf("env") + assignments + arguments.map(::quote)).joinToString(" ")
    val pipe = popen("$command 2>&1", "r") ?: error("cannot start: $command")
    val output = StringBuilder()
    val buffer = ByteArray(4096)
    while (fgets(buffer.refTo(0), buffer.size, pipe) != null) output.append(buffer.toKString())
    // The wait status: the exit code is its second byte (what WEXITSTATUS, a C macro, computes).
    return CommandResult((pclose(pipe) shr 8) and 0xFF, output.toString())
}

internal actual fun processId(): Int = getpid()

internal actual fun hostName(): String {
    val buffer = ByteArray(256)
    gethostname(buffer.refTo(0), buffer.size.toULong())
    return buffer.toKString()
}

internal actual fun createTemporaryDirectory(prefix: String): String {
    val template = "/tmp/${prefix}XXXXXX".encodeToByteArray() + 0
    return mkdtemp(template.refTo(0))?.toKString() ?: error("cannot create a temporary directory")
}

internal actual fun writeTextFile(
    path: String,
    text: String,
) {
    val file = fopen(path, "w") ?: error("cannot write $path")
    try {
        fputs(text, file)
    } finally {
        fclose(file)
    }
}

internal actual fun removeDirectory(path: String) {
    runCommand(listOf("rm", "-rf", "--", path), emptyMap())
}

private fun quote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"

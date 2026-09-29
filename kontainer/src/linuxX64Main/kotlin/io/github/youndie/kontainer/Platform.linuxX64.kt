@file:OptIn(ExperimentalForeignApi::class)

package io.github.youndie.kontainer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.bind
import platform.posix.close
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.gethostname
import platform.posix.getpid
import platform.posix.getsockname
import platform.posix.mkdtemp
import platform.posix.pclose
import platform.posix.popen
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar

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

// Bind to port 0 on 127.0.0.1, ask the kernel which port it gave, and let it go. Free at the moment of
// asking only; `up` retries when compose loses that race.
internal actual fun freeLoopbackPort(): Int =
    memScoped {
        val socket = socket(AF_INET, SOCK_STREAM, 0)
        check(socket >= 0) { "cannot open a socket to find a free port" }
        try {
            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            address.sin_port = 0u
            // 127.0.0.1 in network byte order, as the little-endian host stores it.
            address.sin_addr.s_addr = 0x0100007Fu
            check(
                bind(socket, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0,
            ) { "cannot bind 127.0.0.1:0" }
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            check(getsockname(socket, address.ptr.reinterpret(), length.ptr) == 0) { "cannot read the bound port" }
            val networkOrder = address.sin_port.toInt()
            ((networkOrder and 0xFF) shl 8) or ((networkOrder shr 8) and 0xFF)
        } finally {
            close(socket)
        }
    }

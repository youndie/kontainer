package io.github.youndie.kontainer.docker

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.UnixSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.readByteArray

/** A response read to the end of its connection: status, headers (lower-cased names), body. */
internal class RawResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/**
 * One HTTP/1.1 request over the unix socket, on a connection of its own, read until the engine closes
 * it.
 *
 * WHY NOT THE HTTP CLIENT. `POST /exec/{id}/start` answers with neither a length nor chunked encoding
 * and without `Connection: close` — even when the request asks for it (Docker 29.1, measured in B-03):
 * the body ends when the connection does. curl reads to the close; Ktor's CIO client refuses such a
 * response ("request body length should be specified, chunked transfer encoding should be used or
 * keep-alive should be disabled"). So this one call reads the socket itself.
 */
internal suspend fun rawRequest(
    socketPath: String,
    method: String,
    path: String,
    jsonBody: String,
): RawResponse {
    val payload = jsonBody.encodeToByteArray()
    val head =
        "$method $path HTTP/1.1\r\n" +
            "Host: docker\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${payload.size}\r\n" +
            "Connection: close\r\n\r\n"
    val bytes =
        SelectorManager(Dispatchers.IO).use { selector ->
            aSocket(selector).tcp().connect(UnixSocketAddress(socketPath)).use { socket ->
                val write = socket.openWriteChannel(autoFlush = false)
                write.writeFully(head.encodeToByteArray() + payload)
                write.flush()
                socket.openReadChannel().readBuffer().readByteArray()
            }
        }
    return parseRawResponse(bytes)
}

internal fun parseRawResponse(bytes: ByteArray): RawResponse {
    val end =
        indexOf(bytes, HEADER_END) ?: throw DockerError.EngineError(0, "the engine's answer has no end of headers")
    val lines = bytes.copyOfRange(0, end).decodeToString().split("\r\n")
    val status =
        lines
            .first()
            .split(' ')
            .getOrNull(1)
            ?.toIntOrNull()
            ?: throw DockerError.EngineError(0, "not an HTTP status line: '${lines.first()}'")
    val headers =
        lines
            .drop(1)
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon < 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }.toMap()
    val body = bytes.copyOfRange(end + HEADER_END.size, bytes.size)
    val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
    return RawResponse(status, headers, if (chunked) dechunk(body) else body)
}

/** Chunked transfer coding, in case an engine version does frame this answer after all. */
private fun dechunk(body: ByteArray): ByteArray {
    val parts = ArrayList<ByteArray>()
    var at = 0
    while (true) {
        val lineEnd = indexOf(body, CRLF, at) ?: throw DockerError.EngineError(200, "a chunk header without its end")
        val size =
            body
                .copyOfRange(at, lineEnd)
                .decodeToString()
                .substringBefore(';')
                .trim()
                .toInt(16)
        if (size == 0) break
        val start = lineEnd + CRLF.size
        parts += body.copyOfRange(start, start + size)
        at = start + size + CRLF.size
    }
    val result = ByteArray(parts.sumOf { it.size })
    var offset = 0
    for (part in parts) {
        part.copyInto(result, offset)
        offset += part.size
    }
    return result
}

private val HEADER_END = "\r\n\r\n".encodeToByteArray()
private val CRLF = "\r\n".encodeToByteArray()

private fun indexOf(
    bytes: ByteArray,
    needle: ByteArray,
    from: Int = 0,
): Int? {
    outer@ for (i in from..bytes.size - needle.size) {
        for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
        return i
    }
    return null
}

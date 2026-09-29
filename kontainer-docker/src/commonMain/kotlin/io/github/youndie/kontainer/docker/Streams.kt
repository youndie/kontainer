package io.github.youndie.kontainer.docker

/** What a command run inside a container printed, and how it ended. */
public data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** A container's log, its two streams apart. A container started with a TTY has one stream: [stdout]. */
public data class ContainerLogs(
    val stdout: String,
    val stderr: String,
)

/**
 * Splits the engine's multiplexed stream: frames of `[stream, 0, 0, 0, size as big-endian u32]` and
 * `size` bytes, stream 1 being stdout and 2 stderr (measured, research §1.2). Stdin frames (0) do not
 * occur in output and are dropped. A body that ends inside a frame is an engine error, not a short read
 * to be passed on as if it were the whole output.
 */
internal fun demultiplex(body: ByteArray): Pair<ByteArray, ByteArray> {
    val stdout = ArrayList<ByteArray>()
    val stderr = ArrayList<ByteArray>()
    var offset = 0
    while (offset < body.size) {
        if (body.size - offset < FRAME_HEADER) {
            throw DockerError.EngineError(200, "the stream ends inside a frame header at byte $offset of ${body.size}")
        }
        val stream = body[offset].toInt()
        val size =
            ((body[offset + 4].toInt() and 0xFF) shl 24) or
                ((body[offset + 5].toInt() and 0xFF) shl 16) or
                ((body[offset + 6].toInt() and 0xFF) shl 8) or
                (body[offset + 7].toInt() and 0xFF)
        val start = offset + FRAME_HEADER
        if (size < 0 || body.size - start < size) {
            throw DockerError.EngineError(
                200,
                "a frame of $size bytes at byte $offset overruns the ${body.size}-byte stream",
            )
        }
        val payload = body.copyOfRange(start, start + size)
        when (stream) {
            1 -> stdout += payload
            2 -> stderr += payload
        }
        offset = start + size
    }
    return stdout.concatenated() to stderr.concatenated()
}

private const val FRAME_HEADER = 8

private fun List<ByteArray>.concatenated(): ByteArray {
    val result = ByteArray(sumOf { it.size })
    var at = 0
    for (part in this) {
        part.copyInto(result, at)
        at += part.size
    }
    return result
}

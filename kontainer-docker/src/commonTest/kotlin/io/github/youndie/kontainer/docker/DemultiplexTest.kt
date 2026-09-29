package io.github.youndie.kontainer.docker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemultiplexTest {
    @Test
    fun frames_are_split_by_stream_in_order() {
        val body = frame(1, "a") + frame(2, "b") + frame(1, "c") + frame(0, "ignored")
        val (stdout, stderr) = demultiplex(body)
        assertEquals("ac", stdout.decodeToString())
        assertEquals("b", stderr.decodeToString())
    }

    @Test
    fun a_size_beyond_one_byte_is_read_big_endian() {
        val text = "x".repeat(70_000)
        assertEquals(text, demultiplex(frame(1, text)).first.decodeToString())
    }

    @Test
    fun a_stream_cut_inside_a_frame_is_an_error_not_a_short_output() {
        val whole = frame(1, "abcdef")
        assertFailsWith<DockerError.EngineError> { demultiplex(whole.copyOf(whole.size - 2)) }
        assertFailsWith<DockerError.EngineError> { demultiplex(whole.copyOf(5)) }
    }

    private fun frame(
        stream: Int,
        text: String,
    ): ByteArray {
        val payload = text.encodeToByteArray()
        val size = payload.size
        val header =
            byteArrayOf(
                stream.toByte(),
                0,
                0,
                0,
                (size ushr 24).toByte(),
                (size ushr 16).toByte(),
                (size ushr 8).toByte(),
                size.toByte(),
            )
        return header + payload
    }
}

class RawResponseTest {
    @Test
    fun a_body_read_to_the_close_is_taken_as_it_is() {
        val answer =
            parseRawResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.docker.raw-stream\r\n\r\nabc".encodeToByteArray(),
            )
        assertEquals(200, answer.status)
        assertEquals("application/vnd.docker.raw-stream", answer.headers["content-type"])
        assertEquals("abc", answer.body.decodeToString())
    }

    @Test
    fun a_chunked_body_is_joined() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n"
        assertEquals("abcde", parseRawResponse(raw.encodeToByteArray()).body.decodeToString())
    }
}

package io.github.youndie.kontainer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.random.Random

/**
 * A question asked of a service through its published port, which returns only when the service
 * answered it in its own protocol (research D6). Anything thrown is "not ready yet", and its message is
 * the cause reported if readiness never comes.
 *
 * A TCP connect is never enough: the engine's proxy accepts connections for a paused container
 * (research §1.2).
 */
public fun interface Probe {
    public suspend fun check(
        host: String,
        port: Int,
    )

    public companion object {
        /** An ApiVersions request (v0), answered by a Kafka broker with the same correlation id and no error. */
        public fun kafka(): Probe = Probe { host, port -> kafkaApiVersions(host, port) }

        /**
         * A protocol 3.0 startup packet for [user]. The server asking for authentication, or refusing the
         * user, is ready; `57P03` ("the database system is starting up") is not.
         */
        public fun postgres(user: String = "postgres"): Probe =
            Probe { host, port -> postgresStartup(host, port, user) }

        /** `GET [path]` answered with [status]. */
        public fun http(
            path: String = "/",
            status: Int = 200,
        ): Probe =
            Probe { host, port ->
                val answered = HttpClient(CIO).use { it.get("http://$host:$port$path").status.value }
                if (answered != status) throw ProbeFailure("GET $path answered $answered, not $status")
            }

        /** Any check the caller writes; throwing means "not ready yet". */
        public fun custom(check: suspend (host: String, port: Int) -> Unit): Probe = Probe(check)
    }
}

/** A probe's "not yet", with the reason in the words it will be reported in. */
public class ProbeFailure(
    message: String,
) : Exception(message)

private suspend fun kafkaApiVersions(
    host: String,
    port: Int,
) {
    val correlation = Random.nextInt(1, Int.MAX_VALUE)
    val clientId = "kontainer".encodeToByteArray()
    // Request header v0 and an empty ApiVersions v0 body: api key 18, version 0, correlation id, client id.
    val body = short(18) + short(0) + int(correlation) + short(clientId.size) + clientId
    exchange(host, port, int(body.size) + body) { answer ->
        // The response size is read and not judged: every non-Kafka answer tried (Postgres, an echo) is
        // caught by the correlation id, and a size check beside it was a guard no test could tell apart.
        answer.int()
        val echoed = answer.int()
        if (echoed != correlation) {
            throw ProbeFailure("the answer is not Kafka: correlation id $echoed, not $correlation")
        }
        val error = answer.short()
        if (error != 0) throw ProbeFailure("the broker answered ApiVersions with error code $error")
    }
}

private suspend fun postgresStartup(
    host: String,
    port: Int,
    user: String,
) {
    val parameters = "user\u0000$user\u0000database\u0000postgres\u0000\u0000".encodeToByteArray()
    val body = int(PROTOCOL_3_0) + parameters
    exchange(host, port, int(body.size + 4) + body) { answer ->
        val type = answer.readByte().toInt().toChar()
        // Asked to authenticate: the server accepts connections.
        if (type == 'R') return@exchange
        if (type != 'E') throw ProbeFailure("the answer is not Postgres: message type '$type'")
        val length = answer.int()
        val fields = ByteArray(length - 4).also { answer.readFully(it) }.decodeToString().split('\u0000')
        val code = fields.firstOrNull { it.startsWith("C") }?.drop(1)
        val message = fields.firstOrNull { it.startsWith("M") }?.drop(1)
        // Any refusal but "starting up" comes from a server that accepts connections.
        if (code == "57P03") throw ProbeFailure("Postgres is not accepting connections yet: $message")
    }
}

private suspend fun exchange(
    host: String,
    port: Int,
    request: ByteArray,
    read: suspend (ByteReadChannel) -> Unit,
) {
    SelectorManager(Dispatchers.IO).use { selector ->
        aSocket(selector).tcp().connect(host, port).use { socket ->
            val output = socket.openWriteChannel(autoFlush = false)
            output.writeFully(request)
            output.flush()
            read(socket.openReadChannel())
        }
    }
}

private suspend fun ByteReadChannel.int(): Int {
    val bytes = ByteArray(4).also { readFully(it) }
    return ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
}

private suspend fun ByteReadChannel.short(): Int {
    val bytes = ByteArray(2).also { readFully(it) }
    return ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
}

private fun int(value: Int): ByteArray =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

private fun short(value: Int): ByteArray = byteArrayOf((value ushr 8).toByte(), value.toByte())

private const val PROTOCOL_3_0 = 196_608

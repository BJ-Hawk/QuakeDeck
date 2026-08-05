package cz.misa.quakedeck.time

import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** A network-time anchor expressed against Android's monotonic elapsed clock. */
data class NetworkTimeSample(
    val epochMillisAtReceipt: Long,
    val elapsedRealtimeAtReceipt: Long,
    val roundTripMillis: Long,
    val server: String
)

/**
 * Lightweight SNTP client for QuakeDeck's visible JST clock.
 *
 * NICT's public NTP endpoint is directly tied to Japan Standard Time. We take
 * a few small samples and retain the one with the lowest measured network
 * delay. The resulting epoch is then advanced using elapsedRealtime(), so a
 * manual device-clock change cannot move QuakeDeck's live JST display.
 */
object NetworkTimeSynchronizer {
    const val PRIMARY_SERVER = "ntp.nict.jp"

    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_TO_UNIX_EPOCH_SECONDS = 2_208_988_800L
    private const val NTP_ERA_SECONDS = 0x1_0000_0000L
    private const val DEFAULT_TIMEOUT_MILLIS = 2_500
    private const val SAMPLE_COUNT = 3
    private const val SAMPLE_GAP_MILLIS = 120L

    suspend fun synchronize(
        server: String = PRIMARY_SERVER,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
    ): Result<NetworkTimeSample> = withContext(Dispatchers.IO) {
        try {
            val samples = mutableListOf<NetworkTimeSample>()
            try {
                val address = InetAddress.getByName(server)
                repeat(SAMPLE_COUNT) { index ->
                    try {
                        samples += requestSample(address, server, timeoutMillis)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // One poor packet must not discard the other samples.
                    }
                    if (index < SAMPLE_COUNT - 1) delay(SAMPLE_GAP_MILLIS.milliseconds)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // DNS or socket failure can still fall back to Android's cached
                // non-user-adjustable network clock on supported versions.
            }

            val bestNtpSample = samples.minByOrNull(NetworkTimeSample::roundTripMillis)
            val sample = bestNtpSample ?: androidNetworkTimeFallback()
                ?: error("No valid network-time source is currently available")
            Result.success(sample)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }


    private fun androidNetworkTimeFallback(): NetworkTimeSample? {
        if (Build.VERSION.SDK_INT < 33) return null
        return runCatching {
            val elapsed = SystemClock.elapsedRealtime()
            NetworkTimeSample(
                epochMillisAtReceipt = SystemClock.currentNetworkTimeClock().millis(),
                elapsedRealtimeAtReceipt = elapsed,
                roundTripMillis = 0L,
                server = "Android network time"
            )
        }.getOrNull()
    }

    private fun requestSample(
        address: InetAddress,
        server: String,
        timeoutMillis: Int
    ): NetworkTimeSample {
        val request = ByteArray(NTP_PACKET_SIZE)
        // LI = 0, Version = 4, Mode = 3 (client).
        request[0] = 0x23.toByte()

        val wallSendMillis = System.currentTimeMillis()
        val elapsedSendMillis = SystemClock.elapsedRealtime()
        writeTimestamp(request, wallSendMillis)
        val sentTimestamp = request.copyOfRange(40, 48)

        val response = ByteArray(NTP_PACKET_SIZE)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMillis
            socket.send(DatagramPacket(request, request.size, address, NTP_PORT))

            val packet = DatagramPacket(response, response.size)
            try {
                socket.receive(packet)
            } catch (timeout: SocketTimeoutException) {
                throw IllegalStateException("NTP request to $server timed out", timeout)
            }
            require(packet.length >= NTP_PACKET_SIZE) { "Short NTP response from $server" }
        }

        val elapsedReceiveMillis = SystemClock.elapsedRealtime()
        // Advance from t1 with the monotonic clock instead of trusting the wall
        // clock again at t4; this also survives a user changing device time while
        // the packet is in flight.
        val clientReceiveMillis = wallSendMillis +
            (elapsedReceiveMillis - elapsedSendMillis)

        validateResponse(response, sentTimestamp, server)

        val serverReceiveMillis = readTimestamp(response, 32, wallSendMillis)
        val serverTransmitMillis = readTimestamp(response, 40, wallSendMillis)

        require(serverReceiveMillis > 0L && serverTransmitMillis > 0L) {
            "Invalid NTP timestamps from $server"
        }

        val clockOffsetMillis = (
            (serverReceiveMillis - wallSendMillis) +
                (serverTransmitMillis - clientReceiveMillis)
            ) / 2L
        val rawRoundTripMillis =
            (clientReceiveMillis - wallSendMillis) -
                (serverTransmitMillis - serverReceiveMillis)
        val roundTripMillis = rawRoundTripMillis.coerceAtLeast(0L)

        // Reject obviously nonsensical packets while still allowing a device
        // wall clock that is several years wrong: the offset itself may be very
        // large, but the server's receive/transmit interval must remain sane.
        require(abs(serverTransmitMillis - serverReceiveMillis) <= timeoutMillis + 1_000L) {
            "Implausible NTP server processing interval from $server"
        }

        return NetworkTimeSample(
            epochMillisAtReceipt = clientReceiveMillis + clockOffsetMillis,
            elapsedRealtimeAtReceipt = elapsedReceiveMillis,
            roundTripMillis = roundTripMillis,
            server = server
        )
    }

    private fun validateResponse(
        response: ByteArray,
        sentTimestamp: ByteArray,
        server: String
    ) {
        val leapIndicator = (response[0].toInt() ushr 6) and 0x3
        val mode = response[0].toInt() and 0x7
        val stratum = response[1].toInt() and 0xff

        require(leapIndicator != 3) { "$server reports an unsynchronised NTP clock" }
        require(mode in 4..5) { "Unexpected NTP response mode $mode from $server" }
        require(stratum in 1..15) { "Invalid NTP stratum $stratum from $server" }
        require(response.copyOfRange(24, 32).contentEquals(sentTimestamp)) {
            "NTP originate timestamp mismatch from $server"
        }
    }

    private fun writeTimestamp(buffer: ByteArray, unixMillis: Long) {
        val offset = 40
        val seconds = Math.floorDiv(unixMillis, 1_000L) + NTP_TO_UNIX_EPOCH_SECONDS
        val millis = Math.floorMod(unixMillis, 1_000L)
        val fraction = (millis * 0x1_0000_0000L) / 1_000L

        writeUnsignedInt(buffer, offset, seconds)
        writeUnsignedInt(buffer, offset + 4, fraction)
    }

    private fun readTimestamp(
        buffer: ByteArray,
        offset: Int,
        referenceUnixMillis: Long
    ): Long {
        val rawSeconds = readUnsignedInt(buffer, offset)
        val fraction = readUnsignedInt(buffer, offset + 4)
        if (rawSeconds == 0L && fraction == 0L) return 0L

        // The NTP seconds field wraps every 136 years (next in 2036). Unfold
        // the 32-bit value into the era closest to the client's approximate
        // date, which keeps the clock valid beyond that rollover.
        val referenceNtpSeconds =
            Math.floorDiv(referenceUnixMillis, 1_000L) + NTP_TO_UNIX_EPOCH_SECONDS
        val referenceEra = Math.floorDiv(referenceNtpSeconds, NTP_ERA_SECONDS)
        val unfoldedSeconds = ((referenceEra - 1L)..(referenceEra + 1L))
            .map { era -> rawSeconds + era * NTP_ERA_SECONDS }
            .minByOrNull { candidate -> abs(candidate - referenceNtpSeconds) }
            ?: rawSeconds

        val unixSeconds = unfoldedSeconds - NTP_TO_UNIX_EPOCH_SECONDS
        val millis = (fraction * 1_000L) ushr 32
        return unixSeconds * 1_000L + millis
    }

    private fun writeUnsignedInt(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }

    private fun readUnsignedInt(buffer: ByteArray, offset: Int): Long =
        ((buffer[offset].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 8) or
            (buffer[offset + 3].toLong() and 0xffL)
}

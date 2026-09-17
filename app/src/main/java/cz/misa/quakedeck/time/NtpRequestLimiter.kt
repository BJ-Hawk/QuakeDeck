package cz.misa.quakedeck.time

/** Shared by the NTP client so retries and concurrent callers use the same allowance. */
internal class NtpRequestLimiter(private val elapsedRealtime: () -> Long) {
    private val attempts = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = elapsedRealtime()
        while (attempts.isNotEmpty() && now - attempts.first() >= WINDOW_MILLIS) {
            attempts.removeFirst()
        }
        if (attempts.size >= MAX_REQUESTS) return false

        // Reserve before sending; timeouts and failed responses still consume a request.
        attempts.addLast(now)
        return true
    }

    private companion object {
        // https://www.nict.go.jp/en/sts/ntp_faq.html, Q2-5.
        const val MAX_REQUESTS = 20
        const val WINDOW_MILLIS = 60 * 60 * 1_000L
    }
}

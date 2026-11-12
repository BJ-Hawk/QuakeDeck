package cz.misa.quakedeck.data

/**
 * One owner for credential generations. Persistence and its generation check
 * share this monitor; network calls and callback delivery stay outside it.
 */
internal class OAuthSessionState<T>(
    private val read: () -> T?,
    private val write: (T) -> Unit,
    private val clear: () -> Unit
) {
    private var loaded = false
    private var value: T? = null
    private var generation = 0L

    @Synchronized
    fun snapshot(): Pair<Long, T?> {
        if (!loaded) {
            value = read()
            loaded = true
        }
        return generation to value
    }

    @Synchronized
    fun currentGeneration(): Long = generation

    @Synchronized
    fun advance(): Long = ++generation

    @Synchronized
    fun save(expected: Long, credentials: T): Boolean {
        if (expected != generation) return false
        write(credentials)
        value = credentials
        loaded = true
        return true
    }

    @Synchronized
    fun invalidate(expected: Long = generation): Boolean {
        if (expected != generation) return false
        clear()
        value = null
        loaded = true
        generation++
        return true
    }
}

internal class InvalidOAuthGrantException : IllegalStateException("DM-D.S.S authorization is no longer valid.")

/** Only a definitive grant rejection may delete stored authorization. */
internal fun isDefinitiveOAuthGrantFailure(status: Int, error: String): Boolean =
    status in setOf(400, 401) && error == "invalid_grant"

internal fun validOAuthCallback(
    actualRedirect: String, expectedRedirect: String, state: String?,
    pendingState: String?, ageMillis: Long, maximumAgeMillis: Long
): Boolean = actualRedirect == expectedRedirect && pendingState != null &&
    state == pendingState && ageMillis in 0..maximumAgeMillis

package cz.misa.quakedeck.data

import android.content.Context
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock

/**
 * Keeps a live EEW deadline on the provider's existing handler. Call every
 * method on that handler's looper, including foreground and input checks.
 */
internal class EewExpiryTimer(
    context: Context,
    private val handler: Handler,
    private val owner: String
) {
    private val powerManager by lazy(LazyThreadSafetyMode.NONE) {
        context.applicationContext.getSystemService(PowerManager::class.java)
    }
    private var deadlineMillis = 0L
    private var deadlineElapsedMillis = 0L
    private var onExpired: (() -> Unit)? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val checkDeadline = Runnable {
        if (!expireIfDue()) scheduleCheck()
    }

    fun arm(deadlineMillis: Long, keepCpuAwake: Boolean, onExpired: () -> Unit) {
        cancel()
        val delay = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        this.deadlineMillis = deadlineMillis
        deadlineElapsedMillis = SystemClock.elapsedRealtime() + delay
        this.onExpired = onExpired

        if (keepCpuAwake && delay > 0L) {
            // A partial lock lets the scheduled work run with the screen off.
            // Its timeout also releases it if the scheduler is unexpectedly stalled.
            val timeout = delay.coerceAtMost(EewLifecyclePolicy.ACTIVE_MILLIS) +
                WAKE_LOCK_GRACE_MILLIS
            wakeLock = runCatching {
                powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuakeDeck:EEW-$owner")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire(timeout)
                    }
            }.getOrNull()
        }
        scheduleCheck()
    }

    fun cancel() {
        val heldLock = clear()
        if (heldLock?.isHeld == true) heldLock.release()
    }

    fun expireIfDue(): Boolean {
        val callback = onExpired ?: return false
        if (remainingMillis() > 0L) return false

        // Clear the old schedule before calling the provider so it can arm a
        // new warning without inheriting this callback or its CPU lock.
        val heldLock = clear()
        try {
            callback()
        } finally {
            // The provider can queue the Ended notification on a worker. Give
            // it a short grace period while the lock's own timeout stays armed.
            if (heldLock?.isHeld == true) {
                val release = Runnable { if (heldLock.isHeld) heldLock.release() }
                if (!handler.postDelayed(release, WAKE_LOCK_GRACE_MILLIS)) release.run()
            }
        }
        return true
    }

    private fun remainingMillis(): Long = minOf(
        deadlineMillis - System.currentTimeMillis(),
        deadlineElapsedMillis - SystemClock.elapsedRealtime()
    )

    private fun scheduleCheck() {
        if (onExpired != null &&
            !handler.postDelayed(checkDeadline, remainingMillis().coerceAtLeast(0L))
        ) cancel()
    }

    private fun clear(): PowerManager.WakeLock? {
        handler.removeCallbacks(checkDeadline)
        onExpired = null
        val heldLock = wakeLock
        wakeLock = null
        return heldLock
    }

    private companion object {
        const val WAKE_LOCK_GRACE_MILLIS = 5_000L
    }
}

/** One timer per provider, with a deadline for each warning it has accepted. */
internal class EewIncidentExpiryScheduler(
    context: Context,
    handler: Handler,
    owner: String,
    private val keepCpuAwake: () -> Boolean,
    private val onExpired: (PendingEewExpiry) -> Unit
) {
    private val pending = PendingEewExpiries()
    private val timer = EewExpiryTimer(context, handler, owner)

    fun schedule(event: EarthquakeEvent, deadlineMillis: Long) {
        pending.schedule(event, deadlineMillis)
        armNext()
    }

    fun incident(eventId: String): PendingEewExpiry? = pending.incident(eventId)

    fun newestIncident(): PendingEewExpiry? = pending.newestFirst().firstOrNull()

    fun incidents(primary: EarthquakeEvent?): List<ActiveEewIncident> {
        val entries = pending.newestFirst().map {
            ActiveEewIncident(primary?.takeIf { event -> event.id == it.event.id } ?: it.event, it.deadlineMillis)
        }
        return if (primary == null) entries else {
            entries.sortedByDescending { it.event.id == primary.id }
        }
    }

    fun remove(eventId: String) {
        if (pending.remove(eventId) != null) armNext()
    }

    fun removeDrills() {
        pending.removeDrills()
        armNext()
    }

    fun clear() {
        pending.clear()
        timer.cancel()
    }

    fun expireIfDue() {
        timer.expireIfDue()
    }

    private fun armNext() {
        val deadline = pending.nextDeadlineMillis
        if (deadline == null) {
            timer.cancel()
            return
        }
        timer.arm(deadline, keepCpuAwake() && pending.hasRealWarning) {
            val due = pending.extractDue(maxOf(deadline, System.currentTimeMillis()))
            due.forEach(onExpired)
            armNext()
        }
    }
}

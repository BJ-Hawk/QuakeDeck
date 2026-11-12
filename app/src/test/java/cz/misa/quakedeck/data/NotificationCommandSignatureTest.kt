package cz.misa.quakedeck.data

import org.junit.Assert.*
import org.junit.Test

class NotificationCommandSignatureTest {
    private val key = ByteArray(32) { it.toByte() }
    private val command = NotificationCommand("event", """{"kind":"EEW"}""", true)

    @Test fun genuineCommandSurvivesReconstruction() {
        val signature = NotificationCommandSignature.sign(key, command)
        assertTrue(NotificationCommandSignature.verify(key.clone(), command.copy(), signature))
    }

    @Test fun eachPrivilegedFieldIsAuthenticated() {
        val signature = NotificationCommandSignature.sign(key, command)
        listOf(command.copy(reportId = "other"), command.copy(payload = "{}"),
            command.copy(fullScreen = false)).forEach {
            assertFalse(NotificationCommandSignature.verify(key, it, signature))
        }
    }

    @Test fun unrelatedInstallAndUnsignedLegacyCommandsAreRejected() {
        val signature = NotificationCommandSignature.sign(key, command)
        assertFalse(NotificationCommandSignature.verify(ByteArray(32) { 42 }, command, signature))
        assertFalse(NotificationCommandSignature.verify(key, command, ""))
    }

    @Test fun nullAndEmptyFieldsCannotShareSignatures() {
        val absent = NotificationCommand(null, "", false)
        val signature = NotificationCommandSignature.sign(key, absent)
        assertFalse(NotificationCommandSignature.verify(key, absent.copy(reportId = ""), signature))
        assertFalse(NotificationCommandSignature.verify(key, absent.copy(payload = null), signature))
    }

    @Test fun overlargeInputIsRejectedBeforeMacOrJsonProcessing() {
        assertFalse(NotificationCommandSignature.verify(key, command.copy(payload = "x".repeat(450_001)), "x".repeat(44)))
        assertFalse(NotificationCommandSignature.verify(key, command.copy(reportId = "x".repeat(1_025)), "x".repeat(44)))
    }
}

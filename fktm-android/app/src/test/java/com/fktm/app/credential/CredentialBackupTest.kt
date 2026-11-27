package com.fktm.app.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class CredentialBackupTest {
    private val password = "迁移密码-credential-test🚪".toCharArray()
    private val voc = LockCredential(
        name = "宿舍门🚪",
        address = "AA:BB:CC:DD:EE:FA",
        activationKey = "0123456789ABCDEF0123456789ABCDEF",
        serviceUuid = "0000180f-0000-1000-8000-00805f9b34fb",
        deviceName = "VOC-TEST",
    )
    private val mv = LockCredential("MV 锁", "AA:BB:CC:DD:EE:FB", "", type = LockCredential.TYPE_MV)

    @Test fun `encrypted backup round trips both lock types and preserves their order`() {
        val file = CredentialBackup.encode(listOf(mv, voc), password)
        val restored = CredentialBackup.decode(file, password)
        assertEquals(listOf(mv, voc), restored.locks)
        assertEquals(null, restored.campusSession)
        assertEquals(1, file[8].toInt())
        assertFalse(file.toString(Charsets.UTF_8).contains(voc.activationKey))
        assertFalse(file.contentEquals(CredentialBackup.encode(listOf(mv, voc), password)))
    }

    @Test fun `encrypted backup round trips the optional campus session with locks`() {
        val session = linkedMapOf("cookie2" to "synthetic-session-cookie", "munb" to "synthetic-account")
        val file = CredentialBackup.encode(listOf(mv), password, session)
        val restored = CredentialBackup.decode(file, password)
        assertEquals(2, file[8].toInt())
        assertEquals(listOf(mv), restored.locks)
        assertEquals(session, restored.campusSession)
        assertFalse(file.toString(Charsets.UTF_8).contains("synthetic-session-cookie"))
        assertThrows(CredentialBackup.AuthenticationFailed::class.java) {
            CredentialBackup.decode(file, "incorrect-password".toCharArray())
        }
    }

    @Test fun `session can be restored without a lock and an empty backup is rejected`() {
        val session = mapOf("cookie2" to "synthetic-session-cookie")
        val restored = CredentialBackup.decode(CredentialBackup.encode(emptyList(), password, session), password)
        assertEquals(emptyList<LockCredential>(), restored.locks)
        assertEquals(session, restored.campusSession)
        assertThrows(CredentialBackup.InvalidCredentials::class.java) {
            CredentialBackup.encode(emptyList(), password)
        }
    }

    @Test fun `incomplete campus sessions are rejected before export`() {
        assertThrows(CredentialBackup.InvalidCredentials::class.java) {
            CredentialBackup.encode(listOf(mv), password, mapOf("munb" to "synthetic-account"))
        }
    }

    @Test fun `incorrect password or modified ciphertext cannot be restored`() {
        val file = CredentialBackup.encode(listOf(voc), password)
        assertThrows(CredentialBackup.AuthenticationFailed::class.java) {
            CredentialBackup.decode(file, "incorrect-password".toCharArray())
        }
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(CredentialBackup.AuthenticationFailed::class.java) {
            CredentialBackup.decode(file, password)
        }
    }

    @Test fun `unknown files unsupported versions and oversized streams are rejected`() {
        assertThrows(CredentialBackup.InvalidFile::class.java) {
            CredentialBackup.decode("{}".toByteArray(), password)
        }
        val file = CredentialBackup.encode(listOf(voc), password)
        assertThrows(CredentialBackup.InvalidFile::class.java) {
            CredentialBackup.decode(file.copyOf(8), password)
        }
        // Format magic is eight bytes, followed by the version byte.
        file[8] = 127
        assertThrows(CredentialBackup.UnsupportedVersion::class.java) {
            CredentialBackup.decode(file, password)
        }
        assertThrows(CredentialBackup.InvalidFile::class.java) {
            CredentialBackup.read(ByteArrayInputStream(ByteArray(CredentialBackup.MAX_FILE_BYTES + 1)))
        }
    }

    @Test fun `invalid locks are rejected before export`() {
        assertThrows(CredentialBackup.InvalidCredentials::class.java) {
            CredentialBackup.encode(listOf(mv, voc.copy(activationKey = "invalid")), password)
        }
    }

    @Test fun `restore appends only new addresses without changing local primary or credentials`() {
        val incomingDuplicate = voc.copy(name = "backup name", address = voc.address.lowercase())
        val incomingRepeated = mv.copy(name = "duplicate", address = mv.address.lowercase())
        assertEquals(listOf(voc, mv), CredentialBackup.merge(listOf(voc), listOf(incomingDuplicate, mv, incomingRepeated)))
        assertEquals(listOf(mv, voc), CredentialBackup.merge(emptyList(), listOf(mv, voc)))
    }
}

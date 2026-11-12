package com.raju.aiassistant.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

class SecureKeyStoreErrorTest {
    @Test
    fun aSecretThatCanNeverBeDecryptedAgainIsRemoved() {
        assertTrue(isUnrecoverableSecretError(AEADBadTagException("tag mismatch"))) // new key after reinstall
        assertTrue(isUnrecoverableSecretError(IllegalArgumentException("bad base-64")))
        assertTrue(isUnrecoverableSecretError(UnrecoverableKeyException("gone")))
    }

    @Test
    fun aPassingKeystoreErrorKeepsTheSavedSecret() {
        // These used to delete the user's API key; now the key is kept and works again on the next try.
        assertFalse(isUnrecoverableSecretError(KeyStoreException("busy")))
        assertFalse(isUnrecoverableSecretError(ProviderException("Keystore operation failed")))
        assertFalse(isUnrecoverableSecretError(IllegalStateException("locked")))
        assertFalse(isUnrecoverableSecretError(RuntimeException("vendor glitch")))
    }
}

package io.github.bluewind8791.checkinreminder.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.cookieDataStore by preferencesDataStore(name = "cookie")

/**
 * Keeps the PHPSESSID value encrypted with an AES-256-GCM key in Android Keystore.
 * The key never leaves the device. Only the IV and the ciphertext go to DataStore.
 */
class CookieStore(context: Context) {
    private val dataStore = context.applicationContext.cookieDataStore

    suspend fun save(sessionId: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(sessionId.toByteArray(Charsets.UTF_8))
        dataStore.edit {
            it[IV] = encode(cipher.iv)
            it[CIPHERTEXT] = encode(encrypted)
        }
    }

    /** Returns null when nothing is saved, or when the value cannot be decrypted (for example, the key is gone). */
    suspend fun load(): String? = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val iv = prefs[IV] ?: return@withContext null
        val encrypted = prefs[CIPHERTEXT] ?: return@withContext null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(iv)))
            String(cipher.doFinal(decode(encrypted)), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            // Bad base64.
            null
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as SecretKey?)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cookie-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val IV = stringPreferencesKey("iv")
        val CIPHERTEXT = stringPreferencesKey("ciphertext")
    }
}

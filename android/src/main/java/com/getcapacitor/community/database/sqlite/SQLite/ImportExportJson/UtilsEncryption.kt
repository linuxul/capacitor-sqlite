package com.getcapacitor.community.database.sqlite.SQLite.ImportExportJson

import android.content.Context
import android.util.Base64
import com.getcapacitor.JSObject
import com.getcapacitor.community.database.sqlite.SQLite.UtilsSecret
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

public object UtilsEncryption {
    private const val ITERATION_COUNT = 65536
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    private const val SALT = "jeep_capacitor_sqlite"

    @Suppress("UNUSED_PARAMETER")
    public fun encryptJSONObject(context: Context?, jsonObject: JSONObject): String {
        val jsonString = jsonObject.toString()

        if (!UtilsSecret.isPassphrase()) {
            throw Exception("encryptJSONObject: No Passphrase stored")
        }
        val passphrase: String? = UtilsSecret.getPassphrase()

        try {
            val saltBytes = SALT.toByteArray(Charsets.UTF_8)
            val secretKey = deriveKey(passphrase, saltBytes)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            // Extract the IV from the saltBytes (first 12 bytes for GCM)
            val spec = GCMParameterSpec(128, saltBytes, 0, 12)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val encryptedBytes = cipher.doFinal(jsonString.toByteArray(Charset.defaultCharset()))

            // Concatenate salt and encrypted data for storage
            val combined = ByteArray(saltBytes.size + encryptedBytes.size)
            System.arraycopy(saltBytes, 0, combined, 0, saltBytes.size)
            System.arraycopy(encryptedBytes, 0, combined, saltBytes.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("encryptJSONObject: " + e.message)
        }
    }

    // Decrypts the JSONObject from the Base64 string
    @Suppress("UNUSED_PARAMETER")
    public fun decryptJSONObject(context: Context?, encryptedBase64: String?): JSObject {
        if (!UtilsSecret.isPassphrase()) {
            throw Exception("decryptJSONObject: No Passphrase stored")
        }
        val passphrase: String? = UtilsSecret.getPassphrase()

        try {
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)

            val saltBytes = SALT.toByteArray(Charsets.UTF_8)

            val encryptedBytes = ByteArray(combined.size - saltBytes.size)
            System.arraycopy(combined, 0, saltBytes, 0, saltBytes.size)
            System.arraycopy(combined, saltBytes.size, encryptedBytes, 0, encryptedBytes.size)

            val secretKey = deriveKey(passphrase, saltBytes)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)

            // Extract the IV from the saltBytes (first 12 bytes for GCM)
            val spec = GCMParameterSpec(128, saltBytes, 0, 12)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return JSObject(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("decryptJSONObject: " + e.message)
        }
    }

    /** Derive a secure key from the passphrase using PBKDF2 */
    private fun deriveKey(passphrase: String?, saltBytes: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        // A missing passphrase was a NullPointerException here in Java as well
        val keySpec = PBEKeySpec(passphrase!!.toCharArray(), saltBytes, ITERATION_COUNT, 256)
        return SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")
    }
}

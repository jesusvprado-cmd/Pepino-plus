package com.suso.miiptv.data

import android.os.Build
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de los datos delicados.
 * - Cuenta de la lista y perfil WireGuard: cifrados con una llave que guarda el propio aparato (Android Keystore);
 *   esa llave no se puede sacar del aparato.
 * - PIN: se guarda solo una huella (no se puede recuperar el PIN a partir de ella).
 * - Copia de seguridad: cifrada con una llave sacada del PIN que eliges al guardarla.
 */
object Secure {
    private const val ALIAS = "pepino_datos"
    private const val DEVICE_PREFIX = "k1:"
    private val rnd = SecureRandom()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    // ---------- Llave del aparato ----------

    private fun deviceKey(): SecretKey? {
        if (Build.VERSION.SDK_INT < 23) return null
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Cifra con la llave del aparato. Si el aparato no lo permite (Android 5), se guarda tal cual. */
    fun seal(plain: String?): String? {
        if (plain == null || plain.startsWith(DEVICE_PREFIX)) return plain
        return try {
            val key = deviceKey() ?: return plain
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key)
            DEVICE_PREFIX + b64(c.iv) + ":" + b64(c.doFinal(plain.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            plain
        }
    }

    /** Descifra lo guardado con [seal]; lo que no está cifrado se devuelve igual. null si no se puede descifrar. */
    fun open(stored: String?): String? {
        if (stored == null || !stored.startsWith(DEVICE_PREFIX)) return stored
        return try {
            val (iv, ct) = stored.removePrefix(DEVICE_PREFIX).split(':', limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, deviceKey() ?: return null, GCMParameterSpec(128, unb64(iv)))
            String(c.doFinal(unb64(ct)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun isSealed(s: String?) = s != null && s.startsWith(DEVICE_PREFIX)

    // ---------- PIN ----------

    private fun pbkdf2(pin: String, salt: ByteArray, iter: Int, alg: String, bits: Int): ByteArray =
        SecretKeyFactory.getInstance(alg).generateSecret(PBEKeySpec(pin.toCharArray(), salt, iter, bits)).encoded

    private const val PIN_ITER = 20_000

    /** Huella del PIN para guardarla: «p1:sal:huella». */
    fun pinHash(pin: String): String {
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        return "p1:" + b64(salt) + ":" + b64(pbkdf2(pin, salt, PIN_ITER, "PBKDF2WithHmacSHA1", 256))
    }

    fun pinMatches(pin: String, stored: String): Boolean {
        if (!stored.startsWith("p1:")) return pin == stored
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val h = pbkdf2(pin, unb64(parts[1]), PIN_ITER, "PBKDF2WithHmacSHA1", 256)
        return MessageDigest.isEqual(h, unb64(parts[2]))
    }

    // ---------- Copia de seguridad ----------

    private const val BACKUP_ITER = 150_000

    private fun backupKdf(): String =
        if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    /** Cifra el contenido de la copia con el PIN. Devuelve el bloque «cifrado» que va en el archivo. */
    fun encryptBackup(json: String, pin: String): org.json.JSONObject {
        val kdf = backupKdf()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val key = SecretKeySpec(pbkdf2(pin, salt, BACKUP_ITER, kdf, 256), "AES")
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return org.json.JSONObject()
            .put("kdf", kdf).put("iter", BACKUP_ITER)
            .put("salt", b64(salt)).put("iv", b64(iv))
            .put("datos", b64(c.doFinal(json.toByteArray(Charsets.UTF_8))))
    }

    /** Descifra la copia; lanza una excepción legible si el PIN no es el correcto. */
    fun decryptBackup(o: org.json.JSONObject, pin: String): String = try {
        val key = SecretKeySpec(pbkdf2(pin, unb64(o.getString("salt")), o.getInt("iter"), o.getString("kdf"), 256), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(o.getString("iv"))))
        String(c.doFinal(unb64(o.getString("datos"))), Charsets.UTF_8)
    } catch (e: javax.crypto.AEADBadTagException) {
        throw IllegalStateException("PIN incorrecto")
    } catch (e: java.security.GeneralSecurityException) {
        throw IllegalStateException("PIN incorrecto o copia dañada")
    }
}

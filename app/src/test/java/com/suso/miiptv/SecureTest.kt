package com.suso.miiptv

import com.suso.miiptv.data.Backup
import com.suso.miiptv.data.Secure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Prueba del cifrado: huella del PIN y copia de seguridad cifrada con PIN (ida y vuelta). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SecureTest {
    @Test
    fun huellaDelPin() {
        val h = Secure.pinHash("1234")
        assertFalse(h.contains("1234"))
        assertTrue(Secure.pinMatches("1234", h))
        assertFalse(Secure.pinMatches("1235", h))
        // PIN antiguo guardado tal cual: sigue valiendo.
        assertTrue(Secure.pinMatches("4321", "4321"))
    }

    @Test
    fun copiaCifrada() {
        val ctx = RuntimeEnvironment.getApplication()
        val sp = ctx.getSharedPreferences("miiptv", android.content.Context.MODE_PRIVATE)
        sp.edit().clear().putString("config", """{"type":"xtream","server":"https://demo","user":"yo","pass":"secreta"}""")
            .putBoolean("afr", true).commit()
        val f = Backup.export(ctx, "1234")
        val text = f.readText()
        assertFalse("la contraseña no debe verse en el archivo", text.contains("secreta"))
        assertTrue(Backup.isEncrypted(text))
        try {
            Backup.restoreText(ctx, text, "9999")
            fail("con un PIN incorrecto no debe restaurar")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.startsWith("PIN incorrecto"))
        }
        sp.edit().clear().commit()
        Backup.restoreText(ctx, text, "1234")
        assertTrue(sp.getString("config", "")!!.contains("secreta"))
        assertEquals(true, sp.getBoolean("afr", false))
    }
}

package com.suso.miiptv

import com.suso.miiptv.data.ChannelNames
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.Repo
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Prueba de logos: para cada canal de logo_names.txt calcula el nombre que se muestra y el logo que le toca
 * (dirección + ajustes: fondo blanco, número, retoque de color) y lo compara con logos_golden.tsv.
 * Si un logo cambia sin querer, la compilación se para antes de publicar la versión.
 * Si el cambio es buscado, se actualiza logos_golden.tsv con el informe (app/build/logo-report.tsv).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LogoGoldenTest {
    @Test
    fun logosDeLosCanales() {
        val ctx = RuntimeEnvironment.getApplication()
        Repo.init(ctx)
        Logos.loadForTest(ctx)
        val lines = res("logo_names.txt")!!.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val sections = HashMap<String, String>()
        val names = lines.map { l ->
            val raw = l.substringBefore('|')
            val shown = ChannelNames.display(raw)
            if (l.contains('|')) sections[shown] = l.substringAfter('|')
            raw to shown
        }
        if (sections.isNotEmpty()) Logos.setSections(sections)
        // Como en la app: cada canal trae su propio logo de la lista (aquí, uno de mentira) por si no hay otro mejor.
        val report = names.joinToString("\n") { (raw, shown) -> "$raw\t$shown\t${Logos.url(shown, LIST_LOGO)}" } + "\n"
        val out = File("build/logo-report.tsv")
        out.parentFile?.mkdirs()
        out.writeText(report)
        val golden = res("logos_golden.tsv") ?: return // primera vez: solo se genera el informe
        if (golden != report) {
            val want = golden.lines().associateBy { it.substringBefore('\t') }
            val diffs = report.lines().filter { it.isNotEmpty() && want[it.substringBefore('\t')] != it }
            fail(
                "Han cambiado ${diffs.size} logos o nombres:\n" +
                    diffs.take(40).joinToString("\n") { "ahora:  $it\nantes:  ${want[it.substringBefore('\t')] ?: "(nuevo)"}" }
            )
        }
    }

    private companion object {
        const val LIST_LOGO = "logo-de-la-lista"
    }

    private fun res(name: String): String? = javaClass.classLoader?.getResource(name)?.readText()
}

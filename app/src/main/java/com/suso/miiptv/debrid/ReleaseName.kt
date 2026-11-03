package com.suso.miiptv.debrid

import java.text.Normalizer

/**
 * Lee lo que dice el nombre de un archivo de vídeo: título, año, temporada y capítulo,
 * calidad (4K, 1080p...), HDR, idiomas del audio y subtítulos.
 *
 * Ejemplos que entiende:
 *  - «Dune.Parte.Dos.2024.2160p.UHD.BluRay.HDR.DV.Castellano.Ingles.mkv»
 *  - «The Bear S03E04 1080p WEB-DL DUAL.mkv», «Bluey 2x05.mp4», «Cap.102», «Temporada 1/Episodio 3»
 */
object ReleaseName {
    data class Info(
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
        /** Último capítulo si el archivo trae dos seguidos (S01E01-E02). */
        val episodeEnd: Int?,
        /** 2160, 1080, 720, 480 o 0 si no lo dice. */
        val res: Int,
        /** "DV", "HDR10+", "HDR" o null. */
        val hdr: String?,
        val codec: String?,
        val source: String?,
        /** Idiomas del audio: "es" (castellano), "lat" (latino), "en", "multi"... */
        val audio: Set<String>,
        /** Idiomas de subtítulos: "es", "en"... ("?" = lleva subtítulos sin decir cuáles). */
        val subs: Set<String>,
        val extra: Boolean,
    ) {
        val isEpisode get() = episode != null
    }

    private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "m4v", "ts", "m2ts", "webm", "mov", "wmv", "mpg", "mpeg", "divx", "flv", "3gp", "ogv")

    fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    private val IC = setOf(RegexOption.IGNORE_CASE)

    // Webs y grupos que algunos ponen delante: «[www.web.com]», «www.web.org - ».
    private val SITE = Regex("""\[[^\]]*\b(www\.|\.com|\.org|\.net|\.tv|\.to|\.cc|\.se|\.me|\.li|\.io|\.xyz|\.top)[^\]]*\]|\bwww\.\S+|\b\S+\.(com|org|net|tv|cc|se|me|li|xyz|top)\b""", IC)

    private val SXE = Regex("""\bS(\d{1,2})[ ._-]*E(\d{1,3})(?:[ ._-]*(?:E|-)(\d{1,3})(?!\d))?""", IC)
    private val NXN = Regex("""(?<![\dA-Za-z])(\d{1,2})x(\d{2,3})(?:[-x](\d{2,3}))?(?![\dA-Za-z])""", IC)
    private val CAP = Regex("""\bCap(?:itulo|ítulo|\.)?[ ._]*(\d{1,2})(\d{2})\b""", IC)
    private val TEMP_CAP = Regex("""\bTemporada[ ._]*(\d{1,2}).{0,40}?\bCap(?:itulo|ítulo|\.)?[ ._]*(\d{1,3})\b""", IC)
    private val EP_ONLY = Regex("""\b(?:E|Ep|Episodio|Episode|Capitulo|Capítulo|Cap)[ ._-]*(\d{1,3})\b""", IC)
    private val DASH_NUM = Regex("""\s-\s(\d{2,3})(?=\s|\[|\(|$)""")
    private val SEASON_DIR = Regex("""\b(?:S|Season|Temporada|Temp)[ ._-]*(\d{1,2})\b""", IC)
    private val YEAR = Regex("""(?<![\dA-Za-z])(19[2-9]\d|20[0-4]\d)(?![\dA-Za-z])""")

    private val R2160 = Regex("""\b(2160p|4k|uhd|uhdrip)\b""", IC)
    private val R1080 = Regex("""\b(1080[pi]|fullhd|fhd|bdremux|microhd)\b""", IC)
    private val R720 = Regex("""\b(720p|hdtv720|hdrip)\b""", IC)
    private val R480 = Regex("""\b(480p|576p|360p|dvdrip|dvdscr|sd|xvid|divx|vhsrip|tvrip)\b""", IC)

    private val DV = Regex("""\b(dv|dovi|dolby[ ._-]?vision)\b""", IC)
    private val HDR10P = Regex("""\bhdr10(\+|plus|p)""", IC)
    private val HDR = Regex("""\bhdr(10)?\b|\bhlg\b""", IC)

    private val HEVC = Regex("""\b(x265|h[ .]?265|hevc)\b""", IC)
    private val AVC = Regex("""\b(x264|h[ .]?264|avc)\b""", IC)
    private val AV1 = Regex("""\bav1\b""", IC)

    private val SOURCES = listOf(
        "REMUX" to Regex("""\b(bd)?remux\b""", IC),
        "BluRay" to Regex("""\b(blu[ ._-]?ray|bdrip|brrip|bd\d{2}|bdmv)\b""", IC),
        "WEB-DL" to Regex("""\bweb[ ._-]?dl\b""", IC),
        "WEBRip" to Regex("""\bweb[ ._-]?rip\b|\bweb\b""", IC),
        "HDTV" to Regex("""\bhdtv\b""", IC),
        "MicroHD" to Regex("""\bmicrohd\b""", IC),
        "DVD" to Regex("""\bdvd(rip|r|5|9)?\b""", IC),
        "Screener" to Regex("""\b(ts|telesync|cam|camrip|hdcam|screener|scr)\b""", IC),
    )

    private val CAST = Regex("""\b(castellano|cast|español|espanol|spanish|spa|esp|españa)\b""", IC)
    private val LAT = Regex("""\b(latino|lat|latam|es[ ._-]?la|es[ ._-]?419|mx)\b""", IC)
    private val ENG = Regex("""\b(ingl[eé]s|english|eng)\b""", IC)
    private val VOSE = Regex("""\b(vose|vos|v\.o\.s\.e)\b""", IC)
    private val VO = Regex("""\b(vo|v\.o\.)\b""", IC)
    private val DUAL = Regex("""\b(dual|dual[ ._-]?audio)\b""", IC)
    private val MULTI = Regex("""\b(multi|multilang|multi[ ._-]?audio|trial)\b""", IC)
    private val FRE = Regex("""\b(french|fran[cç]ais|vf|vff|truefrench)\b""", IC)
    private val ITA = Regex("""\b(ita|italian|italiano)\b""", IC)
    private val GER = Regex("""\b(german|deutsch|ger)\b""", IC)
    private val JAP = Regex("""\b(japanese|jap|jpn)\b""", IC)
    private val CAT = Regex("""\b(catal[aà]n?|cat)\b""", IC)
    private val GAL = Regex("""\b(gallego|galego|glg)\b""", IC)

    private val SUBS_ES = Regex("""\b(subs?|subt[ií]tulos?|subtitulad[ao]s?)[ ._-]*(castellano|español|espanol|spanish|spa|esp|es|forzados)\b|\b(castellano|español|spanish|spa|esp)[ ._-]*(subs?|subt[ií]tulos?)\b|\bsubtitulad[ao]\b""", IC)
    private val SUBS_EN = Regex("""\b(subs?|subt[ií]tulos?)[ ._-]*(ingl[eé]s|english|eng|en)\b|\b(english|eng)[ ._-]*subs?\b""", IC)
    private val SUBS_ANY = Regex("""\b(subs?|subt[ií]tulos?|subbed|multisubs?|msubs)\b""", IC)

    private val EXTRA = Regex("""\b(sample|trailer|tr[aá]iler|featurette|extras?|behind[ ._-]the[ ._-]scenes|making[ ._-]of|deleted[ ._-]scenes?|bonus|teaser)\b""", IC)

    /** Primer «etiqueta técnica» del nombre: lo que hay antes es el título. */
    private val TECH = Regex(
        """\b(2160p|1080[pi]|720p|480p|576p|4k|uhd|hdr\d*|dv|dovi|x26[45]|h[ .]?26[45]|hevc|avc|av1|blu[ ._-]?ray|bdrip|brrip|bd\d{2}|""" +
            """(bd)?remux|web[ ._-]?(dl|rip)|hdtv|hdrip|dvdrip|dvd\d?|microhd|castellano|spanish|español|espanol|latino|dual|multi|vose|""" +
            """ac3|dts(-?hd)?|truehd|atmos|aac|ddp?\d?|eac3|10bit|8bit|proper|repack|extended|unrated|remastered|imax|""" +
            """temporada|season|cap[ií]tulo|episodio|complet[ao])\b""",
        IC,
    )

    private val BRACKETS = Regex("""\[[^\]]*\]|\{[^}]*\}""")

    private fun spaces(s: String): String {
        var t = s
        // Puntos y guiones bajos como separadores (pero no en «Mr. Robot» con espacio detrás, que se queda igual).
        if (!t.contains(' ') || t.count { it == '.' } > 2) t = t.replace('.', ' ')
        t = t.replace('_', ' ')
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun tidyTitle(raw: String): String {
        var t = raw.replace(BRACKETS, " ")
        t = t.replace(Regex("""\(\s*\)"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim()
        t = t.trim(' ', '-', '–', '|', ':', '(', '[', ',', '.', '+', '~', '_')
        // Quita «Temporada 1» o «S01» que se hayan quedado al final del título.
        t = t.replace(Regex("""\s*\b(temporada|season|temp|s)\s*\d{1,2}\s*$""", IC), "").trim(' ', '-', '–')
        return t
    }

    fun parse(fileName: String, folder: String? = null): Info {
        val base = fileName.substringBeforeLast('.').let { if (isVideo(fileName)) it else fileName }
        val clean = spaces(base.replace(SITE, " "))
        val folderClean = folder?.let { spaces(it.replace(SITE, " ")) }
        // Para calidad e idiomas se mira el nombre del archivo y el de la carpeta (muchas veces solo lo dice la carpeta).
        val all = if (folderClean != null) "$clean $folderClean" else clean

        var season: Int? = null
        var episode: Int? = null
        var episodeEnd: Int? = null
        var epPos = -1
        SXE.find(clean)?.let {
            season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt()
            episodeEnd = it.groupValues[3].toIntOrNull(); epPos = it.range.first
        }
        if (episode == null) NXN.find(clean)?.let {
            season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt()
            episodeEnd = it.groupValues[3].toIntOrNull(); epPos = it.range.first
        }
        if (episode == null) TEMP_CAP.find(clean)?.let {
            season = it.groupValues[1].toInt()
            val c = it.groupValues[2].toInt()
            // «Temporada 2 Cap 203»: el capítulo lleva delante la temporada.
            episode = if (c >= 100 && c / 100 == season) c % 100 else c
            epPos = it.range.first
        }
        if (episode == null) CAP.find(clean)?.let {
            season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt(); epPos = it.range.first
        }
        if (episode == null) {
            val dirSeason = folderClean?.let { f -> SEASON_DIR.findAll(f).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() }
                ?: SEASON_DIR.find(clean)?.groupValues?.get(1)?.toIntOrNull()
            val m = EP_ONLY.find(clean)
            if (m != null && (dirSeason != null || folderClean != null)) {
                season = dirSeason ?: 1; episode = m.groupValues[1].toInt(); epPos = m.range.first
            } else {
                // Anime: «Serie - 05 [1080p]».
                DASH_NUM.find(clean)?.let {
                    val n = it.groupValues[1].toInt()
                    if (YEAR.matchEntire(it.groupValues[1]) == null) {
                        season = dirSeason ?: 1; episode = n; epPos = it.range.first
                    }
                }
            }
        }

        // Año: el último que aparece antes de las etiquetas técnicas (así «2001 Odisea... 1968» da 1968).
        val techPos = TECH.find(clean)?.range?.first ?: clean.length
        val years = YEAR.findAll(clean).filter { it.range.first > 0 || clean.length <= 4 }.toList()
        val yearM = years.lastOrNull { it.range.first < maxOf(techPos, if (epPos >= 0) epPos else 0) + 1 } ?: years.firstOrNull()
        var year = yearM?.value?.toIntOrNull()
        if (year == null && folderClean != null) year = YEAR.find(folderClean)?.value?.toIntOrNull()

        // Título: lo que hay antes del año, de «S01E02» o de la primera etiqueta técnica.
        val cuts = listOfNotNull(
            yearM?.range?.first?.takeIf { it > 0 },
            epPos.takeIf { it >= 0 },
            TECH.find(clean)?.range?.first?.takeIf { it > 0 },
            clean.indexOf('[').takeIf { it > 0 },
        )
        var title = tidyTitle(clean.substring(0, cuts.minOrNull() ?: clean.length))
        // «S01E02 - Nombre del capítulo.mkv» o «Capitulo 3.mkv»: el título de la serie sale de la carpeta.
        if ((title.isBlank() || title.length < 2 || (episode != null && epPos == 0)) && folderClean != null) {
            val f = folderClean
            val fc = listOfNotNull(
                YEAR.find(f)?.range?.first?.takeIf { it > 0 },
                SEASON_DIR.find(f)?.range?.first?.takeIf { it > 0 },
                SXE.find(f)?.range?.first?.takeIf { it > 0 },
                TECH.find(f)?.range?.first?.takeIf { it > 0 },
                f.indexOf('[').takeIf { it > 0 },
            ).minOrNull()
            title = tidyTitle(f.substring(0, fc ?: f.length))
        }
        if (title.isBlank()) title = tidyTitle(clean).ifBlank { base }

        val res = when {
            R2160.containsMatchIn(all) -> 2160
            R1080.containsMatchIn(all) -> 1080
            R720.containsMatchIn(all) -> 720
            R480.containsMatchIn(all) -> 480
            else -> 0
        }
        val hdr = when {
            DV.containsMatchIn(all) -> "DV"
            HDR10P.containsMatchIn(all) -> "HDR10+"
            HDR.containsMatchIn(all) -> "HDR"
            else -> null
        }
        val codec = when {
            HEVC.containsMatchIn(all) -> "HEVC"
            AV1.containsMatchIn(all) -> "AV1"
            AVC.containsMatchIn(all) -> "H.264"
            else -> null
        }
        val source = SOURCES.firstOrNull { it.second.containsMatchIn(all) }?.first

        // Idiomas: se miran solo las partes «técnicas» (desde la primera etiqueta), para que una
        // palabra del título («Gran Hermano España») no cuente como idioma.
        val techAll = all.substring(minOf(techPos, clean.length).coerceAtMost(all.length))
        val langText = (if (techAll.length > 3) techAll else all).replace(BRACKETS) { " " + it.value.trim('[', ']', '{', '}') + " " }
            .let { t -> t + " " + Regex("""\[([^\]]*)\]""").findAll(all).joinToString(" ") { it.groupValues[1] } }
        val audio = LinkedHashSet<String>()
        val subs = LinkedHashSet<String>()
        val vose = VOSE.containsMatchIn(langText)
        if (CAST.containsMatchIn(langText) && !(vose && !DUAL.containsMatchIn(langText))) audio += "es"
        if (LAT.containsMatchIn(langText)) audio += "lat"
        if (DUAL.containsMatchIn(langText)) {
            if (audio.isEmpty()) audio += "es"
            audio += "en"
        }
        if (ENG.containsMatchIn(langText) && !SUBS_EN.containsMatchIn(langText)) audio += "en"
        if (FRE.containsMatchIn(langText)) audio += "fr"
        if (ITA.containsMatchIn(langText)) audio += "it"
        if (GER.containsMatchIn(langText)) audio += "de"
        if (JAP.containsMatchIn(langText)) audio += "ja"
        if (CAT.containsMatchIn(langText)) audio += "ca"
        if (GAL.containsMatchIn(langText)) audio += "gl"
        if (MULTI.containsMatchIn(langText)) audio += "multi"
        if (vose || VO.containsMatchIn(langText)) audio += "vo"
        if (vose || SUBS_ES.containsMatchIn(langText)) subs += "es"
        if (SUBS_EN.containsMatchIn(langText)) subs += "en"
        if (subs.isEmpty() && SUBS_ANY.containsMatchIn(langText)) subs += "?"

        val extra = EXTRA.containsMatchIn(clean)

        return Info(title, year, season, episode, episodeEnd, res, hdr, codec, source, audio, subs, extra)
    }

    /** Clave para agrupar: sin tildes, en minúsculas y solo letras y números. */
    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
        return n.replace("&", " and ").replace(Regex("""[^a-z0-9]+"""), " ").trim()
            .replace(Regex("""^(the|el|la|los|las) """), "")
    }
}

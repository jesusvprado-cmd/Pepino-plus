package com.suso.miiptv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class Prefs(ctx: Context) {
    data class Progress(val play: PlayItem, val pos: Long, val dur: Long, val ts: Long)

    companion object {
        /** A partir de aquí (92 %) un vídeo cuenta como terminado: visto y fuera de «Continuar viendo». */
        const val FINISHED = 0.92
    }

    private val sp = ctx.getSharedPreferences("miiptv", Context.MODE_PRIVATE)

    init {
        // Una sola vez: el ajuste de frecuencia de la pantalla pasa a desactivado (se activa a mano si hace falta).
        if (!sp.getBoolean("afrOff209", false)) sp.edit().putBoolean("afr", false).putBoolean("afrOff209", true).apply()
        // Datos delicados: la cuenta y el perfil WireGuard se guardan cifrados con la llave del aparato,
        // y del PIN solo su huella. Los que aún estén sin cifrar (versiones anteriores o una copia restaurada) se cifran ahora.
        try {
            val e = sp.edit()
            for (k in listOf("config", "wgConf")) {
                val v = sp.getString(k, null)
                if (v != null && !Secure.isSealed(v)) Secure.seal(v)?.let { e.putString(k, it) }
            }
            val pin = sp.getString("pin", "") ?: ""
            if (pin.isNotEmpty() && !pin.startsWith("p1:")) e.putString("pin", Secure.pinHash(pin))
            e.apply()
        } catch (e: Exception) {
            // Si algo falla, se sigue con los datos como estaban.
        }
    }

    /** Cambia cada vez que cambian favoritos o progreso, para refrescar la interfaz. */
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }

    fun loadConfig(): Config? =
        Secure.open(sp.getString("config", null))?.let { runCatching { Config.fromJson(JSONObject(it)) }.getOrNull() }

    fun saveConfig(c: Config) {
        sp.edit().putString("config", Secure.seal(c.toJson().toString())).apply()
    }

    var lastLive: String?
        get() = sp.getString(pk("lastLive"), null)
        set(v) {
            sp.edit().putString(pk("lastLive"), v).apply()
        }

    // ---------- Perfiles (cada uno con sus favoritos, «Continuar viendo», vistos, recordatorios e idiomas) ----------

    data class Profile(val id: Int, val name: String, val color: Long)

    private val PROFILE_COLORS = listOf(0xFF4FD65AL, 0xFF4FA3F7L, 0xFFF2994AL, 0xFFE0579CL, 0xFFB57BF2L, 0xFFF2D04AL)

    /** Perfil activo (0 = el principal, que usa los datos de siempre). */
    var profileId: Int = sp.getInt("profile", 0)
        private set

    /** Clave de un dato propio del perfil activo. */
    private fun pk(k: String): String = if (profileId == 0) k else "p${profileId}_$k"

    fun profiles(): List<Profile> {
        val l = mutableListOf<Profile>()
        runCatching {
            val a = JSONArray(sp.getString("profiles", "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                l.add(Profile(o.optInt("id"), o.optString("name"), o.optLong("color")))
            }
        }
        if (l.none { it.id == 0 }) l.add(0, Profile(0, "Principal", PROFILE_COLORS[0]))
        return l
    }

    private fun saveProfiles(l: List<Profile>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        sp.edit().putString("profiles", a.toString()).apply()
        settingsVersion++
        bump()
    }

    fun currentProfile(): Profile = profiles().firstOrNull { it.id == profileId } ?: profiles().first()

    fun switchProfile(id: Int) {
        profileId = id
        sp.edit().putInt("profile", id).apply()
        favs = null
        prog = null
        watchedSet = null
        settingsVersion++
        bump()
    }

    fun addProfile(name: String): Profile {
        val l = profiles().toMutableList()
        val id = (l.maxOfOrNull { it.id } ?: 0) + 1
        val p = Profile(id, name, PROFILE_COLORS[l.size % PROFILE_COLORS.size])
        l.add(p)
        saveProfiles(l)
        return p
    }

    fun renameProfile(id: Int, name: String) = saveProfiles(profiles().map { if (it.id == id) it.copy(name = name) else it })

    fun deleteProfile(id: Int) {
        if (id == 0) return
        val prefix = "p${id}_"
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith(prefix) }.forEach { e.remove(it) }
        e.apply()
        if (profileId == id) switchProfile(0)
        saveProfiles(profiles().filter { it.id != id })
    }

    /** Modo ligero (menos animaciones): auto, on, off. Se aplica al volver a abrir la app. */
    var lightMode: String
        get() = str("light_mode", "auto")
        set(v) = putStr("light_mode", v)

    /** Preguntar «¿Quién está viendo?» al abrir la app (si hay más de un perfil). */
    var askProfile: Boolean
        get() = sp.getBoolean("ask_profile", true)
        set(v) {
            sp.edit().putBoolean("ask_profile", v).apply()
            settingsVersion++
        }

    // ---------- Ajustes ----------

    /** Cambia cada vez que cambia un ajuste, para refrescar la pantalla de Ajustes. */
    var settingsVersion by mutableIntStateOf(0)
        private set

    private fun str(k: String, def: String): String = sp.getString(k, def) ?: def
    private fun putStr(k: String, v: String?) {
        sp.edit().putString(k, v).apply()
        settingsVersion++
    }
    private fun putInt(k: String, v: Int) {
        sp.edit().putInt(k, v).apply()
        settingsVersion++
    }
    // ---------------- Audio y subtítulos elegidos en cada película o episodio ----------------

    /** Lo elegido para [key] (o, si es un episodio, para la serie): "a" audio y "t" subtítulos ("off" = sin subtítulos). */
    fun trackChoice(key: String): JSONObject? {
        val own = sp.getString(pk("tracks_$key"), null)
        val series = if (key.startsWith("EP|")) sp.getString(pk("tracks_" + key).substringBeforeLast('|'), null) else null
        return (own ?: series)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    fun saveTrackChoice(key: String, field: String, value: String) {
        val keys = mutableListOf(key)
        if (key.startsWith("EP|")) keys += key.substringBeforeLast('|')
        val e = sp.edit()
        for (k in keys) {
            val o = sp.getString(pk("tracks_$k"), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            o.put(field, value)
            e.putString(pk("tracks_$k"), o.toString())
        }
        e.apply()
    }

    private fun putBool(k: String, v: Boolean) {
        sp.edit().putBoolean(k, v).apply()
        settingsVersion++
    }

    // ---------- Formato de emisión por canal (TS / HLS) ----------

    private var altFmt: MutableSet<String>? = null

    private fun altFormats(): MutableSet<String> =
        altFmt ?: (sp.getStringSet("altfmt", emptySet())?.toMutableSet() ?: mutableSetOf()).also { altFmt = it }

    /** Canal que se ve con el otro formato (TS ↔ HLS), elegido en las opciones del reproductor. */
    fun usesAltFormat(channelKey: String): Boolean = channelKey in altFormats()

    fun setAltFormat(channelKey: String, on: Boolean) {
        val s = altFormats()
        if ((channelKey in s) == on) return
        if (on) s.add(channelKey) else s.remove(channelKey)
        sp.edit().putStringSet("altfmt", HashSet(s)).apply()
    }

    /** Idioma de audio preferido (código ISO, p. ej. "es"). "orig" = sin preferencia. null = español. */
    var audioLang: String?
        get() = sp.getString(pk("audioLang"), null)
        set(v) = putStr(pk("audioLang"), v)

    /** Idioma de subtítulos preferido. "" = desactivados, null = nunca elegido. */
    var subLang: String?
        get() = sp.getString(pk("subLang"), null)
        set(v) = putStr(pk("subLang"), v)

    /** fit, zoom, fill, 16:9, 4:3 */
    var resizeMode: String
        get() = str("resize", "fit")
        set(v) = putStr("resize", v)

    /** low, normal, high */
    var bufferMode: String
        get() = str("buffer", "normal")
        set(v) = putStr("buffer", v)

    /** Segundos por pulsación al avanzar o retroceder. */
    var seekStep: Int
        get() = sp.getInt("seekStep", 10)
        set(v) = putInt("seekStep", v)

    var externalPlayer: Boolean
        get() = sp.getBoolean("external", false)
        set(v) = putBool("external", v)

    /** App del reproductor externo elegido (paquete). */
    var externalPkg: String?
        get() = sp.getString("external_pkg", null)
        set(v) = putStr("external_pkg", v)

    /** true = decodificar siempre el audio en la app (para canales que no se oyen). */
    var audioDecode: Boolean
        get() = sp.getBoolean("audioDecode", false)
        set(v) = putBool("audioDecode", v)

    /** Aumento de volumen en dB (0, 3, 6, 10). */
    var volumeBoost: Int
        get() = sp.getInt("boost", 0)
        set(v) = putInt("boost", v)

    /** s, m, l, xl */
    var subSize: String
        get() = str("subSize", "m")
        set(v) = putStr("subSize", v)

    /** white, yellow */
    var subColor: String
        get() = str("subColor", "white")
        set(v) = putStr("subColor", v)

    /** none (contorno negro), band (franja oscura) */
    var subBg: String
        get() = str("subBg", "none")
        set(v) = putStr("subBg", v)

    /** Logos de canales en alta calidad (bancos abiertos) en vez de los de la lista. */
    var hqLogos: Boolean
        get() = sp.getBoolean("hqLogos", true)
        set(v) = putBool("hqLogos", v)

    /** Ajustar la frecuencia de la pantalla al vídeo (teles que lo admitan). */
    var afr: Boolean
        get() = sp.getBoolean("afr", false)
        set(v) = putBool("afr", v)

    /** Últimos servidores usados (el más reciente primero), para volver a uno sin escribirlo. */
    fun serverHistory(): List<String> = sp.getString("serverHistory", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun rememberServer(server: String) {
        val l = (listOf(server) + serverHistory().filter { it != server }).take(6)
        sp.edit().putString("serverHistory", l.joinToString("\n")).apply()
    }

    /** Al terminar un episodio, empieza solo el siguiente (también el primero de la temporada siguiente). */
    var autoNext: Boolean
        get() = sp.getBoolean("autoNext", true)
        set(v) = putBool("autoNext", v)


    // ---------- VPN ----------

    /** off, warp, wg */
    /** Conectar la VPN sola al abrir la app (si no, se conecta a mano desde Ajustes o el reproductor). */
    var vpnAuto: Boolean
        get() = sp.getBoolean("vpn_auto", false)
        set(v) = putBool("vpn_auto", v)

    /** Si un canal se corta varias veces seguidas, conectar la VPN sola y volver a intentarlo. */
    var vpnFallback: Boolean
        get() = sp.getBoolean("vpn_fallback", true)
        set(v) = putBool("vpn_fallback", v)

    // ---------------- Guía de programación ----------------

    var epgEnabled: Boolean
        get() = sp.getBoolean("epg_on", true)
        set(v) = putBool("epg_on", v)

    /** "provider" (la de tu lista), "external" (la de la dirección elegida) o "both" (la de la lista y, si falta, la externa). */
    var epgMode: String
        get() = str("epg_mode", "provider")
        set(v) = putStr("epg_mode", v)

    var epgUrl: String
        get() = str("epg_url", "")
        set(v) = putStr("epg_url", v)

    /** Ajuste de hora de la guía, en minutos. */
    var epgOffset: Int
        get() = sp.getInt("epg_offset", 0)
        set(v) = putInt("epg_offset", v)

    var vpnMode: String
        get() = str("vpnMode", "off")
        set(v) = putStr("vpnMode", v)

    var warpConf: String?
        get() = sp.getString("warpConf", null)
        set(v) = putStr("warpConf", v)

    var wgConf: String?
        get() = Secure.open(sp.getString("wgConf", null))
        set(v) = putStr("wgConf", Secure.seal(v))

    var wgName: String?
        get() = sp.getString("wgName", null)
        set(v) = putStr("wgName", v)

    // ---------- Categorías ocultas y PIN ----------

    /** Cambia cuando se ocultan o muestran categorías. */
    var hiddenVersion by mutableIntStateOf(0)
        private set

    private var hidden: MutableSet<String>? = null

    private fun hiddenSet(): MutableSet<String> {
        hidden?.let { return it }
        val s = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        hidden = s
        return s
    }

    fun catKey(kind: Kind, catId: String) = "${kind.name}|$catId"

    fun isHidden(kind: Kind, catId: String): Boolean = catKey(kind, catId) in hiddenSet()

    fun setHidden(keys: Collection<String>, hide: Boolean) {
        val s = hiddenSet()
        if (hide) s.addAll(keys) else s.removeAll(keys.toSet())
        sp.edit().putStringSet("hidden", HashSet(s)).apply()
        hiddenVersion++
    }

    /** Categorías ya vistas alguna vez (para aplicar la detección automática solo a las nuevas). */
    fun unseen(keys: Collection<String>): List<String> {
        val seen = sp.getStringSet("seenCats", emptySet()) ?: emptySet()
        return keys.filter { it !in seen }
    }

    fun markSeen(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val seen = HashSet(sp.getStringSet("seenCats", emptySet()) ?: emptySet())
        seen.addAll(keys)
        sp.edit().putStringSet("seenCats", seen).apply()
    }

    /** PIN para ver o cambiar las categorías ocultas (se guarda solo su huella). */
    val hasPin: Boolean get() = str("pin", "").isNotEmpty()

    fun pinMatches(p: String): Boolean = Secure.pinMatches(p, str("pin", ""))

    /** "" quita el PIN. */
    fun setPin(p: String) = putStr("pin", if (p.isEmpty()) "" else Secure.pinHash(p))

    // ---------- Recordatorios de programas ----------

    data class Reminder(val channel: Item, val title: String, val start: Long) {
        val id: String get() = "${channel.key}@$start"
    }

    fun reminders(): List<Reminder> {
        val l = mutableListOf<Reminder>()
        runCatching {
            val a = JSONArray(sp.getString(pk("reminders"), "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val ch = runCatching { Item.fromJson(o.getJSONObject("ch")) }.getOrNull() ?: continue
                l.add(Reminder(ch, o.optString("t"), o.optLong("s")))
            }
        }
        return l
    }

    private fun saveReminders(l: List<Reminder>) {
        val a = JSONArray()
        // Solo los que no han pasado hace más de una hora.
        val min = System.currentTimeMillis() - 3600_000L
        l.filter { it.start > min }.forEach { r ->
            a.put(org.json.JSONObject().put("ch", r.channel.toJson()).put("t", r.title).put("s", r.start))
        }
        sp.edit().putString(pk("reminders"), a.toString()).apply()
        bump()
    }

    fun hasReminder(id: String): Boolean = reminders().any { it.id == id }

    /** Pone o quita el recordatorio. Devuelve true si ha quedado puesto. */
    fun toggleReminder(r: Reminder): Boolean {
        val l = reminders().toMutableList()
        val i = l.indexOfFirst { it.id == r.id }
        val on = if (i >= 0) {
            l.removeAt(i)
            false
        } else {
            l.add(r)
            true
        }
        saveReminders(l)
        return on
    }

    fun removeReminder(id: String) = saveReminders(reminders().filter { it.id != id })

    // ---------- Favoritos ----------

    private var favs: MutableList<Item>? = null

    fun favorites(): List<Item> {
        favs?.let { return it }
        val l = mutableListOf<Item>()
        runCatching {
            val a = JSONArray(sp.getString(pk("favs"), "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                runCatching { Item.fromJson(o) }.getOrNull()?.let { l.add(it) }
            }
        }
        favs = l
        return l
    }

    fun isFavorite(key: String): Boolean = favorites().any { it.key == key }

    /** Devuelve true si se ha añadido y false si se ha quitado. */
    fun toggleFavorite(item: Item): Boolean {
        val l = favorites().toMutableList()
        val i = l.indexOfFirst { it.key == item.key }
        val added = if (i >= 0) {
            l.removeAt(i)
            false
        } else {
            l.add(0, item)
            true
        }
        favs = l
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        sp.edit().putString(pk("favs"), a.toString()).apply()
        bump()
        return added
    }

    // ---------- Continuar viendo ----------

    private var prog: MutableList<Progress>? = null

    fun progressList(): List<Progress> {
        prog?.let { return it }
        val l = mutableListOf<Progress>()
        runCatching {
            val a = JSONArray(sp.getString(pk("prog"), "[]"))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val p = o.optJSONObject("p") ?: continue
                runCatching {
                    l.add(Progress(PlayItem.fromJson(p), o.optLong("pos"), o.optLong("dur"), o.optLong("ts")))
                }
            }
        }
        prog = l
        return l
    }

    fun progress(key: String): Progress? = progressList().firstOrNull { it.play.key == key }

    // ---------- Vistos ----------

    private var watchedSet: MutableSet<String>? = null

    private fun watched(): MutableSet<String> =
        watchedSet ?: (sp.getStringSet(pk("watched"), emptySet())?.toMutableSet() ?: mutableSetOf()).also { watchedSet = it }

    fun isWatched(key: String): Boolean = key in watched()

    fun setWatched(key: String, on: Boolean) {
        val w = watched()
        if ((key in w) == on) return
        if (on) w.add(key) else w.remove(key)
        sp.edit().putStringSet(pk("watched"), HashSet(w)).apply()
        bump()
    }

    fun saveProgress(play: PlayItem, pos: Long, dur: Long) {
        if (play.live || dur <= 0) return
        // Casi terminado (mismo umbral para las dos cosas): queda como visto y sale de «Continuar viendo».
        val done = pos >= dur * FINISHED
        if (done) setWatched(play.key, true)
        val keep = pos > 30_000 && !done
        // Series: una sola entrada por serie, la del episodio que estás viendo.
        val series = seriesPrefix(play.key)
        val l = progressList().filterNot {
            it.play.key == play.key || ((keep || done) && series != null && it.play.key.startsWith(series))
        }.toMutableList()
        if (keep) l.add(0, Progress(play, pos, dur, System.currentTimeMillis()))
        while (l.size > 40) l.removeAt(l.lastIndex)
        storeProgress(l)
    }

    /** Episodio siguiente a uno terminado: entra en «Continuar viendo», desde el principio (posición 0). */
    fun queueNext(play: PlayItem) {
        val series = seriesPrefix(play.key)
        val l = progressList().filterNot { it.play.key == play.key || (series != null && it.play.key.startsWith(series)) }.toMutableList()
        l.add(0, Progress(play, 0L, 0L, System.currentTimeMillis()))
        while (l.size > 40) l.removeAt(l.lastIndex)
        storeProgress(l)
    }

    /** «EP|serie|episodio» → «EP|serie|» (el id de la serie puede llevar «|» en las listas M3U). */
    private fun seriesPrefix(key: String): String? =
        if (key.startsWith("EP|")) key.substringBeforeLast('|') + "|" else null

    fun clearProgress() = storeProgress(mutableListOf())

    /** Quita una película o episodio de «Continuar viendo». */
    fun removeProgress(key: String) = storeProgress(progressList().filterNot { it.play.key == key }.toMutableList())

    private fun storeProgress(l: MutableList<Progress>) {
        prog = l
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("p", it.play.toJson()).put("pos", it.pos).put("dur", it.dur).put("ts", it.ts))
        }
        sp.edit().putString(pk("prog"), a.toString()).apply()
        bump()
    }
}

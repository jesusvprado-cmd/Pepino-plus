package com.shieldstream.tv.domain

/**
 * Copia de la configuración de Pepino+ para pasarla a otra tele: perfiles, preferencias, addons, claves de los
 * servicios, Mi lista y progreso. No incluye las sesiones de Trakt (cada tele se vincula aparte).
 */
interface ConfigBackupRepository {
    /** JSON con toda la configuración copiable. */
    fun export(): String

    /**
     * Sustituye la configuración de esta tele por la de [raw]. Devuelve cuántos apartados se restauraron.
     * Lanza IllegalArgumentException si el archivo no es una copia válida de Pepino+.
     * Después hay que reiniciar la app, porque lo cargado en memoria queda desfasado.
     */
    fun restore(raw: String): Int
}

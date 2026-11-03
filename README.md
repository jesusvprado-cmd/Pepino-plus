# Pepino+

Pepino IPTV con una sección nueva: **AllDebrid**. Se instala aparte (no sustituye a Pepino IPTV).

## AllDebrid

- Menú de arriba › **AllDebrid**: películas y series de tu cuenta (torrents listos y enlaces guardados), con carátulas.
- **Vincular la cuenta:** Ajustes › AllDebrid › *Vincular con un código* (escribes el código en alldebrid.com/pin) o *Escribir la clave API*.
- **Filtros:** calidad (4K, HDR/Dolby Vision, 1080p, 720p), audio (castellano, latino, inglés, VO), subtítulos en español, y orden.
- **Etiquetas** en cada carátula (4K · DV · ES/EN). Si hay varias copias de la misma película, sale una sola carátula; en la ficha se elige la versión (la recomendada lleva ★).
- **Idiomas reales:** la app lee el principio de cada MKV para saber qué audios y subtítulos lleva de verdad (se puede apagar en Ajustes).
- **Fichas:** sin clave salen de Cinemeta (en inglés). Con una clave gratuita de TMDB (Ajustes › AllDebrid) salen en español.
- Series: temporadas, capítulos, «Continuar» y paso automático al siguiente capítulo. Mantén OK en un capítulo para elegir otra versión.
- Lo de AllDebrid también sale en **Buscar** y en **Continuar viendo**.

---

# Mi IPTV (base)

Reproductor IPTV propio para Android TV (Mi Box, Smart TV con Android), tablets y móviles.
No trae canales: se usa con tu propia lista (Xtream Codes o M3U).

## Funciones

- Menú superior: Inicio, TV en directo, Películas, Series, Buscar y Ajustes.
- Categorías a la izquierda y cuadrícula de canales o carátulas a la derecha.
- Favoritos (botón ♥ en las fichas o mantener pulsado OK) y "Últimos añadidos".
- Ficha de película: sinopsis, año, duración, géneros, Reproducir / Continuar, tráiler y títulos relacionados.
- Ficha de serie: temporadas desplegables, episodios, "Continuar SxEy" y paso automático al siguiente episodio.
- Guía EPG: ahora y después en la lista de canales y en el reproductor.
- Reproductor: ▲▼ cambia de canal, ◀ abre la lista de canales, OK marca favorito;
  en películas ◀▶ avanza o retrocede (más rápido si se mantiene pulsado) y OK pausa.
- "Continuar viendo" en Inicio.
- Ajustes: categorías ocultas (las de adultos se ocultan solas) con PIN opcional.
- VPN integrada solo para la app: WARP de Cloudflare o tu propio perfil WireGuard (.conf).
- Vídeo: formato de imagen, búfer, salto, frecuencia de pantalla automática, reproductor externo.
- Audio: decodificación propia (AC3/E-AC3/DTS), aumento de volumen e idioma preferido.
- Subtítulos: idioma, tamaño, color y fondo.

## Cómo obtener el APK (sin instalar nada en el ordenador)

1. Sube esta carpeta a un repositorio de GitHub (rama `main`).
2. GitHub compila solo el APK (pestaña **Actions**, tarda unos 5-8 minutos).
3. El APK aparece en **Releases** como `PepinoPlus.apk`.
   Si el repositorio es público, la Mi Box puede descargarlo con la app Downloader desde:
   `https://github.com/TU_USUARIO/TU_REPOSITORIO/releases/latest/download/PepinoPlus.apk`

Cada versión nueva se instala encima de la anterior sin perder los datos: va firmada siempre con la misma llave, `app/pepino.p12`, que está cifrada. Su contraseña no está en el código: la pone GitHub desde el secreto `SIGNING_PASSWORD` (Settings → Secrets and variables → Actions). Sin ese secreto la compilación se para.

## Compilar en el ordenador (opcional)

Abrir la carpeta con Android Studio y ejecutar `./gradlew assembleRelease`.

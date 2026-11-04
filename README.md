# Pepino+

Copia de Pepino IPTV que se instala aparte (otro nombre e icono). Solo IPTV.

## Base: Mi IPTV

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

# Pepino+

Cliente de streaming para Android TV (Mi Box, Smart TV con Android): catálogo de TMDB, sincronización con Trakt,
fuentes mediante addons tipo Stremio y servicios debrid (Real-Debrid, AllDebrid, TorBox…), subtítulos externos y
varios perfiles.

Basado en [Bambi](https://github.com/grodri99/bambi) 1.0.8 de grodri99, con licencia GPL-3.0 (ver `LICENSE`).
Esta versión cambia el nombre, el icono y el sistema de actualizaciones, y quita la intro animada.

## Cómo obtener el APK

Cada cambio en la rama `main` lo compila GitHub (pestaña **Actions**) y lo publica en **Releases** como
`PepinoPlus.apk`. La app comprueba sola si hay versión nueva y se actualiza encima, sin perder datos.

Va firmado siempre con la misma llave, `app/pepino.p12` (cifrada). Su contraseña la pone GitHub desde el secreto
`SIGNING_PASSWORD` (Settings → Secrets and variables → Actions). Sin ese secreto la compilación se para.

## Compilar en el ordenador (opcional)

Abrir la carpeta con Android Studio y ejecutar:

```bash
./gradlew :app:assembleRelease -PsigningPassword=LA_CONTRASEÑA
```

Sin contraseña se puede compilar la versión de pruebas (`./gradlew assembleDebug`), que se instala aparte como
«Pepino+ (pruebas)».

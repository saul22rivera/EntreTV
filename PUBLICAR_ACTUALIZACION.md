# Publicar una versión nueva de EntreTV

La app busca actualizaciones en
`https://github.com/saul22rivera/EntreTV/releases/latest/download/update.json`
(cada 6 horas al abrirse, o al tocar **Configuración → Buscar actualizaciones**).
Si `versionCode` en ese archivo es mayor que el instalado, ofrece descargar e instalar el APK.

## Requisitos

- El repositorio `saul22rivera/EntreTV` debe ser **público**; si es privado, la app no puede descargar.
- Firma siempre con `entretv-release.jks` y `keystore.properties`. Guarda una copia de ambos fuera de
  la PC: si se pierden, Android rechazará las versiones nuevas sobre las ya instaladas.

## Pasos

1. En `app/build.gradle.kts` sube `versionCode` (por ejemplo 2 → 3) y `versionName` (1.1.0 → 1.2.0).
2. En `gradle.properties` cambia `entretv.releaseNotes` (acentos como `\u00f3`, `\u00e1`, etc.).
3. Compila:
   ```powershell
   $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleRelease
   ```
4. En `app\build\outputs\apk\release\` quedan los dos archivos que se suben:
   - `EntreTV-<versión>.apk` (universal: celular, tablet y TV)
   - `update.json`
5. En GitHub: **Releases → Draft a new release**, etiqueta `v<versión>` (por ejemplo `v1.2.0`),
   adjunta los dos archivos y publica. Debe quedar marcado como **Latest**.

`update.json` se genera solo; tiene este formato:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/saul22rivera/EntreTV/releases/latest/download/EntreTV-1.2.0.apk",
  "notes": "Novedades de esta versión."
}
```

## Instalar por primera vez (Downloader en TV)

En Downloader escribe la dirección del APK de la última versión, por ejemplo:
`https://github.com/saul22rivera/EntreTV/releases/latest/download/EntreTV-1.1.0.apk`
(o crea un código corto en aftvnews.com/dl que apunte a esa dirección).

La versión de prueba instalada antes ("Entre TV", paquete `com.entretv.app.debug`) es otra app:
desinstálala a mano después de instalar la 1.1.0. Las copias de depuración no buscan actualizaciones.

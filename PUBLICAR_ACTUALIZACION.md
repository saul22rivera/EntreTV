# Publicar una versión nueva de EntreTV

La app busca actualizaciones en
`https://github.com/saul22rivera/EntreTV/releases/latest/download/update.json`
**cada vez que se abre** (en silencio, sin mostrar nada si no hay versión nueva) y al tocar
**Configuración → Buscar actualizaciones**.

- Si `versionCode` en ese archivo es mayor que el instalado, ofrece descargar e instalar el APK.
- Si además la actualización es **obligatoria** (ver abajo), muestra la pantalla
  **"Actualización necesaria"**: no se puede cerrar, descarga el APK sola y abre el instalador.
  En TV explica cómo permitir "Instalar apps desconocidas" si el equipo lo pide.

## Requisitos

- El repositorio `saul22rivera/EntreTV` debe ser **público**; si es privado, la app no puede descargar.
- Firma siempre con `entretv-release.jks` y `keystore.properties`. Guarda una copia de ambos fuera de
  la PC: si se pierden, Android rechazará las versiones nuevas sobre las ya instaladas.
  Nunca los subas a GitHub.

## Pasos

1. En `app/build.gradle.kts` sube `versionCode` (por ejemplo 3 → 4) y `versionName` (1.2.0 → 1.3.0).
2. En `gradle.properties`:
   - `entretv.releaseNotes`: novedades (acentos como `\u00f3`, `\u00e1`, etc.).
   - `entretv.minVersionCode`: la versión mínima que puede seguir usándose (ver abajo).
   - `entretv.serverUrl`: servidor de los usuarios (por ejemplo `http://livedttv.com:8080/`).
   - `entretv.tmdbApiKey`: opcional, clave de TMDB (ver abajo).
3. Compila:
   ```powershell
   $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleRelease
   ```
4. En `app\build\outputs\apk\release\` quedan los dos archivos que se suben:
   - `EntreTV-<versión>.apk` (universal: celular, tablet y TV)
   - `update.json`
5. En GitHub: **Releases → Draft a new release**, etiqueta `v<versión>` (por ejemplo `v1.2.0`),
   adjunta los dos archivos y publica. Debe quedar marcado como **Latest**.

## Formato de `update.json`

Se genera solo al compilar el release:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/saul22rivera/EntreTV/releases/latest/download/EntreTV-1.2.0.apk",
  "notes": "Novedades de esta versión.",
  "minVersionCode": 3,
  "mandatory": true,
  "serverUrl": "http://livedttv.com:8080/"
}
```

| Campo | Qué hace |
|---|---|
| `versionCode`, `versionName`, `apkUrl`, `notes` | Igual que antes. Las versiones viejas solo leen estos. |
| `minVersionCode` | Quien tenga un `versionCode` menor queda obligado a actualizar. |
| `mandatory` | `true` obliga a todos los que tengan una versión anterior. El script lo pone en `true` cuando `minVersionCode` es igual o mayor que `versionCode`. |
| `serverUrl` | Opcional. Si cambia, la app reescribe sola las cuentas que usan el servidor anterior (el usuario no tiene que volver a entrar). Nunca se muestra en pantalla. |
| `tmdbApiKey` | Opcional. Activa el Top 10 de tendencias y la ficha completa de TMDB. |

La app considera la actualización obligatoria si hay versión nueva **y**
(`mandatory` es `true` **o** `minVersionCode` es mayor que la versión instalada).

### ¿Por qué la 1.2.0 sale con `minVersionCode=3`?

La 1.2.0 cambia el inicio de sesión (servidor fijo, usuario y contraseña) y el formato de la base
de datos. Las versiones 1.0 y 1.1 no conocen el servidor fijo ni la pantalla de actualización
obligatoria, así que conviene que todos pasen a la 1.2.0. Las 1.0/1.1 instaladas ven la
actualización como normal (no leen los campos nuevos); a partir de la 1.2.0 el bloqueo funciona.

Para una versión futura que **no** obligue a actualizar, deja `entretv.minVersionCode` en un número
menor que el nuevo `versionCode` (por ejemplo, publicar la 4 con `minVersionCode=3`).

### Clave de TMDB (opcional)

Sin clave, la app funciona igual: el Top 10 se arma con lo mejor calificado y lo más reciente del
catálogo, y la ficha muestra lo que manda el servidor. Para activar TMDB:

1. Crea una cuenta en themoviedb.org → Configuración → API y copia la **API Key (v3)**.
2. Ponla en `entretv.tmdbApiKey` de `gradle.properties` y vuelve a compilar el release.
   Queda dentro del APK y también en `update.json`, así que las versiones ya instaladas la reciben
   sin reinstalar. `update.json` es público: usa una clave solo de lectura, nunca otra contraseña.

## Instalar por primera vez (Downloader en TV)

En Downloader escribe la dirección del APK de la última versión, por ejemplo:
`https://github.com/saul22rivera/EntreTV/releases/latest/download/EntreTV-1.2.0.apk`
(o crea un código corto en aftvnews.com/dl que apunte a esa dirección).

La versión de prueba ("Entre TV", paquete `com.entretv.app.debug`) es otra app: desinstálala a mano
después de instalar la versión normal. Las copias de depuración no buscan actualizaciones.

## Cerrar sesión y descargas

"Configuración → Cuenta → Cerrar sesión" borra de ese equipo el catálogo, el progreso,
"Mi lista" y las categorías ocultas de esa cuenta. **Las descargas se conservan** y se pueden
borrar desde Descargas (botón "Borrar todo").

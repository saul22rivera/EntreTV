# EntreTV

Reproductor IPTV/VOD estilo **Netflix**: ligero, fluido y con memoria persistente.
Código 100% original en Kotlin + Jetpack Compose + Media3.

## Experiencia Netflix

- **Seguir viendo** (siempre al frente): fila con póster, `T2:E5`, barra de progreso y
  botón ▶ que continúa **exactamente en el minuto** donde lo dejaste.
- **Reanudación automática**: al reproducir una película o episodio vuelve al segundo
  exacto (aviso "Reanudado en 12:34").
- **Series con memoria**: EntreTV guarda el capítulo que vas viendo; al entrar a la serie
  ves el último episodio y el historial por episodio.
- **Auto-next**: al terminar un episodio pasa solo al siguiente ("A continuación T2:E6").
- **Reiniciar serie**: en la ficha de cada serie, "volver al episodio 1" borra solo esa serie.
- **Borrar caché / progreso** desde el menú superior: caché de imágenes, una serie
  completa o todo el progreso.
- **Actualización al entrar**: cada vez que abres la app se sincronizan todas las listas
  en segundo plano (live + VOD + series en paralelo); el contenido cacheado se ve al
  instante y basta deslizar hacia abajo (pull-to-refresh) para forzarla.

## Reproducción anti-trabas (Media3 1.5)

- Buffer 30–60 s, prioridad tiempo > tamaño, `enableDecoderFallback`, ABR con MIME mixto,
  caché LRU 256 MB, reconexión automática.
- Seekbar con guardado cada 5 s + guardado final al salir (nunca pierdes el minuto).
- PiP automático, reproducción en segundo plano con `MediaSessionService`, audio focus.
- Zapping en vivo y entre episodios; ±10 s / +30 s en VOD.

## Fuentes soportadas

- **M3U/M3U8** (parser tolerante de una pasada).
- **Xtream Codes**: login, live + categorías, VOD, series, episodios y EPG corta.
- Base de datos **Room v2** con migración automática desde v1 (sin perder datos).

## Construir

1. Abrir en Android Studio (JDK 17) → sincronizar Gradle.
2. `./gradlew :app:assembleRelease` → APKs por arquitectura
   (`app/build/outputs/apk/release/`), optimizados con R8 full mode.

## Arquitectura

```
com.entretv.app
├── MainActivity                 # Nav + PiP
├── di/AppModule.kt              # Room (migración 1→2), OkHttp caché, Retrofit
├── data
│   ├── local                    # Room v2: playlists, channels, vod, series,
│   │                            #   episodes, epg, playback_progress
│   ├── remote                   # XtreamApi (live/vod/series/EPG) + M3uParser
│   └── repo/PlaylistRepository  # sync paralelo, progreso, seguir viendo, reset
├── player
│   ├── PlayerManager            # ExoPlayer único + cachés + onEnded
│   └── PlaybackService          # MediaSessionService
└── ui
    ├── HomeViewModel            # filas Netflix, refresh automático al entrar
    ├── HomeScreen               # Seguir viendo, filas, hoja de serie, menú caché
    ├── PlayerViewModel          # resume/zap/auto-next/guardar minuto
    └── PlayerScreen             # seekbar VOD, controles, EPG, avisos
```

## Roadmap 2027

- EPG completo con timeline, grabación/timeshift, Chromecast, multi-perfil,
  descargas offline y recomendaciones.

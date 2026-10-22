package com.entretv.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Paquete de la variante "benchmark" de :app (el mismo que la app de pruebas). */
const val TARGET_PACKAGE = "com.entretv.app.debug"

private const val MAIN_ACTIVITY = "com.entretv.app.MainActivity"

/** Tiempo para que la pantalla responda a cada tecla en un equipo lento. */
private const val KEY_SETTLE_MS = 700L

/**
 * Abre la app y espera su ventana. En el emulador de TV (Android 16) startActivityAndWait()
 * falla porque `gfxinfo framestats` llega vacío aunque la actividad ya se mostró.
 */
fun MacrobenchmarkScope.launchApp() {
    device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$MAIN_ACTIVITY")
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 10_000)
    device.waitForIdle(1_000)
}

private fun MacrobenchmarkScope.key(code: Int, times: Int = 1) {
    repeat(times) {
        device.pressKeyCode(code)
        device.waitForIdle(KEY_SETTLE_MS)
    }
}

/**
 * Recorrido con el control de TV: menú, Películas y Series, filas y categorías, una ficha.
 * Solo usa flechas, OK y Atrás: así recorre las mismas pantallas que un usuario en la tele.
 * Sin sesión iniciada se queda en la pantalla de acceso (solo se mide el arranque).
 */
fun MacrobenchmarkScope.browseCatalog() {
    device.waitForIdle(2_000)
    // Menú lateral → Películas.
    key(KeyEvent.KEYCODE_DPAD_LEFT, 3)
    key(KeyEvent.KEYCODE_DPAD_DOWN)
    key(KeyEvent.KEYCODE_DPAD_CENTER)
    device.waitForIdle(2_000)
    // Filas del inicio de la sección.
    key(KeyEvent.KEYCODE_DPAD_RIGHT)
    key(KeyEvent.KEYCODE_DPAD_DOWN, 4)
    key(KeyEvent.KEYCODE_DPAD_RIGHT, 4)
    // Ficha y de regreso.
    key(KeyEvent.KEYCODE_DPAD_CENTER)
    device.waitForIdle(3_000)
    key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
    key(KeyEvent.KEYCODE_DPAD_UP, 3)
    key(KeyEvent.KEYCODE_BACK)
    // Columna de categorías y cuadrícula de una categoría.
    key(KeyEvent.KEYCODE_DPAD_LEFT, 5)
    key(KeyEvent.KEYCODE_DPAD_DOWN, 4)
    device.waitForIdle(2_000)
    key(KeyEvent.KEYCODE_DPAD_RIGHT)
    key(KeyEvent.KEYCODE_DPAD_DOWN, 6)
    key(KeyEvent.KEYCODE_DPAD_UP, 6)
}

/** Desplazamiento por las filas y la cuadrícula: lo que se mide como fluidez. */
fun MacrobenchmarkScope.scrollCatalog() {
    key(KeyEvent.KEYCODE_DPAD_RIGHT)
    key(KeyEvent.KEYCODE_DPAD_DOWN, 8)
    key(KeyEvent.KEYCODE_DPAD_RIGHT, 6)
    key(KeyEvent.KEYCODE_DPAD_UP, 8)
}

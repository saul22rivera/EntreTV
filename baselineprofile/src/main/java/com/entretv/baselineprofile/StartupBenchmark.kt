package com.entretv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arranque en frío y fluidez al recorrer el catálogo, sin perfil y con perfil.
 * La diferencia entre "None" y "Profile" es lo que gana el usuario con el perfil de arranque.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNone() = startup(CompilationMode.None())

    @Test
    fun startupProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollNone() = scroll(CompilationMode.None())

    @Test
    fun scrollProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5
    ) {
        pressHome()
        launchApp()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            pressHome()
            launchApp()
            device.waitForIdle(2_000)
        }
    ) {
        scrollCatalog()
    }
}

package io.github.kdroidfilter.seforimapp.earthwidget

import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertNotNull

class EarthShaderPipelineTest {
    @Test
    fun shaderCompiles() {
        val pipeline =
            try {
                EarthShaderPipeline.create()
            } catch (error: UnsatisfiedLinkError) {
                Assume.assumeNoException(
                    "Skipping Skia shader compilation test because native graphics libraries are unavailable",
                    error,
                )
                return
            }

        assertNotNull(pipeline, "SkSL sphere shader failed to compile — check EarthShaders.kt syntax")
    }
}

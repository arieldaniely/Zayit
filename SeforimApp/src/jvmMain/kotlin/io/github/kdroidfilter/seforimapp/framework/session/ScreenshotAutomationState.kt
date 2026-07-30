package io.github.kdroidfilter.seforimapp.framework.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

object ScreenshotAutomationState {
    const val CLIPBOARD_DEMO_TEXT = "מֵאֵימָתַי קוֹרִין אֶת שְׁמַע בָּעֲרָבִין? מִשָּׁעָה שֶׁהַכֹּהֲנִים נִכְנָסִים לֶאֱכוֹל בִּתְרוּמָתָן. עַד סוֹף הָאַשְׁמוּרָה הָרִאשׁוֹנָה. דִּבְרֵי רַבִּי אֱלִיעֶזֶר."

    private val _clipboardDemoGeneration = MutableStateFlow(0)
    val clipboardDemoGeneration: StateFlow<Int> = _clipboardDemoGeneration
    private val _replayGeneration = MutableStateFlow(0)
    val replayGeneration: StateFlow<Int> = _replayGeneration

    data class HomeSearchReplay(
        val generation: Int = 0,
        val referenceQuery: String = "",
        val tocQuery: String = "",
    )

    private val _homeSearchReplay = MutableStateFlow(HomeSearchReplay())
    val homeSearchReplay: StateFlow<HomeSearchReplay> = _homeSearchReplay

    @Volatile
    private var renderedDensity: Float? = null

    fun requestClipboardDemo() {
        _clipboardDemoGeneration.value += 1
    }

    fun beginReplay() {
        _replayGeneration.value += 1
        _homeSearchReplay.value = HomeSearchReplay(generation = _replayGeneration.value)
    }

    fun showHomeSearch(
        referenceQuery: String = "",
        tocQuery: String = "",
    ) {
        _homeSearchReplay.value =
            HomeSearchReplay(
                generation = _homeSearchReplay.value.generation + 1,
                referenceQuery = referenceQuery,
                tocQuery = tocQuery,
            )
    }

    fun reportRenderedDensity(density: Float) {
        renderedDensity = density
    }

    suspend fun requireRenderedDensity(expected: Float) {
        repeat(100) {
            val actual = renderedDensity
            if (actual != null) {
                require(abs(actual - expected) < 0.01f) {
                    "Compose density is $actual; expected $expected"
                }
                return
            }
            delay(100)
        }
        error("Compose did not report its rendered density")
    }
}

package io.github.kdroidfilter.seforimapp.framework.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ScreenshotAutomationState {
    const val CLIPBOARD_DEMO_TEXT = "מֵאֵימָתַי קוֹרִין אֶת שְׁמַע בָּעֲרָבִין? מִשָּׁעָה שֶׁהַכֹּהֲנִים נִכְנָסִים לֶאֱכוֹל בִּתְרוּמָתָן. עַד סוֹף הָאַשְׁמוּרָה הָרִאשׁוֹנָה. דִּבְרֵי רַבִּי אֱלִיעֶזֶר."

    private val _clipboardDemoGeneration = MutableStateFlow(0)
    val clipboardDemoGeneration: StateFlow<Int> = _clipboardDemoGeneration

    fun requestClipboardDemo() {
        _clipboardDemoGeneration.value += 1
    }
}
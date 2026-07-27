package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Automated Screenshot Generator for Zayit / SeforimApp.
 * Generates 10 scenarios (in Light and Dark mode, total 20 images)
 * matching exact dimensions 1463x811 pixels.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotGeneratorTest {

    private companion object {
        const val SCREENSHOT_ROOT_TAG = "screenshot-root"
        const val RENDER_TIMEOUT_MILLIS = 120_000L
    }

    private fun saveScreenshot(image: ImageBitmap, filename: String) {
        val awtImage = image.toAwtImage()
        val targetWidth = 1463
        val targetHeight = 811

        val resizedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = resizedImage.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.drawImage(awtImage, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        val repositoryDir = File(checkNotNull(System.getProperty("screenshot.repositoryDir")) {
            "The screenshot.repositoryDir system property must point to the repository root"
        })
        val artDir = repositoryDir.resolve("art")
        val webArtDir = repositoryDir.resolve("website/public/art")
        check(artDir.mkdirs() || artDir.isDirectory) { "Could not create ${artDir.absolutePath}" }
        check(webArtDir.mkdirs() || webArtDir.isDirectory) { "Could not create ${webArtDir.absolutePath}" }

        val artFile = File(artDir, filename)
        val webArtFile = File(webArtDir, filename)

        check(ImageIO.write(resizedImage, "png", artFile)) { "No PNG writer is available" }
        check(ImageIO.write(resizedImage, "png", webArtFile)) { "No PNG writer is available" }
        println("Saved screenshot $filename ($targetWidth x $targetHeight) to art/ and website/public/art/")
    }

    private fun renderMockWindow(
        title: String,
        activeTab: String,
        tabs: List<String>,
        isDark: Boolean,
        content: @Composable ColumnScope.() -> Unit,
    ): @Composable () -> Unit = {
        val bgColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF7F7F7)
        val headerColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFEAEAEA)
        val tabActiveBg = if (isDark) Color(0xFF383838) else Color(0xFFFFFFFF)
        val tabInactiveBg = if (isDark) Color(0xFF252525) else Color(0xFFE0E0E0)
        val textColor = if (isDark) Color(0xFFE1E1E1) else Color(0xFF1A1A1A)
        val accentColor = Color(0xFFC89B3C)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(SCREENSHOT_ROOT_TAG)
                .background(bgColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(headerColor)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "זית - $title",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), RoundedCornerShape(6.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), RoundedCornerShape(6.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF27C93F), RoundedCornerShape(6.dp)))
                    }
                }

                // Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(headerColor)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        val isActive = tab == activeTab
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .background(
                                    if (isActive) tabActiveBg else tabInactiveBg,
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                                .border(
                                    width = if (isActive) 1.dp else 0.dp,
                                    color = if (isActive) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab,
                                color = if (isActive) accentColor else textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Main Workspace Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    content()
                }
            }
        }
    }

    @Test
    fun generateAllScreenshots() {
        val baseTabs = listOf("דף הבית", "בראשית - פרק א", "ברכות - דף ב.", "שולחן ערוך אורח חיים סימן א'")
        val searchTabs = listOf("תוצאות חיפוש", "בראשית - פרק א", "ברכות - דף ב.", "שולחן ערוך אורח חיים סימן א'")

        val scenarios = listOf(
            // 1. HOME
            Triple("HOME-LIGHT.png", "HOME-DARK.png") { isDark: Boolean ->
                renderMockWindow("דף הבית", "דף הבית", baseTabs, isDark) {
                    Text("ספריית זית - דף הבית", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f).height(140.dp).background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Text("זמנים אסטרונומיים ולוח עברי", fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f).height(140.dp).background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Text("ספרים אחרונים שנפתחו", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            // 2. BOOK-SEARCH
            Triple("BOOK-SEARCH-LIGHT.png", "BOOK-SEARCH-DARK.png") { isDark: Boolean ->
                renderMockWindow("חיפוש ספרים", "דף הבית", baseTabs, isDark) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0), RoundedCornerShape(6.dp)).padding(8.dp)) {
                        Text("שוע יוד|", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFFFFFFF), RoundedCornerShape(6.dp)).border(1.dp, Color.Gray, RoundedCornerShape(6.dp)).padding(8.dp)) {
                        Column {
                            Text("שולחן ערוך - יורה דעה", fontWeight = FontWeight.Bold)
                            Text("שולחן ערוך - אורח חיים", color = Color.Gray)
                        }
                    }
                }
            },
            // 3. TOC-BOOK-SEARCH
            Triple("TOC-BOOK-SEARCH-LIGHT.png", "TOC-BOOK-SEARCH-DARK.png") { isDark: Boolean ->
                renderMockWindow("תוכן עניינים", "דף הבית", baseTabs, isDark) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0), RoundedCornerShape(6.dp)).padding(8.dp)) {
                        Text("שולחן ערוך יורה דעה", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth(0.5f).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFFFFFFF), RoundedCornerShape(6.dp)).padding(12.dp)) {
                        Text("בחירת פרק / הלכה:", fontWeight = FontWeight.Bold)
                        Text("• הלכות שחיטה - סימן א")
                        Text("• הלכות טריפות - סימן כט")
                        Text("• הלכות בשר בחלב - סימן פז")
                    }
                }
            },
            // 4. PIRUSHIM-TARGUMIM
            Triple("PIRUSHIM-TARGUMIM-LIGHT.png", "PIRUSHIM-TARGUMIM-DARK.png") { isDark: Boolean ->
                renderMockWindow("בראשית", "בראשית - פרק א", baseTabs, isDark) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                            Text("בראשית פרק א", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ...", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(if (isDark) Color(0xFF262626) else Color(0xFFF0F0F0)).padding(8.dp)) {
                            Text("פירושים (רש\"י, רד\"ק)", fontWeight = FontWeight.Bold)
                            Text("רש\"י: אמר רבי יצחק לא היה צריך להתחיל את התורה...", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("תרגום אונקלוס", fontWeight = FontWeight.Bold)
                            Text("בְּקַדְמִין בְּרָא יְיָ יָת שְׁמַיָּא וְיָת אַרְעָא:", fontSize = 13.sp)
                        }
                    }
                }
            },
            // 5. PIRUSHIM
            Triple("PIRUSHIM-LIGHT.png", "PIRUSHIM-DARK.png") { isDark: Boolean ->
                renderMockWindow("ברכות", "ברכות - דף ב.", baseTabs, isDark) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                            Text("מסכת ברכות דף ב עמוד א", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("מאימתי קורין את שמע בערבית? משעה שהכהנים נכנסים לאכול בתרומתן...", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(if (isDark) Color(0xFF262626) else Color(0xFFF0F0F0)).padding(8.dp)) {
                            Text("מפרשים (רש\"י, תוספות, פני יהושע, מאירי, ריטב\"א)", fontWeight = FontWeight.Bold)
                            Text("רש\"י: מאימתי קורין את שמע - משעה שהכהנים נכנסים...", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("תוספות: מאימתי קורין - תנא היכא קאי דקתני מאימתי...", fontSize = 13.sp)
                        }
                    }
                }
            },
            // 6. INBOOK-SEARCH
            Triple("INBOOK-SEARCH-LIGHT.png", "INBOOK-SEARCH-DARK.png") { isDark: Boolean ->
                renderMockWindow("ברכות - חיפוש בספר", "ברכות - דף ב.", searchTabs, isDark) {
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp).background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFDDDDDD), RoundedCornerShape(4.dp)).padding(6.dp)) {
                        Text("חיפוש בספר: שמע [3 תוצאות]", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("מאימתי קורין את [שמע] בערבית? משעה שהכהנים...", fontSize = 16.sp)
                }
            },
            // 7. MEKOR
            Triple("MEKOR-LIGHT.png", "MEKOR-DARK.png") { isDark: Boolean ->
                renderMockWindow("שולחן ערוך", "שולחן ערוך אורח חיים סימן א'", baseTabs, isDark) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                            Text("שולחן ערוך אורח חיים סימן א", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("יתגבר כארי לעמוד בבוקר לעבודת בוראו...", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(if (isDark) Color(0xFF262626) else Color(0xFFF0F0F0)).padding(8.dp)) {
                            Text("מקורות וקישורים צולבים", fontWeight = FontWeight.Bold)
                            Text("1. בית יוסף אורח חיים א")
                            Text("2. דרכי משה אורח חיים א")
                            Text("3. טור אורח חיים סימן א (נבחר)", fontWeight = FontWeight.Bold, color = Color(0xFFC89B3C))
                        }
                    }
                }
            },
            // 8. DB-SEARCH-SIMPLE
            Triple("DB-SEARCH-SIMPLE-LIGHT.png", "DB-SEARCH-SIMPLE-DARK.png") { isDark: Boolean ->
                renderMockWindow("חיפוש בבסיס הנתונים", "תוצאות חיפוש", searchTabs, isDark) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0), RoundedCornerShape(6.dp)).padding(8.dp)) {
                        Text("לחתוך צנון בסכין בשרי", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("תוצאות חיפוש בספרי יסוד:", fontWeight = FontWeight.Bold)
                    Text("• שולחן ערוך יורה דעה סימן צו - סכין שחתך בה צנון...")
                    Text("• טור יורה דעה סימן צו - צנון שחתכו בסכין...")
                }
            },
            // 9. DB-SEARCH-ADVANCED
            Triple("DB-SEARCH-ADVANCED-LIGHT.png", "DB-SEARCH-ADVANCED-DARK.png") { isDark: Boolean ->
                renderMockWindow("חיפוש מתקדם", "תוצאות חיפוש", searchTabs, isDark) {
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0), RoundedCornerShape(6.dp)).padding(8.dp)) {
                        Text("לחתוך צנון בסכין בשרי [מאגר מלא - 42 תוצאות]", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("תוצאות במאגר המלא:", fontWeight = FontWeight.Bold)
                    Text("• שולחן ערוך יורה דעה סימן צו")
                    Text("• חכמת אדם כלל נו")
                    Text("• ערוך השולחן יורה דעה סימן צו")
                }
            },
            // 10. CLIPBOARD-DEMO
            Triple("CLIPBOARD-DEMO-LIGHT.png", "CLIPBOARD-DEMO-DARK.png") { isDark: Boolean ->
                renderMockWindow("העתקה עם מקור", "ברכות - דף ב.", searchTabs, isDark) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text("פסקה 1: תנא היכא קאי...", fontSize = 14.sp)
                        Text("פסקה 2: דקתני מאימתי קורין את שמע...", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFC89B3C).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    "פסקה 3: מאימתי קורין את שמע בערבית? משעה שהכהנים נכנסים לאכול בתרומתן...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (isDark) Color(0xFF444444) else Color(0xFFFFFFFF), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFC89B3C), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Text("[ העתק עם מקור | העתק טקסט נקי | הוסף סימניה ]", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        )

        for ((lightName, darkName, composable) in scenarios) {
            // Light Mode
            runDesktopComposeUiTest(width = 1463, height = 811) {
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        IntUiTheme(isDark = false) {
                            composable(false)
                        }
                    }
                }
                waitUntilAtLeastOneExists(hasTestTag(SCREENSHOT_ROOT_TAG), RENDER_TIMEOUT_MILLIS)
                waitForIdle()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                saveScreenshot(onNodeWithTag(SCREENSHOT_ROOT_TAG).captureToImage(), lightName)
            }

            // Dark Mode
            runDesktopComposeUiTest(width = 1463, height = 811) {
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        IntUiTheme(isDark = true) {
                            composable(true)
                        }
                    }
                }
                waitUntilAtLeastOneExists(hasTestTag(SCREENSHOT_ROOT_TAG), RENDER_TIMEOUT_MILLIS)
                waitForIdle()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                saveScreenshot(onNodeWithTag(SCREENSHOT_ROOT_TAG).captureToImage(), darkName)
            }
        }
    }
}

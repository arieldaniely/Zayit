package io.github.kdroidfilter.seforimapp.features.personallibrary

import org.jsoup.nodes.Entities
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension

private const val WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

internal fun readPersonalBook(file: Path): List<String> {
    if (!file.extension.equals("docx", true)) return Files.readAllLines(file, Charsets.UTF_8)
    return ZipFile(file.toFile()).use { zip ->
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }

        fun readXml(name: String) =
            zip.getInputStream(requireNotNull(zip.getEntry(name))).use {
                factory.newDocumentBuilder().parse(it)
            }
        val headingLevels = mutableMapOf<String, Int>()
        if (zip.getEntry("word/styles.xml") != null) {
            val styles = readXml("word/styles.xml").getElementsByTagNameNS(WORD_NAMESPACE, "style")
            repeat(styles.length) { index ->
                val style = styles.item(index) as Element
                val outline = style.wordElement("outlineLvl")?.wordValue()?.toIntOrNull()
                if (outline != null && outline in 0..5) headingLevels[style.getAttributeNS(WORD_NAMESPACE, "styleId")] = outline + 1
            }
        }
        val paragraphs = readXml("word/document.xml").getElementsByTagNameNS(WORD_NAMESPACE, "p")
        buildList {
            repeat(paragraphs.length) { index ->
                val paragraph = paragraphs.item(index) as Element
                val text =
                    buildString {
                        val elements = paragraph.getElementsByTagNameNS(WORD_NAMESPACE, "*")
                        repeat(elements.length) { child ->
                            val element = elements.item(child) as Element
                            when (element.localName) {
                                "t" -> append(Entities.escape(element.textContent))
                                "tab" -> append(" ")
                                "br", "cr" -> append("<br>")
                            }
                        }
                    }
                if (text.isNotBlank()) {
                    val properties = paragraph.wordElement("pPr")
                    val style = properties?.wordElement("pStyle")?.wordValue()
                    val outline = properties?.wordElement("outlineLvl")?.wordValue()?.toIntOrNull()
                    val level =
                        when {
                            outline != null -> (outline + 1).takeIf { it in 1..6 }
                            style in headingLevels -> headingLevels[style]
                            else ->
                                style?.let {
                                    Regex(
                                        "Heading([1-6])",
                                        RegexOption.IGNORE_CASE,
                                    ).matchEntire(it)?.groupValues?.get(1)?.toInt()
                                }
                        }
                    add(if (level != null) "<h$level>$text</h$level>" else text)
                }
            }
        }
    }
}

private fun Element.wordElement(name: String): Element? = getElementsByTagNameNS(WORD_NAMESPACE, name).item(0) as? Element

private fun Element.wordValue(): String = getAttributeNS(WORD_NAMESPACE, "val")

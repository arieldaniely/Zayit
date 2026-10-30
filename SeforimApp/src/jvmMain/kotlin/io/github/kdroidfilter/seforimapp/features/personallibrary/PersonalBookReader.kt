package io.github.kdroidfilter.seforimapp.features.personallibrary

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
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
    if (file.extension.equals("pdf", true)) {
        return Loader.loadPDF(file.toFile()).use { document ->
            buildList {
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                repeat(document.numberOfPages) { page ->
                    add("<h1>${page + 1}</h1>")
                    stripper.startPage = page + 1
                    stripper.endPage = page + 1
                    add(Entities.escape(stripper.getText(document)).replace("\n", "<br>"))
                }
            }
        }
    }
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
        val paragraphStyles = mutableMapOf<String, Element>()
        var defaultStyle: String? = null
        if (zip.getEntry("word/styles.xml") != null) {
            val styles = readXml("word/styles.xml").getElementsByTagNameNS(WORD_NAMESPACE, "style")
            repeat(styles.length) { index ->
                val style = styles.item(index) as Element
                if (style.getAttributeNS(WORD_NAMESPACE, "type").let { it.isEmpty() || it == "paragraph" }) {
                    val id = style.getAttributeNS(WORD_NAMESPACE, "styleId")
                    paragraphStyles[id] = style
                    if (style.getAttributeNS(WORD_NAMESPACE, "default") in setOf("1", "true", "on")) defaultStyle = id
                }
            }
        }

        fun headingLevel(styleId: String?): Int? {
            val visited = mutableSetOf<String>()
            var id = styleId
            while (id != null && visited.add(id)) {
                val style = paragraphStyles[id]
                val outline =
                    style
                        ?.wordElement("pPr")
                        ?.wordElement("outlineLvl")
                        ?.wordValue()
                        ?.toIntOrNull()
                // Level 9 explicitly marks body text and must stop inheritance.
                if (outline != null) return outline.takeIf { it in 0..8 }?.let { (it + 1).coerceAtMost(6) }
                val name = style?.wordElement("name")?.wordValue()
                val builtin = Regex("(?:heading|כותרת)\\s*([1-9])", RegexOption.IGNORE_CASE)
                val level = builtin.matchEntire(id) ?: name?.let(builtin::matchEntire)
                if (level != null) return level.groupValues[1].toInt().coerceAtMost(6)
                id = style?.wordElement("basedOn")?.wordValue()
            }
            return null
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
                            outline != null -> outline.takeIf { it in 0..8 }?.let { (it + 1).coerceAtMost(6) }
                            else -> headingLevel(style ?: defaultStyle)
                        }
                    add(if (level != null) "<h$level>$text</h$level>" else text)
                }
            }
        }
    }
}

private fun Element.wordElement(name: String): Element? =
    (0 until childNodes.length)
        .asSequence()
        .mapNotNull { childNodes.item(it) as? Element }
        .firstOrNull { it.namespaceURI == WORD_NAMESPACE && it.localName == name }

private fun Element.wordValue(): String = getAttributeNS(WORD_NAMESPACE, "val")

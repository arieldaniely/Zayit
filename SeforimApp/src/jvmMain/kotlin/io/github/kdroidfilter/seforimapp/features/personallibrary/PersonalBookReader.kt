package io.github.kdroidfilter.seforimapp.features.personallibrary

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.jsoup.nodes.Entities
import org.w3c.dom.Element
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension

private const val WORD_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val RELATIONSHIP_NAMESPACE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

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
        val renderer = DocxInlineRenderer(zip, ::readXml)
        val paragraphs = readXml("word/document.xml").getElementsByTagNameNS(WORD_NAMESPACE, "p")
        buildList {
            repeat(paragraphs.length) { index ->
                val paragraph = paragraphs.item(index) as Element
                val text = renderer.render(paragraph, "document")
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

/** Keeps links and notes in the HTML dialect already understood by the book reader. */
private class DocxInlineRenderer(
    private val zip: ZipFile,
    private val readXml: (String) -> org.w3c.dom.Document,
) {
    private val relationships = mutableMapOf<String, Map<String, String>>()
    private val notes = mutableMapOf<String, Map<String, Element>>()
    private val noteNumbers = linkedMapOf<Pair<String, String>, Int>()
    private val activeNotes = mutableSetOf<Pair<String, String>>()

    fun render(
        root: Element,
        part: String,
    ): String {
        val fields = mutableListOf<Field>()

        fun linked(
            text: String,
            target: String?,
        ): String {
            val href = target?.takeIf(::isSupportedLink) ?: return text
            return "<a class=\"ref\" href=\"${Entities.escape(href).replace("\"", "&quot;")}\">$text</a>"
        }

        fun children(element: Element): String =
            buildString {
                element.wordChildren().forEach { child ->
                    when (child.localName) {
                        "pPr", "rPr", "footnoteRef", "endnoteRef" -> Unit
                        "fldChar" -> {
                            when (child.getAttributeNS(WORD_NAMESPACE, "fldCharType")) {
                                "begin" -> fields += Field()
                                "separate" -> fields.lastOrNull()?.showResult = true
                                "end" -> if (fields.isNotEmpty()) fields.removeAt(fields.lastIndex)
                            }
                        }
                        "instrText" -> fields.lastOrNull()?.instruction?.append(child.textContent)
                        "t", "tab", "br", "cr" -> {
                            if (fields.any { !it.showResult }) return@forEach
                            val text =
                                when (child.localName) {
                                    "t" -> Entities.escape(child.textContent)
                                    "tab" -> " "
                                    else -> "<br>"
                                }
                            append(
                                linked(
                                    text,
                                    fields
                                        .lastOrNull()
                                        ?.instruction
                                        ?.toString()
                                        ?.let(::fieldTarget),
                                ),
                            )
                        }
                        "hyperlink" -> {
                            val id = child.getAttributeNS(RELATIONSHIP_NAMESPACE, "id")
                            append(linked(children(child), relationships(part)[id]))
                        }
                        "fldSimple" -> append(linked(children(child), fieldTarget(child.getAttributeNS(WORD_NAMESPACE, "instr"))))
                        "footnoteReference", "endnoteReference" -> {
                            val kind = if (child.localName == "footnoteReference") "footnotes" else "endnotes"
                            val id = child.getAttributeNS(WORD_NAMESPACE, "id")
                            val key = kind to id
                            val note = notes(kind)[id]
                            if (note != null && activeNotes.add(key)) {
                                try {
                                    val number = noteNumbers.getOrPut(key) { noteNumbers.size + 1 }
                                    val content =
                                        note.wordChildren().filter { it.localName == "p" }.joinToString(
                                            "<br>",
                                        ) { render(it, kind) }
                                    append("<sup class=\"footnote-marker\">$number</sup>")
                                    append("<i class=\"footnote\"> $number. $content </i>")
                                } finally {
                                    activeNotes.remove(key)
                                }
                            }
                        }
                        else -> append(children(child))
                    }
                }
            }

        return children(root)
    }

    private fun relationships(part: String): Map<String, String> =
        relationships.getOrPut(part) {
            val path = "word/_rels/$part.xml.rels"
            if (zip.getEntry(path) == null) return@getOrPut emptyMap()
            val entries = readXml(path).getElementsByTagNameNS("*", "Relationship")
            buildMap {
                repeat(entries.length) { index ->
                    val entry = entries.item(index) as Element
                    if (entry.getAttribute("Type").endsWith("/hyperlink") && entry.getAttribute("TargetMode") == "External") {
                        put(entry.getAttribute("Id"), entry.getAttribute("Target"))
                    }
                }
            }
        }

    private fun notes(kind: String): Map<String, Element> =
        notes.getOrPut(kind) {
            val path = "word/$kind.xml"
            if (zip.getEntry(path) == null) return@getOrPut emptyMap()
            val entries = readXml(path).getElementsByTagNameNS(WORD_NAMESPACE, kind.removeSuffix("s"))
            buildMap {
                repeat(entries.length) { index ->
                    val entry = entries.item(index) as Element
                    if (entry.getAttributeNS(WORD_NAMESPACE, "type").let { it.isEmpty() || it == "normal" }) {
                        put(entry.getAttributeNS(WORD_NAMESPACE, "id"), entry)
                    }
                }
            }
        }

    private class Field(
        val instruction: StringBuilder = StringBuilder(),
        var showResult: Boolean = false,
    )

    private fun fieldTarget(instruction: String): String? =
        Regex("""^\s*HYPERLINK\s+(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)
            .find(instruction)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun isSupportedLink(target: String): Boolean =
        runCatching { URI(target).scheme?.lowercase() in setOf("https", "http", "otzaria", "zayit", "zayita") }
            .getOrDefault(false)
}

private fun Element.wordChildren(): List<Element> =
    (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }
        .filter { it.namespaceURI == WORD_NAMESPACE }

private fun Element.wordElement(name: String): Element? =
    (0 until childNodes.length)
        .asSequence()
        .mapNotNull { childNodes.item(it) as? Element }
        .firstOrNull { it.namespaceURI == WORD_NAMESPACE && it.localName == name }

private fun Element.wordValue(): String = getAttributeNS(WORD_NAMESPACE, "val")

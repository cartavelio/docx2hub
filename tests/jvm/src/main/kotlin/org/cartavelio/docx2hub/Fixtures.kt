package org.cartavelio.docx2hub

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object Fixtures {
    private const val ROWS = 100
    private const val PARAGRAPHS = 499
    private const val ALIASES = 103
    private const val RELATIONSHIPS =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>"

    fun paragraph(
        text: String,
        prefix: String = "w",
    ): String = "<$prefix:p><w:r><w:t>$text</w:t></w:r></$prefix:p>"

    fun source(): String =
        buildString {
            append("<w:document xmlns:w=\"${Xml.WORD}\" xmlns:q=\"${Xml.WORD}\"><w:body><w:tbl>")
            repeat(ROWS) { row ->
                append("<w:tr><w:tc>${paragraph("cell-$row")}")
                if (row == 0) {
                    append("<w:tbl><w:tr><w:tc>${paragraph("nested")}</w:tc></w:tr></w:tbl>")
                }
                append("</w:tc></w:tr>")
            }
            append("</w:tbl>")
            repeat(PARAGRAPHS) { index ->
                append(paragraph("para-$index"))
                if (index < ALIASES) append(paragraph("alias-$index", "q"))
                append("<w:bookmarkStart w:id=\"$index\" w:name=\"mark-$index\"/>")
            }
            append("</w:body></w:document>")
        }

    fun packageDocument(
        root: Path,
        omitted: Set<String> = emptySet(),
    ): Path {
        val parts =
            mapOf(
                "word/document.xml" to source(),
                "word/styles.xml" to "<w:styles xmlns:w=\"${Xml.WORD}\"/>",
                "word/settings.xml" to
                    "<w:settings xmlns:w=\"${Xml.WORD}\"><w:zoom w:percent=\"93\"/></w:settings>",
                "word/fontTable.xml" to
                    "<w:fonts xmlns:w=\"${Xml.WORD}\"><w:font w:name=\"Synthetic\"/></w:fonts>",
                "word/_rels/document.xml.rels" to RELATIONSHIPS,
                "_rels/.rels" to RELATIONSHIPS,
                "[Content_Types].xml" to
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            )
        for ((name, content) in parts) {
            if (name.substringAfterLast('/') !in omitted) {
                root.resolve(name).apply {
                    parent.createDirectories()
                    writeText(content)
                }
            }
        }
        return root.resolve("word/document.xml")
    }

    fun notes(): String =
        "<hub xmlns=\"${Xml.DOCBOOK}\"><para>Lead <footnote xml:id=\"a\">Foot</footnote>" +
            "<footnote role=\"endnote\" xml:id=\"b\">End</footnote></para></hub>"

    fun table(): String =
        "<w:tbl xmlns:w=\"${Xml.WORD}\"><w:tblGrid><w:gridCol w:w=\"4513.25\"/>" +
            "<w:gridCol w:w=\"20\"/></w:tblGrid><w:tr><w:tc><w:tcPr>" +
            "<w:tcW w:type=\"dxa\" w:w=\"4513.25\"/></w:tcPr>${paragraph("Cell")}" +
            "</w:tc></w:tr></w:tbl>"

    fun secondary(size: Int): String =
        "<w:hdr xmlns:w=\"${Xml.WORD}\" xmlns:q=\"${Xml.WORD}\">" +
            (0 until size).joinToString("") { paragraph("secondary-$it", if (it % 2 == 0) "w" else "q") } +
            "</w:hdr>"

    fun boundary(size: Int): String =
        "<w:document xmlns:w=\"${Xml.WORD}\" xmlns:q=\"${Xml.WORD}\"><w:body>" +
            (0 until size).joinToString("") { paragraph("boundary-$it", if (it % 2 == 0) "w" else "q") } +
            "</w:body></w:document>"
}

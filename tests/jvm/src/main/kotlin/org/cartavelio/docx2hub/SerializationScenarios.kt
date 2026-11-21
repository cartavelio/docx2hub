package org.cartavelio.docx2hub

import javax.xml.XMLConstants
import kotlin.io.path.readText

object SerializationScenarios {
    fun compile(case: SaxonCase) {
        val (_, log) = case.execute(Transform(case.main))
        check(("XTSE0090" in log) == case.version.baseline) { log }
    }

    fun notes(case: SaxonCase) {
        val (output, log) =
            case.execute(
                Transform(case.main, case.input("notes.xml", Fixtures.notes()), "{${Xml.IMPORTER}}join-runs"),
            )
        check(("XTDE0540" in log) == case.version.baseline) { log }
        val document = Xml.parse(output)
        val notes = Xml.elements(document).filter { it.namespaceURI == Xml.DOCBOOK && it.localName == "footnote" }
        check(notes.map { it.getAttributeNS(XMLConstants.XML_NS_URI, "id") } == listOf("fn-1", "en-1"))
        check(document.documentElement.textContent == "Lead FootEnd") { "Authored note text changed" }
    }

    fun debug(case: SaxonCase) {
        val stylesheet =
            case.wrapper(
                """
                <xsl:template match="/">
                <xsl:result-document href="debug-output.xml" format="debug">
                <hub><para>A<link>B</link>C</para><para>D</para></hub></xsl:result-document>
                <ok/></xsl:template>
                """.trimIndent(),
            )
        case.execute(Transform(stylesheet, case.input("input.xml", "<input/>")))
        if (!case.version.baseline) {
            val serialized = case.directory.resolve("debug-output.xml").readText()
            check(serialized.endsWith("<para>A<link>B</link>C</para><para>D</para></hub>")) { serialized }
        }
    }
}

package org.cartavelio.docx2hub

import java.math.BigDecimal
import java.math.MathContext

object WidthScenarios {
    fun widths(case: SaxonCase) {
        val stylesheet =
            case.wrapper(
                """
                <xsl:template match="/">
                <widths twips="{d:twips2mm(4513.25)}" points="{d:pt-length('22.5')}"
                  integer="{d:pt-length('20')}" bad="{d:pt-length('invalid')}" empty="{d:pt-length('')}"/>
                </xsl:template>
                """.trimIndent(),
            )
        val (output) =
            case.execute(
                Transform(
                    stylesheet,
                    case.input("input.xml", "<input/>"),
                    error = if (case.version.baseline) "XPTY0004" else null,
                ),
            )
        if (!case.version.baseline) {
            check(
                Xml.attributes(Xml.parse(output).documentElement) ==
                    mapOf(
                        "twips" to "79.6087202925mm",
                        "points" to "1.125pt",
                        "integer" to "1pt",
                        "bad" to "0",
                        "empty" to "0",
                    ),
            ) { "Decimal/integer/fallback widths" }
        }
    }

    fun points(case: SaxonCase) {
        val stylesheet =
            case.wrapper(
                """<xsl:template match="/"><point value="{d:pt-length('22.5')}"/></xsl:template>""",
            )
        val (output) = case.execute(Transform(stylesheet, case.input("input.xml", "<input/>")))
        val expected = if (case.version.baseline) "0" else "1.125pt"
        check(Xml.parse(output).documentElement.getAttribute("value") == expected) { "Fractional point width" }
    }

    fun table(case: SaxonCase) {
        val (output) =
            case.execute(
                Transform(
                    case.main,
                    case.input("table.xml", Fixtures.table()),
                    "wml-to-dbk",
                    error = if (case.version.baseline) "FORG0001" else null,
                ),
            )
        if (!case.version.baseline) {
            val document = Xml.parse(output)
            val columns = Xml.elements(document).filter { it.namespaceURI == Xml.DOCBOOK && it.localName == "colspec" }
            val observed = columns.map { it.getAttribute("colwidth") }
            check(observed == listOf("79.6087202925mm", "0.3527778mm")) { observed }
            check("Cell" in document.documentElement.textContent) { "Table text lost" }
            // Independent physical units: 25.4 mm/inch and 1440 twips/inch.
            val expected =
                listOf("4513.25", "20").map {
                    BigDecimal(it).multiply(BigDecimal("25.4")).divide(BigDecimal("1440"), MathContext.DECIMAL128)
                }
            observed.zip(expected).forEach { (value, physical) ->
                check(BigDecimal(value.removeSuffix("mm")).subtract(physical).abs() < BigDecimal("0.00001"))
            }
            Evidence.writeJson(case.directory.resolve("physical-widths.json"), expected.map { it.toPlainString() })
        }
    }
}

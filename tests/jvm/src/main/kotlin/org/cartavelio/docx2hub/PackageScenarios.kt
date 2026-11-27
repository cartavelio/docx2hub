package org.cartavelio.docx2hub

object PackageScenarios {
    private const val LOCATIONS = 2109
    private val selected = setOf("w:p", "w:r", "w:t", "w:tbl", "w:tc")

    fun locations(case: SaxonCase) {
        val input = Fixtures.packageDocument(case.directory.resolve("package"))
        val (output) = case.execute(Transform(case.main, input, "insert-xpath", case.sourceOptions(input)))
        val expected =
            Xml
                .lexicalLocations(Fixtures.source())
                .filter { it.first.nodeName in selected }
                .map { it.second }
        val document = Xml.parse(output)
        val nodes = Xml.elements(document)
        val actual =
            nodes
                .filter { it.hasAttribute("srcpath") }
                .map { it.getAttribute("srcpath").substringAfter("?xpath=") }
        check(expected.size == LOCATIONS && actual == expected) { "2109 lexical source locations differ" }
        val zoom = nodes.single { it.namespaceURI == Xml.WORD && it.localName == "zoom" }
        val font = nodes.single { it.namespaceURI == Xml.WORD && it.localName == "font" }
        check(zoom.getAttributeNS(Xml.WORD, "percent") == "93")
        check(font.getAttributeNS(Xml.WORD, "name") == "Synthetic")
        Evidence.writeJson(
            case.directory.resolve("location-count.json"),
            mapOf("checked_source_locations" to actual.size),
        )
    }

    fun missingSettings(case: SaxonCase) = missing(case, setOf("settings.xml"))

    fun missingFonts(case: SaxonCase) = missing(case, setOf("fontTable.xml"))

    fun missingBoth(case: SaxonCase) = missing(case, setOf("settings.xml", "fontTable.xml"))

    private fun missing(
        case: SaxonCase,
        omitted: Set<String>,
    ) {
        val input = Fixtures.packageDocument(case.directory.resolve("package"), omitted)
        val (output) =
            case.execute(
                Transform(
                    case.main,
                    input,
                    "insert-xpath",
                    case.sourceOptions(input),
                    if (case.version.baseline) "FODC0002" else null,
                ),
            )
        if (!case.version.baseline) {
            val document = Xml.parse(output)
            check("para-498" in document.documentElement.textContent) { "Missing optional part lost body" }
            val nodes = Xml.elements(document)
            for (part in omitted) {
                val local = if (part == "settings.xml") "settings" else "fonts"
                check(nodes.none { it.namespaceURI == Xml.WORD && it.localName == local }) { "Invented optional part" }
            }
        }
    }
}

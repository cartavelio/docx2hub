package org.cartavelio.docx2hub

object PathScenarios {
    private const val SMALL_SECONDARY = 3
    private const val WIDE_SECONDARY = 130
    private const val UNINDEXED_BOUNDARY = 64
    private const val INDEXED_BOUNDARY = 65

    fun functions(case: SaxonCase) = compare(case, Fixtures.source(), Fixtures.secondary(SMALL_SECONDARY))

    fun boundary64(case: SaxonCase) = compare(case, Fixtures.boundary(UNINDEXED_BOUNDARY))

    fun boundary65(case: SaxonCase) = compare(case, Fixtures.boundary(INDEXED_BOUNDARY))

    fun wideSecondary(case: SaxonCase) = compare(case, Fixtures.source(), Fixtures.secondary(WIDE_SECONDARY))

    private fun compare(
        case: SaxonCase,
        principal: String,
        secondary: String? = null,
    ) {
        val input = case.input("source.xml", principal)
        val secondarySelection =
            secondary
                ?.let {
                    val file = case.input("secondary.xml", it)
                    ", document('${Xml.escape(file.toUri().toString().replace("'", "''"))}')//*"
                }.orEmpty()
        val stylesheet =
            case.wrapper(
                """
                <xsl:template match="/">
                <paths><xsl:for-each select="//*$secondarySelection">
                <path one="{d:srcpath(.)}" two="{d:srcpath(., if (parent::*) then d:srcpath(..) else '')}"/>
                </xsl:for-each></paths></xsl:template>
                """.trimIndent(),
            )
        val (output) = case.execute(Transform(stylesheet, input))
        val expected = Xml.lexicalLocations(principal) + secondary?.let { Xml.lexicalLocations(it) }.orEmpty()
        val actual = Xml.elements(Xml.parse(output)).filter { it.tagName == "path" }
        check(actual.size == expected.size) { "Missing overload paths" }
        actual.zip(expected).forEach { (node, location) ->
            check(node.getAttribute("one").substringAfter("?xpath=") == location.second) { Xml.attributes(node) }
            check(node.getAttribute("two") == node.getAttribute("one")) { Xml.attributes(node) }
        }
    }
}

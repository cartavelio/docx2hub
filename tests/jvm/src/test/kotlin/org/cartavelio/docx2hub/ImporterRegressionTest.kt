package org.cartavelio.docx2hub

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Every test executes the real importer; the two baselines must reproduce their failures. */
class ImporterRegressionTest {
    @TestFactory
    fun widths(): List<DynamicTest> =
        versions(
            "widths" to WidthScenarios::widths,
            "points" to WidthScenarios::points,
            "table" to WidthScenarios::table,
        )

    @TestFactory
    fun notesAndSerialization(): List<DynamicTest> =
        versions(
            "compile" to SerializationScenarios::compile,
            "notes" to SerializationScenarios::notes,
            "debug" to SerializationScenarios::debug,
        )

    @TestFactory
    fun packageParts(): List<DynamicTest> =
        versions(
            "locations" to PackageScenarios::locations,
            "without-settings" to PackageScenarios::missingSettings,
            "without-fontTable" to PackageScenarios::missingFonts,
        )

    @TestFactory
    fun sourcePathFunctions(): List<DynamicTest> = versions("functions" to PathScenarios::functions)

    @Test
    fun evidencePathCharacters() {
        val special = ImporterEnvironment(environment.evidence.resolve("ampersand & apostrophe '"))
        val version = special.versions.single { !it.baseline }
        PathScenarios.functions(special.case(version, "functions"))
        PathScenarios.wideSecondary(special.case(version, "wide-secondary"))
    }

    @TestFactory
    fun optionalAndBoundaryCases(): List<DynamicTest> =
        listOf(
            "without-both-parts" to PackageScenarios::missingBoth,
            "boundary-64" to PathScenarios::boundary64,
            "boundary-65" to PathScenarios::boundary65,
            "wide-secondary" to PathScenarios::wideSecondary,
        ).map { (name, scenario) -> test(environment.versions.single { !it.baseline }, name, scenario) }

    private fun versions(vararg cases: Pair<String, (SaxonCase) -> Unit>): List<DynamicTest> =
        environment.versions.flatMap { version -> cases.map { (name, scenario) -> test(version, name, scenario) } }

    private fun test(
        version: SourceVersion,
        name: String,
        scenario: (SaxonCase) -> Unit,
    ): DynamicTest = DynamicTest.dynamicTest("${version.name}: $name") { scenario(environment.case(version, name)) }

    companion object {
        private val environment by lazy { ImporterEnvironment() }
    }
}

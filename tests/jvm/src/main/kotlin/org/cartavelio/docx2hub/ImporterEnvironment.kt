package org.cartavelio.docx2hub

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

data class SourceVersion(
    val name: String,
    val directory: Path,
    val baseline: Boolean,
)

class ImporterEnvironment {
    val repo: Path = Path.of(requireNotNull(System.getProperty("importer.repo"))).toRealPath()
    val evidence: Path =
        System.getProperty("importer.evidence")?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: Files.createTempDirectory("docx2hub-kotlin-tests-")
    val cache: Path =
        System.getProperty("importer.dependencies")?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: evidence.resolve("dependencies")
    val java: String = Path.of(System.getProperty("java.home"), "bin", "java").toString()
    val versions: List<SourceVersion>

    init {
        check(Runtime.version().feature() == JAVA_MAJOR) { "Importer regressions require Java 17" }
        check(!evidence.startsWith(repo) && !cache.startsWith(repo)) { "Keep evidence/dependencies outside source" }
        evidence.createDirectories()
        val util = Dependencies.prepare(cache, evidence)
        evidence.resolve("catalog.xml").writeText(
            """
            <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
              <rewriteURI uriStartString="http://transpect.io/xslt-util/" rewritePrefix="${util.toUri().toString().trimEnd(
                '/',
            )}/"/>
              <rewriteURI uriStartString="http://www.functx.com/" rewritePrefix="${util.resolve(
                "functx",
            ).toUri().toString().trimEnd('/')}/"/>
            </catalog>
            """.trimIndent(),
        )
        versions = BASELINES.mapIndexed { index, revision ->
            val root = evidence.resolve("baseline-source-$index").createDirectories()
            Evidence.archive(repo, revision, root.resolve("source.zip"))
            SourceVersion("baseline-$index", root.resolve("source"), true)
        } + SourceVersion("corrected", repo, false)
        Evidence.writeJson(evidence.resolve("versions.json"), versions.map { it.name to it.directory.toString() })
        println("Importer regression evidence: $evidence")
    }

    fun case(
        version: SourceVersion,
        name: String,
    ): SaxonCase = SaxonCase(this, version, evidence.resolve(version.name).resolve(name).createDirectories())

    fun classpath(): String =
        listOf("Saxon-HE-12.10.jar", "xmlresolver-6.1.0.jar", "xmlresolver-6.1.0-data.jar")
            .joinToString(File.pathSeparator) { cache.resolve(it).toString() }

    companion object {
        private const val JAVA_MAJOR = 17
        val BASELINES =
            listOf(
                "29e264dafd09dc684d903005feaa4e4cc693f4b1",
                "68b4ec45624e918a357c5a628bed6c3ec3158669",
            )
    }
}

data class Transform(
    val stylesheet: Path,
    val input: Path? = null,
    val mode: String? = null,
    val options: List<String> = emptyList(),
    val error: String? = null,
)

class SaxonCase(
    val environment: ImporterEnvironment,
    val version: SourceVersion,
    val directory: Path,
) {
    val main: Path = version.directory.resolve("xsl/main.xsl")

    fun input(
        name: String,
        xml: String,
    ): Path = directory.resolve(name).apply { writeText(xml) }

    fun wrapper(body: String): Path =
        input(
            "wrapper.xsl",
            """
            <xsl:stylesheet xmlns:xsl="${Xml.XSL}" xmlns:d="${Xml.IMPORTER}" xmlns:w="${Xml.WORD}" version="2.0">
            <xsl:import href="${Xml.escape(main.toUri().toString())}"/>$body</xsl:stylesheet>
            """.trimIndent(),
        )

    fun execute(transform: Transform): Pair<Path, String> {
        val output = directory.resolve("output.xml")
        val command =
            mutableListOf(
                environment.java,
                "-Xmx512m",
                "-cp",
                environment.classpath(),
                "net.sf.saxon.Transform",
                "-catalog:${environment.evidence.resolve("catalog.xml")}",
                "-xsl:${transform.stylesheet}",
            )
        if (transform.input == null) {
            command.add("-nogo")
        } else {
            command.addAll(listOf("-s:${transform.input}", "-o:$output"))
        }
        transform.mode?.let { command.add("-im:$it") }
        command.addAll(transform.options)
        val result = Evidence.command(command, directory.resolve("transform.log"))
        Evidence.writeJson(directory.resolve("commands.json"), result)
        check(result.exit == if (transform.error == null) 0 else 2) { result }
        transform.error?.let { check(it in result.log) { result.log } }
        if (!version.baseline) {
            check("XTSE0090" !in result.log && "XTDE0540" !in result.log) { result.log }
        }
        return output to result.log
    }

    fun sourceOptions(document: Path): List<String> =
        listOf(
            "srcpaths=yes",
            "srcpaths-on-runs=yes",
            "extract-dir-uri=${document.parent.parent.toUri().toString().trimEnd('/')}/",
            "local-href=${document.parent.parent.resolve("synthetic.docx").toUri()}",
        )
}

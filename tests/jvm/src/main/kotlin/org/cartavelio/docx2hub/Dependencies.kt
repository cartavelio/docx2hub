package org.cartavelio.docx2hub

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object Archives {
    private fun destination(
        root: Path,
        name: String,
    ): Path {
        val path = root.resolve(name).normalize()
        check(path.startsWith(root.normalize())) { "Archive traversal: $name" }
        return path
    }

    fun zip(
        bytes: ByteArray,
        root: Path,
    ) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                val target = destination(root, entry.name)
                if (entry.isDirectory) {
                    target.createDirectories()
                } else {
                    target.parent.createDirectories()
                    Files.copy(archive, target)
                }
            }
        }
    }

    fun tar(
        path: Path,
        root: Path,
    ) {
        TarArchiveInputStream(GZIPInputStream(Files.newInputStream(path))).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                check(entry.isDirectory || entry.isFile) { "Unsupported archive member: ${entry.name}" }
                val target = destination(root, entry.name)
                if (entry.isDirectory) {
                    target.createDirectories()
                } else {
                    target.parent.createDirectories()
                    Files.copy(archive, target)
                }
            }
        }
    }
}

data class Download(
    val name: String,
    val url: String,
    val sha256: String,
)

object Dependencies {
    const val UTIL_REVISION = "a6cd8637b3aabede1c9c0167e2c51e66ac479f47"
    private const val MAVEN = "https://repo.maven.apache.org/maven2/"
    private val downloads =
        listOf(
            Download(
                "Saxon-HE-12.10.jar",
                MAVEN + "net/sf/saxon/Saxon-HE/12.10/Saxon-HE-12.10.jar",
                "b571af282f25d7301059f788b9a149aab8b5cdc14ef3d212dc5425d3dcbb9a97",
            ),
            Download(
                "xmlresolver-6.1.0.jar",
                MAVEN + "org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0.jar",
                "bf787a469cfc591e3a896dc8ef388c2c8bce11500cc7d37aed16113728382033",
            ),
            Download(
                "xmlresolver-6.1.0-data.jar",
                MAVEN + "org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0-data.jar",
                "1d6e4db99401ae8ba3c390ab7cb4d794a46084e1a36bdcafae91e2338e7c5b0d",
            ),
            Download(
                "xslt-util.tar.gz",
                "https://codeload.github.com/transpect/xslt-util/tar.gz/$UTIL_REVISION",
                "9ce1a5cdd57b24df9a5d193bea5c83d1c26efc01e1aea2be853cc98c2eb974b8",
            ),
        )

    fun prepare(
        cache: Path,
        evidence: Path,
    ): Path {
        cache.createDirectories()
        for (download in downloads) {
            val target = cache.resolve(download.name)
            if (!target.exists()) {
                val connection =
                    URI(download.url).toURL().openConnection().apply {
                        connectTimeout = 60_000
                        readTimeout = 60_000
                    }
                connection.getInputStream().use { target.writeBytes(it.readBytes()) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(target.readBytes())
            check(digest.joinToString("") { "%02x".format(Locale.ROOT, it) } == download.sha256) {
                "Checksum mismatch: $target"
            }
        }
        Archives.tar(cache.resolve("xslt-util.tar.gz"), evidence)
        return evidence.resolve("xslt-util-$UTIL_REVISION")
    }
}

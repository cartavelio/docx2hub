import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("dev.detekt") version "2.0.0-alpha.3"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        allWarningsAsErrors.set(true)
    }
}

ktlint {
    version.set("1.5.0")
}

detekt {
    toolVersion = "2.0.0-alpha.3"
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("17")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn("ktlintCheck", "detektMain", "detektTest")
}

// This is the repository's sole wrapper/build, scoped to the importer test harness.
dependencies {
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
}

tasks.test {
    maxHeapSize = "512m"
    systemProperty("importer.repo", rootDir.resolve("../..").canonicalPath)
    providers.gradleProperty("importerEvidenceDir").orNull?.let {
        systemProperty("importer.evidence", it)
    }
    providers.gradleProperty("importerDependencyDir").orNull?.let {
        systemProperty("importer.dependencies", it)
    }
    // Always exercise the current source, even when only files outside this build changed.
    inputs.dir(rootDir.resolve("../../xsl"))
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

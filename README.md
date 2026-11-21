# docx2hub

Converts Microsoft docx to flat hub XML

Please note that this is just a library and not a standalone converter. For further information and an example implementation see the [frontend project](https://github.com/transpect/docx2hub-frontend). Another usage example is [docx2tex](https://github.com/transpect/docx2tex).

The options for `docx2hub.xpl` are documented in the [file itself](xpl/docx2hub.xpl).

If you want to include this library in your XProc pipeline, please refer to this [tutorial](https://transpect.github.io/getting-started.html).

## Maintained importer behavior

- The named XSLT `debug` output uses serialization supported by Saxon HE and
  adds no indentation whitespace to mixed content.
- In the `docx2hub:join-runs` mode, endnote IDs use the `en-` prefix and footnote
  IDs use `fn-`; the endnote rule takes precedence without an ambiguous match.
- Source-path sibling positions count lexical names, including prefixes, so
  `w:p` and `q:p` have separate positions even when they share a namespace.
  Wide sibling groups use indexed positions; small groups and nodes outside
  the index retain the original fallback calculation.
- The `insert-xpath` stage loads `word/settings.xml` and `word/fontTable.xml`
  when available and skips unavailable parts.
- Table grid widths and the `docx2hub:pt-length` and `docx2hub:twips2mm`
  conversions accept decimal twip values as well as integers.

## Maintained importer regressions

The test-only Kotlin/JVM harness lives in [tests/jvm](tests/jvm). Its sole
checked-in Gradle wrapper uses Java 17, pinned Kotlin/JUnit, ktlint, and type-aware
detekt checks. It does not change the XSLT library or add an application runtime.
Git, network access, and a caller-supplied JDK 17 are prerequisites. Set
`JAVA_HOME` to that JDK or put its `java` on `PATH` before launching Gradle.
The harness selects a local JDK 17 and has no automatic JDK provisioning.
If Java cannot be found, the wrapper reports how to set `JAVA_HOME`.
From the repository root, run the same non-writing full check as PR CI:

```sh
tests/jvm/gradlew -p tests/jvm check --no-daemon
```

The harness executes real Saxon HE 12.10 child processes against both historical
baselines (`29e264dafd09dc684d903005feaa4e4cc693f4b1` and
`68b4ec45624e918a357c5a628bed6c3ec3158669`) and the current importer. Each has
10 scenarios: compile, decimal widths, fractional points, notes, named debug
serialization, source locations, missing settings, missing font table, both
source-path overloads, and table conversion. All 30 outcomes are required;
baselines must reproduce their known failures and the current source must pass.

Four further current-source cases cover both optional parts missing, the exact
64/65-child indexing boundary, and wide secondary-document fallback. Assertions
check 2,109 lexical locations against an independent DOM sibling-count oracle,
explicit authored content and IDs, emitted XML, exact decimal outputs, and an
independent physical-unit crosscheck. The tests import actual `xsl/main.xsl`;
no source or binary patching is involved. No proprietary inputs, fonts or images
are required. This focused coverage does not claim full DOCX/XProc acceptance.
An additional importer regression checks catalog resolution and both source-path
overloads with ampersands and apostrophes in the evidence directory.

Evidence (exact commands, exit statuses, logs and XML) is retained outside the
source checkout; the directory is printed by the test JVM. Choose a fresh
`-PimporterEvidenceDir=/absolute/external/directory` for each execution. A reusable
external dependency cache can be specified separately with
`-PimporterDependencyDir=/absolute/external/cache`. Downloads of Saxon HE 12.10,
XML Resolver 6.1.0 and immutable transpect/xslt-util source are SHA-256 verified;
the Gradle wrapper distribution and build plugin/dependency versions are pinned.
CI runs the full check directly on pull requests and uploads synthetic evidence
and JUnit results.

Focused behavior check, formatting, and non-writing quality checks:

```sh
tests/jvm/gradlew -p tests/jvm test --tests '*ImporterRegressionTest.widths' --no-daemon
tests/jvm/gradlew -p tests/jvm ktlintFormat --no-daemon
tests/jvm/gradlew -p tests/jvm ktlintCheck detektMain detektTest --no-daemon
```

The implementation/test pair is [WidthScenarios.kt](tests/jvm/src/main/kotlin/org/cartavelio/docx2hub/WidthScenarios.kt)
and [ImporterRegressionTest.kt](tests/jvm/src/test/kotlin/org/cartavelio/docx2hub/ImporterRegressionTest.kt).
Compilation during development: `tests/jvm/gradlew -p tests/jvm compileKotlin compileTestKotlin`.
New harness code has warnings-as-errors, no suppressions and no analyzer baseline.

# docx2hub

Converts Microsoft docx to flat hub XML

Please note that this is just a library and not a standalone converter. For further information and an example implementation see the [frontend project](https://github.com/transpect/docx2hub-frontend). Another usage example is [docx2tex](https://github.com/transpect/docx2tex).

The options for `docx2hub.xpl` are documented in the [file itself](https://github.com/transpect/docx2hub/blob/master/xpl/docx2hub.xpl#L97).

If you want to include this library in your XProc pipeline, please refer to this [tutorial](https://transpect.github.io/getting-started.html).

## Maintained importer regressions

Run the synthetic source tests with Python 3.10+ and Java 17:

```sh
python3 tests/importer_regressions.py
```

The runner downloads checksum-pinned Saxon HE 12.10, XML Resolver 6.1.0 and
transpect/xslt-util source into an external temporary directory. It imports the
actual `xsl/main.xsl`; no source or binary patching is involved. Use
`--work-dir /absolute/external/directory` to retain the dependency cache and
per-case commands, XML and logs. Choose a fresh directory for each execution.
No Python packages, proprietary inputs, fonts or images are required.

CI runs the same command with these additional arguments to reproduce the
known failures on the frozen proposal base and the newer upstream base:

```sh
--baseline-ref 29e264dafd09dc684d903005feaa4e4cc693f4b1 \
--baseline-ref 68b4ec45624e918a357c5a628bed6c3ec3158669
```

Each baseline must exhibit the expected decimal width errors, missing-part
errors, HE serialization warning and endnote ambiguity. The corrected source
must pass without those diagnostics. Assertions cover decimal table columns
and point lengths, authored note text and IDs, emitted debug serialization,
optional settings/fonts (present and absent), and 2,109 source locations checked
against an independent lexical-sibling DOM oracle. Both source-path function
overloads also run on a secondary document to exercise the uncached fallback.
These focused importer tests do not claim full DOCX/XProc pipeline acceptance.

For a non-writing syntax/whitespace check, run:

```sh
python3 -c "import ast, pathlib; ast.parse(pathlib.Path('tests/importer_regressions.py').read_text())"
git diff --check
```

#!/usr/bin/env python3
"""Execute maintained importer contracts with real Saxon HE, using synthetic XML only."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
from urllib.request import urlopen
from xml.dom import minidom
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

XSL = 'http://www.w3.org/1999/XSL/Transform'
W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
DB = 'http://docbook.org/ns/docbook'
D = 'http://transpect.io/docx2hub'
CAT = 'urn:oasis:names:tc:entity:xmlns:xml:catalog'
REPO = Path(__file__).resolve().parents[1]
MAVEN = 'https://repo.maven.apache.org/maven2/'
UTIL_REV = 'a6cd8637b3aabede1c9c0167e2c51e66ac479f47'
DEPENDENCIES = {
    'Saxon-HE-12.10.jar': (
        MAVEN + 'net/sf/saxon/Saxon-HE/12.10/Saxon-HE-12.10.jar',
        'b571af282f25d7301059f788b9a149aab8b5cdc14ef3d212dc5425d3dcbb9a97'),
    'xmlresolver-6.1.0.jar': (
        MAVEN + 'org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0.jar',
        'bf787a469cfc591e3a896dc8ef388c2c8bce11500cc7d37aed16113728382033'),
    'xmlresolver-6.1.0-data.jar': (
        MAVEN + 'org/xmlresolver/xmlresolver/6.1.0/xmlresolver-6.1.0-data.jar',
        '1d6e4db99401ae8ba3c390ab7cb4d794a46084e1a36bdcafae91e2338e7c5b0d'),
    'xslt-util.tar.gz': (
        f'https://codeload.github.com/transpect/xslt-util/tar.gz/{UTIL_REV}',
        '9ce1a5cdd57b24df9a5d193bea5c83d1c26efc01e1aea2be853cc98c2eb974b8'),
}


def require(condition, detail):
    if not condition:
        raise AssertionError(detail)


def unpack(data, destination):
    """Accept only ordinary files/directories contained in a verified archive."""
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive.getmembers():
            target = (destination / member.name).resolve()
            require(target.is_relative_to(destination.resolve()), member.name)
            require(member.isfile() or member.isdir(), member.name)
        archive.extractall(destination)


def prepare(root):
    deps = root / 'dependencies'
    deps.mkdir(exist_ok=True)
    for name, (url, digest) in DEPENDENCIES.items():
        target = deps / name
        if not target.exists():
            with urlopen(url, timeout=60) as response:
                target.write_bytes(response.read())
        require(hashlib.sha256(target.read_bytes()).hexdigest() == digest,
                f'Checksum mismatch: {target}')
    unpack((deps / 'xslt-util.tar.gz').read_bytes(), deps)
    catalog = ET.Element(f'{{{CAT}}}catalog')
    util = deps / f'xslt-util-{UTIL_REV}'
    for prefix, path in [('http://transpect.io/xslt-util/', util),
                         ('http://www.functx.com/', util / 'functx')]:
        ET.SubElement(catalog, f'{{{CAT}}}rewriteURI',
                      uriStartString=prefix, rewritePrefix=path.as_uri() + '/')
    ET.ElementTree(catalog).write(root / 'catalog.xml', encoding='utf-8')
    return deps


def paragraph(text, prefix='w'):
    return f'<{prefix}:p><w:r><w:t>{text}</w:t></w:r></{prefix}:p>'


def source():
    # Wide lexical p/q:p siblings share a namespace but have separate positions.
    # The 100-row table exercises wide groups below the body; nested cells and
    # runs exercise the original fallback for small groups.
    rows = []
    for i in range(100):
        nested = ('<w:tbl><w:tr><w:tc>' + paragraph('nested') +
                  '</w:tc></w:tr></w:tbl>') if i == 0 else ''
        rows.append('<w:tr><w:tc>' + paragraph(f'cell-{i}') + nested + '</w:tc></w:tr>')
    body = ['<w:tbl>' + ''.join(rows) + '</w:tbl>']
    for i in range(499):
        body.append(paragraph(f'para-{i}'))
        if i < 103:
            body.append(paragraph(f'alias-{i}', 'q'))
        body.append(f'<w:bookmarkStart w:id="{i}" w:name="mark-{i}"/>')
    return f'<w:document xmlns:w="{W}" xmlns:q="{W}"><w:body>{"".join(body)}</w:body></w:document>'


def lexical_locations(xml):
    """Independent DOM oracle: count lexical sibling names, never expanded names."""
    result = []

    def visit(node, parent_path):
        counts = {}
        for child in node.childNodes:
            if child.nodeType != child.ELEMENT_NODE:
                continue
            counts[child.tagName] = counts.get(child.tagName, 0) + 1
            path = f'{parent_path}/{child.tagName}[{counts[child.tagName]}]'
            result.append((child, path))
            visit(child, path)

    visit(minidom.parseString(xml), '')
    return result


def package(root):
    parts = {
        'word/document.xml': source(),
        'word/styles.xml': f'<w:styles xmlns:w="{W}"/>',
        'word/settings.xml': f'<w:settings xmlns:w="{W}"><w:zoom w:percent="93"/></w:settings>',
        'word/fontTable.xml': f'<w:fonts xmlns:w="{W}"><w:font w:name="Synthetic"/></w:fonts>',
        'word/_rels/document.xml.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>',
        '_rels/.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>',
        '[Content_Types].xml': '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>',
    }
    for relative, content in parts.items():
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
    return root / 'word/document.xml'


class Suite:
    def __init__(self, root, source_dir, deps, java, baseline):
        self.root, self.source_dir = root, source_dir
        self.deps, self.java, self.baseline = deps, java, baseline
        root.mkdir()
        self.results = []

    def wrapper(self, name, body):
        target = self.root / f'{name}.xsl'
        target.write_text(f'<xsl:stylesheet xmlns:xsl="{XSL}" xmlns:d="{D}" xmlns:w="{W}" version="2.0">'
                          f'<xsl:import href="{escape((self.source_dir / "xsl/main.xsl").as_uri())}"/>'
                          f'{body}</xsl:stylesheet>', encoding='utf-8')
        return target

    def run(self, name, stylesheet=None, source_file=None, mode=None, options=(), error=None):
        output = self.root / f'{name}.xml'
        command = [self.java, '-Xmx512m', '-cp', str(self.deps / '*'),
                   'net.sf.saxon.Transform', f'-catalog:{self.root.parent}/catalog.xml',
                   f'-xsl:{stylesheet or self.source_dir / "xsl/main.xsl"}']
        command += [f'-s:{source_file}', f'-o:{output}'] if source_file else ['-nogo']
        if mode:
            command.append(f'-im:{mode}')
        command += list(options)
        completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   timeout=45, check=False)
        log = completed.stdout.decode('utf-8')
        (self.root / f'{name}.log').write_text(log, encoding='utf-8')
        self.results.append({'case': name, 'command': command, 'exit': completed.returncode})
        require(completed.returncode == (2 if error else 0), f'{name}: {log}')
        if error:
            require(error in log, f'{name}: expected {error}: {log}')
        if not self.baseline:
            require('XTSE0090' not in log and 'XTDE0540' not in log, f'{name}: {log}')
        return output, log

    def execute(self):
        baseline = self.baseline
        _, log = self.run('compile')
        require(('XTSE0090' in log) == baseline, log)
        empty = self.root / 'input.xml'
        empty.write_text('<input/>', encoding='utf-8')
        widths = self.wrapper('widths', '''<xsl:template match="/">
          <widths twips="{d:twips2mm(4513.25)}" points="{d:pt-length('22.5')}"
                  integer="{d:pt-length('20')}" bad="{d:pt-length('invalid')}"
                  empty="{d:pt-length('')}"/>
          </xsl:template>''')
        output, _ = self.run('widths', widths, empty, error='XPTY0004' if baseline else None)
        if not baseline:
            require(ET.parse(output).getroot().attrib == {
                'twips': '79.6087202925mm', 'points': '1.125pt', 'integer': '1pt', 'bad': '0', 'empty': '0'},
                'Decimal/integer/fallback width conversion')
        # Test point widths separately: otherwise a twips compile error can hide it.
        points = self.wrapper('points', '''<xsl:template match="/">
          <point value="{d:pt-length('22.5')}"/></xsl:template>''')
        output, _ = self.run('points', points, empty)
        require(ET.parse(output).getroot().get('value') == ('0' if baseline else '1.125pt'), 'Point width')
        notes = self.root / 'notes-input.xml'
        notes.write_text(f'<hub xmlns="{DB}"><para>Lead <footnote xml:id="a">Foot</footnote>'
                         '<footnote role="endnote" xml:id="b">End</footnote></para></hub>', encoding='utf-8')
        output, log = self.run('notes', source_file=notes, mode=f'{{{D}}}join-runs')
        require(('XTDE0540' in log) == baseline, log)
        tree = ET.parse(output)
        require([n.get('{http://www.w3.org/XML/1998/namespace}id') for n in tree.iter(f'{{{DB}}}footnote')]
                == ['fn-1', 'en-1'], 'Footnote/endnote IDs')
        require(''.join(tree.getroot().itertext()) == 'Lead FootEnd', 'Authored note text')
        debug = self.wrapper('debug', '''<xsl:template match="/">
          <xsl:result-document href="debug-output.xml" format="debug"><hub><para>A<link>B</link>C</para><para>D</para></hub></xsl:result-document>
          <ok/></xsl:template>''')
        self.run('debug', debug, empty)
        if not baseline:
            serialized = (self.root / 'debug-output.xml').read_text(encoding='utf-8')
            require(serialized.endswith('<para>A<link>B</link>C</para><para>D</para></hub>'), serialized)
        document = package(self.root / 'package')
        options = ('srcpaths=yes', 'srcpaths-on-runs=yes',
                   f'extract-dir-uri={document.parents[1].as_uri()}/',
                   f'local-href={document.parents[1].as_uri()}/synthetic.docx')
        output, _ = self.run('locations', source_file=document, mode='insert-xpath', options=options)
        selected = {'w:p', 'w:r', 'w:t', 'w:tbl', 'w:tc'}
        expected = [path for node, path in lexical_locations(source()) if node.tagName in selected]
        actual = [node.get('srcpath').partition('?xpath=')[2]
                  for node in ET.parse(output).iter() if node.get('srcpath')]
        require(len(expected) == 2109 and actual == expected, '2,109 lexical locations (including q:p siblings)')
        tree = ET.parse(output)
        require(tree.find(f'.//{{{W}}}zoom').get(f'{{{W}}}percent') == '93', 'Present settings retained')
        require(tree.find(f'.//{{{W}}}font').get(f'{{{W}}}name') == 'Synthetic', 'Present fonts retained')
        for part in ('settings.xml', 'fontTable.xml'):
            path = document.parent / part
            saved = path.read_bytes()
            path.unlink()
            output, _ = self.run('without-' + part, source_file=document, mode='insert-xpath', options=options,
                                 error='FODC0002' if baseline else None)
            path.write_bytes(saved)
            if not baseline:
                tree = ET.parse(output)
                require('para-498' in ''.join(tree.getroot().itertext()), 'Missing optional part lost body')
                missing = 'settings' if part == 'settings.xml' else 'fonts'
                require(tree.find(f'.//{{{W}}}{missing}') is None, 'Optional part unexpectedly synthesized')
        # Both overloads, including a secondary document never present in the index.
        other = self.root / 'secondary.xml'
        other_xml = f'<w:hdr xmlns:w="{W}" xmlns:q="{W}">' + paragraph('one') + paragraph('alias', 'q') + paragraph('two') + '</w:hdr>'
        other.write_text(other_xml, encoding='utf-8')
        functions = self.wrapper('functions', f'''<xsl:template match="/">
          <paths><xsl:for-each select="//*, document('{other.as_uri()}')//*">
            <path one="{{d:srcpath(.)}}" two="{{d:srcpath(., if (parent::*) then d:srcpath(..) else '')}}"/>
          </xsl:for-each></paths></xsl:template>''')
        output, _ = self.run('functions', functions, document)
        expected_paths = lexical_locations(source()) + lexical_locations(other_xml)
        actual_paths = list(ET.parse(output).getroot())
        require(len(actual_paths) == len(expected_paths), 'All overload paths returned')
        for node, (_, expected_path) in zip(actual_paths, expected_paths):
            require(node.get('one').partition('?xpath=')[2] == expected_path, node.attrib)
            require(node.get('two') == node.get('one'), node.attrib)
        table = self.root / 'table-input.xml'
        table.write_text(f'<w:tbl xmlns:w="{W}"><w:tblGrid><w:gridCol w:w="4513.25"/>'
                         '<w:gridCol w:w="20"/></w:tblGrid><w:tr><w:tc><w:tcPr>'
                         '<w:tcW w:type="dxa" w:w="4513.25"/></w:tcPr>' + paragraph('Cell') +
                         '</w:tc></w:tr></w:tbl>', encoding='utf-8')
        output, _ = self.run('table', source_file=table, mode='wml-to-dbk',
                             error='FORG0001' if baseline else None)
        if not baseline:
            tree = ET.parse(output)
            require([c.get('colwidth') for c in tree.iter(f'{{{DB}}}colspec')] ==
                    ['79.6087202925mm', '0.3527778mm'], 'Decimal table column widths')
            require('Cell' in ''.join(tree.getroot().itertext()), 'Table cell preserved')
        (self.root / 'commands.json').write_text(json.dumps(self.results, indent=2) + '\n', encoding='utf-8')
        print(f'{self.root.name}: {len(self.results)} cases passed; 2109 lexical locations checked', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, help='External dependency cache and evidence directory')
    parser.add_argument('--java', default='java')
    parser.add_argument('--baseline-ref', action='append', default=[], help='Also reproduce known failures at this Git revision')
    args = parser.parse_args()
    root = (args.work_dir or Path(tempfile.mkdtemp(prefix='docx2hub-tests-'))).resolve()
    require(not root.is_relative_to(REPO), 'Keep test output outside the source checkout')
    root.mkdir(parents=True, exist_ok=True)
    version = subprocess.run([args.java, '-version'], capture_output=True, text=True, check=True)
    require('version "17.' in version.stderr, 'These regressions require Java 17')
    deps = prepare(root)
    for index, ref in enumerate(args.baseline_ref):
        baseline = root / f'baseline-source-{index}'
        baseline.mkdir()
        archive = subprocess.check_output(['git', '-C', str(REPO), 'archive', ref])
        unpack(archive, baseline)
        Suite(root / f'baseline-{index}', baseline, deps, args.java, True).execute()
    Suite(root / 'corrected', REPO, deps, args.java, False).execute()
    print(f'Evidence: {root}')


if __name__ == '__main__':
    main()

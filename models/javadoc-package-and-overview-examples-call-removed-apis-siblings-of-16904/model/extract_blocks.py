#!/usr/bin/env python3
"""Extract Java code examples from Lucene documentation sources, verbatim, with their source line numbers.

Handles two forms:
  * overview.html / package.html:  <pre><code class="language-java"> ... </code></pre>
  * javadoc comments (*.java):      the same, inside a /** ... */ block (leading ' * ' stripped)
HTML entities are unescaped and {@literal X} is replaced by X (that is how javadoc renders the example).

Usage:  extract_blocks.py <file> [<file> ...]      -> JSON list on stdout
        each item: {"file", "start_line" (1-based line of the first code line), "lines": [...], "text"}
Used by gen_tests.py (the repro generator) and by sweep.py (the capped inventory leg).
"""
import html
import json
import re
import sys

OPEN = re.compile(r'<pre><code class="language-java">|<pre class="prettyprint">')  # the latter: releases before #15573
CLOSE = re.compile(r'</code></pre>|</pre>')
LITERAL = re.compile(r'\{@literal ([^}]*)\}')
CODE = re.compile(r'\{@code ([^}]*)\}')


def strip_javadoc(line):
    m = re.match(r'^\s*\*( |$)', line)
    if m:
        return line[m.end():] if m.group(1) == ' ' else ''
    return line


def render(line):
    line = LITERAL.sub(lambda m: m.group(1), line)
    line = CODE.sub(lambda m: m.group(1), line)
    return html.unescape(line)


def extract(path):
    out = []
    with open(path, encoding='utf-8') as f:
        src = f.read().split('\n')
    javadoc = path.endswith('.java')
    i = 0
    while i < len(src):
        line = src[i]
        m = OPEN.search(line)
        if not m:
            i += 1
            continue
        rest = line[m.end():]
        block, start = [], i + 2  # first code line is the next line (1-based)
        if rest.strip() and not CLOSE.search(rest):
            block.append(rest)
            start = i + 1
        j = i + 1
        while j < len(src) and not CLOSE.search(src[j]):
            block.append(strip_javadoc(src[j]) if javadoc else src[j])
            j += 1
        if j < len(src):
            tail = src[j][:CLOSE.search(src[j]).start()]
            tail = strip_javadoc(tail) if javadoc else tail
            if tail.strip():
                block.append(tail)
        lines = [render(b) for b in block]
        # drop leading empty lines but keep the line mapping
        while lines and not lines[0].strip():
            lines.pop(0)
            start += 1
        while lines and not lines[-1].strip():
            lines.pop()
        out.append({"file": path, "start_line": start, "lines": lines, "text": "\n".join(lines)})
        i = j + 1
    return out


if __name__ == '__main__':
    res = []
    for p in sys.argv[1:]:
        res.extend(extract(p))
    json.dump(res, sys.stdout, ensure_ascii=False, indent=1)

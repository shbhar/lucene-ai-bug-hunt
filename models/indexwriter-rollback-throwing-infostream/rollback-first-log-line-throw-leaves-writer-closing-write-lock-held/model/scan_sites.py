#!/usr/bin/env python3
"""Unit indexwriter-rollback-throwing-infostream, InfoStream census, step 1 (mechanical): every infoStream.message(...) call of IndexWriter,
DocumentsWriter and DocumentsWriterFlushControl with its enclosing method and the stack of enclosing blocks
(synchronized methods and blocks = Java monitors, which a throw releases by itself; try/catch/finally; lambdas).
The explicit locks, permits and counters at each site are added BY READING in census_table.py (step 2).
usage: scan_sites.py <lucene checkout> <out.tsv>
"""
import re, sys

FILES = ["IndexWriter.java", "DocumentsWriter.java", "DocumentsWriterFlushControl.java"]
BASE = "lucene/core/src/java/org/apache/lucene/index/"


def strip(src):
    """Blank out comments, string and char literals (keeping length and newlines)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i) + 2
            out.append("".join(ch if ch == "\n" else " " for ch in src[i:j])); i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3) + 3
            out.append("".join(ch if ch == "\n" else " " for ch in src[i:j])); i = j
        elif c == '"':
            j = i + 1
            while src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            out.append('"' + " " * (j - i - 1) + '"'); i = j + 1
        elif c == "'":
            j = i + 1
            while src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            out.append("'" + " " * (j - i - 1) + "'"); i = j + 1
        else:
            out.append(c); i += 1
    return "".join(out)


def kind(header):
    h = " ".join(header.split())
    h = re.sub(r"@\w+(\s*\([^)]*\))?\s*", "", h)  # annotations in front of a method header
    if re.search(r"\bsynchronized\s*\(", h):
        return "sync(" + re.search(r"synchronized\s*\((.*)\)\s*$", h).group(1) + ")"
    if re.match(r"^(\}?\s*)?try\b", h):
        return "try"
    if re.match(r"^(\}?\s*)?finally\b", h):
        return "finally"
    if re.match(r"^(\}?\s*)?catch\b", h):
        return "catch(" + re.search(r"catch\s*\((.*)\)", h).group(1).split()[-2 if len(re.search(r"catch\s*\((.*)\)", h).group(1).split()) > 1 else 0] + ")"
    if h.endswith("->"):
        return "lambda"
    if re.match(r"^(else\s+)?if\b|^else\b|^for\b|^while\b|^do\b|^switch\b|^case\b", h):
        return None  # control flow: not part of the lock context
    m = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*\([^;]*\)\s*(throws [^{;]*)?$", h)
    if m and not re.match(r"^(new|return)\b", h):
        return ("sync-method " if re.search(r"\bsynchronized\b", h) else "method ") + m.group(1)
    if re.search(r"\b(class|interface|enum|record)\b", h):
        return "class " + re.search(r"\b(?:class|interface|enum|record)\s+(\w+)", h).group(1)
    if re.search(r"new\s+[\w.<>]+\s*\([^;]*\)\s*$", h):
        return "anon-class"
    return None


def scan(root, name):
    src = open(root + "/" + BASE + name, encoding="utf-8").read()
    raw_lines = src.split("\n")
    s = strip(src)
    stack = []  # (kind, line)
    rows = []
    last = 0  # start of the current statement header
    line = 1
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == "\n":
            line += 1
        elif c == "{":
            stack.append((kind(s[last:i]), line)); last = i + 1
        elif c == "}":
            stack.pop(); last = i + 1
        elif c == ";":
            last = i + 1
        elif s.startswith("infoStream.message(", i):
            comp = raw_lines[line - 1]
            # the component and the first literal of the message, from the raw source
            j = src.find("infoStream.message(", sum(len(l) + 1 for l in raw_lines[: line - 1]))
            seg = src[j : j + 400]
            m = re.match(r'infoStream\.message\(\s*"(\w+)"\s*,\s*(.*)', seg, re.S)
            component = m.group(1) if m else "?"
            rest = m.group(2) if m else seg
            lit = re.search(r'"((?:[^"\\]|\\.)*)"', rest)
            text = lit.group(1) if lit else ""
            first = re.split(r"[\s,+]", rest.strip(), maxsplit=1)[0]
            if not rest.strip().startswith('"'):
                text = "<" + first[:30] + "> " + text
            ctx = [k for k, _ in stack if k]
            method = [k for k in ctx if k.startswith("method ") or k.startswith("sync-method ")]
            monitors = [k for k in ctx if k.startswith("sync")]
            blocks = [k for k in ctx if k in ("try", "finally", "lambda") or k.startswith("catch")]
            rows.append((name, line, component, text[:60], method[-1] if method else "?", " > ".join(method), "; ".join(monitors) or "-", " > ".join(blocks) or "-"))
        i += 1
    assert not stack, (name, stack)
    return rows


if __name__ == "__main__":
    root, out = sys.argv[1], sys.argv[2]
    rows = []
    for f in FILES:
        rows += scan(root, f)
    with open(out, "w", encoding="utf-8") as w:
        w.write("file\tline\tcomponent\tmessage (first literal)\tenclosing method\tmethod chain\tmonitors (released by a throw)\ttry/catch/finally/lambda\n")
        for r in rows:
            w.write("\t".join(str(x) for x in r) + "\n")
    print(len(rows), "sites")

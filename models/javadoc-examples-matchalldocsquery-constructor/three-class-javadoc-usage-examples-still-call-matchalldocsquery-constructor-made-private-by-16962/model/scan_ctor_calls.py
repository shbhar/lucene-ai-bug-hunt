#!/usr/bin/env python3
"""Static scan: every place of a source tree that calls the no-argument constructor of MatchAllDocsQuery or
MatchNoDocsQuery, classified by where it stands.

usage: scan_ctor_calls.py <label> <git checkout dir> [<git rev>]
  With a rev the tracked files are read from that rev of the repository (git grep, one line at a time); without one
  the tracked files of the checkout are read whole, so a call that is broken over two lines is found too.

Mapping to the Java lines (branch_11x 3221dd4cf3a1):
  the constructor:  lucene/core/src/java/org/apache/lucene/search/MatchAllDocsQuery.java:28  'private MatchAllDocsQuery() {'
                    lucene/core/src/java/org/apache/lucene/search/MatchNoDocsQuery.java:34   'private MatchNoDocsQuery() {'
  a hit of kind 'javadoc or comment' in a file under src/java is a line of documentation that the release ships.
Kinds: own-class (the INSTANCE field of the class itself), code, javadoc-or-comment, html, markdown, other.
"""
import re, subprocess, sys

PAT = re.compile(r"new\s+Match(All|No)DocsQuery\s*\(\s*\)")


def kind(path, line_text, in_comment):
    if path.endswith(("search/MatchAllDocsQuery.java", "search/MatchNoDocsQuery.java")) and "INSTANCE" in line_text:
        return "own-class"
    if path.endswith(".html"):
        return "html"
    if path.endswith(".md"):
        return "markdown"
    if path.endswith(".java"):
        return "javadoc-or-comment" if in_comment else "code"
    return "other"


def comment_lines(text):
    """Line numbers (1-based) that lie inside /* ... */ or after // (a plain lexer; strings are not parsed)."""
    inside, out = False, set()
    for n, ln in enumerate(text.split("\n"), 1):
        s = ln.strip()
        if inside or s.startswith(("/*", "*", "//")):
            out.add(n)
        if "/*" in ln and "*/" not in ln[ln.index("/*"):]:
            inside = True
        elif inside and "*/" in ln:
            inside = False
    return out


def main():
    label, repo = sys.argv[1], sys.argv[2]
    rev = sys.argv[3] if len(sys.argv) > 3 else None
    hits = []
    if rev:
        out = subprocess.run(["git", "-C", repo, "grep", "-nE", r"new\s+Match(All|No)DocsQuery\s*\(\s*\)", rev, "--"],
                             capture_output=True, text=True).stdout
        for ln in out.splitlines():
            _, path, no, text = ln.split(":", 3)
            s = text.strip()
            hits.append((path, int(no), kind(path, text, s.startswith(("*", "//", "/*"))), s))
        files = "git grep over the tracked files of the rev (one line at a time)"
    else:
        names = subprocess.run(["git", "-C", repo, "ls-files"], capture_output=True, text=True).stdout.splitlines()
        n = 0
        for p in names:
            try:
                text = open(repo + "/" + p, encoding="utf-8", errors="replace").read()
            except (OSError, IsADirectoryError):
                continue
            n += 1
            if "DocsQuery" not in text:
                continue
            cl = comment_lines(text) if p.endswith(".java") else set()
            for m in PAT.finditer(text):
                no = text.count("\n", 0, m.start()) + 1
                line = text.split("\n")[no - 1]
                hits.append((p, no, kind(p, line, no in cl), line.strip()))
        files = "%d tracked files read whole" % n
    print("## %s: %s; %d hits" % (label, files, len(hits)))
    by = {}
    for h in hits:
        by[h[2]] = by.get(h[2], 0) + 1
    print("## by kind: " + ", ".join("%s=%d" % kv for kv in sorted(by.items())))
    for p, no, k, s in sorted(hits):
        if k != "code" or "/src/java/" in p:
            print("%s\t%s:%d\t%s" % (k, p, no, s))
    tests = sum(1 for h in hits if h[2] == "code" and "/src/java/" not in h[0])
    print("## code hits outside src/java (tests, benchmarks; not listed): %d" % tests)


if __name__ == "__main__":
    main()

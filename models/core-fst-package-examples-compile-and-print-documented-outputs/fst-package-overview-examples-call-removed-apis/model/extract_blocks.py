#!/usr/bin/env python3
"""Extract the <pre> code blocks of an FST package-info.java (any release) and emit one Java class.

Model element -> source mapping (file:line refers to main 645d4fe14a20):
  block 1 construction      package-info.java:37-53
  block 2 lookup by key     package-info.java:57-60
  block 3 lookup by output  package-info.java:64-68
  block 4 sorted iteration  package-info.java:72-80
  block 5 N-shortest paths  package-info.java:84-96

The blocks are concatenated in page order inside one method (they share fst and scratchBytes, as
the page intends). Only import statements are added; no variable is declared by the harness.
Each emitted line carries a trailing marker '//@L<n>' with its page line, so javac diagnostics
map back to the page. Usage: extract_blocks.py <package-info.java> <ClassName> <out.java>
"""
import html
import re
import sys

IMPORTS = """import java.util.Comparator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.*;
import org.apache.lucene.util.fst.BytesRefFSTEnum.InputOutput;
import org.apache.lucene.util.fst.FST.Arc;
import org.apache.lucene.util.fst.FST.INPUT_TYPE;
"""


def blocks(lines):
  out, cur, start = [], None, None
  for i, raw in enumerate(lines, 1):
    s = re.sub(r"^\s*\*\s?", "", raw.rstrip("\n"))
    if cur is None and re.search(r"<pre\b", s):
      cur, start = [], i
      continue
    if cur is not None:
      if re.search(r"</pre>", s):
        out.append((start, cur))
        cur = None
        continue
      cur.append((i, html.unescape(s)))
  return out


def main():
  src, cls, dst = sys.argv[1], sys.argv[2], sys.argv[3]
  with open(src, encoding="utf-8") as f:
    lines = f.readlines()
  bl = blocks(lines)
  body = []
  for start, code in bl:
    for ln, text in code:
      body.append("    " + text + "  //@L" + str(ln))
  java = (IMPORTS + "\npublic class " + cls + " {\n  public static void main(String[] args) throws Exception {\n"
          + "\n".join(body) + "\n  }\n}\n")
  with open(dst, "w", encoding="utf-8") as f:
    f.write(java)
  print("blocks=%d lines=%d" % (len(bl), sum(len(c) for _, c in bl)))


if __name__ == "__main__":
  main()

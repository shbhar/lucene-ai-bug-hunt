#!/usr/bin/env python3
"""Unit termautomatonquery-non-scoring-use, secondary method (static scan, confirmed by execution in src/ScanConfirm.java).

Lists every TermStates.build(...) call in the main sources of a Lucene checkout whose needsStats argument is not the
constant true, and for the class of each call every docFreq() / totalTermFreq() read of a TermStates-typed value with
the nearest enclosing guard line ('needsScores' within the 6 lines before the read, in the same method text).
usage: scan_termstates.py <lucene checkout> <out.tsv>
"""
import os, re, sys

root, out = sys.argv[1], sys.argv[2]
rows = []
for dirpath, dirnames, filenames in os.walk(os.path.join(root, "lucene")):
    if "/src/java" not in dirpath.replace(os.sep, "/"):
        continue
    if "/test-framework/" in dirpath.replace(os.sep, "/") + "/":
        continue
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dirpath, fn)
        text = open(p, encoding="utf-8").read()
        if "TermStates.build(" not in text:
            continue
        lines = text.split("\n")
        rel = os.path.relpath(p, root)
        for i, line in enumerate(lines):
            if "TermStates.build(" not in line or line.strip().startswith("//") or line.strip().startswith("*"):
                continue
            stmt = line.strip()
            j = i
            while stmt.count("(") > stmt.count(")") and j + 1 < len(lines):
                j += 1
                stmt += " " + lines[j].strip()
            m = re.search(r"TermStates\.build\((.*)$", stmt)
            args = m.group(1) if m else stmt
            flag = args.rsplit(",", 1)[-1].strip()
            flag = re.sub(r"\)+;?$", "", flag) + (")" if flag.startswith("scoreMode.needsScores(") else "")
            reads = []
            for k, l2 in enumerate(lines):
                if re.search(r"\.(docFreq|totalTermFreq)\(\)", l2) and not l2.strip().startswith(("//", "*")):
                    window = lines[max(0, k - 6) : k + 1]
                    guard = [w.strip() for w in window if "needsScores" in w]
                    reads.append("%d:%s" % (k + 1, "guarded by '%s'" % guard[-1] if guard else "NO needsScores guard within 6 lines"))
            rows.append((rel, i + 1, flag, "; ".join(reads) if reads else "no docFreq()/totalTermFreq() read in this file"))
rows.sort()
with open(out, "w", encoding="utf-8") as f:
    f.write("file\tline\tneedsStats argument\tdocFreq()/totalTermFreq() reads in the same file (line:guard)\n")
    for r in rows:
        f.write("%s\t%d\t%s\t%s\n" % r)
print("call sites: %d; with a flag that is not the constant true: %d" % (len(rows), sum(1 for r in rows if r[2] != "true")))
for r in rows:
    if r[2] != "true":
        print("%s:%d  flag=%s\n    %s" % r)

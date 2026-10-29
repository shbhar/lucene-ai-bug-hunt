#!/usr/bin/env python3
"""Lists public void no-arg instance methods in Lucene test sources that look like tests but whose names do not
start with "test" (LuceneTestCase's JUnit3-style provider only runs methods named test*; JUnit4 @Test is the other
way in). Usage: python3 -I scan_misnamed_tests.py <lucene-checkout>"""
import os, re, sys
root = sys.argv[1]
pat = re.compile(r"^\s*public\s+void\s+((?:tes|tset|tets|tst|Test|TEST|Tset)[A-Za-z0-9_]*)\s*\(\s*\)")
for dp, dn, fn in os.walk(os.path.join(root, "lucene")):
    if "/src/test" not in dp.replace("\\", "/"):
        continue
    for f in fn:
        if not f.endswith(".java"):
            continue
        p = os.path.join(dp, f)
        lines = open(p, encoding="utf-8", errors="replace").read().splitlines()
        for i, l in enumerate(lines):
            m = pat.match(l)
            if m and not m.group(1).startswith("test"):
                annotated = any("@Test" in lines[j] for j in range(max(0, i - 3), i))
                print(f"{os.path.relpath(p, root)}:{i + 1}\t{m.group(1)}\t{'@Test (runs)' if annotated else 'NOT RUN'}")

#!/usr/bin/env python3
"""Static census: which analysis factories of a Lucene checkout have no leftover-argument
check ("Unknown parameter") in their Map<String,String> constructor or in a superclass constructor it calls?

Model element -> Java line (branch_11x b98f116b3179):
  the check            -> e.g. ASCIIFoldingFilterFactory.java:50-52  if (!args.isEmpty()) throw new IllegalArgumentException("Unknown parameters: " + args)
  the factory bases    -> lucene/core/src/java/org/apache/lucene/analysis/{TokenFilterFactory,TokenizerFactory,CharFilterFactory}.java
  the Map constructor  -> AbstractAnalysisFactory.java:96  protected AbstractAnalysisFactory(Map<String, String> args)

A class is a factory when its 'extends' chain (resolved by simple class name over all main-source files of the
checkout) ends in one of the three bases. It is reported as CHECKS when its own source or a superclass on that chain
(below the three bases) holds the text 'Unknown parameter' or an 'args.isEmpty()' test; otherwise as NO CHECK.
Abstract classes are listed apart (they cannot be built). usage: static_scan.py <lucene checkout>
"""
import os, re, sys
root = sys.argv[1]
BASES = {"TokenFilterFactory", "TokenizerFactory", "CharFilterFactory"}
cls = {}
import glob
srcdirs = sorted(glob.glob(os.path.join(root, "lucene", "*", "src", "java")) + glob.glob(os.path.join(root, "lucene", "analysis", "*", "src", "java")))
walk = [x for sd in srcdirs for x in os.walk(sd)]
for d, _, fs in walk:
    for f in fs:
        if not f.endswith(".java"):
            continue
        p = os.path.join(d, f)
        s = open(p, encoding="utf-8").read()
        m = re.search(r"\b(?:public\s+)?(?:(abstract|final)\s+)?class\s+" + re.escape(f[:-5]) + r"\b(?:<[^>{]*>)?\s+extends\s+([A-Za-z0-9_.]+)", s)
        if not m:
            continue
        cls[f[:-5]] = dict(path=os.path.relpath(p, root), abstract=(m.group(1) == "abstract"), sup=m.group(2).split(".")[-1],
                           check=("Unknown parameter" in s) or bool(re.search(r"!\s*args\.isEmpty\(\)", s)),
                           mapctor=bool(re.search(r"\(\s*(?:final\s+)?Map<String,\s*String>\s+\w+\s*\)", s)))
def chain(n):
    out = []
    while n in cls and n not in BASES and n not in out:  # 'n not in out': a class named like its superclass ends the walk
        out.append(n)
        n = cls[n]["sup"]
    return out, n
fact = {}
for n in cls:
    c, top = chain(n)
    if top in BASES and n not in BASES:
        fact[n] = (c, top)
checks = sorted(n for n, (c, _) in fact.items() if not cls[n]["abstract"] and any(cls[x]["check"] for x in c))
nocheck = sorted(n for n, (c, _) in fact.items() if not cls[n]["abstract"] and not any(cls[x]["check"] for x in c))
abstract = sorted(n for n in fact if cls[n]["abstract"])
print("checkout:", root)
print("concrete factory classes (extends chain ends in TokenFilterFactory, TokenizerFactory or CharFilterFactory):", len(checks) + len(nocheck))
print("  with a leftover-argument check in the class or a superclass of the chain:", len(checks))
print("  WITHOUT one:", len(nocheck))
for n in nocheck:
    print("    NO CHECK  %-40s %s  (chain: %s -> %s; has a Map constructor: %s)" % (n, cls[n]["path"], " -> ".join(fact[n][0]), fact[n][1], cls[n]["mapctor"]))
print("abstract factory bases (not counted):", len(abstract))
for n in abstract:
    print("    abstract  %-40s %s  (own check: %s)" % (n, cls[n]["path"], cls[n]["check"]))
by_mod = {}
for n in checks + nocheck:
    mod = "/".join(cls[n]["path"].split("/")[1:3]) if cls[n]["path"].split("/")[1] == "analysis" else cls[n]["path"].split("/")[1]
    by_mod.setdefault(mod, [0, 0])[0 if n in checks else 1] += 1
print("per module (with check, without):", ", ".join("%s %d+%d" % (k, v[0], v[1]) for k, v in sorted(by_mod.items())))

#!/usr/bin/env python3
"""Bytecode static scan (method_primary): for every class in the given Lucene jars, disassemble equals(Object) and
hashCode() with javap and flag classes whose equals uses a primitive floating-point comparison (fcmpl/fcmpg/dcmpl/
dcmpg, which treats +0.0 and -0.0 as equal and NaN as unequal) while hashCode hashes floating-point bits
(Float.floatToIntBits / floatToRawIntBits / Float.hashCode / Double.doubleToLongBits / doubleToRawLongBits /
Double.hashCode, or Float.valueOf/Double.valueOf boxing into Objects.hash / Objects.hashCode).
Classes whose equals also calls Float.compare / Double.compare / floatToIntBits on some field are reported as
'mixed' (some fields compared by bits, others by ==). The scan over-approximates: a flagged class still needs a
public-API pair (+0.0 vs -0.0 accepted by the constructor) to be a reachable violation.
usage: bytecode_scan.py JAVAP OUT_TSV JAR...
"""
import re, subprocess, sys, zipfile

javap, out = sys.argv[1], sys.argv[2]
jars = sys.argv[3:]
FCMP = re.compile(r"\b(fcmpl|fcmpg|dcmpl|dcmpg)\b")
HBITS = re.compile(r"java/lang/(Float\.(floatToIntBits|floatToRawIntBits|hashCode)|Double\.(doubleToLongBits|doubleToRawLongBits|hashCode)|Float\.valueOf|Double\.valueOf)")
EQBITS = re.compile(r"java/lang/(Float\.(compare|floatToIntBits|floatToRawIntBits)|Double\.(compare|doubleToLongBits|doubleToRawLongBits))")
rows = []
for jar in jars:
    with zipfile.ZipFile(jar) as z:
        names = [n[:-6].replace("/", ".") for n in z.namelist() if n.endswith(".class") and "module-info" not in n]
    for i in range(0, len(names), 200):
        batch = names[i:i + 200]
        txt = subprocess.run([javap, "-J-XX:ActiveProcessorCount=2", "-c", "-p", "-cp", jar] + batch,
                             capture_output=True, text=True).stdout
        cls = None; meth = None; body = {}
        for line in txt.splitlines():
            m = re.match(r"^(?:Compiled from .*)$", line)
            mc = re.match(r"^(?:public |final |abstract |private |protected |static |sealed |non-sealed |strictfp )*(?:class|interface|enum|record) ([\w.$]+)", line)
            if mc:
                cls = mc.group(1); body.setdefault(cls, {}); meth = None; continue
            mm = re.match(r"^  (?:public |protected |private |final |synchronized |abstract )*(boolean equals\(java\.lang\.Object\)|boolean equalsTo\([\w.$]+\)|int hashCode\(\));", line)
            if mm and cls:
                meth = "equals" if "equals" in mm.group(1) else "hashCode"; body[cls][meth] = []; continue
            if re.match(r"^  \S", line) and not line.startswith("  Code:"):
                meth = None
            if meth and cls:
                body[cls][meth].append(line)
        for c, ms in body.items():
            if "equals" in ms and "hashCode" in ms:
                e = "\n".join(ms["equals"]); h = "\n".join(ms["hashCode"])
                if FCMP.search(e) and HBITS.search(h):
                    kind = "mixed" if EQBITS.search(e) else "pure"
                    rows.append((jar.rsplit("/", 1)[-1], c, kind, len(FCMP.findall(e))))
with open(out, "w") as f:
    f.write("jar\tclass\tequals_kind\tprimitive_fp_compares_in_equals\n")
    for r in rows:
        f.write("\t".join(map(str, r)) + "\n")
print(len(rows), "classes flagged ->", out)

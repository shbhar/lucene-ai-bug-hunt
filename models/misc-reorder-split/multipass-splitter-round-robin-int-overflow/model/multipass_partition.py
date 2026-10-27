"""
Z3 model of MultiPassIndexSplitter.split's partition
(lucene/misc/src/java/org/apache/lucene/misc/index/MultiPassIndexSplitter.java:83-108), exact Java 32-bit int
semantics (BitVec 32; Java '/' on non-negative ints = bvsdiv, Java '%' = bvsrem, '+'/'*' wrap).

File:line map (model element -> Java)
  maxDoc = input.maxDoc()                                      :84
  partLen = maxDoc / numParts                                  :85
  seq:  lo = partLen * i ; hi = lo + partLen                   :89-90
        delete j < lo ; delete j >= hi unless i == numParts-1  :92-100
  round-robin: delete j unless (j + numParts - i) % numParts == 0   :103-107
Contract: split() javadoc (:61-69): the source index is split into the output parts, i.e. every document lands in
exactly one part (plan contract MultiPassIndexSplitter.java:70, paraphrase).
Preconditions (documented limits): 2 <= maxDoc <= IndexWriter.MAX_DOCS = 2^31-129, 0 <= j < maxDoc.

numParts is a constant per query and the part index is unrolled (i = 0..numParts-1); maxDoc and j are fully
symbolic 32-bit values. Obligations, each as a counterexample query (unsat = proved for that numParts):
  LOST: no part keeps j            DUP: two distinct parts keep j
A vacuity mutant (seq without the 'last part collects the rest' rule) must produce a LOST counterexample.
"""
import sys
import time
from z3 import BitVec, BitVecVal, And, Or, Not, Solver, SRem, sat, unsat

MAX_DOCS = 2**31 - 129


def bv(n):
    return BitVecVal(n, 32)


def kept_seq(j, i, maxDoc, n, last_rule=True):
    partLen = maxDoc / bv(n)
    lo = partLen * bv(i)
    hi = lo + partLen
    if last_rule and i == n - 1:
        return Not(j < lo)
    return Not(Or(j < lo, j >= hi))


def kept_rr(j, i, n):
    return SRem(j + bv(n) - bv(i), bv(n)) == 0


def check(label, kept_fn, n):
    j, maxDoc = BitVec("j", 32), BitVec("maxDoc", 32)
    res = []
    for kind in ("LOST", "DUP"):
        s = Solver()
        s.set("timeout", 120000)
        s.add(maxDoc >= 2, maxDoc <= bv(MAX_DOCS), j >= 0, j < maxDoc)
        ks = [kept_fn(j, i, maxDoc, n) for i in range(n)]
        if kind == "LOST":
            s.add(And([Not(k) for k in ks]))
        else:
            # at least two parts keep j: pairwise disjunction is quadratic, use a cardinality-free encoding
            # "some i keeps j and some i' > i keeps j" via prefix ORs
            pref = [ks[0]]
            for i in range(1, n):
                pref.append(Or(pref[-1], ks[i]))
            s.add(Or([And(pref[i - 1], ks[i]) for i in range(1, n)]))
        t0 = time.time()
        r = s.check()
        dt = time.time() - t0
        if r == sat:
            m = s.model()
            res.append(f"{kind}: COUNTEREXAMPLE j={m[j].as_signed_long()} maxDoc={m[maxDoc].as_signed_long()} ({dt:.1f}s)")
        elif r == unsat:
            res.append(f"{kind}: proved ({dt:.1f}s)")
        else:
            res.append(f"{kind}: {r} ({dt:.1f}s)")
    print(f"{label} numParts={n}: " + "; ".join(res), flush=True)


NPS = [int(x) for x in sys.argv[1:]] or [2, 3, 7, 64, 128, 129, 130, 131]
for n in NPS:
    check("seq", lambda j, i, m, n: kept_seq(j, i, m, n), n)
    check("round-robin", lambda j, i, m, n: kept_rr(j, i, n), n)
check("MUTANT seq without last-part rule (must find LOST)", lambda j, i, m, n: kept_seq(j, i, m, n, last_rule=False), 7)

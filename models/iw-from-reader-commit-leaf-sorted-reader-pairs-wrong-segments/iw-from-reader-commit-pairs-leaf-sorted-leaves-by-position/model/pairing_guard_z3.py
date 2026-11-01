"""which leaf orders survive the IndexWriter(from reader commit) constructor? (Z3, unbounded ints)

Model element -> Java line (main 645d4fe14a20):
  info(k) paired with leaves().get(k)            ReaderPool.java:95-103
  leaves().get(k) = segment p[k] (leaf sorter)    BaseCompositeReader.java:75-76
  guard 1: numDocs(leaf) <= maxDoc(info)          SegmentReader.java:136-138
  guard 2: liveDocs == null || liveDocs.length() == maxDoc(info)
           (liveDocs != null iff the leaf has deletions; liveDocs.length() == maxDoc(leaf))
                                                  SegmentReader.java:139-142
Segment i: maxDoc m[i] >= 1, deletions d[i] in [0, m[i]]; p is a permutation of 0..n-1.

Q_equal  (expect UNSAT): all guards hold and some segment is paired with a reader of a different maxDoc.
         => whenever the constructor succeeds, every segment got a reader of a segment with the same maxDoc.
Q_silent (expect SAT):   all guards hold and p is not the identity (a silent mispairing exists).
Q_loud   (expect UNSAT): p maps some segment to a reader of a different maxDoc and no guard fails.
         (same as Q_equal, stated as "smaller-into-larger or larger-into-smaller always throws somewhere")
"""
import sys
from z3 import And, Distinct, If, Implies, Int, Not, Or, Solver, sat, unsat


def sel(vals, idx):
    e = vals[-1]
    for j in range(len(vals) - 2, -1, -1):
        e = If(idx == j, vals[j], e)
    return e


def model(n):
    m = [Int(f"m{i}") for i in range(n)]
    d = [Int(f"d{i}") for i in range(n)]
    p = [Int(f"p{i}") for i in range(n)]
    base = []
    for i in range(n):
        base += [m[i] >= 1, d[i] >= 0, d[i] <= m[i], p[i] >= 0, p[i] < n]
    base.append(Distinct(*p))
    guards = []
    for k in range(n):
        ml, dl = sel(m, p[k]), sel(d, p[k])
        guards.append(And(ml - dl <= m[k], Implies(dl > 0, ml == m[k])))
    return m, d, p, base, guards


def check(n):
    out = {}
    m, d, p, base, guards = model(n)
    s = Solver(); s.add(*base, *guards, Or(*[sel(m, p[k]) != m[k] for k in range(n)])); out["Q_equal"] = s.check()
    s = Solver(); s.add(*base, *guards, Or(*[p[k] != k for k in range(n)])); r = s.check(); out["Q_silent"] = r
    wit = None
    if r == sat:
        mo = s.model(); wit = {"m": [mo.eval(x) for x in m], "d": [mo.eval(x) for x in d], "p": [mo.eval(x) for x in p]}
    s = Solver(); s.add(*base, Or(*[sel(m, p[k]) != m[k] for k in range(n)]), And(*guards)); out["Q_loud"] = s.check()
    return out, wit


ok = True
for n in range(2, 9):
    out, wit = check(n)
    good = out["Q_equal"] == unsat and out["Q_silent"] == sat and out["Q_loud"] == unsat
    ok &= good
    print(f"n={n}: Q_equal={out['Q_equal']} Q_silent={out['Q_silent']} Q_loud={out['Q_loud']} witness={wit} {'OK' if good else 'UNEXPECTED'}")

# vacuity: drop guard 1 -> Q_equal must become SAT (a smaller reader in a larger segment would then survive)
m, d, p, base, guards = model(3)
weak = [Implies(sel(d, p[k]) > 0, sel(m, p[k]) == m[k]) for k in range(3)]
s = Solver(); s.add(*base, *weak, Or(*[sel(m, p[k]) != m[k] for k in range(3)]))
r = s.check(); print("vacuity (guard 1 dropped, n=3): Q_equal =", r, "(expect sat)"); ok &= r == sat
sys.exit(0 if ok else 1)

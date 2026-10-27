# Z3 proof (exact Java int semantics via 32-bit bit-vectors) of the partition arithmetic in
# IndexSearcher.slicesWithSegmentPartitions (Lucene main @ ea57360912ed, IndexSearcher.java:392-417):
#   numSlices = Math.min(5, Math.ceilDiv(maxDoc, maxDocsPerSlice));
#   numDocs   = maxDoc / numSlices;
#   maxDocId = numDocs; minDocId = 0;
#   for (i = 0; i < numSlices - 1; i++) { part [minDocId, maxDocId); minDocId = maxDocId; maxDocId += numDocs; }
#   last part [minDocId, maxDoc)
# Preconditions: 1 <= maxDoc <= IndexWriter.MAX_DOCS (2^31 - 129), maxDocsPerSlice >= 1, maxDoc > maxDocsPerSlice.
# Obligations (for each numSlices value 2..5, the loop is unrolled):
#   O1 no int overflow in ceilDiv, division, or the maxDocId += numDocs accumulation
#   O2 every partition satisfies LeafReaderContextPartition's checks: 0 <= min < max, min < maxDoc
#   O3 partitions are contiguous and tile [0, maxDoc) exactly (every doc in exactly one partition)
#   O4 the int sum currentSliceNumDocs (IndexSearcher.java:425) cannot overflow: it is bounded by the
#      reader's total maxDoc, which BaseCompositeReader limits to IndexWriter.MAX_DOCS
from z3 import *

MAX_DOCS = 2**31 - 129
maxDoc, mdps = BitVecs('maxDoc mdps', 32)
pre = And(maxDoc >= 1, maxDoc <= MAX_DOCS, mdps >= 1, maxDoc > mdps)

# Math.ceilDiv(x, y) for positive ints: q = x / y; if (x % y != 0) q + 1 (no overflow for y >= 1)
q = maxDoc / mdps
ceil = If(SRem(maxDoc, mdps) != 0, q + 1, q)
numSlices = If(ceil < 5, ceil, BitVecVal(5, 32))
numDocs = maxDoc / numSlices

results = {}
def prove(name, f):
    s = Solver()
    s.set('timeout', 600000)
    s.add(Not(f))
    r = s.check()
    results[name] = str(r)
    print(name, '->', 'PROVED (unsat)' if r == unsat else r, s.model() if r == sat else '')

# O1a: ceilDiv quotient + 1 does not overflow (q < MAX_INT)
prove('O1a ceilDiv no overflow', Implies(pre, q < 0x7fffffff))
prove('O1b numSlices in [2,5]', Implies(pre, And(numSlices >= 2, numSlices <= 5)))
prove('O1c numDocs >= 1', Implies(pre, numDocs >= 1))
for k in range(2, 6):
    pk = And(pre, numSlices == k)
    lo = BitVecVal(0, 32); hi = numDocs
    obl = []
    wide = []  # same arithmetic in 64-bit to detect wrap
    lo64 = BitVecVal(0, 64); hi64 = ZeroExt(32, numDocs)
    for i in range(k - 1):
        obl += [lo >= 0, lo < hi, lo < maxDoc]
        wide += [ZeroExt(32, hi) == hi64]
        lo, hi = hi, hi + numDocs
        lo64, hi64 = hi64, hi64 + ZeroExt(32, numDocs)
    # last partition [lo, maxDoc)
    obl += [lo >= 0, lo < maxDoc]
    prove(f'O2/O3 numSlices={k}: partitions valid, contiguous, tile [0,maxDoc)', Implies(pk, And(*obl)))
    prove(f'O1d numSlices={k}: no overflow in maxDocId += numDocs', Implies(pk, And(*wide)))

# O4: sum of segment sizes in a group <= total reader maxDoc <= MAX_DOCS < 2^31
a, b = BitVecs('a b', 32)
prove('O4 two-segment int sum no overflow under MAX_DOCS', Implies(And(a >= 0, b >= 0, ZeroExt(32, a) + ZeroExt(32, b) <= MAX_DOCS), And(a + b >= 0, ZeroExt(32, a + b) == ZeroExt(32, a) + ZeroExt(32, b))))

# vacuity: the precondition is satisfiable, and a wrong obligation (numDocs * numSlices == maxDoc) is refuted
s = Solver(); s.add(pre); print('vacuity: precondition satisfiable ->', s.check())
s = Solver(); s.add(pre, Not(numDocs * numSlices == maxDoc)); r = s.check(); print('mutant obligation "last partition same size" ->', r, '(sat expected: remainder goes to the last partition)', s.model() if r == sat else '')
import json, sys
print(json.dumps(results))

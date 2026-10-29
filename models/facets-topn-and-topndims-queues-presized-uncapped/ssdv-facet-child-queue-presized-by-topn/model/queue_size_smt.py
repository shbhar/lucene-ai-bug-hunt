"""solver queries for facet top-N queue sizing (z3, exact 32-bit Java int semantics).

Model element -> Java line (Lucene main-a9779ef @ a9779ef3c193):
  validateTopN(topN): topN > 0                     Facets.java:80-84
  PriorityQueue(maxSize) throws IAE iff
     maxSize < 0 || maxSize >= MAX_ARRAY_LENGTH    PriorityQueue.java:117-124
  heap slots allocated = maxSize + 1 (maxSize>0)  PriorityQueue.java:129-134
  MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 16        (message: "< 2147483631")
  SSDV child queue: maxSize = topN                 AbstractSortedSetDocValueFacetCounts.java:325
  SSDV dim queue:   maxSize = topNDims             AbstractSortedSetDocValueFacetCounts.java:153-156
  taxo dim queue:   maxSize = topNDims             TaxonomyFacets.java:610-613
  taxo child queue: maxSize = min(size, topN)      TaxonomyFacets.java:251 (control, capped)
  SSDV label count: valueCount < Integer.MAX_VALUE DefaultSortedSetDocValuesReaderState.java:95-99
  fix: maxSize = min(topN, L), L = state.getSize() / taxoReader.getSize()

Queries
  Q1 stock: exists topN accepted by validateTopN with IAE  -> SAT, and the exact IAE set is
     [2147483631, 2147483647] (17 values), proved by two UNSAT side queries.
  Q2 stock: exists accepted topN with slots > 64 * (L+1) for a small field (L <= 12) -> SAT
     (allocation proportional to topN, not to labels).
  Q3 fixed: for all accepted topN and all L in [0, MAX_ARRAY_LENGTH - 1], no IAE and
     slots <= L + 1 (or 2 when L == 0)  -> UNSAT of the negation.
  Q4 fixed result equivalence premise: if the queue holds at most L entries anyway (a path has at
     most L children, a reader at most L dims), min(topN, L) >= the number of inserted entries, so
     insertWithOverflow never evicts -> UNSAT of "evicts while count <= L".
"""

from z3 import BitVec, BitVecVal, If, And, Or, Not, Solver, ULT, sat, unsat

MAX = 2**31 - 1
MAL = MAX - 16  # ArrayUtil.MAX_ARRAY_LENGTH


def bv(x):
    return BitVecVal(x, 32)


def pq_iae(max_size):
    return Or(max_size < 0, max_size >= bv(MAL))


def slots(max_size):
    # heapSize = maxSize == 0 ? 2 : maxSize + 1 (as 64-bit to avoid wrap in the model)
    from z3 import SignExt
    m = SignExt(32, max_size)
    return If(max_size == 0, BitVecVal(2, 64), m + 1)


def check(name, s, expect):
    r = s.check()
    ok = r == expect
    print(f"{name}: {r} (expected {expect}) {'OK' if ok else 'MISMATCH'}")
    if r == sat:
        print("   model:", s.model())
    return ok


def main():
    allok = True
    topN = BitVec("topN", 32)
    L = BitVec("L", 32)
    accepted = topN > 0

    # Q1 stock IAE reachable
    s = Solver(); s.add(accepted, pq_iae(topN)); allok &= check("Q1 stock IAE reachable", s, sat)
    # Q1a exact set: IAE and topN < MAL impossible
    s = Solver(); s.add(accepted, pq_iae(topN), topN < bv(MAL)); allok &= check("Q1a IAE below 2147483631", s, unsat)
    # Q1b: every topN >= MAL throws
    s = Solver(); s.add(accepted, topN >= bv(MAL), Not(pq_iae(topN))); allok &= check("Q1b no IAE at >= 2147483631", s, unsat)

    # Q2 stock allocation proportional to topN on a 12-label field
    from z3 import SignExt
    s = Solver(); s.add(accepted, Not(pq_iae(topN)), L >= 0, L <= 12,
                        slots(topN) > 64 * (SignExt(32, L) + 1))
    allok &= check("Q2 stock slots >> labels", s, sat)

    # Q3 fixed: maxSize = min(topN, L)
    fixed = If(topN < L, topN, L)
    s = Solver(); s.add(accepted, L >= 0, L < bv(MAL),
                        Or(pq_iae(fixed), slots(fixed) > If(L == 0, BitVecVal(2, 64), SignExt(32, L) + 1)))
    allok &= check("Q3 fixed: IAE or slots > L+1", s, unsat)

    # Q4 fixed never evicts when the number of candidates c <= L (and topN >= c)
    c = BitVec("c", 32)
    s = Solver(); s.add(accepted, L >= 0, L < bv(MAL), c >= 0, c <= L, topN >= c,
                        c > fixed)  # an eviction happens only when more than maxSize entries arrive
    allok &= check("Q4 fixed evicts although topN >= candidates", s, unsat)

    print("ALL OK" if allok else "SOME QUERY MISMATCHED")


if __name__ == "__main__":
    main()


def vacuity():
    """Mutant: the 'fixed' size without the cap (maxSize = topN) must make Q3 SAT."""
    from z3 import SignExt
    topN = BitVec("topN", 32); L = BitVec("L", 32)
    s = Solver(); s.add(topN > 0, L >= 0, L < bv(MAL),
                        Or(pq_iae(topN), slots(topN) > If(L == 0, BitVecVal(2, 64), SignExt(32, L) + 1)))
    return check("Q3-mutant (cap removed) must be sat", s, sat)


if __name__ == "__main__":
    vacuity()

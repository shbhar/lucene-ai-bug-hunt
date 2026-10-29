"""Z3 model of FrequentTermsSpanBooleanQueryRewrite queue sizing (stock vs lazily sized fix).

Mapping (model element -> Java line, Lucene main a9779ef3c193; branch_10_6 4ae946e5a9b is the same logic with
`new PriorityQueue<>(maxSize) { lessThan }`):
  maxSize                 -> SpanMultiTermQueryWrapper.java:306-307 (constructor stores the caller's int, no check)
  stock capacity = maxSize-> SpanMultiTermQueryWrapper.java:312-314 (getTopLevelBuilder: PriorityQueue.usingComparator(maxSize, order)),
                             called by ScoringRewrite.java:107 BEFORE collectTerms (ScoringRewrite.java:109)
  PQ guard / heap slots   -> PriorityQueue.java:117-130 (maxSize == 0 -> 2 slots; maxSize < 0 or >= MAX_ARRAY_LENGTH -> IAE;
                             else maxSize + 1 slots, allocated at PriorityQueue.java:134)
  MAX_ARRAY_LENGTH        -> ArrayUtil.MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 16 = 2147483631 (64-bit JVM header 16)
  n (collected terms)     -> ScoringRewrite.java:111 col.terms.size(); a BytesRefHash, so n < MAX_ARRAY_LENGTH
  op(k), k = 1..n         -> ScoringRewrite.java:116-121 calls addClause for each term in sorted order;
                             addClause -> insertWithOverflow (SpanMultiTermQueryWrapper.java:338), which is add() while
                             size < capacity (PriorityQueue.java:232-234) and overflow handling afterwards
  fix capacity            -> fix/src: capacity = n if n <= maxSize (queue built in build() from the pending list),
                             else maxSize (queue created when the (maxSize+1)-th term arrives, i.e. once n > maxSize is known)

Queries (32-bit two's complement bit-vectors, exact Java int semantics):
  Q1 SAT  : stock throws IAE although only n <= 3 terms are collected (witness expected: maxSize >= 2147483631).
  Q2 SAT  : stock allocates more than 1,000,000 heap slots for n <= 3 collected terms (witness: any large maxSize).
  Q3 UNSAT: fix throws IAE for some maxSize >= 0 and 0 <= n < MAX_ARRAY_LENGTH.
  Q4 UNSAT: fix allocates more than max(n + 1, 2) heap slots.
  Q5 UNSAT: for some k in [1, n] the stock and the fix take a different branch (add vs overflow) in insertWithOverflow,
            restricted to the inputs where the stock does not throw (0 <= maxSize < MAX_ARRAY_LENGTH). Since both run the
            same add/updateTop code on the same element sequence, equal branch sequences give equal heaps, so the kept
            set and the pop order (the SpanOrQuery clause order) are identical: the fix preserves every result the stock
            code returns.
  Q6 SAT  : vacuity probe: the same query as Q5 with a wrong fix capacity (n - 1) must find a divergence.
"""
import sys
from z3 import BitVec, BitVecVal, Solver, And, Or, Not, If, ULT, sat, unsat

MAX_ARRAY_LENGTH = 2147483631
W = 32
M = BitVecVal(MAX_ARRAY_LENGTH, W)
ZERO = BitVecVal(0, W)
ONE = BitVecVal(1, W)
TWO = BitVecVal(2, W)


def pq_throws(cap):
    # PriorityQueue.java:117-125
    return And(cap != ZERO, Or(cap < ZERO, cap >= M))


def pq_slots(cap):
    # PriorityQueue.java:117-130 (only meaningful when not pq_throws)
    return If(cap == ZERO, TWO, cap + ONE)


def fix_cap(max_size, n):
    return If(n <= max_size, n, max_size)


results = []


def check(name, expect, *constraints):
    s = Solver()
    s.set("timeout", 60000)
    s.add(*constraints)
    r = s.check()
    line = f"{name}: {r} (expected {expect})"
    if r == sat:
        m = s.model()
        line += "  model: " + ", ".join(f"{d.name()}={m[d].as_signed_long()}" for d in m.decls())
    ok = (expect == "sat" and r == sat) or (expect == "unsat" and r == unsat)
    results.append(ok)
    print(line + ("" if ok else "   <-- UNEXPECTED"))


max_size = BitVec("maxSize", W)
n = BitVec("n", W)
k = BitVec("k", W)
legal_n = And(n >= ZERO, n < M)

# Q1: stock IAE with a tiny term set
check("Q1 stock throws IAE with n<=3", "sat", legal_n, n <= BitVecVal(3, W), max_size >= ZERO, pq_throws(max_size))

# Q2: stock allocates > 1e6 slots for n <= 3 (no throw)
check("Q2 stock allocates >1e6 slots for n<=3", "sat", legal_n, n <= BitVecVal(3, W),
      Not(pq_throws(max_size)), ULT(BitVecVal(1_000_000, W), pq_slots(max_size)))

# Q3: fix never throws for maxSize >= 0
check("Q3 fix throws for maxSize>=0", "unsat", legal_n, max_size >= ZERO, pq_throws(fix_cap(max_size, n)))

# Q4: fix allocation bounded by the collected terms
bound = If(n + ONE >= TWO, n + ONE, TWO)
check("Q4 fix allocates > max(n+1,2) slots", "unsat", legal_n, max_size >= ZERO,
      Not(pq_throws(fix_cap(max_size, n))), ULT(bound, pq_slots(fix_cap(max_size, n))))

# Q5: identical add/overflow branch for every k in [1, n] where the stock does not throw
# insertWithOverflow takes the add() branch iff size < capacity; before the k-th insert, size = min(k-1, capacity)
def takes_add(cap, kk):
    return (kk - ONE) < cap


stock_ok = And(max_size >= ZERO, max_size < M)
check("Q5 fix diverges from stock on some insert", "unsat", legal_n, stock_ok, k >= ONE, k <= n,
      takes_add(max_size, k) != takes_add(fix_cap(max_size, n), k))

# Q6: vacuity probe (wrong capacity n-1 when n <= maxSize) must diverge
bad_cap = If(n <= max_size, n - ONE, max_size)
check("Q6 vacuity: wrong fix capacity diverges", "sat", legal_n, stock_ok, k >= ONE, k <= n,
      takes_add(max_size, k) != takes_add(bad_cap, k))

print(f"summary: {sum(results)}/{len(results)} as expected")
sys.exit(0 if all(results) else 1)

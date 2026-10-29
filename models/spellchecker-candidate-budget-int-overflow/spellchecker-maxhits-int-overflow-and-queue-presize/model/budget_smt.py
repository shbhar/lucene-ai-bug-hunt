# Z3 bit-vector model of the spell checkers' candidate budgets (Java int = 32-bit two's complement).
#
# file:line mapping (Lucene main-a9779ef @ a9779ef3c193, paths under lucene/suggest/src/java/org/apache/lucene/search/spell/)
#   insp  = n * m                         DirectSpellChecker.java:359  int inspections = numSug * maxInspections;
#   pass1 (ed = 1) with budget insp       DirectSpellChecker.java:362  suggestSimilar(term, inspections, ir, docfreq, 1, ...)
#   pass2 guard                           DirectSpellChecker.java:363  if (maxEdits > 1 && terms.size() < inspections)
#   NPE site (budget <= 0, >= 1 term      DirectSpellChecker.java:435  if (stQueue.size() >= numSug && score <= stQueue.peek().boost)
#     enumerated by the FuzzyTermsEnum)   (java.util.PriorityQueue.peek() returns null on an empty queue)
#   queue bound                           DirectSpellChecker.java:473  st = (stQueue.size() > numSug) ? stQueue.poll() : ...
#   final trim to numSug                  DirectSpellChecker.java:387-391
#   maxHits = 10 * n                      SpellChecker.java:361        int maxHits = 10 * numSug;
#   IAE when capped maxHits <= 0          IndexSearcher.java:588-599 (cappedNumHits = min(numHits, max(1, maxDoc)))
#                                         TopScoreDocCollectorManager.java:62-64 ("numHits must be > 0")
#   queue pre-size                        SpellChecker.java:366 new SuggestWordQueue(numSug, ...) -> PriorityQueue.java:113-134
#                                         heap = new Object[maxSize + 1]; IAE if maxSize >= ArrayUtil.MAX_ARRAY_LENGTH
#
# Abstraction of DirectSpellChecker's result size (default INTERNAL_LEVENSHTEIN distance, so score == boost, and every
# ed-1 candidate outranks every ed-2 candidate):
#   e1 = number of terms the ed-1 FuzzyTermsEnum enumerates (before the docfreq / accuracy / exact-match filters)
#   f1 = filtered ed-1 candidates, f2 = filtered ed<=maxEdits candidates (f1 <= f2, f1 <= e1)
#   insp <= 0 : NPE if e1 >= 1; otherwise pass 1 is empty and the pass-2 guard 0 < insp is false -> empty result
#   insp >  0 : |result| = min(n, min(insp, f2))   (pass 2 runs iff min(insp, f1) < insp, i.e. f1 < insp)
#   reference (unbounded budget): |result| = min(n, f2)
# Every claim below is checked by z3 (sat = witness found; unsat = theorem proved over ALL 32-bit inputs in range).
import sys
from z3 import BitVec, BitVecVal, Solver, And, Or, Not, If, ULT, sat, unsat, SignExt, Extract

INT_MAX = 2**31 - 1
W = 32

def bv(v):
    return BitVecVal(v, W)

def smin(a, b):
    return If(a < b, a, b)

results = []

def check(name, constraints, expect, show=None):
    s = Solver()
    s.set("timeout", 600000)
    s.add(*constraints)
    r = s.check()
    ok = (r == expect)
    line = f"{'OK  ' if ok else 'FAIL'} {name}: {r} (expected {expect})"
    if r == sat and show is not None:
        m = s.model()
        line += "  witness: " + ", ".join(f"{str(v)}={m.eval(v, model_completion=True).as_signed_long()}" for v in show)
    print(line)
    results.append((name, str(r), str(expect), ok))
    return r

n = BitVec('numSug', W)
m = BitVec('maxInspections', W)
e1 = BitVec('e1', W)
f2 = BitVec('f2', W)
insp = n * m   # bvmul == Java int multiplication

legal = [n >= 1, m >= 1]                     # signed comparisons (z3 >= on BitVec is signed)
cands = [e1 >= 0, f2 >= 0, f2 <= 1000000, e1 <= 1000000]

def dsc_size(insp_e):
    # result size when insp > 0
    return smin(n, smin(insp_e, f2))

ref_size = smin(n, f2)

print("== DirectSpellChecker: inspections = numSug * maxInspections (DirectSpellChecker.java:359)")
# T1: with maxInspections = Integer.MAX_VALUE the budget is <= 0 exactly for even numSug.
check("T1 m=MAX: insp<=0 <-> numSug even (negation unsat)",
      legal + [m == bv(INT_MAX), Not((insp <= 0) == (Extract(0, 0, n) == 0))], unsat)
# T1b: for even numSug the budget is exactly -numSug; for odd it is 2^31 - numSug.
check("T1b m=MAX: insp == (even ? -n : 2^31-n) (negation unsat)",
      legal + [m == bv(INT_MAX),
               Not(insp == If(Extract(0, 0, n) == 0, -n, bv(2**31) - n))], unsat)
# T2: with m = MAX an odd numSug <= 2^30 never truncates (budget 2^31 - numSug >= numSug).
#     (model fix 1: the first version claimed this for every odd numSug; z3 refuted it for odd numSug > 2^30.)
check("T2 m=MAX, numSug odd <= 2^30: insp >= numSug (negation unsat)",
      legal + [m == bv(INT_MAX), Extract(0, 0, n) == 1, n <= bv(2**30), Not(insp >= n)], unsat)
check("T2b m=MAX, odd numSug > 2^30 can truncate (sat expected)",
      legal + [m == bv(INT_MAX), Extract(0, 0, n) == 1, insp > 0, insp < n], sat, show=[n, insp])
# W1: NPE witness with the natural setting m = MAX: an even numSug and >= 1 enumerated ed-1 term.
check("W1 m=MAX NPE witness (numSug<=10, e1>=1)",
      legal + cands + [m == bv(INT_MAX), n <= 10, insp <= 0, e1 >= 1], sat, show=[n, insp])
# W2: SILENT empty result with m = MAX: even numSug, no ed-1 term at all, but ed-2 candidates exist.
check("W2 m=MAX silent empty witness (e1=0, f2>=1)",
      legal + cands + [m == bv(INT_MAX), n <= 10, insp <= 0, e1 == 0, f2 >= 1, ref_size >= 1], sat,
      show=[n, insp, f2])
# W3: silent truncation with the DEFAULT maxInspections = 5.
check("W3 m=5 silent truncation witness (0 < insp < min(n, f2), f2 = 10)",
      legal + cands + [m == bv(5), f2 == bv(10), insp > 0, dsc_size(insp) < ref_size], sat, show=[n, insp])
# T3: no overflow -> stock result size equals the reference (sanity / vacuity guard of the abstraction).
nn = SignExt(32, n); mm = SignExt(32, m)
check("T3 no-overflow (n*m <= MAX as long): size == reference (negation unsat)",
      legal + cands + [nn * mm <= BitVecVal(INT_MAX, 64), Not(dsc_size(insp) == ref_size)], unsat)
# smallest numSug that hits each misbehaviour at the default m = 5
for lab, extra in [("first NPE/empty numSug at m=5", [insp <= 0]),
                   ("first truncating numSug at m=5 (f2=10)", [insp > 0, insp < smin(n, bv(10))])]:
    lo, hi = 1, INT_MAX
    # binary search for the minimal witness using z3 (monotone search over the bound)
    best = None
    while lo <= hi:
        mid = (lo + hi) // 2
        s = Solver(); s.add(*legal, m == bv(5), n <= bv(mid), *extra)
        if s.check() == sat:
            best = s.model().eval(n).as_signed_long(); hi = mid - 1
        else:
            lo = mid + 1
    print(f"INFO {lab}: {best}")

print("== Fix: inspections = (int) Math.min((long) numSug * maxInspections, Integer.MAX_VALUE)")
p64 = nn * mm
insp_fix = If(p64 > BitVecVal(INT_MAX, 64), bv(INT_MAX), Extract(31, 0, p64))
check("F1 fix: insp_fix >= numSug and >= 1 for all numSug, m >= 1 (negation unsat)",
      legal + [Not(And(insp_fix >= n, insp_fix >= 1))], unsat)
check("F2 fix: size == reference for all inputs (negation unsat)",
      legal + cands + [Not(dsc_size(insp_fix) == ref_size)], unsat)
check("F3 fix preserves stock value whenever no overflow (negation unsat)",
      legal + [p64 <= BitVecVal(INT_MAX, 64), Not(insp_fix == insp)], unsat)
# product of two positive ints fits in a signed long: no 64-bit overflow in the fix (vacuity of F1-F3)
check("F4 (long) n*m never overflows 64 bits for n,m in [1,MAX] (negation unsat)",
      legal + [Not(And(p64 >= BitVecVal(1, 64), p64 <= BitVecVal(INT_MAX * INT_MAX, 64)))], unsat)
# mutation / vacuity probe: a WRONG fix (int product, then min) must be refuted
bad_fix = smin(insp, bv(INT_MAX))
check("M1 mutant fix (int product then min) is refuted (sat expected)",
      legal + cands + [Not(dsc_size(bad_fix) == ref_size), bad_fix > 0], sat, show=[n, m])

print("== SpellChecker: maxHits = 10 * numSug (SpellChecker.java:361)")
maxHits = bv(10) * n
maxDoc = BitVec('maxDoc', W)
capped = smin(maxHits, If(maxDoc > 1, maxDoc, bv(1)))
check("S1 IAE witness: capped maxHits <= 0 for some numSug >= 1",
      [n >= 1, maxDoc >= 1, capped <= 0], sat, show=[n, maxHits])
check("S2 capped maxHits <= 0 <-> maxHits <= 0 (negation unsat)",
      [n >= 1, maxDoc >= 1, Not((capped <= 0) == (maxHits <= 0))], unsat)
check("S3 no IAE/truncation below 214748365 (negation unsat)",
      [n >= 1, n <= bv(214748364), Not(maxHits == bv(10) * n), ], unsat)
check("S3b numSug <= 214748364 -> maxHits == 10*numSug exactly, > 0 (negation unsat)",
      [n >= 1, n <= bv(214748364), Not(And(maxHits > 0, SignExt(32, maxHits) == BitVecVal(10, 64) * nn))], unsat)
check("S4 silent truncation witness: 0 < maxHits < numSug",
      [n >= 1, maxHits > 0, maxHits < n], sat, show=[n, maxHits])
# minimal numSug for each SpellChecker misbehaviour
for lab, extra in [("first IAE numSug", [maxHits <= 0]), ("first truncating numSug (0<maxHits<n)", [maxHits > 0, maxHits < n])]:
    lo, hi = 1, INT_MAX; best = None
    while lo <= hi:
        mid = (lo + hi) // 2
        s = Solver(); s.add(n >= 1, n <= bv(mid), *extra)
        if s.check() == sat:
            best = s.model().eval(n).as_signed_long(); hi = mid - 1
        else:
            lo = mid + 1
    note = ("IndexSearcher throws before the queue is built" if "IAE" in lab else
            f"SuggestWordQueue pre-size >= {(best + 1) * 4 / 2**30:.2f} GiB of references (4-byte refs) before the truncated hits are read")
    print(f"INFO {lab}: {best} -> {note}")

bad = [r for r in results if not r[3]]
print(f"checks={len(results)} unexpected={len(bad)}")
sys.exit(1 if bad else 0)

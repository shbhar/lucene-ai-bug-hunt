"""SMT model (Z3, 32-bit bit-vectors = exact Java int semantics) of how Lookup.lookup's `num` flows
through BlendedInfixSuggester and AnalyzingSuggester on Lucene main a9779ef3c193.

Model element -> Java line (paths relative to the Lucene checkout):
  P  = wrap32(num * numFactor)      lucene/suggest/.../analyzing/BlendedInfixSuggester.java:199
                                    (super.lookup(key, contextQuery, num * numFactor, ...))
  P <= 0 -> IllegalArgumentException  lucene/core/.../search/TopFieldCollectorManager.java:62-65
                                    (reached from AnalyzingInfixSuggester.java:739)
  hits = min(P, M)                  TopFieldCollector keeps the P best of the M matching entries
  A  = P / numFactor  (Java int /)  BlendedInfixSuggester.java:227 (int actualNum = num / numFactor)
  A == 0 and hits >= 1 -> NoSuchElementException
                                    BlendedInfixSuggester.java:297-299 (results.size() >= 0, results.first()
                                    on an empty TreeSet)
  returned = min(A, M)              boundedTreeAdd keeps at most A results (:294-306)
  contract: returned == min(num, M) Lookup.java:190 '@param num maximum number of results to return'

  Q  = wrap32(num * k)              lucene/suggest/.../analyzing/AnalyzingSuggester.java:786
                                    (k = maxAnalyzedPathsForOneInput, AnalyzingSuggester.java:484)
  prune when queue.size() == Q      lucene/core/.../util/fst/Util.java:215, :248 (never fires for Q < 0)
  isComplete = wrap32(rejectCount + topN) <= Q
                                    Util.java:409; asserted at AnalyzingSuggester.java:824

Documented limits used as preconditions: num >= 1 (num is the maximum number of results; num <= 0 is a
caller error, LFV-070 covers num = 0), numFactor >= 1 (constructor argument, no validation), M <= 2^31-129
(IndexWriter.MAX_DOCS), k >= 1.

Queries (each prints sat/unsat and, for sat, a witness that the Java harness re-executes on the real jar):
  S1 safe region: for 1 <= num <= floor(MAX/numFactor), P == num*numFactor and A == num: integer arithmetic for
     every numFactor >= 1, exact 32-bit bit-vectors for 11 concrete numFactor values (expect unsat of the negation)
  S2 the bound is tight: num = floor(MAX/numFactor) + 1 wraps                    (expect unsat of negation)
  W1 IAE witness (P <= 0), smallest num, numFactor = 10 and 1000
  W2 NoSuchElementException witness (0 < P < numFactor), smallest num
  W3 silent truncation witness: P > 0, 1 <= A < min(num, M), M = 8
  W4 AnalyzingSuggester: Q >= 0 and Q < topN (queue pruned below topN), smallest num for k = 2, 3
  W5 AnalyzingSuggester: num = Integer.MAX_VALUE makes isComplete false for every k >= 2 (assert fires under -ea)
"""

import z3

MAX = 2**31 - 1


def bv(x):
    return z3.BitVecVal(x, 32)


def smallest(s, var, lo=1):
    """Binary-search the smallest model value of var (signed) satisfying s."""
    if s.check() != z3.sat:
        return None
    best = s.model()[var].as_signed_long()
    hi = best
    while lo < hi:
        mid = (lo + hi) // 2
        s.push()
        s.add(var <= bv(mid))
        if s.check() == z3.sat:
            hi = s.model()[var].as_signed_long()
            best = hi
        else:
            lo = mid + 1
        s.pop()
    return best


def main():
    num = z3.BitVec("num", 32)
    f = z3.BitVec("numFactor", 32)
    P = num * f  # wraps exactly like Java int
    A = P / f  # bit-vector signed division truncates toward zero, as Java int division does

    out = []

    # S1: safe region. General numFactor: unbounded integer arithmetic (the bit-vector form is a 32x32
    # nonlinear product that did not finish in 120 s); per concrete numFactor: exact 32-bit bit-vectors.
    ni, nf = z3.Ints("num_i numFactor_i")
    s = z3.Solver()
    s.set("timeout", 60000)
    s.add(nf >= 1, ni >= 1, ni <= MAX / nf, ni * nf > MAX)
    r = s.check()
    out.append(("S1-int for every numFactor >= 1: 1 <= num <= MAX/numFactor implies num*numFactor <= MAX (integer arithmetic)",
                "PROVED (negation unsat)" if r == z3.unsat else f"{r} {s.model() if r == z3.sat else ''}"))
    for fac in (1, 2, 3, 5, 7, 10, 100, 1000, 10**4, 10**5, 10**6):
        s = z3.Solver()
        s.add(f == bv(fac), num >= 1, num <= bv(MAX // fac))
        wide = z3.SignExt(32, num) * z3.SignExt(32, f)
        s.add(z3.Or(z3.SignExt(32, P) != wide, A != num))
        r = s.check()
        out.append((f"S1-bv numFactor={fac}: no wrap and actualNum == num for 1 <= num <= {MAX // fac}",
                    "PROVED (negation unsat)" if r == z3.unsat else f"COUNTEREXAMPLE {s.model()}"))
        if fac >= 2:
            s = z3.Solver()
            s.add(f == bv(fac), num == bv(MAX // fac + 1))
            wide = z3.SignExt(32, num) * z3.SignExt(32, f)
            s.add(wide <= z3.BitVecVal(MAX, 64))
            r = s.check()
            out.append((f"S2 numFactor={fac}: num = {MAX // fac + 1} overflows int (bound is tight)",
                        "PROVED (negation unsat)" if r == z3.unsat else f"COUNTEREXAMPLE {s.model()}"))

    for fac in (10, 1000):
        # W1: IAE
        s = z3.Solver()
        s.add(f == bv(fac), num >= 1, P <= 0)
        w = smallest(s, num)
        out.append((f"W1 numFactor={fac}: smallest num with P <= 0 (IllegalArgumentException)",
                    f"num={w} P={(w * fac + 2**31) % 2**32 - 2**31}"))
        # W2: NSEE
        s = z3.Solver()
        s.add(f == bv(fac), num >= 1, P > 0, A == 0)
        w = smallest(s, num)
        out.append((f"W2 numFactor={fac}: smallest num with 0 < P < numFactor (NoSuchElementException)",
                    f"num={w} P={(w * fac + 2**31) % 2**32 - 2**31}"))
        # W3: silent truncation with M = 8 matches
        M = 8
        s = z3.Solver()
        s.add(f == bv(fac), num >= 1, P > 0, A >= 1, A < bv(M), A < num)
        w = smallest(s, num)
        p = (w * fac + 2**31) % 2**32 - 2**31
        out.append((f"W3 numFactor={fac}, M={M}: smallest num with 1 <= actualNum < min(num, M) (silently fewer results)",
                    f"num={w} P={p} actualNum={p // fac}"))
        # W3b: Integer.MAX_VALUE itself
        p = (MAX * fac + 2**31) % 2**32 - 2**31
        out.append((f"W3b numFactor={fac}: num=Integer.MAX_VALUE", f"P={p} -> "
                    + ("IllegalArgumentException" if p <= 0 else ("NoSuchElementException" if p // fac == 0 else f"actualNum={p // fac}"))))

    # W4/W5: AnalyzingSuggester
    k = z3.BitVec("k", 32)
    Q = num * k
    for kk in (2, 3, 4):
        s = z3.Solver()
        s.add(k == bv(kk), num >= 1, Q >= 0, Q < num)
        w = smallest(s, num)
        if w is None:
            out.append((f"W4 k={kk}: num with 0 <= maxQueueDepth < topN (queue pruned below topN)", "none (unsat)"))
        else:
            q = (w * kk + 2**31) % 2**32 - 2**31
            out.append((f"W4 k={kk}: smallest num with 0 <= maxQueueDepth < topN (queue pruned below topN)",
                        f"num={w} maxQueueDepth={q}"))
    # W5: num = MAX, k >= 2, rejectCount in [0, 1]: isComplete false
    s = z3.Solver()
    rc = z3.BitVec("rejectCount", 32)
    s.add(k >= 2, k <= 1 << 20, rc >= 0, rc <= 1)
    s.add(rc + bv(MAX) <= bv(MAX) * k)  # isComplete true
    s.add(z3.Not(rc + bv(MAX) < 0))  # exclude the second wrap (rejectCount + topN overflows: isComplete true by accident)
    r = s.check()
    out.append(("W5 num=MAX, 2 <= k <= 2^20, rejectCount=0: isComplete is false (assert at AnalyzingSuggester.java:824 fires)",
                "PROVED (isComplete-true unsat)" if r == z3.unsat else f"COUNTEREXAMPLE {s.model()}"))

    for name, res in out:
        print(f"{name}: {res}")


if __name__ == "__main__":
    main()

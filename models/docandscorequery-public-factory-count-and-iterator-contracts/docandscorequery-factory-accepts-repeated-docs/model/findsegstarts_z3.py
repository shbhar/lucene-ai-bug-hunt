# Z3 model of DocAndScoreQuery.findSegmentStarts (leaf partitioning of the factory's doc array).
#
# Model element -> Java line (lucene/core/src/java/org/apache/lucene/search/DocAndScoreQuery.java @ a9779ef3c193)
#   docs[0..n-1]              <- :126-131  topK sorted by doc id (Arrays.sort, comparingInt(doc)) -> non-decreasing
#   base[1..L-1]              <- :153      leaves.get(i).docBase  (non-decreasing, base[0] = 0)
#   bsearch(a, from, to, key) <- :154      java.util.Arrays.binarySearch(int[],int,int,int) = binarySearch0
#                                          (JDK 25 source: low=from; high=to-1; while(low<=high){mid=(low+high)>>>1;
#                                           if(a[mid]<key) low=mid+1; else if(a[mid]>key) high=mid-1; else return mid;}
#                                           return -(low+1);)
#   r < 0 -> -1 - r           <- :155-157
#   starts[i] = r; from = r   <- :154,158
#   starts[0] = 0, starts[L] = n <- :146-147 (array default 0, explicit last entry)
#   leaf i slice [starts[i], starts[i+1])  <- :193-195 (count), :204-205 (scorer lower/upper)
#   leaf-relative doc docs[j] - base[i]    <- :261
#
# Reference (the factory javadoc ':104 matches exactly the documents in topK', DocIdSetIterator per-leaf ids in
# [0, maxDoc)): starts[i] must equal lowerBound(docs, base[i]) = #{j : docs[j] < base[i]} for every 1 <= i < L,
# so that leaf i receives exactly the entries with base[i] <= doc < base[i+1].
#
# Queries (bounded in n = #entries and L = #leaves; doc values are unbounded 32-bit signed ints >= 0):
#   Q1 strictly increasing docs                 -> expect UNSAT (partitioning correct)
#   Q2 non-decreasing docs (duplicates allowed)  -> expect SAT (witness)
#   Q3 non-decreasing docs, but no leaf docBase value occurs twice -> expect UNSAT (duplicates of a docBase are
#      the only failing shape)
#   Q4 non-decreasing docs: some leaf slice holds an entry >= the next leaf's docBase (leaf-relative id >= leaf
#      maxDoc) -> expect SAT (witness)
import sys
from z3 import BitVec, BitVecVal, BoolVal, If, And, Or, Not, Solver, LShR, sat, unsat

W = 32


def bv(v):
    return BitVecVal(v, W)


def select(arr, idx, n):
    # arr is a python list of BV terms; idx a BV term known to be in [0, n)
    e = arr[n - 1]
    for j in range(n - 2, -1, -1):
        e = If(idx == bv(j), arr[j], e)
    return e


def bsearch(arr, frm, to, key, n):
    # exact unrolling of Arrays.binarySearch0; the loop runs at most bitlength(n)+1 times
    low, high = frm, to - 1
    done = BoolVal(False)
    res = bv(0)
    for _ in range(n.bit_length() + 2):
        cond = And(Not(done), low <= high)  # signed compare, like Java ints
        mid = LShR(low + high, 1)
        midval = select(arr, mid, n)
        lt = midval < key
        gt = midval > key
        new_low = If(And(cond, lt), mid + 1, low)
        new_high = If(And(cond, Not(lt), gt), mid - 1, high)
        hit = And(cond, Not(lt), Not(gt))
        res = If(hit, mid, res)
        done = Or(done, hit)
        low, high = new_low, new_high
    # if not done, loop must have terminated (low > high): add as side condition
    term = Or(done, low > high)
    res = If(done, res, -(low + 1))
    return res, term


def lower_bound(arr, frm, to, key, n):
    # MUTANT used only for the vacuity check: the fix (first index with a[idx] >= key) in place of binarySearch
    c = frm
    for j in range(n):
        c = c + If(And(bv(j) >= frm, arr[j] < key), bv(1), bv(0))
    return c, BoolVal(True)


def model(n, L, mode, order, search=None):
    search = search or bsearch
    s = Solver()
    docs = [BitVec(f"d{j}", W) for j in range(n)]
    base = [bv(0)] + [BitVec(f"b{i}", W) for i in range(1, L)]
    for j in range(n):
        s.add(docs[j] >= 0)
    for j in range(n - 1):
        s.add(docs[j] < docs[j + 1] if order == "strict" else docs[j] <= docs[j + 1])
    for i in range(1, L):
        s.add(base[i] >= base[i - 1])
    # keep doc ids below the last leaf's end: add an explicit maxDoc bound (any value) > all docs
    maxdoc = BitVec("maxDoc", W)
    s.add(maxdoc >= base[L - 1], maxdoc > 0)
    for j in range(n):
        s.add(docs[j] < maxdoc)
    s.add(maxdoc < bv(2**31 - 129))  # IndexWriter.MAX_DOCS
    starts = [bv(0)] * (L + 1)
    starts[L] = bv(n)
    frm = bv(0)
    terms = []
    for i in range(1, L):
        r, t = search(docs, frm, bv(n), base[i], n)
        terms.append(t)
        r = If(r < 0, -1 - r, r)
        starts[i] = r
        frm = r
    for t in terms:
        s.add(t)
    lb = []
    for i in range(1, L):
        c = bv(0)
        for j in range(n):
            c = c + If(docs[j] < base[i], bv(1), bv(0))
        lb.append(c)
    wrong = Or([starts[i] != lb[i - 1] for i in range(1, L)]) if L > 1 else (bv(0) != bv(0))
    if mode == "sanity":
        pass  # vacuity probe: the constraint set alone must be SAT
    elif mode == "wrong-start":
        s.add(wrong)
    elif mode == "wrong-start-no-dup-base":
        for i in range(1, L):
            # docBase value b_i occurs at most once
            s.add(Not(Or([And(docs[j] == base[i], docs[j + 1] == base[i]) for j in range(n - 1)])) if n > 1 else True)
        s.add(wrong)
    elif mode == "out-of-leaf":
        # some leaf i (0 <= i < L-1) slice contains an entry docs[j] >= base[i+1]
        conds = []
        ends = [base[i] for i in range(1, L)] + [maxdoc]
        for i in range(L):
            lo, hi = starts[i], starts[i + 1]
            for j in range(n):
                conds.append(And(lo <= bv(j), bv(j) < hi, Or(docs[j] >= ends[i], docs[j] < base[i])))
        s.add(Or(conds))
    return s, docs, base, maxdoc, starts


def run():
    results = []
    plan = [
        # (property, order, expected, search)
        ("sanity", "strict", sat, None),
        ("sanity", "nondec", sat, None),
        ("wrong-start", "strict", unsat, None),          # Q1
        ("out-of-leaf", "strict", unsat, None),          # Q1'
        ("wrong-start", "nondec", sat, None),            # Q2
        ("wrong-start-no-dup-base", "nondec", unsat, None),  # Q3
        ("out-of-leaf", "nondec", sat, None),            # Q4
        ("wrong-start", "nondec", unsat, "lower_bound"), # mutant: the lower-bound fix closes Q2
        ("out-of-leaf", "nondec", unsat, "lower_bound"), # mutant: the lower-bound fix closes Q4
    ]
    for mode, order, expect, sname in plan:
        search = lower_bound if sname == "lower_bound" else None
        anysat = False
        for n in range(1, 9):
            for L in range(1, 6):
                s, docs, base, maxdoc, starts = model(n, L, mode, order, search)
                r = s.check()
                tag = f"{mode}/{order}/{sname or 'binarySearch'}"
                line = f"{tag:45s} n={n} L={L} -> {r}"
                if r == sat:
                    anysat = True
                    m = s.model()
                    dv = [m.eval(d).as_signed_long() for d in docs]
                    bvv = [m.eval(b).as_signed_long() for b in base]
                    st = [m.eval(x, model_completion=True).as_signed_long() for x in starts]
                    line += f"  docs={dv} docBases={bvv} maxDoc={m.eval(maxdoc).as_signed_long()} starts={st}"
                print(line, flush=True)
                results.append((tag, n, L, str(r), expect))
                if expect == unsat and r != unsat:
                    print("UNEXPECTED (expected unsat):", line, flush=True)
        if expect == sat and mode != "sanity" and not anysat:
            print("UNEXPECTED: no SAT witness for", mode, order, flush=True)
    return results


if __name__ == "__main__":
    res = run()
    bad_unsat = [r for r in res if r[4] == unsat and r[3] != "unsat"]
    sanity_bad = [r for r in res if r[0].startswith("sanity") and r[3] != "sat"]
    nondec_sat = [r for r in res if r[0] == "wrong-start/nondec/binarySearch" and r[3] == "sat"]
    out_sat = [r for r in res if r[0] == "out-of-leaf/nondec/binarySearch" and r[3] == "sat"]
    print(f"SUMMARY checks={len(res)} expected-UNSAT-violations={len(bad_unsat)} (expect 0); sanity-not-SAT={len(sanity_bad)} (expect 0); "
          f"wrong-start SAT (dups)={len(nondec_sat)}; out-of-leaf SAT (dups)={len(out_sat)}")
    sys.exit(1 if (bad_unsat or sanity_bad) else 0)

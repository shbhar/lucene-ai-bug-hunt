"""
Z3 (QF_BV, exact Java int semantics) model of the doc-ID / vector-ordinal arithmetic in
SlowCompositeCodecReaderWrapper (Lucene main ea57360912ed).

Model element -> Java line (lucene/core/src/java/org/apache/lucene/index/...)
  docStarts prefix sums (int +=)            SlowCompositeCodecReaderWrapper.java:68-75
  docIdToReaderId (checkIndex+binarySearch)  SlowCompositeCodecReaderWrapper.java:100-107
  java.util.Arrays.binarySearch(int[],int)   JDK Arrays.binarySearch0: low=0, high=len-1,
                                             mid=(low+high)>>>1, < : low=mid+1, > : high=mid-1, == : return mid,
                                             end: return -(low+1)
  callers of docIdToReaderId                 :157-158 (stored prefetch), :163-165 (stored document),
                                             :248-251 (TV prefetch), :257-262 (TV get)
  ReaderUtil.subIndex(int,int[])             ReaderUtil.java (scan to last match) -- used by MultiBits,
                                             MultiDocValues; the reference/fix semantics
  vector ord starts / findSub / binarySearchStarts   :826-841, :877-884, :1082-1106
  MergedDocIterator ord bookkeeping          :337-354 (ord = ordStart-1 on sub switch, ++ord per doc)
  vectorValue(ord) -> sub.vectorValue(ord - ordStart) :912-920
  addIndexes numDocs-only reservation        IndexWriter.java:3289-3295 (numDocs += leaf.numDocs(); testReserveDocs)
  MultiReader total maxDoc check             BaseCompositeReader (maxDoc > IndexWriter.getActualMaxDocs() -> IAE)
  merged min/max packed value                :681-743

Documented limits used as preconditions: IndexWriter.MAX_DOCS = 2^31 - 129 (Integer.MAX_VALUE - 128).

Every query prints sat/unsat; a sat model is printed as a concrete witness and is re-checked in Python
with plain Python ints that re-implement the Java code (no solver in the loop), so a spurious model would
be caught.
"""
import itertools
import sys
from z3 import (BitVec, BitVecVal, Solver, If, And, Or, Not, ULT, ULE, UGE, UGT, LShR, ZeroExt, SignExt,
                sat, unsat, simplify, Extract, Concat, Implies, BoolVal, Distinct)

W = 32
MAX_DOCS = 2**31 - 1 - 128

def bv(x):
    return BitVecVal(x, W)

# ---------------------------------------------------------------- Java Arrays.binarySearch (exact, unrolled)
def java_binary_search(arr, key, lo=0, hi_excl=None):
    """Symbolic Arrays.binarySearch(a, fromIndex, toIndex, key) on a Python list of 32-bit BV terms.
    Indices are concrete loop states encoded symbolically (low/high are BV terms); arr is indexed by a
    symbolic index through an If-chain. Unrolled len+1 times (enough: the range halves every step)."""
    n = len(arr)
    if hi_excl is None:
        hi_excl = n
    def at(idx):
        e = arr[0]
        for j in range(1, n):
            e = If(idx == bv(j), arr[j], e)
        return e
    low = bv(lo) if isinstance(lo, int) else lo
    high = (bv(hi_excl) if isinstance(hi_excl, int) else hi_excl) - 1
    done = BoolVal(False)
    result = bv(0)
    for _ in range(n + 2):
        active = And(Not(done), low <= high)          # signed compare, like Java
        mid = LShR(low + high, 1)                      # (low + high) >>> 1
        mv = at(mid)
        lt = mv < key                                  # signed int compare
        gt = mv > key
        found = And(active, Not(lt), Not(gt))
        result = If(found, mid, result)
        new_low = If(And(active, lt), mid + 1, low)
        new_high = If(And(active, Not(lt), gt), mid - 1, high)
        done = Or(done, found)
        low, high = new_low, new_high
    # loop exits with low > high if not found
    result = If(done, result, -(low + 1))
    return result, Not(And(Not(done), low <= high))   # second value: "loop terminated within unrolling"

def java_binary_search_py(a, key, lo=0, hi_excl=None):
    if hi_excl is None:
        hi_excl = len(a)
    low, high = lo, hi_excl - 1
    while low <= high:
        mid = (low + high) >> 1
        if a[mid] < key:
            low = mid + 1
        elif a[mid] > key:
            high = mid - 1
        else:
            return mid
    return -(low + 1)

def doc_id_to_reader_id_py(doc_starts, doc):
    assert 0 <= doc < doc_starts[-1]
    r = java_binary_search_py(doc_starts, doc)
    if r < 0:
        r = -2 - r
    return r

def reader_util_sub_index_py(n, doc_starts):
    size = len(doc_starts); lo = 0; hi = size - 1
    while hi >= lo:
        mid = (lo + hi) >> 1
        v = doc_starts[mid]
        if n < v: hi = mid - 1
        elif n > v: lo = mid + 1
        else:
            while mid + 1 < size and doc_starts[mid + 1] == v:
                mid += 1
            return mid
    return hi

results = []
def record(name, res, note, witness=None):
    results.append((name, str(res), note, witness))
    print(f"[{name}] {res}: {note}" + (f"\n    witness: {witness}" if witness else ""))

# ---------------------------------------------------------------- Q1/Q2: docIdToReaderId correctness
def docid_query(nreaders, allow_empty, use_fix=False):
    s = Solver()
    md = [BitVec(f"maxDoc{i}", W) for i in range(nreaders)]
    starts = [bv(0)]
    for i in range(nreaders):
        starts.append(starts[-1] + md[i])            # int +=, may wrap
    for i in range(nreaders):
        s.add(md[i] >= (0 if allow_empty else 1))
        s.add(md[i] <= MAX_DOCS)
    # merge-path precondition: total maxDoc <= MAX_DOCS (checked in 64-bit to exclude wrap)
    tot64 = sum(ZeroExt(32, m) for m in md)
    s.add(ULE(tot64, BitVecVal(MAX_DOCS, 64)))
    doc = BitVec("doc", W)
    s.add(doc >= 0, doc < starts[-1])                # Objects.checkIndex(doc, docStarts[last])
    if not use_fix:
        r, term = java_binary_search(starts, doc)
        r = If(r < 0, -2 - r, r)
    else:
        # fixed variant: after a hit, scan to the last equal start (as ReaderUtil.subIndex does)
        r0, term = java_binary_search(starts, doc)
        r = If(r0 < 0, -2 - r0, r0)
        for _ in range(nreaders + 1):
            nxt = r + 1
            def at(idx):
                e = starts[0]
                for j in range(1, len(starts)):
                    e = If(idx == bv(j), starts[j], e)
                return e
            r = If(And(r0 >= 0, nxt < bv(len(starts)), at(nxt) == doc), nxt, r)
    s.add(term)
    def at(idx):
        e = starts[0]
        for j in range(1, len(starts)):
            e = If(idx == bv(j), starts[j], e)
        return e
    ok = And(r >= 0, r < bv(nreaders), at(r) <= doc, doc < at(r + 1))
    s.add(Not(ok))
    res = s.check()
    wit = None
    if res == sat:
        m = s.model()
        mds = [m.eval(x, model_completion=True).as_signed_long() for x in md]
        d = m.eval(doc, model_completion=True).as_signed_long()
        ds = [0]
        for x in mds: ds.append(ds[-1] + x)
        rr = doc_id_to_reader_id_py(ds, d)
        good = reader_util_sub_index_py(d, ds)
        # replay in plain Python (no solver): must really be wrong
        assert not (ds[rr] <= d < ds[rr + 1]), "spurious model"
        wit = dict(maxDocs=mds, docStarts=ds, doc=d, docIdToReaderId=rr, correct_leaf=good,
                   chosen_leaf_maxDoc=mds[rr])
    return res, wit

for n in range(2, 7):
    res, wit = docid_query(n, allow_empty=False)
    record(f"Q1.docIdToReaderId.nonempty.n={n}", res,
           "all leaves maxDoc>=1, total<=MAX_DOCS: unsat = lookup always returns the leaf containing doc")
for n in range(2, 7):
    res, wit = docid_query(n, allow_empty=True)
    record(f"Q2.docIdToReaderId.emptyAllowed.n={n}", res,
           "leaves may have maxDoc=0: sat = lookup returns a leaf that does not contain doc", wit)
for n in range(2, 7):
    res, wit = docid_query(n, allow_empty=True, use_fix=True)
    record(f"Q3.docIdToReaderId.fixed(scan-to-last).n={n}", res,
           "same as Q2 with the ReaderUtil.subIndex-style scan to the last equal start: unsat = fix is complete")

# ---------------------------------------------------------------- Q2b: exhaustive replay on small sizes
# Exhaustive (plain Python, exact Java semantics) over all maxDoc vectors in {0..4}^n, n<=5, every doc:
# count the wrong lookups, and confirm every wrong lookup lands on an empty leaf whose start == doc.
tot = wrong = 0
wrong_shapes = set()
for n in range(2, 6):
    for mds in itertools.product(range(0, 5), repeat=n):
        ds = [0]
        for x in mds: ds.append(ds[-1] + x)
        for d in range(ds[-1]):
            tot += 1
            r = doc_id_to_reader_id_py(ds, d)
            if not (ds[r] <= d < ds[r + 1]):
                wrong += 1
                assert mds[r] == 0 and ds[r] == d
                assert reader_util_sub_index_py(d, ds) != r
                wrong_shapes.add((n, tuple(1 if x else 0 for x in mds)))
record("Q2b.exhaustive.maxDoc<=4.n<=5", f"{wrong}/{tot} wrong",
       "plain-Python replay of the Java code over all shapes; every wrong lookup picks an EMPTY leaf whose start "
       "equals doc (i.e. the doc is the first doc of a later non-empty leaf)",
       {"distinct_empty_patterns_hit": len(wrong_shapes)})

# ---------------------------------------------------------------- Q4: vector findSub with lastSubIndex cache
def findsub_query(nsubs):
    s = Solver()
    sz = [BitVec(f"size{i}", W) for i in range(nsubs)]
    starts = [bv(0)]
    for i in range(nsubs):
        s.add(sz[i] >= 0, sz[i] <= MAX_DOCS)
        starts.append(starts[-1] + sz[i])
    s.add(ULE(sum(ZeroExt(32, x) for x in sz), BitVecVal(MAX_DOCS, 64)))
    size = starts[-1]
    # starts array as in MergedFloatVectorValues: [ordStart_0..ordStart_{n-1}, size]
    arr = starts
    ord_ = BitVec("ord", W)
    last = BitVec("lastSubIndex", W)
    s.add(ord_ >= 0, ord_ < size)
    # reachable lastSubIndex values: 0 initially, or any index i<nsubs that contains some ord (a previous result)
    s.add(last >= 0, last < bv(nsubs))
    def at(idx):
        e = arr[0]
        for j in range(1, len(arr)):
            e = If(idx == bv(j), arr[j], e)
        return e
    s.add(Or(last == 0, at(last) < at(last + 1)))
    def bss(frm, to):
        pos, term = java_binary_search(arr, ord_, frm, to)
        p = pos
        for _ in range(len(arr)):
            p = If(And(pos >= 0, p < bv(len(arr) - 1), at(p + 1) == ord_), p + 1, p)
        return If(pos < 0, -2 - pos, p), term
    r1, t1 = bss(last + 1, len(arr))
    r2, t2 = bss(bv(0), last)
    r = If(ord_ >= at(last), If(ord_ >= at(last + 1), r1, last), r2)
    s.add(t1, t2)
    ok = And(r >= 0, r < bv(nsubs), at(r) <= ord_, ord_ < at(r + 1))
    s.add(Not(ok))
    return s.check()

for n in range(2, 6):
    record(f"Q4.vector.findSub.n={n}", findsub_query(n),
           "vector ord -> sub with empty/null subs and any reachable lastSubIndex: unsat = always the sub that owns ord")

# ---------------------------------------------------------------- Q5: prefix-sum overflow / MAX_DOCS
# (a) merge path: total maxDoc <= MAX_DOCS (IndexWriter reserves maxDoc) -> no int wrap in docStarts
def overflow_query(n, addindexes):
    s = Solver()
    md = [BitVec(f"maxDoc{i}", W) for i in range(n)]
    nd = [BitVec(f"numDocs{i}", W) for i in range(n)]
    for i in range(n):
        s.add(md[i] >= 1, md[i] <= MAX_DOCS, nd[i] >= 0, nd[i] <= md[i])
    tot_md = sum(ZeroExt(32, m) for m in md)
    tot_nd = sum(ZeroExt(32, x) for x in nd)
    if addindexes:
        s.add(ULE(tot_nd, BitVecVal(MAX_DOCS, 64)))      # testReserveDocs(numDocs)
        s.add(UGT(tot_md, BitVecVal(MAX_DOCS, 64)))      # yet the composite's MultiReader sees maxDoc > MAX_DOCS
    else:
        s.add(ULE(tot_md, BitVecVal(MAX_DOCS, 64)))
        acc = bv(0)
        wraps = []
        for m in md:
            acc2 = acc + m
            wraps.append(acc2 < acc)
            acc = acc2
        s.add(Or(*wraps))
    res = s.check()
    wit = None
    if res == sat:
        m = s.model()
        wit = dict(maxDocs=[m.eval(x).as_signed_long() for x in md], numDocs=[m.eval(x).as_signed_long() for x in nd])
    return res, wit

for n in (2, 3):
    res, wit = overflow_query(n, addindexes=False)
    record(f"Q5a.mergepath.docStarts.wrap.n={n}", res, "unsat = no int overflow when total maxDoc <= MAX_DOCS")
    res, wit = overflow_query(n, addindexes=True)
    record(f"Q5b.addIndexes.numDocsOK.maxDocTooBig.n={n}", res,
           "sat = addIndexes(CodecReader...) accepts (sum numDocs <= MAX_DOCS) a reader set whose sum maxDoc > MAX_DOCS; "
           "the composite's MultiReader then throws IAE (mergeMiddle never sees this: IW reserves maxDoc)", wit)

# ---------------------------------------------------------------- Q6: MergedDocIterator ord vs vectorValue(ord)
# For sub i with s_i vectors, the k-th doc returned by the merged iterator gets merged ord ordStart_i + k
# (the iterator ignores the sub iterator's index()). vectorValue(ordStart_i + k) reads sub.vectorValue(k).
# Correct iff the sub's own iterator reports index() == k for its k-th doc. Model the sub's index() as a
# permutation pi of [0, s) (allowed by SortingCodecReader.SortingValuesIterator: "index() may skip around").
def ord_query(s_sub, monotone):
    s = Solver()
    pi = [BitVec(f"pi{k}", W) for k in range(s_sub)]
    for k in range(s_sub):
        s.add(pi[k] >= 0, pi[k] < s_sub)
    s.add(Distinct(*pi))
    if monotone:
        for k in range(s_sub - 1):
            s.add(pi[k] < pi[k + 1])
    k = BitVec("k", W)
    s.add(k >= 0, k < s_sub)
    pik = pi[0]
    for j in range(1, s_sub):
        pik = If(k == j, pi[j], pik)
    # vector read for the k-th doc = sub vector at ord k; the doc's real vector is at ord pi[k]
    s.add(pik != k)
    res = s.check()
    wit = None
    if res == sat:
        m = s.model()
        wit = dict(sub_index_per_position=[m.eval(x).as_signed_long() for x in pi], k=m.eval(k).as_signed_long())
    return res, wit

for ssub in (2, 3, 4):
    res, _ = ord_query(ssub, monotone=True)
    record(f"Q6a.mergedIter.monotoneSub.s={ssub}", res,
           "sub ords strictly increasing with doc (all codec formats): unsat = merged ord <-> vectorValue agree")
    res, wit = ord_query(ssub, monotone=False)
    record(f"Q6b.mergedIter.sortingSub.s={ssub}", res,
           "sub = SortingCodecReader values (index() non-monotone): sat = k-th doc is given the vector of another doc", wit)

# ---------------------------------------------------------------- Q7: merged min/max packed values
# Unsigned big-endian byte-wise comparison over one dimension of B bytes == unsigned BV comparison, and the
# loop keeps the per-dimension min/max over subs (3 subs, 2 dims, 4 bytes per dim).
def bytes_of(x, B):
    return [Extract(8 * (B - 1 - j) + 7, 8 * (B - 1 - j), x) for j in range(B)]

def lex_lt(a, b):
    # ArrayUtil.getUnsignedComparator(B).compare(a, b) < 0, big-endian unsigned bytes
    res = BoolVal(False)
    for j in reversed(range(len(a))):
        res = If(a[j] == b[j], res, ULT(a[j], b[j]))
    return res

def minmax_query(B, nsubs=3, dims=2, which="min"):
    s = Solver()
    vals = [[BitVec(f"v{i}_{d}", 8 * B) for d in range(dims)] for i in range(nsubs)]
    out = []
    for d in range(dims):
        cur = vals[0][d]
        for i in range(1, nsubs):
            if which == "min":
                cur = If(lex_lt(bytes_of(vals[i][d], B), bytes_of(cur, B)), vals[i][d], cur)
            else:
                cur = If(lex_lt(bytes_of(cur, B), bytes_of(vals[i][d], B)), vals[i][d], cur)
        out.append(cur)
    bad = []
    for d in range(dims):
        for i in range(nsubs):
            bad.append(UGT(out[d], vals[i][d]) if which == "min" else ULT(out[d], vals[i][d]))
        bad.append(And(*[out[d] != vals[i][d] for i in range(nsubs)]))
    s.add(Or(*bad))
    return s.check()

for B in (1, 2, 4, 8):
    record(f"Q7.points.mergedMin.B={B}", minmax_query(B, which="min"),
           "unsat = merged min packed value is the per-dimension unsigned minimum over subs")
    record(f"Q7.points.mergedMax.B={B}", minmax_query(B, which="max"),
           "unsat = merged max packed value is the per-dimension unsigned maximum over subs")

# ---------------------------------------------------------------- vacuity probes
# Mutant 1: binarySearch without the -2-pos correction must give sat even with non-empty leaves.
s = Solver()
md = [BitVec(f"m{i}", W) for i in range(3)]
st = [bv(0)]
for m in md:
    s.add(m >= 1, m <= 1000); st.append(st[-1] + m)
doc = BitVec("doc", W); s.add(doc >= 0, doc < st[-1])
r, term = java_binary_search(st, doc); s.add(term)
def at(idx):
    e = st[0]
    for j in range(1, len(st)):
        e = If(idx == bv(j), st[j], e)
    return e
s.add(Not(And(r >= 0, r < 3, at(r) <= doc, doc < at(r + 1))))   # mutant: raw binarySearch result used
record("V1.vacuity.mutant-no-insertion-point-fix", s.check(), "must be sat (mutant killed)")
# Mutant 2: findSub without the scan-to-last must be sat when empty subs are allowed
def findsub_mutant(nsubs=3):
    s = Solver()
    sz = [BitVec(f"size{i}", W) for i in range(nsubs)]
    starts = [bv(0)]
    for i in range(nsubs):
        s.add(sz[i] >= 0, sz[i] <= 100); starts.append(starts[-1] + sz[i])
    ord_ = BitVec("ord", W); s.add(ord_ >= 0, ord_ < starts[-1])
    pos, term = java_binary_search(starts, ord_); s.add(term)
    r = If(pos < 0, -2 - pos, pos)
    def at(idx):
        e = starts[0]
        for j in range(1, len(starts)):
            e = If(idx == bv(j), starts[j], e)
        return e
    s.add(Not(And(r >= 0, r < nsubs, at(r) <= ord_, ord_ < at(r + 1))))
    return s.check()
record("V2.vacuity.mutant-findSub-no-scan", findsub_mutant(4), "must be sat (mutant killed)")
# Probe 3: a trivially false property must be sat for the Q1 encoding (encoding is not over-constrained)
s = Solver()
md = [BitVec(f"m{i}", W) for i in range(3)]
for m in md: s.add(m >= 1, m <= MAX_DOCS)
s.add(ULE(sum(ZeroExt(32, m) for m in md), BitVecVal(MAX_DOCS, 64)))
s.add(md[0] + md[1] + md[2] == bv(MAX_DOCS))
record("V3.vacuity.preconditions-satisfiable-at-MAX_DOCS", s.check(), "must be sat (limits reachable)")

import json
with open(sys.argv[1] if len(sys.argv) > 1 else "leaf_lookup_smt.results.json", "w") as f:
    json.dump([dict(query=a, result=b, note=c, witness=d) for a, b, c, d in results], f, indent=1)

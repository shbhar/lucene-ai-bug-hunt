"""bkd-write-niche-configs: SMT obligations for the int-sized heap buffers of the BKD write path.

Model element -> Java (lucene/core/src/java/org/apache/lucene/util/bkd/, main a9779ef3c193):
  M   = maxPointsSortInHeap = (int) ((maxMBSortInHeap * 1024 * 1024) / bytesPerDoc)   BKDWriter.java:196
        (the double->int cast saturates; any int M >= maxLeaf is reachable by picking maxMBSortInHeap,
         and BKDWriter.java:199 rejects M < maxPointsInLeafNode)
  S1  initPointWriter: heap iff totalPointCount <= M, new HeapPointWriter(config, toIntExact(T))  BKDWriter.java:227-231
  S2  BKDRadixSelector.getPointWriter: heap iff count <= M / 2                                  BKDRadixSelector.java:574-579
  S3  BKDRadixSelector.getDeltaPointWriter: heap iff delta <= M - (heap points in use)           BKDRadixSelector.java:552-560
  S4  BKDWriter.switchToHeap: new HeapPointWriter(config, count), count <= maxLeaf               BKDWriter.java:1606-1609
  HPW HeapPointWriter: block = new byte[config.bytesPerDoc() * size] (int multiply)              HeapPointWriter.java:43
  S5  OneDimensionBKDWriter: leafValues = new byte[maxLeaf * packedBytesLength] (int multiply)   BKDWriter.java:687
  bytesPerDoc = numDims * bytesPerDim + 4, numDims in 1..16, bytesPerDim in 1..16                BKDConfig.java
  Java array limit: ArrayUtil.MAX_ARRAY_LENGTH ~ Integer.MAX_VALUE - 16 (an int multiply above
  Integer.MAX_VALUE wraps; a negative length throws NegativeArraySizeException).

Each query asks z3 for a configuration in which the int product that sizes the array exceeds
Integer.MAX_VALUE (i.e. the 32-bit product differs from the mathematical one), under the code's own
guards. "sat" = reachable overflow (with the witness); "unsat" = proved impossible in that regime.
"""
import json
import sys

from z3 import (And, BitVecVal, Int, Int2BV, BV2Int, Optimize, Or, Solver, sat, unsat, If)

INT_MAX = 2**31 - 1
MAX_ARRAY_LENGTH = INT_MAX - 16  # conservative (ArrayUtil uses the JVM's real max)


def wraps(a, b):
    """True iff a*b (both nonneg ints < 2^31) does not fit in a Java int."""
    return a * b > INT_MAX


def java_int_mul(a, b):
    """The 32-bit two's-complement result of a*b as a z3 Int (for reporting)."""
    return BV2Int(Int2BV(a * b, 32), is_signed=True)


def config_vars(s):
    nd, bpd, M, maxLeaf = Int("numDims"), Int("bytesPerDim"), Int("maxPointsSortInHeap"), Int("maxPointsInLeafNode")
    s.add(nd >= 1, nd <= 16, bpd >= 1, bpd <= 16)
    s.add(maxLeaf >= 1, maxLeaf <= MAX_ARRAY_LENGTH)
    s.add(M >= maxLeaf, M <= INT_MAX)  # BKDWriter.java:199 guard, int cast saturates
    bpdoc = nd * bpd + 4
    return nd, bpd, M, maxLeaf, bpdoc


def mb_for(M, bpdoc):
    """smallest maxMBSortInHeap (double) giving exactly M points in heap"""
    return M * bpdoc / (1024 * 1024)


results = []

# Nonlinear products nd*bpd*T are split over the small factors (study lesson: case-split the small
# factor): every (numDims, bytesPerDim) shape gives a constant bytesPerDoc, so every query is linear.
SHAPES = [(nd, bpd) for nd in range(1, 17) for bpd in range(1, 17)]


def solve_all_shapes(name, build, expect, shapes=SHAPES):
    """build(s, nd, bpd, bpdoc) adds constraints and returns the int term to minimise.
    sat if any shape is sat (witness = the shape with the smallest minimised term)."""
    best = None
    n_sat = 0
    for nd, bpd in shapes:
        bpdoc = nd * bpd + 4
        o = Optimize()
        term, vars_ = build(o, nd, bpd, bpdoc)
        o.minimize(term)
        r = o.check()
        if r == sat:
            n_sat += 1
            m = o.model()
            w = {"numDims": nd, "bytesPerDim": bpd, "bytesPerDoc": bpdoc}
            for v in vars_:
                w[str(v)] = m.eval(v).as_long()
            if "maxPointsSortInHeap" in w:
                w["maxMBSortInHeap_min"] = round(w["maxPointsSortInHeap"] * bpdoc / (1024 * 1024), 3)
            val = m.eval(term).as_long()
            if best is None or val < best[0]:
                best = (val, w)
        elif r != unsat:
            raise RuntimeError(f"{name}: {r} for shape {nd}x{bpd}")
    res = "sat" if n_sat else "unsat"
    rec = {"query": name, "result": res, "expected": expect, "ok": res == expect,
           "shapes_checked": len(shapes), "shapes_sat": n_sat}
    if best:
        rec["witness_min"] = best[1]
    results.append(rec)
    print(json.dumps(rec))


def heapM(o, bpdoc, maxLeaf, regime_mb_max, strict=False):
    M = Int("maxPointsSortInHeap")
    o.add(M >= maxLeaf, M <= INT_MAX)
    if regime_mb_max is not None:
        # M = floor(mb * 2^20 / bpdoc) with mb <= regime  =>  M * bpdoc <= regime * 2^20
        # (strict: mb < regime  =>  M * bpdoc < regime * 2^20)
        if strict:
            o.add(M * bpdoc < regime_mb_max * 1024 * 1024)
        else:
            o.add(M * bpdoc <= regime_mb_max * 1024 * 1024)
    return M


def leafVar(o):
    maxLeaf = Int("maxPointsInLeafNode")
    o.add(maxLeaf >= 1, maxLeaf <= MAX_ARRAY_LENGTH)
    return maxLeaf


def S1(regime=None, strict=False):
    def build(o, nd, bpd, bpdoc):
        maxLeaf = leafVar(o)
        M = heapM(o, bpdoc, maxLeaf, regime, strict)
        T = Int("totalPointCount")
        o.add(T >= 1, T <= M, T * bpdoc > INT_MAX)  # heap branch of initPointWriter + int overflow
        return T, [T, M, maxLeaf]
    return build


solve_all_shapes("S1 initPointWriter heap buffer overflows (any maxMBSortInHeap)", S1(), "sat")
solve_all_shapes("S1 with maxMBSortInHeap <= 2047", S1(2047), "unsat")
solve_all_shapes("S1 with maxMBSortInHeap < 2048 (strict)", S1(2048, strict=True), "unsat")
# exactly 2048 MB: M * bytesPerDoc can reach 2^31 = Integer.MAX_VALUE + 1 when bytesPerDoc is a power of two
solve_all_shapes("S1 with maxMBSortInHeap <= 2048 (boundary)", S1(2048), "sat")
solve_all_shapes("S1 with maxMBSortInHeap = 16 (default)", S1(16), "unsat")
for label, nd_v, bpd_v in [("LatLonPoint/XYPoint 2x4", 2, 4), ("LatLonShape triangle 7x4", 7, 4),
                           ("DoublePoint 2-D 2x8", 2, 8), ("InetAddressRange 2x16", 2, 16),
                           ("16x16 (largest)", 16, 16), ("IntPoint 1x4 (generic merge path only)", 1, 4)]:
    solve_all_shapes("S1 minimal totalPointCount, " + label, S1(), "sat", shapes=[(nd_v, bpd_v)])


def S2(regime=None):
    def build(o, nd, bpd, bpdoc):
        maxLeaf = leafVar(o)
        M = heapM(o, bpdoc, maxLeaf, regime)
        c = Int("count")
        o.add(c >= 1, 2 * c <= M, c * bpdoc > INT_MAX)  # count <= maxPointsSortInHeap / 2
        return M, [c, M, maxLeaf]
    return build


solve_all_shapes("S2 radix-selector left/right heap writer overflows", S2(), "sat")
solve_all_shapes("S2 with maxMBSortInHeap <= 4095", S2(4095), "unsat")


def S3(regime=None):
    def build(o, nd, bpd, bpdoc):
        maxLeaf = leafVar(o)
        M = heapM(o, bpdoc, maxLeaf, regime)
        d, used = Int("delta"), Int("heapPointsInUse")
        o.add(used >= 0, d >= 1, d + used <= M, d * bpdoc > INT_MAX)
        return M, [d, used, M, maxLeaf]
    return build


solve_all_shapes("S3 radix-selector delta heap writer overflows", S3(), "sat")
solve_all_shapes("S3 with maxMBSortInHeap <= 2047", S3(2047), "unsat")


def S4(maxleaf_max=None):
    def build(o, nd, bpd, bpdoc):
        maxLeaf = leafVar(o)
        heapM(o, bpdoc, maxLeaf, None)
        c = Int("leafCount")
        o.add(c >= 1, c <= maxLeaf, c * bpdoc > INT_MAX)
        if maxleaf_max is not None:
            o.add(maxLeaf <= maxleaf_max)
        return maxLeaf, [c, maxLeaf]
    return build


solve_all_shapes("S4 switchToHeap leaf buffer overflows (needs a huge maxPointsInLeafNode)", S4(), "sat")
solve_all_shapes("S4 with maxPointsInLeafNode <= 8,000,000", S4(8_000_000), "unsat")


def S5(maxleaf_max=None):
    def build(o, nd, bpd, bpdoc):
        maxLeaf = leafVar(o)
        o.add(maxLeaf * bpd > INT_MAX)  # 1-D: packedBytesLength = bytesPerDim
        if maxleaf_max is not None:
            o.add(maxLeaf <= maxleaf_max)
        return maxLeaf, [maxLeaf]
    return build


one_d = [(1, b) for b in range(1, 17)]
solve_all_shapes("S5 OneDimensionBKDWriter leaf buffer overflows (huge maxPointsInLeafNode)", S5(), "sat", shapes=one_d)
solve_all_shapes("S5 with maxPointsInLeafNode <= 134,217,727", S5(134_217_727), "unsat", shapes=one_d)

ok = all(r["ok"] for r in results)
print(json.dumps({"summary": {"queries": len(results), "as_expected": sum(r["ok"] for r in results), "all_ok": ok}}))
sys.exit(0 if ok else 1)

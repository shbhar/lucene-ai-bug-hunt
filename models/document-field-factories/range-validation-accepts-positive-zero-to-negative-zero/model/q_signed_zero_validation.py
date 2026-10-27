"""SMT model (Z3, exact IEEE-754 + bit-vector) of the range-field min/max validation versus the
sortable-bytes order used by every RangeFieldQuery / BinaryRangeFieldRangeQuery comparison.

Model element -> Java source (Lucene main ea57360912ed):
  valid_java(a,b)   = !isNaN(a) && !isNaN(b) && !(a > b)
                      DoubleRange.verifyAndEncode            DoubleRange.java:136-151  (NaN checks, `min[d] > max[d]`)
                      FloatRange.verifyAndEncode             FloatRange.java:134-151
                      DoubleRangeDocValuesField.checkArgs    DoubleRangeDocValuesField.java:84
                      FloatRangeDocValuesField.checkArgs     FloatRangeDocValuesField.java:84
  enc(x)            = longToSortableBytes(doubleToSortableLong(x))   DoubleRange.java:154-156
                      sortableDoubleBits(bits) = bits ^ (bits >> 63) & 0x7fffffffffffffffL   NumericUtils.java:84-86
                      longToSortableBytes: value ^= 0x8000000000000000L, big-endian          NumericUtils.java:201-205
                      (float: sortableFloatBits NumericUtils.java:89-91, intToSortableBytes :178-182)
  cmp(x,y)          = unsigned lexicographic compare of the big-endian bytes = BV unsigned compare
                      ArrayUtil.getUnsignedComparator(bytesPerDim)   RangeFieldQuery.java:354
  INTERSECTS.matches(q,p) = cmp(q.max, p.min) >= 0 && cmp(q.min, p.max) <= 0   RangeFieldQuery.java:104-115
  WITHIN.matches(q,p)     = cmp(q.min, p.min) <= 0 && cmp(q.max, p.max) >= 0   RangeFieldQuery.java:147-158
  CONTAINS.matches(q,p)   = cmp(q.min, p.min) >= 0 && cmp(q.max, p.max) <= 0   RangeFieldQuery.java:190-201

Queries (each re-executed in Java by the JUnit repro, see candidates/.../repro):
  Q1  exists accepted (a,b) whose encoded order is inverted          -> SAT, witness printed
  Q2  Q1 with the witness (+0.0,-0.0) excluded                       -> UNSAT: it is the only one
  Q3  exists rejected (a,b) whose encoded order is NOT inverted       -> UNSAT: the check never rejects a valid range
  Q4  exists accepted (a,b): the document [a,b] is WITHIN and CONTAINS the query [a,b] but does not INTERSECT it
                                                                       -> SAT, same witness
  Q5  Q4 with the witness excluded                                    -> UNSAT
Run with:  python3 -I q_signed_zero_validation.py
"""
import z3


def model(ebits, sbits):
    w = ebits + sbits
    S = z3.FPSort(ebits, sbits)
    A, B = z3.BitVecs("a_bits b_bits", w)
    a, b = z3.fpBVToFP(A, S), z3.fpBVToFP(B, S)

    def enc(bits):
        sortable = bits ^ ((bits >> (w - 1)) & z3.BitVecVal((1 << (w - 1)) - 1, w))  # >> is arithmetic in z3py
        return sortable ^ z3.BitVecVal(1 << (w - 1), w)  # flip sign bit before big-endian write

    def cmp_ge(x, y):  # comparator.compare(x, y) >= 0
        return z3.UGE(x, y)

    def cmp_le(x, y):
        return z3.ULE(x, y)

    not_nan = z3.And(z3.Not(z3.fpIsNaN(a)), z3.Not(z3.fpIsNaN(b)))
    accepted = z3.And(not_nan, z3.Not(z3.fpGT(a, b)))
    rejected_by_order = z3.And(not_nan, z3.fpGT(a, b))
    ea, eb = enc(A), enc(B)
    inverted = z3.UGT(ea, eb)
    # self query q == p == [a, b]
    q_min, q_max, p_min, p_max = ea, eb, ea, eb
    intersects = z3.And(cmp_ge(q_max, p_min), cmp_le(q_min, p_max))
    within = z3.And(cmp_le(q_min, p_min), cmp_ge(q_max, p_max))
    contains = z3.And(cmp_ge(q_min, p_min), cmp_le(q_max, p_max))
    pz = z3.BitVecVal(0, w)
    nz = z3.BitVecVal(1 << (w - 1), w)
    witness = z3.And(A == pz, B == nz)
    return dict(A=A, B=B, a=a, b=b, accepted=accepted, rejected=rejected_by_order, inverted=inverted,
                intersects=intersects, within=within, contains=contains, witness=witness)


def solve(name, constraints, m):
    s = z3.Solver()
    s.set("timeout", 600000)
    s.add(*constraints)
    r = s.check()
    line = f"{name}: {r}"
    if r == z3.sat:
        mod = s.model()
        av, bv = mod.eval(m["A"]).as_long(), mod.eval(m["B"]).as_long()
        line += f"  a_bits=0x{av:x} b_bits=0x{bv:x}  a={mod.eval(m['a'])} b={mod.eval(m['b'])}"
    print(line, flush=True)
    return r


results = {}
for label, (e, sb) in {"float64 (DoubleRange)": (11, 53), "float32 (FloatRange)": (8, 24)}.items():
    m = model(e, sb)
    print("==", label)
    results[(label, "Q1")] = solve("Q1 accepted and encoded-inverted", [m["accepted"], m["inverted"]], m)
    results[(label, "Q2")] = solve("Q2 same, excluding (+0.0,-0.0)", [m["accepted"], m["inverted"], z3.Not(m["witness"])], m)
    results[(label, "Q3")] = solve("Q3 rejected but encoded-ordered", [m["rejected"], z3.Not(m["inverted"])], m)
    results[(label, "Q4")] = solve("Q4 accepted, self WITHIN and CONTAINS but not INTERSECTS",
                                   [m["accepted"], m["within"], m["contains"], z3.Not(m["intersects"])], m)
    results[(label, "Q5")] = solve("Q5 same, excluding (+0.0,-0.0)",
                                   [m["accepted"], m["within"], m["contains"], z3.Not(m["intersects"]), z3.Not(m["witness"])], m)

expected = {"Q1": z3.sat, "Q2": z3.unsat, "Q3": z3.unsat, "Q4": z3.sat, "Q5": z3.unsat}
ok = all(results[k] == expected[k[1]] for k in results)
print("ALL AS EXPECTED" if ok else "UNEXPECTED RESULT")

# ---- vacuity / mutation evidence -------------------------------------------------------------
# F1 (fix check): validation with Double.compare semantics (`Double.compare(min, max) > 0` rejects)
#     -> Q1 must become UNSAT.
# M1 (mutant that the model must detect): drop the sortable transform for negatives (raw bits as
#     unsigned) -> Q3 must become SAT (a rejected pair whose encoding is ordered), proving Q3 is not
#     vacuously UNSAT.
print("== vacuity / mutation")
for label, (e, sb) in {"float64": (11, 53), "float32": (8, 24)}.items():
    m = model(e, sb)
    w = e + sb
    a, b = m["a"], m["b"]
    not_nan = z3.And(z3.Not(z3.fpIsNaN(a)), z3.Not(z3.fpIsNaN(b)))
    java_compare_gt = z3.Or(z3.fpGT(a, b), z3.And(z3.fpIsZero(a), z3.fpIsZero(b), z3.fpIsPositive(a), z3.fpIsNegative(b)))
    accepted_fixed = z3.And(not_nan, z3.Not(java_compare_gt))
    r1 = solve(f"F1 {label} fixed validation, accepted and encoded-inverted", [accepted_fixed, m["inverted"]], m)
    A, B = m["A"], m["B"]
    bad_enc = lambda x: x ^ z3.BitVecVal(1 << (w - 1), w)
    r2 = solve(f"M1 {label} mutant encoding, rejected but encoded-ordered", [m["rejected"], z3.Not(z3.UGT(bad_enc(A), bad_enc(B)))], m)
    print("  F1 unsat:", r1 == z3.unsat, " M1 sat (mutant killed):", r2 == z3.sat)

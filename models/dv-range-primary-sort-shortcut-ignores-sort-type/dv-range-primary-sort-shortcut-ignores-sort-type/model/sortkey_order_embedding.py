"""
Z3 model: when does an index sort keep documents in the order of their stored long values?

SortedNumericDocValuesRangeQuery's primary-sort shortcut (SortedSkipperScorerSupplier) turns the
skipper's long min/max into ONE contiguous doc-ID interval. That is correct for every query range
iff, for the stored values a, b that the field can hold,

    a < b (signed long)  ==>  cmp(key(a), key(b)) < 0                                  (EMB)

where cmp/key are what the segment's IndexSorter uses; reverseMul flips the doc order and
SortedSkipperScorerSupplier flips its predicates on getReverse(), so we check EMB for the
reverse=true case as "a < b ==> reverseMul*cmp > 0", which is the same obligation (encoded, not assumed).
A tie (cmp == 0 with a != b) is also a violation: ties are broken by later sort fields or doc ID,
so the larger long can come first.

file:line map (Lucene main-a9779ef @ a9779ef3c193):
  key/cmp SortField(LONG)     IndexSorter.java:298,322   Long.compare(v)
  key/cmp SortField(INT)      IndexSorter.java:249,252   Integer.compare((int) v)
  key/cmp SortField(FLOAT)    IndexSorter.java:382,385   Float.compare(Float.intBitsToFloat((int) v))
  key/cmp SortField(DOUBLE)   IndexSorter.java:446,449   Double.compare(Double.longBitsToDouble(v))
  SortedNumericSortField      SortedNumericSortField.java:374-387 -> same sorters, values via
                              SortedNumericSelector.java:76-88: FLOAT -> sortableFloatBits((int) v),
                              DOUBLE -> sortableDoubleBits(v), INT/LONG -> v
  sortable*Bits               NumericUtils.java:84-91
  reverse                     IndexSorter reverseMul; SortedSkipperScorerSupplier.java:121-129,147-180
  the shortcut's own guard    SortedNumericDocValuesRangeQuery.java:237-247 (name + density only)
Encodings (what each documented field stores, as a long):
  raw-long      NumericDocValuesField / SortedNumericDocValuesField / LongField: any long
  int           IntField or an int value: sign-extended int
  long>int      an INT-sorted field holding a long outside the int range (IndexWriter accepts it)
  float-raw     FloatDocValuesField: sign-extended Float.floatToRawIntBits (any 32-bit pattern)
  float-sort    FloatField: sign-extended NumericUtils.floatToSortableInt (Float.floatToIntBits, canonical NaN)
  double-raw    DoubleDocValuesField: Double.doubleToRawLongBits (any 64-bit pattern)
  double-sort   DoubleField: NumericUtils.doubleToSortableLong (canonical NaN)
Java Float.compare/Double.compare are transcribed from the JDK source: '<' and '>' in IEEE
arithmetic first, then the signed comparison of floatToIntBits/doubleToLongBits (NaN canonical).
"""
import json
import sys
import time

from z3 import (BitVec, BitVecVal, Concat, Extract, If, Not, Or, And, Solver, SignExt, fpBVToFP,
                fpIsNaN, fpLT, fpGT, Float32, Float64, sat, unsat, BV2Int, simplify)

CANON_NAN32 = BitVecVal(0x7FC00000, 32)
CANON_NAN64 = BitVecVal(0x7FF8000000000000, 64)


def to_int(v64):  # (int) v
    return Extract(31, 0, v64)


def sortable32(bits):  # NumericUtils.sortableFloatBits: bits ^ (bits >> 31) & 0x7fffffff
    return bits ^ ((bits >> 31) & BitVecVal(0x7FFFFFFF, 32))


def sortable64(bits):
    return bits ^ ((bits >> 63) & BitVecVal(0x7FFFFFFFFFFFFFFF, 64))


def sign(cmp_lt, cmp_gt):
    return If(cmp_lt, -1, If(cmp_gt, 1, 0))


def float_compare(x32, y32):
    fx, fy = fpBVToFP(x32, Float32()), fpBVToFP(y32, Float32())
    bx = If(fpIsNaN(fx), CANON_NAN32, x32)  # floatToIntBits
    by = If(fpIsNaN(fy), CANON_NAN32, y32)
    return If(fpLT(fx, fy), -1, If(fpGT(fx, fy), 1, If(bx == by, 0, If(bx < by, -1, 1))))


def double_compare(x64, y64):
    fx, fy = fpBVToFP(x64, Float64()), fpBVToFP(y64, Float64())
    bx = If(fpIsNaN(fx), CANON_NAN64, x64)
    by = If(fpIsNaN(fy), CANON_NAN64, y64)
    return If(fpLT(fx, fy), -1, If(fpGT(fx, fy), 1, If(bx == by, 0, If(bx < by, -1, 1))))


def long_compare(a, b):
    return If(a < b, -1, If(a > b, 1, 0))


def cmp(sortkind, a, b):
    cls, t = sortkind
    if t == "LONG":
        return long_compare(a, b)
    if t == "INT":
        return long_compare(SignExt(32, to_int(a)), SignExt(32, to_int(b)))
    if t == "FLOAT":
        xa, xb = to_int(a), to_int(b)
        if cls == "SortedNumericSortField":
            xa, xb = sortable32(xa), sortable32(xb)
        return float_compare(xa, xb)
    if t == "DOUBLE":
        xa, xb = a, b
        if cls == "SortedNumericSortField":
            xa, xb = sortable64(xa), sortable64(xb)
        return double_compare(xa, xb)
    raise ValueError(t)


def domain(enc, v, s):
    """Constraint: the long v is a value that encoding enc stores; s is a free 32/64-bit source."""
    if enc == "raw-long" or enc == "double-raw":
        return v == s
    if enc == "int" or enc == "float-raw":
        return v == SignExt(32, Extract(31, 0, s))
    if enc == "long>int":
        return Or(v == s, v == SignExt(32, Extract(31, 0, s)))  # any long (incl. outside int range)
    if enc == "float-sort":
        x = Extract(31, 0, s)
        canon = If(fpIsNaN(fpBVToFP(x, Float32())), CANON_NAN32, x)
        return v == SignExt(32, sortable32(canon))
    if enc == "double-sort":
        canon = If(fpIsNaN(fpBVToFP(s, Float64())), CANON_NAN64, s)
        return v == sortable64(canon)
    raise ValueError(enc)


SORTS = [(c, t) for c in ("SortField", "SortedNumericSortField")
         for t in ("LONG", "INT", "FLOAT", "DOUBLE")]
ENCODINGS = ["raw-long", "int", "long>int", "float-raw", "float-sort", "double-raw", "double-sort"]
# documented pairings (the encoding a field type writes for that sort)
DOCUMENTED = {
    ("SortField", "LONG"): ["raw-long"],
    ("SortField", "INT"): ["int", "long>int"],
    ("SortField", "FLOAT"): ["float-raw"],
    ("SortField", "DOUBLE"): ["double-raw"],
    ("SortedNumericSortField", "LONG"): ["raw-long"],
    ("SortedNumericSortField", "INT"): ["int", "long>int"],
    ("SortedNumericSortField", "FLOAT"): ["float-sort"],
    ("SortedNumericSortField", "DOUBLE"): ["double-sort"],
}


def check(sortkind, reverse, enc, timeout_ms):
    a, b = BitVec("a", 64), BitVec("b", 64)
    sa, sb = BitVec("sa", 64), BitVec("sb", 64)
    rm = -1 if reverse else 1
    c = cmp(sortkind, a, b)
    # violation of EMB: a < b but the doc with b may sort before (or tie with) the doc with a,
    # in the direction the shortcut assumes (ascending long for reverse=false, descending for true)
    if reverse:
        bad = And(a < b, Not(rm * c > 0))
    else:
        bad = And(a < b, Not(rm * c < 0))
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(domain(enc, a, sa), domain(enc, b, sb), bad)
    t0 = time.time()
    r = s.check()
    dt = round(time.time() - t0, 3)
    out = {"sort": "%s(%s)" % sortkind, "reverse": reverse, "encoding": enc,
           "documented_pairing": enc in DOCUMENTED[sortkind], "result": str(r), "secs": dt}
    if r == sat:
        m = s.model()
        av = m.eval(a, model_completion=True).as_signed_long()
        bv = m.eval(b, model_completion=True).as_signed_long()
        cv = m.eval(c, model_completion=True)
        out.update({"witness_a": av, "witness_b": bv, "cmp_key": str(simplify(cv)),
                    "safe": False})
    elif r == unsat:
        out["safe"] = True
    else:
        out["safe"] = None
    return out


def main():
    timeout_ms = int(sys.argv[1]) if len(sys.argv) > 1 else 120000
    rows = []
    for sk in SORTS:
        for reverse in (False, True):
            for enc in ENCODINGS:
                r = check(sk, reverse, enc, timeout_ms)
                rows.append(r)
                print(json.dumps(r), flush=True)
    safe_doc = sorted({r["sort"] + "/" + r["encoding"] for r in rows
                       if r["documented_pairing"] and r["safe"] is True})
    unsafe_doc = sorted({r["sort"] + "/" + r["encoding"] for r in rows
                         if r["documented_pairing"] and r["safe"] is False})
    unknown = sorted({r["sort"] + "/" + r["encoding"] + ("/rev" if r["reverse"] else "")
                      for r in rows if r["safe"] is None})
    print("SUMMARY safe documented pairings:", safe_doc)
    print("SUMMARY unsafe documented pairings:", unsafe_doc)
    print("SUMMARY unknown (timeout):", unknown)
    json.dump(rows, open(sys.argv[2], "w"), indent=1) if len(sys.argv) > 2 else None


if __name__ == "__main__":
    main()


def extra_witnesses(timeout_ms=60000):
    """For the unsafe documented pairings: a witness with ordinary (finite, non-NaN, in-range-int
    where relevant) values, and a tie witness (cmp == 0 with a != b) if one exists."""
    rows = []
    cases = [(("SortField", "FLOAT"), "float-raw"), (("SortField", "DOUBLE"), "double-raw"),
             (("SortField", "INT"), "long>int"), (("SortedNumericSortField", "INT"), "long>int")]
    for sk, enc in cases:
        for kind in ("ordinary", "tie"):
            a, b = BitVec("a", 64), BitVec("b", 64)
            sa, sb = BitVec("sa", 64), BitVec("sb", 64)
            c = cmp(sk, a, b)
            s = Solver()
            s.set("timeout", timeout_ms)
            s.add(domain(enc, a, sa), domain(enc, b, sb), a < b)
            if sk[1] == "FLOAT":
                fa, fb = fpBVToFP(to_int(a), Float32()), fpBVToFP(to_int(b), Float32())
            elif sk[1] == "DOUBLE":
                fa, fb = fpBVToFP(a, Float64()), fpBVToFP(b, Float64())
            else:
                fa = fb = None
            if kind == "ordinary":
                s.add(c > 0)
                if fa is not None:
                    from z3 import fpIsInf, fpIsZero
                    s.add(Not(fpIsNaN(fa)), Not(fpIsNaN(fb)), Not(fpIsInf(fa)), Not(fpIsInf(fb)),
                          Not(fpIsZero(fa)), Not(fpIsZero(fb)))
            else:
                s.add(c == 0)
            r = s.check()
            row = {"sort": "%s(%s)" % sk, "encoding": enc, "kind": kind, "result": str(r)}
            if r == sat:
                m = s.model()
                row["a"] = m.eval(a, model_completion=True).as_signed_long()
                row["b"] = m.eval(b, model_completion=True).as_signed_long()
                if fa is not None:
                    row["a_value"] = str(m.eval(fa, model_completion=True))
                    row["b_value"] = str(m.eval(fb, model_completion=True))
            rows.append(row)
            print("EXTRA " + json.dumps(row), flush=True)
    return rows


if __name__ == "__main__" and len(sys.argv) > 3 and sys.argv[3] == "extra":
    json.dump(extra_witnesses(), open(sys.argv[2].replace(".json", ".extra.json"), "w"), indent=1)

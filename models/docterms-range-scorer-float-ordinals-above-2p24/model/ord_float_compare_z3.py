"""Z3 model of DocTermsIndexDocValues.getRangeScorer's match test (main e1faeba08bd).

Mapping to Java (lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/DocTermsIndexDocValues.java):
  b, ord               int ll / uu (lines 149-150, exact ords from lookupTerm) and ordVal(doc) (line 156)
  F(x) = fp(RNE, x)    JLS 5.1.2 int->float widening (round to nearest, ties to even): `float docVal = ordVal(doc)`
                        (line 156) and the promotion of ll/uu to float in `docVal >= ll && docVal <= uu` (line 157, JLS 5.6.2)
  0 <= ord, b < MAXORD ords of a segment: 0 <= ord < getValueCount() <= maxDoc <= IndexWriter.MAX_DOCS = 2^31-129
Exact (intended) test: ord >= b (lower bound), ord <= b (upper bound), as before LUCENE-9258 (int ord compare).
"""
from z3 import BitVec, BitVecVal, Solver, And, ULT, ULE, UGE, fpSignedToFP, RNE, Float32, fpGEQ, fpLEQ, fpLT, fpGT, sat, unsat

MAXORD = 2**31 - 129
ord_, b = BitVec("ord", 32), BitVec("b", 32)
F = lambda x: fpSignedToFP(RNE(), x, Float32())
legal = And(ULT(ord_, BitVecVal(MAXORD, 32)), ULT(b, BitVecVal(MAXORD, 32)))

def q(name, *c, expect):
    s = Solver(); s.add(legal, *c); r = s.check()
    m = s.model() if r == sat else None
    wit = "" if m is None else " witness ord=%d b=%d" % (m[ord_].as_long(), m[b].as_long())
    print("%-62s %-6s (expect %s)%s" % (name, r, expect, wit))
    assert str(r) == expect, name
    return m

# 1. Never drops an in-range value (int->float is monotone).
q("Q1 lower: ord >= b but float test rejects", UGE(ord_, b), fpLT(F(ord_), F(b)), expect="unsat")
q("Q2 upper: ord <= b but float test rejects", ULE(ord_, b), fpGT(F(ord_), F(b)), expect="unsat")
# 2. Admits out-of-range values: exists.
q("Q3 lower: ord < b admitted (reachable)", ULT(ord_, b), fpGEQ(F(ord_), F(b)), expect="sat")
q("Q4 upper: ord > b admitted (reachable)", ULT(b, ord_), fpLEQ(F(ord_), F(b)), expect="sat")
# 3. Only above 2^24: every ord <= 2^24 is exact, so no wrong match unless max(ord, b) > 2^24.
P24 = BitVecVal(2**24, 32)
q("Q5 any wrong match with ord, b <= 2^24", ULE(ord_, P24), ULE(b, P24),
  ULT(ord_, b), fpGEQ(F(ord_), F(b)), expect="unsat")
q("Q6 the repro case ord=2^24, b=2^24+1 is admitted", ord_ == P24, b == P24 + 1, fpGEQ(F(ord_), F(b)), expect="sat")
# 4. Window size per bound: max (b - ord) of a wrongly admitted ord, over all legal ords.
for k in (128, 129):
    q("Q7 lower: some admitted ord with b - ord >= %d" % k, ULT(ord_, b), b - ord_ >= k, ULT(BitVecVal(0,32), b - ord_),
      fpGEQ(F(ord_), F(b)), expect="sat" if k == 128 else "unsat")
# 5. For bounds in [2^24, 2^25) (dictionaries of up to 33.5M terms) the window is at most 2 values per bound
#    (spacing 2; ties-to-even makes rounding classes of 3 ints around each multiple of 4), not 1 as the plan said.
P25 = BitVecVal(2**25, 32)
for k, e in ((2, "sat"), (3, "unsat")):
    q("Q8 lower in [2^24,2^25): admitted ord with b - ord >= %d" % k, ULE(P24, b), ULT(b, P25), ULT(ord_, b),
      UGE(b - ord_, BitVecVal(k, 32)), fpGEQ(F(ord_), F(b)), expect=e)
print("ALL OBLIGATIONS AS EXPECTED")

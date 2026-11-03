# Mutation check for sortable_vs_raw.py P2': replace the buggy decode (raw longBitsToDouble of the sortable long)
# by the fixed decode (sortableLongToDouble). P2' must then be REFUTED (a counterexample exists), i.e. the obligation
# only holds because of the raw decode at DocValuesStats.java:436.
from z3 import BitVec, BitVecVal, Extract, Solver, Not, And, fpBVToFP, Float64, fpIsNaN, fpEQ, sat
MASK = BitVecVal(0x7FFFFFFFFFFFFFFF, 64)
s_ = lambda b: b ^ ((b >> 63) & MASK)
fp = lambda b: fpBVToFP(b, Float64())
b = BitVec('b', 64); v = fp(b)
dec_fix = fp(s_(s_(b)))  # longBitsToDouble(sortableDoubleBits(doubleToSortableLong bits))
s = Solver(); s.add(Not(fpIsNaN(v)), Extract(63, 63, b) == 1, Not(Not(And(Not(fpIsNaN(dec_fix)), fpEQ(dec_fix, v)))))
r = s.check(); print("P2' with fixed decoder: negation", r, "->", "REFUTED as expected (mutation killed)" if r == sat else "UNEXPECTED")
if r == sat: print("   example bits:", hex(s.model()[b].as_long()))

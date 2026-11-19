"""Unit scoring-term-join-unknown-term-count: for which inputs is the cost that TermsIncludingScoreQuery hands to BitSetIterator negative?

Mapping (branch_11x 89e68e0f3a50; the file is the same on main 596ef11ddf96):
  maxDoc   int   context.reader().maxDoc()                     TermsIncludingScoreQuery.java:182
  size     long  terms.size()                                  TermsIncludingScoreQuery.java:182, Terms.java:98-103
  cost     long  maxDoc * size (the int is widened, the product wraps at 64 bits)   TermsIncludingScoreQuery.java:182
  check          cost < 0  ->  IllegalArgumentException          BitSetIterator.java:68-70
  fix      long  size < 0 ? maxDoc : maxDoc * size              fix/src (the candidate's one-line fix shape)

Preconditions (the documented limits):
  1 <= maxDoc <= IndexWriter.MAX_DOCS = 2^31 - 129   (a leaf with a non-null Terms has at least one document)
  size == -1 ("this measure isn't stored by the codec") or size >= 0 ("the number of terms for this field")

Java arithmetic is modelled exactly with 64-bit bit-vectors. Every sat answer prints its model; the claimed one
(size == -1) is replayed by the JUnit of the candidate, the other one (overflow) is a bound only and is not claimed.
"""
from z3 import BitVec, BitVecVal, SignExt, Solver, Optimize, And, Or, Not, If, sat, unsat

MAX_DOCS = 2**31 - 129
maxDoc = BitVec("maxDoc", 32)
size = BitVec("size", 64)
md64 = SignExt(32, maxDoc)
cost = md64 * size
fix = If(size < 0, md64, md64 * size)
pre_maxdoc = And(maxDoc >= 1, maxDoc <= BitVecVal(MAX_DOCS, 32))


def check(name, *constraints, expect):
    s = Solver()
    s.add(*constraints)
    r = s.check()
    line = "%-74s %s" % (name, r)
    if r == sat:
        m = s.model()
        md, sz = m.eval(maxDoc, True).as_signed_long(), m.eval(size, True).as_signed_long()
        prod = (md * sz + 2**63) % 2**64 - 2**63
        line += "  witness maxDoc=%d size=%d cost=%d" % (md, sz, prod)
    print(line + ("" if r == expect else "   UNEXPECTED (expected %s)" % expect))
    return r == expect


ok = True
# Q1: the documented value. With size == -1 the cost is negative for EVERY legal maxDoc (no legal maxDoc gives >= 0).
ok &= check("Q1  size == -1 and cost >= 0 (a legal leaf that survives the check)", pre_maxdoc, size == -1, cost >= 0, expect=unsat)
ok &= check("Q1b size == -1 and cost < 0 (a witness)", pre_maxdoc, size == -1, cost < 0, maxDoc == 2, expect=sat)
ok &= check("Q1c size == -1 and cost != -maxDoc", pre_maxdoc, size == -1, cost != -md64, expect=unsat)
# Q2: a known size. A negative cost needs a 64-bit overflow of the product.
ok &= check("Q2  size >= 0 and cost < 0 (overflow only)", pre_maxdoc, size >= 0, cost < 0, expect=sat)
ok &= check("Q2b 0 <= size <= 2^32 and cost < 0", pre_maxdoc, size >= 0, size <= 2**32, cost < 0, expect=unsat)
o = Optimize()
o.add(pre_maxdoc, size >= 0, cost < 0)
o.minimize(size)
assert o.check() == sat
m = o.model()
print("Q2c the smallest known size with a negative cost: size=%d at maxDoc=%d (more than 2^32 = %d unique terms in one field of one segment: a bound, not a claim)"
      % (m.eval(size, True).as_signed_long(), m.eval(maxDoc, True).as_signed_long(), 2**32))
# Q3: the fix shape. Never negative for the documented -1, and the stock value for every known size.
ok &= check("Q3  fix: size == -1 and fixed cost < 0", pre_maxdoc, size == -1, fix < 0, expect=unsat)
ok &= check("Q3b fix: size == -1 and fixed cost != maxDoc", pre_maxdoc, size == -1, fix != md64, expect=unsat)
ok &= check("Q3c fix: size >= 0 and fixed cost != stock cost", pre_maxdoc, size >= 0, fix != cost, expect=unsat)
# Vacuity: the preconditions are satisfiable, and a mutant of the fix (size <= 0 in place of size < 0) is caught by Q3c.
ok &= check("V1  the preconditions alone", pre_maxdoc, Or(size == -1, size >= 0), expect=sat)
mut = If(size <= 0, md64, md64 * size)
ok &= check("V2  mutant fix (size <= 0): size >= 0 and mutant cost != stock cost", pre_maxdoc, size >= 0, mut != cost, expect=sat)
print("ALL AS EXPECTED" if ok else "SOMETHING UNEXPECTED")

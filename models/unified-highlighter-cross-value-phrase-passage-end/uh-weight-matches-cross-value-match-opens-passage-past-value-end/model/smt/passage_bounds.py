"""Unbounded companion to the bounded-exhaustive harness: Z3 check of FieldHighlighter's passage bounds.

Model element -> Java line (lucene/highlighter/src/java/org/apache/lucene/search/uhighlight/FieldHighlighter.java, main 5adff78bb168)
  opens   : start >= previous passage end (= lastPrev)            :168
  pStart  : min(start, max(preceding(max(start+1, center)), lastPrev)) :178-182
  pEnd    : max(end, min(following(min(end-1, center)), len))        :183-186 (STOCK)
  pEndFix : first separator in (start, pEnd) if any, else pEnd       (fix sketch)
  printed : DefaultPassageFormatter.format prints content[pStart, pEnd) contiguously (:64-95)
The break iterator results prec/foll are left FREE (any int, incl. DONE = -1): no assumption about the iterator.
Claim S (stock): a match with start < sep < end that opens a passage always gets sep in [pStart, pEnd) -> U+0000 printed.
Claim F (fixed): with pEndFix the separator is never inside [pStart, pEndFix), and pEndFix > start (match start stays inside).
"""
from z3 import Ints, Solver, If, And, Or, Not, sat, unsat

start, end, sep, ln, prec, foll, lastPrev = Ints("start end sep len prec foll lastPrev")
zmin = lambda a, b: If(a < b, a, b)
zmax = lambda a, b: If(a > b, a, b)
pre = And(0 <= start, start < sep, sep < end, end <= ln, 0 <= lastPrev, lastPrev <= start)
pStart = zmin(start, zmax(prec, lastPrev))
pEnd = zmax(end, zmin(foll, ln))

s = Solver(); s.add(pre, Not(And(pStart <= sep, sep < pEnd)))
r1 = s.check(); print("S: exists a crossing, passage-opening match whose passage does NOT contain the separator? ->", r1)

pEndFix = If(And(start < sep, sep < pEnd), sep, pEnd)  # first separator after start (only one in a 2-value model)
f = Solver(); f.add(pre, Or(And(pStart <= sep, sep < pEndFix), pEndFix <= start))
r2 = f.check(); print("F: with the fix, can the separator still be inside the passage, or the match start fall outside? ->", r2)

# vacuity: the precondition is satisfiable and the stock passage really reaches past the separator
v = Solver(); v.add(pre); print("vacuity: precondition satisfiable ->", v.check())
ok = (r1 == unsat and r2 == unsat)
print("PROVED (both claims, all integer values, break-iterator results unconstrained)" if ok else "NOT PROVED")
raise SystemExit(0 if ok else 1)

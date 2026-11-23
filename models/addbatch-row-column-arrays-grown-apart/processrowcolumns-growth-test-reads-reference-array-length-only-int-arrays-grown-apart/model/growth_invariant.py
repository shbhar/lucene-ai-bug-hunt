#!/usr/bin/env python3
"""SMT queries (Z3) on the growth block of IndexingChain.processRowColumns.

model element                         IndexingChain.java (branch_11x 262e019500ec and main b5a4546a5964)
state (n, a, h)                       n = numRowCols (:853); a = adapters.length (:854); h = heads.length = rowPfIndices.length
                                      (:855-856; the two int arrays start equal and are grown by the same call with the same
                                      arguments, so one variable stands for both)
guard  n >= a                         :867  if (numRowCols >= adapters.length)
a' = grow(a, n+1, Gref)               :868  ArrayUtil.grow(adapters, numRowCols + 1)       (ArrayUtil.java:230-236)
h' = grow(h, n+1, Gint)               :869-870  ArrayUtil.grow(heads|rowPfIndices, numRowCols + 1)  (ArrayUtil.java:376-378, growInRange :328-346)
grow(len, m, G) = G(m) if len < m else len
safe  n < a' and n < h'               :873-875  the writes adapters[n], rowPfIndices[n], heads[n]
Gref, Gint                            ArrayUtil.oversize(m, bytesPerElement) (ArrayUtil.java:133-205). Its javadoc promises
                                      only 'an array size >= minTargetSize' (:121).

Q1  contract only: Gref and Gint are ANY functions with G(m) >= m. Is an out-of-bounds write reachable from (0,4,4)?
Q2  Gref == Gint (one element size, the layout with compressed references): is 'a == h' inductive and the write safe?
Q3  the stock arithmetic of a 64-bit JVM, references of 8 bytes and ints of 4: the least n with an unsafe write.
Q4  the repair 'one new length for the three arrays' under ANY G(m) >= m: inductive and safe?
Q5  the repair 'the test reads every length' under ANY Gref, Gint with G(m) >= m: safe at every step?
"""
import sys
from z3 import And, Function, If, Int, IntSort, Not, Or, Solver, sat, unsat

out = []


def say(s):
    out.append(s)
    print(s)


def grow(length, m, G):
    return If(length < m, G(m), length)


def oversize64(m, bytes_per_element):
    """ArrayUtil.oversize on a 64-bit JVM for a concrete 0 < m well below MAX_ARRAY_LENGTH (ArrayUtil.java:157-188)."""
    extra = max(m >> 3, 3)
    new = m + extra
    if bytes_per_element == 4:
        return (new + 1) & 0x7ffffffe         # round up to a multiple of 2
    if bytes_per_element == 8:
        return new                            # no rounding
    raise ValueError(bytes_per_element)


# ---- Q1: reachability under the documented promise only, bounded unrolling
K = 8
s = Solver()
Gref = Function('Gref', IntSort(), IntSort())
Gint = Function('Gint', IntSort(), IntSort())
for mm in range(1, K + 1):   # the promise at every argument the unrolling uses (elsewhere G(m) = m completes the functions)
    s.add(Gref(mm) >= mm, Gint(mm) >= mm)
a = [Int('a%d' % i) for i in range(K + 1)]
h = [Int('h%d' % i) for i in range(K + 1)]
s.add(a[0] == 4, h[0] == 4)
bad = []
for n in range(K):
    fired = n >= a[n]
    s.add(a[n + 1] == If(fired, grow(a[n], n + 1, Gref), a[n]))
    s.add(h[n + 1] == If(fired, grow(h[n], n + 1, Gint), h[n]))
    bad.append(Or(n >= a[n + 1], n >= h[n + 1]))
s.add(Or(bad))
r = s.check()
say('Q1 contract-only (any Gref, Gint with G(m) >= m), unrolled %d columns: %s' % (K, r))
if r == sat:
    mo = s.model()
    tr = [(mo.eval(a[i]).as_long(), mo.eval(h[i]).as_long()) for i in range(K + 1)]
    first = next(n for n in range(K) if n >= tr[n + 1][0] or n >= tr[n + 1][1])
    say('   a trace (adapters.length, int-array length) after each column: %s' % tr[1:first + 2])
    say('   first unsafe write at numRowCols=%d: the documented promise of oversize alone does not make the block safe' % first)

# ---- Q2: one growth function for both -> a == h is inductive and the write is safe (unbounded)
s = Solver()
G = Function('G', IntSort(), IntSort())
n, a0, h0 = Int('n'), Int('a'), Int('h')
s.add(G(n + 1) >= n + 1)       # the promise at the one argument the step uses (fewer axioms: unsat here is unsat with all)
fired = n >= a0
a1 = If(fired, grow(a0, n + 1, G), a0)
h1 = If(fired, grow(h0, n + 1, G), h0)
s.add(n >= 0, a0 == h0, a0 >= 4)
s.add(Not(And(a1 == h1, n < a1, n < h1)))
r = s.check()
say('Q2 one growth function for the three arrays (references of 4 bytes): negation of [a == h inductive and write safe]: %s%s'
    % (r, ' -> proved for every n' if r == unsat else ' -> NOT proved'))

# ---- Q3: stock arithmetic, references of 8 bytes, ints of 4: least failing n by unrolling
K = 64
s = Solver()
a = [Int('a%d' % i) for i in range(K + 1)]
h = [Int('h%d' % i) for i in range(K + 1)]
s.add(a[0] == 4, h[0] == 4)
for n in range(K):
    fired = n >= a[n]
    s.add(a[n + 1] == If(fired, If(a[n] < n + 1, oversize64(n + 1, 8), a[n]), a[n]))
    s.add(h[n + 1] == If(fired, If(h[n] < n + 1, oversize64(n + 1, 4), h[n]), h[n]))
r = s.check()
mo = s.model()
tr = [(mo.eval(a[i]).as_long(), mo.eval(h[i]).as_long()) for i in range(K + 1)]
unsafe = [n for n in range(K) if n >= tr[n + 1][0] or n >= tr[n + 1][1]]
say('Q3 stock arithmetic, 64-bit, references of 8 bytes: the trace is unique (%s); least unsafe numRowCols in 0..%d: %s'
    % (r, K - 1, unsafe[0] if unsafe else 'none'))
if unsafe:
    n0 = unsafe[0]
    say('   lengths (adapters, int arrays) when column %d is written: %s; distinct length pairs on the way: %s'
        % (n0 + 1, tr[n0 + 1], sorted(set(tr[1:n0 + 2]))))
# the same with references of 4 bytes: no unsafe write in the unrolling (Q2 proves it for every n)
s = Solver()
a = [Int('a%d' % i) for i in range(K + 1)]
h = [Int('h%d' % i) for i in range(K + 1)]
s.add(a[0] == 4, h[0] == 4)
for n in range(K):
    fired = n >= a[n]
    s.add(a[n + 1] == If(fired, If(a[n] < n + 1, oversize64(n + 1, 4), a[n]), a[n]))
    s.add(h[n + 1] == If(fired, If(h[n] < n + 1, oversize64(n + 1, 4), h[n]), h[n]))
s.add(Or([Or(n >= a[n + 1], n >= h[n + 1]) for n in range(K)]))
say('Q3b stock arithmetic, 64-bit, references of 4 bytes: an unsafe write within %d columns: %s' % (K, s.check()))

# ---- Q4: the repair 'one new length' under any G(m) >= m
s = Solver()
G = Function('G', IntSort(), IntSort())
s.add(G(n + 1) >= n + 1)
fired = n >= a0
a1 = If(fired, G(n + 1), a0)
h1 = If(fired, G(n + 1), h0)
s.add(n >= 0, a0 == h0, a0 >= 4)
s.add(Not(And(a1 == h1, n < a1, n < h1)))
r = s.check()
say('Q4 repair "one new length for the three arrays", any G(m) >= m: negation of [a == h inductive and write safe]: %s%s'
    % (r, ' -> proved for every n' if r == unsat else ' -> NOT proved'))

# ---- Q5: the repair 'the test reads every length' under any Gref, Gint with G(m) >= m
s = Solver()
Gref = Function('Gref', IntSort(), IntSort())
Gint = Function('Gint', IntSort(), IntSort())
s.add(Gref(n + 1) >= n + 1, Gint(n + 1) >= n + 1)
fired = Or(n >= a0, n >= h0)
a1 = If(fired, grow(a0, n + 1, Gref), a0)
h1 = If(fired, grow(h0, n + 1, Gint), h0)
s.add(n >= 0, a0 >= 0, h0 >= 0)
s.add(Not(And(n < a1, n < h1)))
r = s.check()
say('Q5 repair "the test reads every length", any Gref, Gint with G(m) >= m, any lengths: negation of [write safe]: %s%s'
    % (r, ' -> proved for every n' if r == unsat else ' -> NOT proved'))

if len(sys.argv) > 1:
    open(sys.argv[1], 'w').write('\n'.join(out) + '\n')

"""Z3 model of AbstractDictionary.hash2(char[]) and the probe loop of BigramDictionary.getBigramItemIndex.

Model -> Java mapping (lucene/analysis/smartcn/src/java/org/apache/lucene/analysis/cn/smart/hhmm/):
  stepA(h, d) = ((h << 5) + h) + d & 0x00FF   AbstractDictionary.java:195  ('+' binds tighter than '&')
  stepB(h, d) = ((h << 5) + h) + d >> 8       AbstractDictionary.java:196  ('+' binds tighter than '>>')
  hash2 = fold of (stepA; stepB) over the chars, start 5381  AbstractDictionary.java:189-200
  probe: index_i = (hash1 + i*hash2) % P, i = 1..P-1         BigramDictionary.java:197-216, P = 402137
Java int arithmetic is modelled exactly with 32-bit bit-vectors (signed >> = arithmetic shift).

Q1 (UNSAT expected): after any char, stepB's result lies in [0, 288]  -> hash2 in [0, 288] for every key of length >= 1.
Q2 (UNSAT expected): hash2 == 0 implies the last char is < 256 (so only keys ending in a Latin-1 char can get step 0).
Q3 (SAT expected):   a key "<word>@," with hash2 == 0 exists for some previous hash value (witness).
Q4 (UNSAT expected): with hash2 == 0 every probe index equals hash1, i.e. the loop never leaves the home slot
                     (so the fix "check only the home slot when hash2 == 0" returns exactly what the loop returns).
"""
import sys
from z3 import BitVec, BitVecVal, Solver, And, Or, ULT, ULE, UGE, Not, sat, unsat, URem, ZeroExt

def stepA(h, d):
    return (((h << 5) + h) + d) & 0xFF

def stepB(h, d):
    return (((h << 5) + h) + d) >> 8   # arithmetic shift on a 32-bit int, as in Java

def res(s):
    r = s.check()
    return r

ok = True
# Q1: for any h in [0, 288] or h = 5381 (start), any char d in [0, 65535]: stepB(stepA(h,d), d) in [0, 288]
h = BitVec('h', 32); d = BitVec('d', 32)
s = Solver()
s.add(Or(And(h >= 0, h <= 288), h == 5381), d >= 0, d <= 65535)
out = stepB(stepA(h, d), d)
s.add(Or(out < 0, out > 288))
r = res(s); print('Q1 hash2 outside [0,288] possible?', r); ok &= (r == unsat)

# Q2: hash2 == 0 and last char >= 256
s = Solver(); s.add(Or(And(h >= 0, h <= 288), h == 5381), d >= 256, d <= 65535, stepB(stepA(h, d), d) == 0)
r = res(s); print('Q2 hash2 == 0 with last char >= 256?', r); ok &= (r == unsat)

# Q3: witness: previous hash in [0,288] (after the chars of "<word>@"), last char ',' (0x2C), hash2 == 0
s = Solver(); s.add(h >= 0, h <= 288, stepB(stepA(h, BitVecVal(0x2C, 32)), BitVecVal(0x2C, 32)) == 0)
r = res(s); print('Q3 hash2 == 0 for a key ending in "," reachable?', r); ok &= (r == sat)
if r == sat:
    m = s.model(); print('   witness previous hash =', m[h])
    # how many previous-hash values in [0,288] give step 0 for ','
    cnt = sum(1 for hv in range(289) if ((((hv * 33) + 0x2C) & 0xFF) * 33 + 0x2C) >> 8 == 0)
    print('   previous-hash values (of 289) giving step 0 for ",":', cnt)

# Q4: P = 402137; hash1 in [0, P); hash2 == 0; i in [1, P-1]: (hash1 + i*hash2) % P != hash1 ?
P = 402137
h1 = BitVec('h1', 32); i = BitVec('i', 32)
s = Solver(); s.add(h1 >= 0, h1 < P, i >= 1, i < P)
idx = URem(h1 + i * BitVecVal(0, 32), BitVecVal(P, 32))
s.add(idx != h1)
r = res(s); print('Q4 zero step leaves the home slot?', r); ok &= (r == unsat)

print('ALL AS EXPECTED' if ok else 'UNEXPECTED RESULT')
sys.exit(0 if ok else 1)

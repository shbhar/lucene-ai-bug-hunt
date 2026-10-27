# SMT (Z3 bit-vectors) proofs that the specialised byte comparators used by every BKD relate /
# split / leaf-encoding decision agree with the lexicographic unsigned definition used in the
# Dafny model (dafny/Bytes.dfy Cmp), for ALL inputs:
#   ArrayUtil.compareUnsigned4/8   util/ArrayUtil.java:816-826 (Integer/Long.compareUnsigned of big-endian loads)
#   BKDUtil.commonPrefixLength4/8  util/bkd/BKDUtil.java:44-58 (nlz(reverseBytes(LE(a) ^ LE(b))) >>> 3)
#   BKDUtil.equals4/8              util/bkd/BKDUtil.java:91-103
# run: python3 -I z3/comparators.py
import time
from z3 import BitVec, Concat, Extract, If, ULT, UGT, Solver, Or, And, Not, BitVecVal, unsat


def be(bs):   # big-endian load: first byte is most significant
    return Concat(*bs)


def le(bs):   # little-endian load
    return Concat(*reversed(bs))


def lex(a, b):  # Arrays.compareUnsigned sign: first differing byte decides
    r = 0
    for x, y in reversed(list(zip(a, b))):
        r = If(ULT(x, y), -1, If(UGT(x, y), 1, r))
    return r


def common_prefix(a, b):  # number of leading equal bytes
    n = len(a)
    r = n
    for i in reversed(range(n)):
        r = If(a[i] != b[i], i, r)
    return r


def nlz(x, w):  # Integer/Long.numberOfLeadingZeros
    r = w
    for i in range(w):  # highest set bit wins
        r = If(Extract(i, i, x) == 1, w - 1 - i, r)
    return r


def reverse_bytes(x, n):
    return Concat(*[Extract(8 * i + 7, 8 * i, x) for i in range(n)])


def prove(name, claim):
    s = Solver()
    s.add(Not(claim))
    t = time.time()
    r = s.check()
    print(("PROVED " if r == unsat else "FAILED ") + name + f" ({r}, {time.time() - t:.1f}s)", flush=True)
    if r != unsat:
        print("   model:", s.model())


for n in (4, 8):
    a = [BitVec(f"a{i}", 8) for i in range(n)]
    b = [BitVec(f"b{i}", 8) for i in range(n)]
    A, B = be(a), be(b)
    cu = If(ULT(A, B), -1, If(UGT(A, B), 1, 0))  # Integer/Long.compareUnsigned sign
    prove(f"compareUnsigned{n} == lexicographic unsigned byte compare", cu == lex(a, b))
    x = le(a) ^ le(b)
    cpl = nlz(reverse_bytes(x, n), 8 * n) / 8  # >>> 3 on a value in [0, 8n]
    prove(f"commonPrefixLength{n} == common prefix length", cpl == common_prefix(a, b))
    prove(f"equals{n} == bytewise equality", (le(a) == le(b)) == And(*[p == q for p, q in zip(a, b)]))

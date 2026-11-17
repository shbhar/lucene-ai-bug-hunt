"""SMT model of the in-place loop of CharacterUtils.simpleCaseFold (Z3, bit-vectors).

Model element -> Java line (main 01deb8e63e0b, lucene/core/src/java/org/apache/lucene/analysis/CharacterUtils.java;
the file is byte-identical at the tag releases/lucene/10.6.0 and on branch_11x eaa0bfbcfa4c):
  loop()                 -> :85-89   while (i < limit) { i += Character.toChars(foldCase(codePointAt(buffer, i, limit)), buffer, i); }
  code_point_at()        -> java.lang.Character.codePointAt(char[], int, int): a high surrogate followed inside the
                            limit by a low surrogate is one code point, any other unit is itself
  to_chars()             -> java.lang.Character.toChars(int, char[], int): one unit for a BMP code point, two for a
                            supplementary one; writing the second unit past the array end throws
  f (uninterpreted)      -> UnicodeUtil.foldCase (:88; UnicodeUtil.java:691-693), any mapping from code points to
                            code points that maps scalar values to scalar values and leaves a lone surrogate alone
  reference              -> the contract: the UTF-16 form of f(c) for each code point c of the input, in order
  ASCII fast path :82-84,:91-93 is not modelled: it rewrites one unit by one unit and hands over to the loop.

Scope: every well-formed input of 1 to 4 UTF-16 units (11 shapes of one- and two-unit code points), all values
symbolic, the array as long as the limit (the tightest case for a growing write).

Queries:
  Q1  f keeps the UTF-16 length of every code point  ==> the loop's result equals the reference.   expected: unsat (no counterexample) for every shape
  Q2  some code point may get SHORTER (never longer) ==> can the result hold an unpaired surrogate? expected: sat, with a witness
  Q3  some code point may get LONGER (never shorter) ==> can the loop, without throwing, give a result with an unpaired surrogate
      or with fewer code points than the input (the next unit overwritten)? expected: sat. No such mapping exists in the
      tables that were executed (harness/out/sweep-*.txt: 0 longer); the query only records the other direction.
  Q4  vacuity: Q1 without the length premise must be sat (the premise is what makes Q1 hold).
"""
import itertools
import sys
from z3 import And, BitVec, BitVecVal, BoolVal, Concat, Extract, Function, BitVecSort, If, Not, Or, Solver, ULE, ULT, UGE, ZeroExt, sat, unsat

W = 21  # code points
U = 16  # UTF-16 units
f = Function("f", BitVecSort(W), BitVecSort(W))

def cp(v): return BitVecVal(v, W)
def is_high(u): return And(UGE(u, 0xD800), ULE(u, 0xDBFF))
def is_low(u): return And(UGE(u, 0xDC00), ULE(u, 0xDFFF))
def is_sur_cp(c): return And(UGE(c, cp(0xD800)), ULE(c, cp(0xDFFF)))
def is_supp(c): return UGE(c, cp(0x10000))
def valid_cp(c): return ULE(c, cp(0x10FFFF))
def high_of(c): return Extract(15, 0, ((c - cp(0x10000)) >> 10)) + BitVecVal(0xD800, U)
def low_of(c): return (Extract(15, 0, c) & BitVecVal(0x3FF, U)) + BitVecVal(0xDC00, U)
def join(h, l): return (ZeroExt(W - U, h - BitVecVal(0xD800, U)) << 10) + ZeroExt(W - U, l - BitVecVal(0xDC00, U)) + cp(0x10000)

def sel(buf, i, n):
    """buf[i] for a symbolic index i (3-bit) over a python list of n units."""
    r = buf[n - 1]
    for k in range(n - 2, -1, -1):
        r = If(i == k, buf[k], r)
    return r

def loop(buf, n, applied):
    """Unrolled model of the while loop for a buffer of n units with limit == n. Returns (buffer, threw)."""
    I = 3
    i = BitVecVal(0, I)
    threw = BoolVal(False)
    buf = list(buf)
    for _ in range(n):  # at most n iterations: every iteration advances by at least one unit
        active = And(ULT(i, n), Not(threw))
        u0 = sel(buf, i, n)
        has_next = ULT(i + 1, n)
        u1 = sel(buf, i + 1, n)
        pair = And(is_high(u0), has_next, is_low(u1))
        c = If(pair, join(u0, u1), ZeroExt(W - U, u0))
        m = f(c)
        applied.append(c)
        two = is_supp(m)
        threw = Or(threw, And(active, two, Not(has_next)))  # toChars writes index i+1 past the array end
        new = []
        for k in range(n):
            w0 = And(active, i == k)
            w1 = And(active, two, i + 1 == k)
            new.append(If(w0, If(two, high_of(m), Extract(15, 0, m)), If(w1, low_of(m), buf[k])))
        buf = new
        i = If(active, i + If(two, BitVecVal(2, I), BitVecVal(1, I)), i)
    return buf, threw

def malformed(buf):
    n = len(buf)
    bad = []
    for k in range(n):
        lone_low = And(is_low(buf[k]), Not(is_high(buf[k - 1])) if k > 0 else BoolVal(True))
        lone_high = And(is_high(buf[k]), Not(is_low(buf[k + 1])) if k + 1 < n else BoolVal(True))
        bad += [lone_low, lone_high]
    return Or(bad)

def shapes(max_units=4):
    out = []
    for k in range(1, max_units + 1):
        for s in itertools.product((1, 2), repeat=k):
            if sum(s) <= max_units:
                out.append(s)
    return out

def run(shape, mode):
    s = Solver()
    cps = [BitVec("c%d" % j, W) for j in range(len(shape))]
    buf, ref_cps = [], []
    for c, units in zip(cps, shape):
        if units == 1:
            s.add(ULT(c, cp(0x10000)), Not(is_sur_cp(c)))
            buf.append(Extract(15, 0, c))
        else:
            s.add(is_supp(c), valid_cp(c))
            buf += [high_of(c), low_of(c)]
        ref_cps.append(c)
    n = len(buf)
    applied = []
    out, threw = loop(buf, n, applied)
    pts = applied + ref_cps
    for x in pts:  # f: code points to code points, scalar values to scalar values, a lone surrogate stays
        s.add(valid_cp(f(x)), If(is_sur_cp(x), f(x) == x, Not(is_sur_cp(f(x)))))
    same_len = [is_supp(f(x)) == is_supp(x) for x in pts]
    not_longer = [Or(Not(is_supp(f(x))), is_supp(x)) for x in pts]
    not_shorter = [Or(is_supp(f(x)), Not(is_supp(x))) for x in pts]
    # the reference under "same length": same shape, unit by unit
    ref = []
    for c, units in zip(ref_cps, shape):
        ref += [Extract(15, 0, f(c))] if units == 1 else [high_of(f(c)), low_of(f(c))]
    differs = Or([out[k] != ref[k] for k in range(n)])
    if mode == "Q1":
        s.add(same_len); s.add(Or(threw, differs))
    elif mode == "Q4":
        s.add(Or(threw, differs))
    elif mode == "Q2":
        s.add(not_longer); s.add(Not(threw)); s.add(malformed(out))
    elif mode == "Q3":
        s.add(not_shorter); s.add(Not(threw))
        # some input code point grows, and the unit after it is not what the reference has there: it was overwritten
        s.add(Or([And(Not(is_supp(c)), is_supp(f(c))) for c in ref_cps]))
        # the unit after the grown code point was overwritten: the result either holds an unpaired surrogate or
        # has fewer code points than the input
        cnt_in = len(shape)
        firsts = [If(Or(k == 0, Not(is_low(out[k]))), 1, 0) for k in range(n)]
        s.add(Or(malformed(out), sum(firsts) < cnt_in))
    r = s.check()
    w = ""
    if r == sat and mode in ("Q2", "Q3"):
        m = s.model()
        ins = [m.eval(c, model_completion=True).as_long() for c in cps]
        fs = [m.eval(f(c), model_completion=True).as_long() for c in cps]
        o = [m.eval(x, model_completion=True).as_long() for x in out]
        w = " input=" + " ".join("U+%04X" % x for x in ins) + " f=" + " ".join("U+%04X" % x for x in fs) + " loop result units=" + " ".join("%04X" % x for x in o)
    return r, w

def main():
    ok = True
    for mode, want in (("Q1", unsat), ("Q4", sat), ("Q2", sat), ("Q3", sat)):
        for shape in shapes():
            if mode == "Q3" and (len(shape) < 2 or 1 not in shape[:-1]):
                continue  # a growing write needs a one-unit code point with something after it
            if mode == "Q2" and 2 not in shape:
                continue  # a shrinking write needs a two-unit code point
            if mode == "Q4" and 2 not in shape and len(shape) < 2:
                continue  # a single one-unit code point in a one-unit array: the only failure is the throw
            r, w = run(shape, mode)
            flag = "as expected" if r == want else "UNEXPECTED"
            ok &= r == want
            print("%s shape=%s -> %s (%s)%s" % (mode, "".join(map(str, shape)), r, flag, w))
    print("ALL AS EXPECTED" if ok else "SOME UNEXPECTED")
    sys.exit(0 if ok else 1)

main()

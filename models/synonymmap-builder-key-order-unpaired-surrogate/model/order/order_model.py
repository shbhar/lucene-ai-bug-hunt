#!/usr/bin/env python3
"""
Order model for SynonymMap.Builder.build (Z3, strings of 0..3 UTF-16 units, every unit value 0..0xFFFF).

Mapping (model element -> Java, Lucene 10.6.0 tag = 4ae946e5a9b):
  old_cmp   -> lucene/core/src/java/org/apache/lucene/util/CharsRef.java:176-209
               (UTF16SortedAsUTF8Comparator.compare: first differing unit, fixed up only when both units are
               >= 0xD800: units >= 0xE000 move down by 0x800, surrogate units move up by 0x2000)
               called from lucene/analysis/common/src/java/org/apache/lucene/analysis/synonym/SynonymMap.java:243
  decode    -> lucene/core/src/java/org/apache/lucene/util/fst/Util.java (toUTF32(CharSequence, IntsRefBuilder):
               Character.codePointAt + Character.charCount; an unpaired surrogate unit keeps its own value)
               called from SynonymMap.java:290 (fstCompiler.add(Util.toUTF32(input, scratchIntsRef), ...))
  new_cmp   -> lucene/core/src/java/org/apache/lucene/util/IntsRef.java (compareTo: lexicographic, then length),
               the order FSTCompiler.add requires (FSTCompiler.java:840 'must be sorted after the previous one
               according to IntsRef#compareTo')
The 11.x comparison (SynonymMap.Builder.compareCodePoints, 2eb462541b1c) is new_cmp by construction: it compares
Character.codePointAt values and then the remaining lengths.

Queries (each for all 16 length pairs la, lb in 0..3):
  T1  well-formed(a) and well-formed(b) and sign(old) != sign(new)            expected UNSAT
  T2  sign(old) != sign(new)  <=>  D(a, b)                                     expected UNSAT of the negation
      D: at the first differing unit, one key holds an UNPAIRED surrogate unit and the other key holds
         (i) a unit >= 0xE000, or (ii) the high half of a well-formed pair that is smaller than the unpaired unit.
  W   witnesses of both classes of D (SAT), replayed on the release jar by a small Java program that is not published here
  V   vacuity: T1 without the well-formedness premise is SAT; a mutant of old_cmp without the fix-up breaks T1.
"""
import sys, itertools, json
from z3 import Int, IntVal, If, And, Or, Not, Solver, sat, unsat, BoolVal

L = 3
def H(u): return And(u >= 0xD800, u <= 0xDBFF)
def Lo(u): return And(u >= 0xDC00, u <= 0xDFFF)
def S(u): return And(u >= 0xD800, u <= 0xDFFF)
def P(h, l): return 0x10000 + (h - 0xD800) * 0x400 + (l - 0xDC00)
def sign(e): return If(e > 0, 1, If(e < 0, -1, 0))

def fix(u):  # CharsRef.java:189-201, applied only when both units are >= 0xD800
    return If(u >= 0xE000, u - 0x800, u + 0x2000)

def old_cmp(a, la, b, lb, fixup=True):
    m = min(la, lb)
    res = IntVal(la - lb)
    for i in reversed(range(m)):
        x, y = a[i], b[i]
        if fixup:
            both = And(x >= 0xD800, y >= 0xD800)
            d = If(both, fix(x) - fix(y), x - y)
        else:
            d = x - y
        res = If(x != y, d, res)
    return res

def decode(u, n):
    """padded list of 3 code points, -1 = end (smaller than every code point)"""
    E = IntVal(-1)
    if n == 0: return [E, E, E]
    if n == 1: return [u[0], E, E]
    p01 = And(H(u[0]), Lo(u[1]))
    if n == 2:
        return [If(p01, P(u[0], u[1]), u[0]), If(p01, E, u[1]), E]
    p12 = And(H(u[1]), Lo(u[2]))
    return [If(p01, P(u[0], u[1]), u[0]),
            If(p01, u[2], If(p12, P(u[1], u[2]), u[1])),
            If(p01, E, If(p12, E, u[2]))]

def new_cmp(a, la, b, lb):
    ca, cb = decode(a, la), decode(b, lb)
    res = IntVal(0)
    for i in reversed(range(L)):
        res = If(ca[i] != cb[i], ca[i] - cb[i], res)
    return res

def paired_high(u, n, i): return And(H(u[i]), Lo(u[i + 1])) if i + 1 < n else BoolVal(False)
def paired_low(u, n, i): return And(H(u[i - 1]), Lo(u[i])) if i >= 1 else BoolVal(False)
def lone(u, n, i): return And(S(u[i]), Not(paired_high(u, n, i)), Not(paired_low(u, n, i)))
def wellformed(u, n): return And([Not(lone(u, n, i)) for i in range(n)]) if n else BoolVal(True)

def D(a, la, b, lb):
    m = min(la, lb)
    res = BoolVal(False)
    for i in reversed(range(m)):
        x, y = a[i], b[i]
        here = Or(And(lone(a, la, i), y >= 0xE000), And(lone(b, lb, i), x >= 0xE000),
                  And(lone(a, la, i), paired_high(b, lb, i), y < x),
                  And(lone(b, lb, i), paired_high(a, la, i), x < y))
        res = If(x != y, here, res)
    return res

def units(name): return [Int(f"{name}{i}") for i in range(L)]
def dom(u): return And([And(x >= 0, x <= 0xFFFF) for x in u])

def check(extra, la, lb, a, b):
    s = Solver(); s.set("timeout", 120000)
    s.add(dom(a), dom(b)); s.add(extra)
    r = s.check()
    w = None
    if r == sat:
        m = s.model()
        w = (["%04X" % m.eval(a[i], model_completion=True).as_long() for i in range(la)],
             ["%04X" % m.eval(b[i], model_completion=True).as_long() for i in range(lb)])
    return r, w

def main():
    a, b = units("a"), units("b")
    out = {"T1": [], "T2": [], "V_no_premise": [], "V_mutant": [], "W": []}
    ok = True
    for la, lb in itertools.product(range(L + 1), repeat=2):
        o, n = old_cmp(a, la, b, lb), new_cmp(a, la, b, lb)
        dis = sign(o) != sign(n)
        r1, w1 = check([wellformed(a, la), wellformed(b, lb), dis], la, lb, a, b)
        out["T1"].append((la, lb, str(r1))); ok &= (r1 == unsat)
        r2, w2 = check([dis != D(a, la, b, lb)], la, lb, a, b)
        out["T2"].append((la, lb, str(r2), w2)); ok &= (r2 == unsat)
        r3, w3 = check([dis], la, lb, a, b)
        out["V_no_premise"].append((la, lb, str(r3), w3))
        om = old_cmp(a, la, b, lb, fixup=False)
        r4, w4 = check([wellformed(a, la), wellformed(b, lb), sign(om) != sign(n)], la, lb, a, b)
        out["V_mutant"].append((la, lb, str(r4), w4))
    # witnesses of the two classes of D at lengths (1,1) and (1,2)
    o, n = old_cmp(a, 1, b, 1), new_cmp(a, 1, b, 1)
    out["W"].append(("class i: unpaired surrogate against a unit >= U+E000",) + check([sign(o) != sign(n), lone(a, 1, 0), b[0] >= 0xE000], 1, 1, a, b))
    o, n = old_cmp(a, 1, b, 2), new_cmp(a, 1, b, 2)
    out["W"].append(("class ii: unpaired surrogate against a smaller high half of a well-formed pair",) + check([sign(o) != sign(n), lone(a, 1, 0), paired_high(b, 2, 0)], 1, 2, a, b))
    for k in ("T1", "T2", "V_no_premise", "V_mutant", "W"):
        print("==", k)
        for row in out[k]: print("  ", row)
    any_sat_no_premise = any(r[2] == "sat" for r in out["V_no_premise"])
    any_sat_mutant = any(r[2] == "sat" for r in out["V_mutant"])
    print("SUMMARY T1 all unsat:", all(r[2] == "unsat" for r in out["T1"]),
          "| T2 all unsat:", all(r[2] == "unsat" for r in out["T2"]),
          "| vacuity: disagreement reachable without the premise:", any_sat_no_premise,
          "| mutant without the fix-up breaks T1:", any_sat_mutant)
    print("RESULT:", "PASS" if ok and any_sat_no_premise and any_sat_mutant else "FAIL")

if __name__ == "__main__":
    main()

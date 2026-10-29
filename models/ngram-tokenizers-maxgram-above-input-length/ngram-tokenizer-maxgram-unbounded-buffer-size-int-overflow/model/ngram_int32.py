"""
Z3 (QF_BV, exact Java int32 wrap) model of NGramTokenizer.init sizing and the incrementToken index arithmetic.

Code under test: Lucene main 645d4fe14a20.

Mapping table (model element -> Java line, lucene/analysis/common/src/java/org/apache/lucene/analysis/ngram/
NGramTokenizer.java unless noted):
  B   = 2*maxGram + 1024            -> init, lines 128-131 (CharacterUtils.newCharacterBuffer(2 * maxGram + 1024));
                                       buffer = new int[B] line 133
  IAE_B  : B < 2                    -> lucene/core/.../analysis/CharacterUtils.java:40-41 ("buffersize must be >= 2")
  T   = 2*maxGram                   -> init line 135 termAtt.resizeBuffer(2 * maxGram)
  TERMLEN = (T > 16) ? T' : 16      -> lucene/core/.../tokenattributes/CharTermAttributeImpl.java:32 (initial
                                       oversize(10, 2) = 16 chars) and :58 (grow only if length < newSize)
  COMPACT : bufferEnd - maxGram - 1 -> line 146
  NEXTOFF : bufferStart + gramSize  -> line 162 (evaluated only when gramSize <= maxGram, '||' short circuit)
  NEXTOFF2: bufferStart + 1 + minGram -> line 163
  TERMEND : bufferStart + gramSize - 1 -> line 195
  CONTAINS: bufferStart + gramSize  -> line 175
  FILLN   : B - bufferEnd' (after compaction) -> line 154; CharacterUtils.java:177 requires 2 <= numChars <= B

Design obligations the streaming tokenizer relies on (stated in the code comment at lines 130-132: "2 * maxGram in
case all code points require 2 chars and + 1024 for buffering"):
  O1  every gram of g <= maxGram code points fits the term buffer: 2*g <= TERMLEN   (toChars line 184)
  O2  after a compaction (bufferEnd' <= maxGram + 1) the refill asks for >= 2 chars: B - (maxGram+1) >= 2
  O3  none of the index expressions above wraps, given the state invariants
      0 <= bufferStart <= bufferEnd <= B, minGram <= gramSize <= maxGram + 1, 1 <= minGram <= maxGram.

Queries:
  part-*   exact partition of maxGram in [1, Integer.MAX_VALUE] by what init does (each region proven by UNSAT of
           its negation, and each boundary shown tight by a SAT witness one past it)
  obl-*    O1/O2/O3 without a bound (SAT witnesses) and with the fix bound maxGram <= CAP (UNSAT = proved)
"""
import json
import sys

from z3 import (BitVec, BitVecVal, And, Or, Not, If, Solver, sat, unsat, ULE, SignExt, ZeroExt)

INT_MAX = 2**31 - 1
MAX_ARRAY_LENGTH = INT_MAX - 16  # ArrayUtil.MAX_ARRAY_LENGTH with a 16-byte array header (compressed oops)
CAP = (INT_MAX - 1025) // 3      # fix-sketch bound used for the O3 proof (any smaller documented limit also works)

results = []


def bv(v):
    return BitVecVal(v, 32)


def wide(x):
    """exact mathematical value of a 32-bit signed bit-vector, as a 64-bit bit-vector"""
    return SignExt(32, x)


def check(name, constraints, expect):
    s = Solver()
    s.set("timeout", 600000)
    s.add(*constraints)
    r = s.check()
    model = None
    if r == sat:
        m = s.model()
        model = {str(d): m[d].as_signed_long() if hasattr(m[d], "as_signed_long") else str(m[d]) for d in m.decls()}
    ok = (str(r) == expect)
    results.append({"query": name, "result": str(r), "expected": expect, "ok": ok, "model": model})
    print(f"{'OK ' if ok else 'BAD'} {name}: {r} (expected {expect}){'  model=' + json.dumps(model) if model else ''}")
    return r


maxGram = BitVec("maxGram", 32)
minGram = BitVec("minGram", 32)
bufferStart = BitVec("bufferStart", 32)
bufferEnd = BitVec("bufferEnd", 32)
gramSize = BitVec("gramSize", 32)

B = maxGram * 2 + 1024          # Java int arithmetic: wraps mod 2^32 (z3 BV mul/add wrap identically)
T = maxGram * 2
B_math = wide(maxGram) * 2 + 1024
legal_ctor = And(maxGram >= 1, maxGram <= bv(INT_MAX))   # init accepts every maxGram >= minGram >= 1

# ---------------------------------------------------------------------------------------------------------------
# partition of maxGram by init's behaviour
R_OK_HI = (INT_MAX - 1024) // 2           # 1073741311: largest maxGram with no wrap in 2*maxGram+1024
R_IAE_HI = (2**32 - 1022) // 2 - 1         # 2147483136: largest maxGram whose wrapped B is < 2
print(f"constants: R_OK_HI={R_OK_HI} R_IAE_HI={R_IAE_HI} CAP={CAP} MAX_ARRAY_LENGTH={MAX_ARRAY_LENGTH}")

# region A: [1, R_OK_HI]: B does not wrap and B >= 1026
check("part-A-no-wrap", [legal_ctor, maxGram <= bv(R_OK_HI), Or(wide(B) != B_math, B < 1026)], "unsat")
check("part-A-tight", [maxGram == bv(R_OK_HI + 1), wide(B) != B_math], "sat")
# region B: [R_OK_HI+1, R_IAE_HI]: B wraps to a value < 2 -> IllegalArgumentException("buffersize must be >= 2")
check("part-B-iae", [maxGram >= bv(R_OK_HI + 1), maxGram <= bv(R_IAE_HI), B >= 2], "unsat")
check("part-B-tight", [maxGram == bv(R_IAE_HI + 1), B >= 2], "sat")
# region C: [R_IAE_HI+1, INT_MAX] (511 values): construction SUCCEEDS with a wrapped buffer 2 <= B <= 1022,
# and the term buffer is never grown (T < 0)
check("part-C-ctor-succeeds-small-buffer",
      [maxGram >= bv(R_IAE_HI + 1), legal_ctor, Not(And(B >= 2, B <= 1022, T < 0))], "unsat")
# in region C, B as a function of maxGram: B = 2*(maxGram - (R_IAE_HI+1)) + 2
check("part-C-closed-form", [maxGram >= bv(R_IAE_HI + 1), legal_ctor, B != (maxGram - bv(R_IAE_HI + 1)) * 2 + 2],
      "unsat")
# T (termAtt size request) wraps negative exactly for maxGram >= 2^30 (inside regions B and C)
check("termatt-wrap-from-2^30", [legal_ctor, maxGram >= bv(2**30), T >= 0], "unsat")
check("termatt-no-wrap-below-2^30", [legal_ctor, maxGram < bv(2**30), wide(T) != wide(maxGram) * 2], "unsat")

# ---------------------------------------------------------------------------------------------------------------
# obligations O1, O2 for constructible tokenizers (B >= 2), with and without the fix bound
TERMLEN_lower = If(T > 16, T, bv(16))   # term buffer length is >= max(16, T) (oversize only adds)
ctor_ok = And(legal_ctor, B >= 2)
# O1: a gram of g = 9 code points (18 chars of supplementary text, or 17 BMP chars with g = 17) must fit
g = BitVec("g", 32)
o1_violated = And(g >= 1, g <= maxGram, wide(g) * 2 > wide(TERMLEN_lower))
check("O1-violated-no-bound", [ctor_ok, o1_violated], "sat")
check("O1-holds-under-cap", [ctor_ok, maxGram <= bv(CAP), g >= 1, g <= maxGram, wide(g) * 2 > wide(TERMLEN_lower)],
      "unsat")
# O2: B - (maxGram + 1) >= 2 (mathematically)
check("O2-violated-no-bound", [ctor_ok, wide(B) - (wide(maxGram) + 1) < 2], "sat")
check("O2-holds-under-cap", [ctor_ok, maxGram <= bv(CAP), wide(B) - (wide(maxGram) + 1) < 2], "unsat")

# ---------------------------------------------------------------------------------------------------------------
# O3: index expressions never wrap, given the state invariants
inv = And(minGram >= 1, minGram <= maxGram, bufferStart >= 0, bufferStart <= bufferEnd, bufferEnd <= B,
          gramSize >= minGram, gramSize <= maxGram + 1)


def wraps_add(*terms):
    """the Java int sum of terms differs from the mathematical sum"""
    java = terms[0]
    math = wide(terms[0])
    for t in terms[1:]:
        java = java + t
        math = math + wide(t)
    return wide(java) != math


exprs = {
    "COMPACT line146 bufferEnd-maxGram-1": (lambda: wraps_add(bufferEnd, -maxGram, bv(-1)), True),
    # line 162 second operand: evaluated only when gramSize <= maxGram
    "NEXTOFF line162 bufferStart+gramSize": (lambda: wraps_add(bufferStart, gramSize), gramSize <= maxGram),
    "NEXTOFF2 line163 bufferStart+1+minGram": (lambda: wraps_add(bufferStart, bv(1), minGram), True),
    # lines 175/195: reached only after the line-162 test failed, i.e. gramSize <= maxGram
    "TERMEND line195 bufferStart+gramSize-1": (lambda: wraps_add(bufferStart, gramSize, bv(-1)), gramSize <= maxGram),
    "B line130 2*maxGram+1024": (lambda: wide(B) != B_math, True),
}
for name, (w, guard) in exprs.items():
    check("O3-wraps-under-cap: " + name, [ctor_ok, maxGram <= bv(CAP), inv, guard, w()], "unsat")
# without the bound, the state invariant uses the wrapped B: show wrap witnesses exist for the line-163/195 sums
check("O3-wraps-no-bound: NEXTOFF2 line163", [ctor_ok, inv, wraps_add(bufferStart, bv(1), minGram)], "sat")

# vacuity probes: the constraint sets used in the UNSAT proofs are satisfiable without the 'violation' conjunct
check("vacuity-O3-context", [ctor_ok, maxGram <= bv(CAP), inv, gramSize <= maxGram], "sat")
check("vacuity-O1-context", [ctor_ok, maxGram <= bv(CAP), g >= 1, g <= maxGram], "sat")
# mutant kill: with a bound one-third too large (CAP*3/2) the line-163 sum can wrap -> SAT, so the CAP matters
check("mutant-cap-too-large-line163",
      [ctor_ok, maxGram <= bv(CAP * 3 // 2), inv, wraps_add(bufferStart, bv(1), minGram)], "sat")

bad = [r for r in results if not r["ok"]]
json.dump({"results": results, "constants": {"R_OK_HI": R_OK_HI, "R_IAE_HI": R_IAE_HI, "CAP": CAP,
           "region_C_size": INT_MAX - R_IAE_HI}}, open(sys.argv[1] if len(sys.argv) > 1 else "z3-results.json", "w"),
          indent=1)
print(f"queries={len(results)} unexpected={len(bad)}")
sys.exit(1 if bad else 0)

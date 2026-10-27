"""twin-family-static-diff SMT model: MutableValueFloat / MutableValueDouble equality vs hashCode vs compareTo.

Model element -> Java line (lucene/core/src/java/org/apache/lucene/util/mutable/):
  EQ_CUR(a,b)   MutableValueFloat.java:52  `value == b.value && exists == b.exists` (fp ==)
                MutableValueDouble.java:52 same for double
  HASH(a)       MutableValueFloat.java:68  `Float.floatToIntBits(value)` (canonical NaN bits)
                MutableValueDouble.java:68-69 `Double.doubleToLongBits(value)` folded
  CMP(a,b)==0   MutableValueFloat.java:57  `Float.compare(value, b.value)` (== 0 iff
                floatToIntBits equal: Float.compare orders -0.0 < 0.0 and NaN == NaN)
  EQ_FIX(a,b)   proposed fix: `Float.floatToIntBits(value) == Float.floatToIntBits(b.value)`
Only exists == true is modelled (exists == false forces value == 0.0 by the class contract).

Inputs are RAW bit patterns (BitVec 32/64, every NaN payload included); the Java value is
fpBVToFP(bits). floatToIntBits(x) = isNaN ? 0x7fc00000 : raw bits (canonical NaN), which is
what Float.compare and hashCode use; floatToRawIntBits = raw bits. (A first version used the
SMT FP sort directly; it has a single NaN, so the raw-bits mutant M1 was not expressible:
logged as a model fix.)

Obligations (expected result in brackets):
  Q1 [SAT]   exists a: EQ_CUR(a,a) false                         (reflexivity broken: NaN)
  Q2 [UNSAT] exists a: not NaN and EQ_CUR(a,a) false             (only NaN breaks reflexivity)
  Q3 [SAT]   exists a,b: EQ_CUR(a,b) and bits(a) != bits(b)      (equal but different hash: +-0)
  Q4 [UNSAT] exists a,b: EQ_CUR(a,b), bits differ, not both zero (only the zero pair)
  Q5 [UNSAT] exists a,b: EQ_FIX(a,b) xor (CMP(a,b)==0)           (fix agrees with compareTo)
  Q6 [UNSAT] exists a,b: EQ_FIX(a,b) and HASH(a) != HASH(b)      (fix agrees with hashCode)
  Q7 [UNSAT] exists a: EQ_FIX(a,a) false                         (fix reflexive)
  M1 [SAT]   mutant: EQ_FIX replaced by raw-bits equality (floatToRawIntBits) breaks Q5 for NaN
             payloads (vacuity probe: the encoding distinguishes NaN payloads)
  M3 [SAT]   the current fp == disagrees with compareTo (witness: NaN pair or +-0 pair)
CMP is modelled structurally from the JDK implementation (fp <, fp >, then floatToIntBits
equality), so Q5 is not true by construction.
Run: python3 -I mutablevalue_fp_equality.py
"""
from z3 import (BitVec, Float32, Float64, Solver, And, Or, Not, Xor, If, BitVecVal, fpIsNaN,
                fpIsZero, fpEQ, fpLT, fpGT, fpBVToFP, sat, unsat)


def run(sortname, srt, width, nanbits):
    ra, rb = BitVec('a_bits', width), BitVec('b_bits', width)
    a, b = fpBVToFP(ra, srt), fpBVToFP(rb, srt)
    canon = lambda r, x: If(fpIsNaN(x), BitVecVal(nanbits, width), r)
    eq_cur = lambda x, y: fpEQ(x, y)  # Java fp == : false for NaN, true for +0 == -0
    eq_fix = lambda: canon(ra, a) == canon(rb, b)
    # Float.compare / Double.compare as implemented in the JDK:
    #   if (f1 < f2) return -1; if (f1 > f2) return 1;
    #   bits1 = floatToIntBits(f1); bits2 = floatToIntBits(f2); return bits1 == bits2 ? 0 : ...
    cmp0 = lambda: And(Not(fpLT(a, b)), Not(fpGT(a, b)), canon(ra, a) == canon(rb, b))
    eq_mut = lambda: ra == rb  # mutant: floatToRawIntBits equality
    res = {}

    def q(name, f, want):
        s = Solver()
        s.set('timeout', 120000)
        s.add(f)
        r = s.check()
        model = s.model() if r == sat else None
        ok = (r == sat) == want
        res[name] = (str(r), 'as expected' if ok else 'UNEXPECTED', str(model) if model else '')
        print(f'{sortname} {name}: {r} ({"as expected" if ok else "UNEXPECTED"}) {model if model else ""}')

    q('Q1 reflexivity broken', Not(eq_cur(a, a)), True)
    q('Q2 only NaN breaks reflexivity', And(Not(fpIsNaN(a)), Not(eq_cur(a, a))), False)
    q('Q3 equal but hash bits differ', And(eq_cur(a, b), canon(ra, a) != canon(rb, b)), True)
    q('Q4 only +-0 equal with different bits',
      And(eq_cur(a, b), canon(ra, a) != canon(rb, b), Not(And(fpIsZero(a), fpIsZero(b)))), False)
    q('Q5 fix agrees with compareTo', Xor(eq_fix(), cmp0()), False)
    q('Q6 fix agrees with hashCode',
      And(eq_fix(), canon(ra, a) != canon(rb, b)), False)
    q('Q7 fix reflexive', Not(canon(ra, a) == canon(ra, a)), False)
    q('M1 mutant raw-bits equality disagrees with compareTo', Xor(eq_mut(), cmp0()), True)
    q('M3 current == disagrees with compareTo', Xor(eq_cur(a, b), cmp0()), True)
    return res


if __name__ == '__main__':
    r1 = run('float32', Float32(), 32, 0x7fc00000)
    r2 = run('float64', Float64(), 64, 0x7ff8000000000000)
    bad = [k for r in (r1, r2) for k, v in r.items() if v[1] != 'as expected']
    print('ALL AS EXPECTED' if not bad else f'UNEXPECTED: {bad}')

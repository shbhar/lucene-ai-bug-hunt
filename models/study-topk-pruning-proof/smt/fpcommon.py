"""Shared IEEE-754 helpers that mirror Java float/double semantics in Z3's FP theory.
Java: float+float in double context = exact widening then RNE double add; (float) d = RNE narrowing."""
import z3
F32 = z3.Float32(); F64 = z3.Float64(); RNE = z3.RNE()

def f32(name):
    return z3.FP(name, F32)

def widen(x):            # (double) floatValue  -- exact
    return z3.fpToFP(RNE, x, F64)

def narrow(d):           # (float) doubleValue -- round to nearest even
    return z3.fpToFP(RNE, d, F32)

def dadd(a, b):          # double + double
    return z3.fpAdd(RNE, a, b)

def dmul(a, b):
    return z3.fpMul(RNE, a, b)

def dconst(v):
    return z3.FPVal(v, F64)

def fconst(v):
    return z3.FPVal(v, F32)

def seqsum(vals):        # double s = 0; for (float v : vals) s += v;
    s = dconst(0.0)
    for v in vals:
        s = dadd(s, widen(v) if v.sort() == F32 else v)
    return s

def sum_upper_bound(s, n):
    """MathUtil.sumUpperBound(double sum, int numValues), MathUtil.java:177-194."""
    if n <= 2:
        return s
    b = (n - 1) * 2.0 ** -52          # sumRelativeErrorBound, MathUtil.java:162-169 (exact in double)
    return dmul(dconst(1.0 + 2 * b), s)

def nonneg_finite(x):
    return z3.And(z3.Not(z3.fpIsNaN(x)), z3.Not(z3.fpIsInf(x)), z3.Not(z3.fpIsNegative(x)))

def strictly_pos_finite(x):
    return z3.And(nonneg_finite(x), z3.Not(z3.fpIsZero(x)))

def next_up(x):          # Math.nextUp(float) for positive finite x: bit pattern + 1
    bv = z3.fpToIEEEBV(x)
    return z3.fpBVToFP(bv + 1, F32)

def fval(m, x):
    v = m.eval(x, model_completion=True)
    return v

def hexf(m, x):
    """Return a Java float hex literal for a model value."""
    import struct
    bv = m.eval(z3.fpToIEEEBV(x), model_completion=True).as_long()
    f = struct.unpack('>f', struct.pack('>I', bv))[0]
    return float.hex(f).replace('0x', '0x') + 'f', bv, f

def dump_smt2(solver, path):
    """Write the solver's assertions as an SMT-LIB2 benchmark (QF_FP / QF_FPLRA) for cvc5 cross-checking."""
    with open(path, 'w') as f:
        f.write('(set-option :produce-models true)\n')
        f.write(solver.to_smt2())

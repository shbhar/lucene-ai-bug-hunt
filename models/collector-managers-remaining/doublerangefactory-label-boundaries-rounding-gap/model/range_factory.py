"""collector-managers-remaining: SMT model of LongRangeFactory.getRange / DoubleRangeFactory.getRange (Lucene main @ ea57360912ed).

Model element -> Java line map:
  DoubleRangeFactory.java:54-71 getRange: value < min -> [Double.MIN_VALUE, min); value >= max -> [max, Double.MAX_VALUE);
      bucket = Math.floor((value - min) / width); lo = min + bucket * width; hi = lo + width
  LongRangeFactory.java:54-71 getRange: value < min -> [Long.MIN_VALUE, min); value >= max -> [max, Long.MAX_VALUE);
      bucket = (value - min) / width (Java long division, truncating, wrapping); lo = min + bucket*width; hi = lo + width
  Contract (factory javadoc, :30-37): ranges are inclusive at the lower end, exclusive at the upper end; all ranges
      between min and max are `width` wide "with the exception of the final range which may be up to this width";
      LongRange/DoubleRange javadoc: inclusive min, exclusive max. The returned range should contain the value.
Queries (each SAT = counterexample, re-executed in Java by RangeFactoryReplay.java):
  D1 rounding: min <= v < max, finite, width > 0, yet NOT (lo <= v < hi)
  D2 final bucket: min <= v < max and hi > max (label crosses into the [max, ...) bucket)
  D3 below min: v < min and NOT (Double.MIN_VALUE <= v < min)
  L1 final bucket (long): min <= v < max and hi > max
  L2 overflow (long): min <= v < max, width > 0, and NOT (lo <= v < hi) (signed 64-bit wrap)
Precondition used: width > 0 (width <= 0 is not meaningful; the javadoc calls it 'a standard width').
"""
from z3 import *

def fp_queries():
    D = Float64()
    rne, rtn = RNE(), RTN()
    out = {}
    def mk():
        mn, w, mx, v = FP('min', D), FP('width', D), FP('max', D), FP('v', D)
        s = Solver()
        for x in (mn, w, mx, v):
            s.add(Not(fpIsNaN(x)), Not(fpIsInf(x)))
        s.add(fpGT(w, FPVal(0.0, D)))
        q = fpDiv(rne, fpSub(rne, v, mn), w)
        b = fpRoundToIntegral(rtn, q)
        lo = fpAdd(rne, mn, fpMul(rne, b, w))
        hi = fpAdd(rne, lo, w)
        return s, mn, w, mx, v, lo, hi
    # D1 rounding, restricted to "decimal-looking" small magnitudes to get a natural witness
    s, mn, w, mx, v, lo, hi = mk()
    s.add(fpLEQ(mn, v), fpLT(v, mx))
    s.add(Not(And(fpLEQ(lo, v), fpLT(v, hi))))
    s.add(fpGEQ(mn, FPVal(-1000.0, D)), fpLEQ(mx, FPVal(1000.0, D)), fpGEQ(w, FPVal(0.001, D)))
    out['D1'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    # D2 final bucket overlap
    s, mn, w, mx, v, lo, hi = mk()
    s.add(fpLEQ(mn, v), fpLT(v, mx), fpGT(hi, mx))
    s.add(fpGEQ(mn, FPVal(0.0, D)), fpLEQ(mx, FPVal(100.0, D)), fpGEQ(w, FPVal(1.0, D)))
    out['D2'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    # D3 below-min label
    s, mn, w, mx, v, lo, hi = mk()
    s.add(fpLT(v, mn), Not(And(fpLEQ(FPVal(5e-324, D), v), fpLT(v, mn))))
    out['D3'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    return out

def long_queries():
    out = {}
    def mk():
        mn, w, mx, v = BitVecs('min width max v', 64)
        s = Solver()
        s.add(w > 0)
        b = v - mn            # Java long subtraction wraps
        bucket = b / w        # BitVec '/' on signed is SDIV (truncating), as Java long division
        lo = mn + bucket * w
        hi = lo + w
        return s, mn, w, mx, v, lo, hi
    s, mn, w, mx, v, lo, hi = mk()
    s.add(mn <= v, v < mx, hi > mx, mx - mn > 0, mn >= 0, mx <= 1000, w <= 100)
    out['L1'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    s, mn, w, mx, v, lo, hi = mk()
    s.add(mn <= v, v < mx, Not(And(lo <= v, v < hi)))
    out['L2'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    # L2b: overflow but with no wrap in hi itself, and the 'min' of the factory small (realistic min)
    s, mn, w, mx, v, lo, hi = mk()
    s.add(mn <= v, v < mx, Not(And(lo <= v, v < hi)), mn >= -1000, mn <= 0, w <= 1000)
    out['L2b'] = (s.check(), s.model() if s.check() == sat else None, (mn, w, mx, v, lo, hi))
    return out

def show(name, res):
    st, m, terms = res
    print(name, st)
    if m is not None:
        vals = []
        for t in terms:
            e = m.eval(t, model_completion=True)
            if is_fp(e) or isinstance(e, FPNumRef):
                vals.append(str(e) if not isinstance(e, FPNumRef) else repr(float(e.as_string()) if False else e))
            vals.append(str(e))
        print('   ', ['min', 'width', 'max', 'v', 'lo', 'hi'])
        for t in terms:
            e = m.eval(t, model_completion=True)
            if isinstance(e, FPNumRef):
                # exact decimal of the double
                import struct
                sgn = e.sign(); 
                print('    %s = %r' % (t, float(eval_fp(e))))
            else:
                print('    %s = %s' % (t, e.as_signed_long() if hasattr(e, 'as_signed_long') else e))

def eval_fp(e):
    # convert z3 FPNumRef to python float via its bits
    import struct
    bv = simplify(fpToIEEEBV(e))
    return struct.unpack('>d', bv.as_long().to_bytes(8, 'big'))[0]

if __name__ == '__main__':
    for k, r in fp_queries().items():
        show(k, r)
    for k, r in long_queries().items():
        st, m, terms = r
        print(k, st)
        if m is not None:
            for t in terms:
                e = m.eval(t, model_completion=True)
                print('    %s = %d' % (t, e.as_signed_long()))

"""
Z3 FP model of FunctionValues.getRangeScorer (default, float) as used by
ValueSource.fromDoubleValuesSource, versus the double comparison of DoubleDocValues.getRangeScorer.

Model -> code map (main @ ea57360912ed):
  F(v) = fp.to_fp(Float32, RNE, v)  <-> FromDoubleValuesSource...floatVal: (float) inner.doubleValue()
                                        (ValueSource.java:309-314)
  F(b)                              <-> FunctionValues.getRangeScorer: Float.parseFloat(lowerVal/upperVal)
                                        (FunctionValues.java:239-251); assumption A1: parseFloat(Double.toString(b))
                                        == (float) b (checked separately on the JVM, see parse_check.log)
  ge/gt/le/lt on F(.)               <-> FunctionValues.java:256-291 (docVal >= l etc.)
  reference ge/gt on doubles        <-> DoubleDocValues.getRangeScorer (DoubleDocValues.java:110-146)
Domain: non-NaN doubles (NaN never matches either path).
"""
from z3 import *

D = Float64(); S = Float32()
v, b = FP('v', D), FP('b', D)
F = lambda x: fpToFP(RNE(), x, S)
notnan = And(Not(fpIsNaN(v)), Not(fpIsNaN(b)))

def run(name, cond, expect):
    s = Solver(); s.set('timeout', 600000)
    s.add(notnan, cond)
    r = s.check()
    w = ''
    if r == sat:
        m = s.model()
        w = ' witness v=%s b=%s' % (m.eval(v), m.eval(b))
    ok = (str(r) == expect)
    print('%-58s %-6s expected %-6s %s%s' % (name, r, expect, 'OK' if ok else 'UNEXPECTED', w))
    return ok

allok = True
# inclusive lower bound x >= b: float path may over-match but never miss
allok &= run('Q1 inclusive: v<b but F(v)>=F(b) (over-match)', And(fpLT(v, b), fpGEQ(F(v), F(b))), 'sat')
allok &= run('Q2 inclusive: v>=b but not F(v)>=F(b) (miss)', And(fpGEQ(v, b), Not(fpGEQ(F(v), F(b)))), 'unsat')
# exclusive lower bound x > b: float path may miss but never over-match
allok &= run('Q3 exclusive: v>b but not F(v)>F(b) (miss)', And(fpGT(v, b), Not(fpGT(F(v), F(b)))), 'sat')
allok &= run('Q4 exclusive: not v>b but F(v)>F(b) (over-match)', And(Not(fpGT(v, b)), fpGT(F(v), F(b))), 'unsat')
# exact characterisation: the paths disagree on >= iff v<b and F(v)==F(b)
allok &= run('Q5 >= disagreement <=> v<b & F(v)==F(b)  (negated)',
             Not((fpGEQ(v, b) != fpGEQ(F(v), F(b))) == And(fpLT(v, b), fpEQ(F(v), F(b)))), 'unsat')
allok &= run('Q6 >  disagreement <=> v>b & F(v)==F(b)  (negated)',
             Not((fpGT(v, b) != fpGT(F(v), F(b))) == And(fpGT(v, b), fpEQ(F(v), F(b)))), 'unsat')
# float-representable values and bounds: no disagreement (the bug needs a non-float value or bound)
rep = And(fpEQ(fpToFP(RNE(), F(v), D), v), fpEQ(fpToFP(RNE(), F(b), D), b))
allok &= run('Q7 representable v,b: any disagreement on >= or >', And(rep, Or(fpGEQ(v, b) != fpGEQ(F(v), F(b)), fpGT(v, b) != fpGT(F(v), F(b)))), 'unsat')
# beyond float range: two finite doubles above Float.MAX_VALUE collapse to +Inf
allok &= run('Q8 finite v>b, both > Float.MAX_VALUE, F(v)==F(b)==+Inf',
             And(fpGT(v, b), fpGT(b, fpToFP(RNE(), FPVal(3.4028234663852886e38, D), D)), fpIsInf(F(v)), fpIsInf(F(b)), Not(fpIsInf(v))), 'sat')
# fix: comparing doubles (DoubleDocValues) equals the reference by construction; mutant check: a fix that casts only the bound
allok &= run('M1 mutant "cast only the bound": v>=F(b) vs v>=b differ', fpGEQ(v, fpToFP(RNE(), F(b), D)) != fpGEQ(v, b), 'sat')
print('ALL AS EXPECTED' if allok else 'SOME UNEXPECTED')

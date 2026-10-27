# Z3 model: Lucene AffixCondition decomposition vs. the Hunspell condition semantics.
#
# Mapping (model element -> Java, lucene/analysis/common/src/java/org/apache/lucene/analysis/hunspell/):
#   spec()                      -> hunspell(5) "condition" (manual): the condition's k char patterns must match the
#                                  first k chars (PFX) / last k chars (SFX) of the root word; root = strip+stem (PFX)
#                                  or stem+strip (SFX); root shorter than k never matches.
#   uniqueKey()                 -> AffixCondition.java:72-79 (ALWAYS_TRUE_KEY shortcut used by Dictionary.java:749-760)
#   lucene_regexp_short()       -> AffixCondition.java:102-106  (conditionChars <= strip.length())
#   lucene_regexp_long_prefix() -> AffixCondition.java:108-114
#   lucene_regexp_long_suffix() -> AffixCondition.java:116-120
#   lucene_regexpCondition      -> AffixCondition.java:182-192 (length >= charCount && automaton on the stem window)
# Elements are abstract: each condition element is a set of allowed chars (bitmask over an alphabet of size N);
# '.', a literal, [..] and [^..] are all such sets.  This abstracts away regex syntax (dash, metachars), which the
# bounded-exhaustive harness on the real jar covers (harness/.../AffixConditionBE.java).  Only the regexp branch of
# compile() is modelled here (conditions that contain '[', '.' or '-'), with the uniqueKey shortcut disabled
# (it can only fire when the condition string literally occurs in the strip).
import sys
from z3 import *

N = 4          # alphabet size
KMAX = 3       # condition elements
MMAX = 3       # strip length
LMAX = 4       # stem length

def run(extra_constraints, label, fixed=False):
    s = Solver()
    kind = Int('kind')  # 0 = PREFIX, 1 = SUFFIX
    k = Int('k'); m = Int('m'); l = Int('l')
    s.add(Or(kind == 0, kind == 1))
    s.add(k >= 1, k <= KMAX, m >= 0, m <= MMAX, l >= 0, l <= LMAX)
    E = [BitVec('E%d' % i, N) for i in range(KMAX)]      # element i = set of chars it accepts
    for e in E:
        s.add(e != 0)                                     # every element accepts at least one char
    ST = [Int('st%d' % i) for i in range(MMAX)]
    SM = [Int('sm%d' % i) for i in range(LMAX)]
    for v in ST + SM:
        s.add(v >= 0, v < N)

    def acc(e, ch):  # element e accepts char ch (bit ch of the element's mask)
        return Extract(0, 0, LShR(e, Int2BV(ch, N))) == 1

    def elem(i):  # symbolic element i (only meaningful for i < k)
        return E[i]

    # root char at position p (0-based) for PFX: strip+stem; for SFX: stem+strip
    def root_char(p, is_suffix):
        # returns (defined, char) as z3 expressions, via nested If over concrete lengths
        cands = []
        for mm in range(MMAX + 1):
            for ll in range(LMAX + 1):
                if p >= mm + ll:
                    continue
                if is_suffix:
                    ch = SM[p] if p < ll else ST[p - ll]
                else:
                    ch = ST[p] if p < mm else SM[p - mm]
                cands.append((And(m == mm, l == ll), ch))
        expr = IntVal(0)
        for cond, ch in cands:
            expr = If(cond, ch, expr)
        return expr

    def spec(is_suffix):
        clauses = []
        for kk in range(1, KMAX + 1):
            for mm in range(MMAX + 1):
                for ll in range(LMAX + 1):
                    rl = mm + ll
                    if rl < kk:
                        ok = BoolVal(False)
                    else:
                        parts = []
                        for i in range(kk):
                            p = i if not is_suffix else rl - kk + i
                            if is_suffix:
                                ch = SM[p] if p < ll else ST[p - ll]
                            else:
                                ch = ST[p] if p < mm else SM[p - mm]
                            parts.append(acc(E[i], ch))
                        ok = And(parts)
                    clauses.append(And(k == kk, m == mm, l == ll, ok))
        return Or(clauses)

    def lucene(is_suffix):
        clauses = []
        for kk in range(1, KMAX + 1):
            for mm in range(MMAX + 1):
                for ll in range(LMAX + 1):
                    if kk <= mm:
                        # AffixCondition.java:103-105: regex = PREFIX ? ".*"+cond : cond+".*" ; strip.matches(regex)
                        if fixed:
                            # corrected form: PREFIX -> cond+".*" (first kk chars), SUFFIX -> ".*"+cond (last kk)
                            if not is_suffix:
                                ok = And([acc(E[i], ST[i]) for i in range(kk)])
                            else:
                                ok = And([acc(E[i], ST[mm - kk + i]) for i in range(kk)])
                        elif not is_suffix:
                            # ".*" + cond: cond matches the LAST kk chars of strip
                            ok = And([acc(E[i], ST[mm - kk + i]) for i in range(kk)])
                        else:
                            # cond + ".*": cond matches the FIRST kk chars of strip
                            ok = And([acc(E[i], ST[i]) for i in range(kk)])
                    else:
                        c = kk - mm  # charCount for regexpCondition
                        if not is_suffix:
                            # strip must match first mm elements; stem start must match the remaining c elements
                            strip_ok = And([acc(E[i], ST[i]) for i in range(mm)]) if mm else BoolVal(True)
                            if ll < c:
                                stem_ok = BoolVal(False)
                            else:
                                stem_ok = And([acc(E[mm + j], SM[j]) for j in range(c)])
                        else:
                            # strip must match the last mm elements; stem end must match the first c elements
                            strip_ok = And([acc(E[c + i], ST[i]) for i in range(mm)]) if mm else BoolVal(True)
                            if ll < c:
                                stem_ok = BoolVal(False)
                            else:
                                stem_ok = And([acc(E[j], SM[ll - c + j]) for j in range(c)])
                        ok = And(strip_ok, stem_ok)
                    clauses.append(And(k == kk, m == mm, l == ll, ok))
        return Or(clauses)

    sp = If(kind == 0, spec(False), spec(True))
    lu = If(kind == 0, lucene(False), lucene(True))
    s.add(sp != lu)
    for c in extra_constraints(k, m, l, kind):
        s.add(c)
    r = s.check()
    print('[%s] %s' % (label, r))
    if r == sat:
        md = s.model()
        kk = md.eval(k).as_long(); mm = md.eval(m).as_long(); ll = md.eval(l).as_long()
        alpha = 'abcd'
        def setstr(bv):
            v = md.eval(bv).as_long()
            return '[' + ''.join(alpha[c] for c in range(N) if (v >> c) & 1) + ']'
        cond = ''.join(setstr(E[i]) for i in range(kk))
        strip = ''.join(alpha[md.eval(ST[i]).as_long()] for i in range(mm))
        stem = ''.join(alpha[md.eval(SM[i]).as_long()] for i in range(ll))
        print('   kind=%s cond=%s strip=%r stem=%r spec=%s lucene=%s' % (
            'PFX' if md.eval(kind).as_long() == 0 else 'SFX', cond, strip, stem,
            md.eval(sp), md.eval(lu)))
    return r


if __name__ == '__main__':
    results = {}
    # Q1: any mismatch at all?
    results['Q1_any'] = run(lambda k, m, l, kind: [], 'Q1 any mismatch')
    # Q2: mismatch with k == m (condition exactly as long as the strip)?
    results['Q2_k_eq_m'] = run(lambda k, m, l, kind: [k == m], 'Q2 k == m')
    # Q3: mismatch with k > m (the stem part is involved)?
    results['Q3_k_gt_m'] = run(lambda k, m, l, kind: [k > m], 'Q3 k > m')
    # Q4: mismatch with k < m, per kind
    results['Q4_k_lt_m_PFX'] = run(lambda k, m, l, kind: [k < m, kind == 0], 'Q4 k < m, PFX')
    results['Q4_k_lt_m_SFX'] = run(lambda k, m, l, kind: [k < m, kind == 1], 'Q4 k < m, SFX')
    # Q5: the corrected short branch (swap the two regex forms) -> expect UNSAT: decomposition == spec in bounds.
    results['Q5_fixed_any'] = run(lambda k, m, l, kind: [], 'Q5 fixed model, any mismatch', fixed=True)
    print({k: str(v) for k, v in results.items()})

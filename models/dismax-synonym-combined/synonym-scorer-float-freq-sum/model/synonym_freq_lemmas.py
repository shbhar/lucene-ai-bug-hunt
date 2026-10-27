"""
SynonymQuery merged-impacts bound: arithmetic lemmas (Lucene main @ ea57360912ed), lemma-split.

Model element -> Java line (lucene/core/src/java/org/apache/lucene/search/SynonymQuery.java):
  real freq term   x_i = fl32(b_i * fl32(f_i))              DisiWrapperFreq.freq:659-661
  real freq        fl32 sequential sum of x_i (topList order) SynonymScorer.freq:610-617
  bound term       y_i = (int) ceil(fl32(fl32(F_i) * b_i)), F_i if b_i == 1     mergeImpacts:494-507
  bound            fl32( (int) min(MAX_INT, sum_i y_i) )    mergeImpacts:553 + MaxScoreCache.computeMaxScore:80
  sum f_i <= MAX_INT  field length limit (IndexingChain:2000 Math.addExact)
  f_i <= F_i       the doc's freq is dominated by an impact of term i whose norm is <= the doc's norm;
                   the merged entry at that norm sums, per term, a boosted freq >= y_i (merge logic is
                   checked separately, exhaustively, on the real code: harness/SynonymMergeExhaustive.java)

S1 (boost 1, T terms, no precondition): is fl32seq(fl32(f_i)) <= fl32(sum F_i) ?   (counterexample search)
S1s (same, precondition sum F_i < 2^24)                                             (proof)
S2 (boosts in (0,1], precondition sum y_i < 2^24): fl32seq(x_i) <= fl32(sum y_i)   (proof)
Usage: python3 -I synonym_freq_lemmas.py <S1|S1s|S2> <T> <timeout_s>
"""
import sys, time, itertools
from z3 import *

Q = sys.argv[1]; T = int(sys.argv[2]); TO = int(sys.argv[3])
F32 = Float32(); R = RNE(); MAXI = 2 ** 31 - 1

def i2f(bv):
    return fpSignedToFP(R, bv, F32)

res = []
for order in itertools.permutations(range(T)):
    if T > 1 and order[0] > order[1]:
        continue  # first addition is commutative
    s = SolverFor("QF_BVFP"); s.set("timeout", TO * 1000)
    f = [BitVec("f%d" % i, 32) for i in range(T)]
    F = [BitVec("F%d" % i, 32) for i in range(T)]
    for i in range(T):
        s.add(f[i] >= 1, F[i] >= f[i])  # signed: 1 <= f <= F <= MAX_INT
    # legal doc: the synonyms are distinct terms of one field of one doc, so their freqs sum to at most
    # the field length, which IndexingChain keeps <= Integer.MAX_VALUE (Math.addExact, IndexingChain:2000)
    flen = BitVecVal(0, 64)
    for i in range(T):
        flen = flen + ZeroExt(32, f[i])
    s.add(ULE(flen, BitVecVal(MAXI, 64)))
    if Q in ("S1", "S1s"):
        xs = [i2f(f[i]) for i in range(T)]
        ys = [F[i] for i in range(T)]
    else:
        b = [FP("b%d" % i, F32) for i in range(T)]
        xs, ys = [], []
        for i in range(T):
            s.add(fpGT(b[i], FPVal(0.0, F32)), fpLEQ(b[i], FPVal(1.0, F32)))
            xs.append(fpMul(R, b[i], i2f(f[i])))
            c = fpRoundToIntegral(RTP(), fpMul(R, i2f(F[i]), b[i]))
            ys.append(If(fpEQ(b[i], FPVal(1.0, F32)), F[i], fpToSBV(RTZ(), c, BitVecSort(32))))
    real = xs[order[0]]
    for i in order[1:]:
        real = fpAdd(R, real, xs[i])
    tot = BitVecVal(0, 64)
    for y in ys:
        tot = tot + ZeroExt(32, y)
    capped = If(UGT(tot, BitVecVal(MAXI, 64)), BitVecVal(MAXI, 32), Extract(31, 0, tot))
    bound = i2f(capped)
    if Q in ("S1s", "S2"):
        s.add(ULT(tot, BitVecVal(2 ** 24, 64)))
    s.add(fpGT(real, bound))
    t0 = time.time(); r = s.check(); dt = time.time() - t0
    msg = "%s T=%d order=%s -> %s (%.1fs)" % (Q, T, order, r, dt)
    if r == sat:
        m = s.model()
        msg += "\n   f=%s F=%s real=%s bound=%s" % ([m.eval(x).as_signed_long() for x in f],
                                                    [m.eval(x).as_signed_long() for x in F],
                                                    m.eval(real), m.eval(bound))
        if Q == "S2":
            msg += " b=%s" % [m.eval(x) for x in b]
    print(msg, flush=True)
    res.append(str(r))
print("SUMMARY", Q, T, res)

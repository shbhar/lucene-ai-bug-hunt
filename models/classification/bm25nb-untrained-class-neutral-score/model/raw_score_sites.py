"""
Z3 (QF_FP + UF log) site-by-site check of every division / Math.log / Math.exp site that feeds a classifier score
(inventory produced by grep over lucene/classification/src/java, see logs/raw_score_sites.log header).

For each site: the operands as Java computes them (int -> double conversions are exact below 2^53), the
precondition the index or API guarantees, and two queries:
  <site>_safe  : precondition  ==>  result finite (and > 0 where it is then logged)        expect unsat of negation
  <site>_trig  : the precondition's negation that the API still accepts (or 'illegal' if the API cannot reach it)
                 produces NaN/Inf                                                             expect sat
The NaN/Inf characterisation of the normalisation (nb_norm_lemmas.py + harness/NormSpecialValues.java) then
decides whether the class probabilities come out NaN.

Sites (Lucene main @ ea57360912ed, paths under lucene/classification/src/java/org/apache/lucene/classification/):
  S1  SimpleNaiveBayesClassifier.java:279       log(df) - log(D)          pre: 1 <= df <= D  (term enumerated from the
                                                                          class field; D = Terms.getDocCount() >= 1)
  S2  SimpleNaiveBayesClassifier.java:217-225   log((hits+1) / (A*df + D)) pre: hits >= 0, df >= 1, D >= 1, A >= 0 finite
  S3  SimpleNaiveBayesClassifier.java:246       sumDocFreq / docCount     pre: docCount >= 1 (Terms non-null; a null
                                                                          Terms is an NPE, not a NaN)
  S4  SimpleNaiveBayesDocumentClassifier.java:222  result / tokenizedText.length   pre: NONE in the code; length 0 is
                                                                          reachable (a field value that analyses to no
                                                                          tokens, e.g. "" or only stop words)
  S5  BM25NBClassifier.java:219,230             log(BM25 score)           pre: score > 0 finite (BM25 scores of matching
                                                                          docs are positive)
  S6  CachingNaiveBayesClassifier.java:120-140  log((hits+1) / (ctf + D)) same as S2
  S7  KNearestNeighborClassifier.java:207 / KNearestFuzzyClassifier.java:172   score / maxScore
                                                                          pre: maxScore >= score > 0 (the MUST
                                                                          WildcardQuery contributes a constant 1.0)
  S8  KNearestNeighborClassifier.java:222-232   (count * (boost/count)) / k, * k / sumdoc   pre: count, k, sumdoc >= 1
  S9  BooleanPerceptronClassifier.java:102-103  sumTotalTermFreq / docCount  pre: docCount >= 1 (textTerms non-null)
  S10 ConfusionMatrixGenerator.java:149         time / counter            pre: NONE; counter == 0 when no document
                                                                          got a non-null class -> NaN/Inf average time
  S11 ConfusionMatrixGenerator.java:281,298,313 accuracy / macro P / macro R over an empty matrix -> 0/0
"""
import sys
import z3

F = z3.Float64()
RNE = z3.RNE()
A, O, N = z3.And, z3.Or, z3.Not
log = z3.Function("log", F, F)
MAX_DOCS = 2147483519  # IndexWriter.MAX_DOCS
results = []


def fp(v):
    return z3.FPVal(v, F)


def fin(x):
    return A(N(z3.fpIsNaN(x)), N(z3.fpIsInf(x)))


def i2d(i):
    """Java (double) int: exact for |i| < 2^53"""
    return z3.fpSignedToFP(RNE, z3.Int2BV(i, 64), F)


def log_ax(t):
    l = log(t)
    return [
        z3.Implies(O(z3.fpIsNaN(t), z3.fpLT(t, fp(0.0))), z3.fpIsNaN(l)),
        z3.Implies(z3.fpIsZero(t), A(z3.fpIsInf(l), z3.fpIsNegative(l))),
        z3.Implies(A(z3.fpIsInf(t), z3.fpIsPositive(t)), A(z3.fpIsInf(l), z3.fpIsPositive(l))),
        z3.Implies(A(fin(t), z3.fpGT(t, fp(0.0))), A(fin(l), z3.fpGEQ(l, fp(-745.2)), z3.fpLEQ(l, fp(709.8)))),
    ]


def check(name, cons, expect):
    s = z3.Solver()
    s.set("timeout", 300000)
    s.add(*cons)
    r = str(s.check())
    ok = r == expect
    results.append(ok)
    msg = f"{name}: {r} (expect {expect}) {'OK' if ok else 'MISMATCH'}"
    if r == "sat":
        m = s.model()
        msg += "  " + " ".join(f"{d.name()}={m[d]}" for d in sorted(m.decls(), key=lambda d: d.name())
                               if d.arity() == 0)
    print(msg, flush=True)


def main():
    # S1
    df, D = z3.Ints("S1_df S1_D")
    t1, t2 = i2d(df), i2d(D)
    prior = z3.fpSub(RNE, log(t1), log(t2))
    pre = [df >= 1, df <= D, D <= MAX_DOCS]
    check("S1_safe log(df)-log(D)", pre + log_ax(t1) + log_ax(t2) + [N(fin(prior))], "unsat")
    check("S1_trig D=0 (unreachable: classes!=null => getDocCount()>=1)", [df >= 1, D == 0] + log_ax(t1) + log_ax(t2)
          + [z3.fpIsInf(prior)], "sat")

    # S2 / S6 (split: the monolithic query with Int2BV conversions timed out after 300 s in Z3 4.12.2)
    #  S2a den = A*df + D with df, D integral in [1, MAX_DOCS] (exact doubles) and 0 <= A <= 2^31 finite
    #      ==> den finite and den >= 1
    #  S2b wp = num / den with num integral in [1, 2^31] and 1 <= den <= 2^63 finite ==> wp finite, wp > 0, normal
    #  S2c log(wp) finite (log axiom on a finite positive argument)
    Aavg, dfd, Dd = z3.Consts("S2_A S2_df S2_D", F)
    integral = lambda v, lo, hi: [fin(v), z3.fpRoundToIntegral(RNE, v) == v, z3.fpGEQ(v, fp(lo)), z3.fpLEQ(v, fp(hi))]
    den = z3.fpAdd(RNE, z3.fpMul(RNE, Aavg, dfd), Dd)
    pre = integral(dfd, 1, MAX_DOCS) + integral(Dd, 1, MAX_DOCS) + [fin(Aavg), z3.fpGEQ(Aavg, fp(0.0)),
                                                                     z3.fpLEQ(Aavg, fp(2.0 ** 31))]
    check("S2a_safe den = A*df + D finite and >= 1", pre + [N(A(fin(den), z3.fpGEQ(den, fp(1.0)),
                                                              z3.fpLEQ(den, fp(2.0 ** 63))))], "unsat")
    numv, denv = z3.Consts("S2_num S2_den", F)
    wp = z3.fpDiv(RNE, numv, denv)
    pre = integral(numv, 1, 2.0 ** 31) + [fin(denv), z3.fpGEQ(denv, fp(1.0)), z3.fpLEQ(denv, fp(2.0 ** 63))]
    check("S2b_safe wp = num/den finite, normal, > 0", pre + [N(A(fin(wp), z3.fpGT(wp, fp(0.0)),
                                                                 z3.fpIsNormal(wp)))], "unsat")
    wv = z3.Const("S2_wp", F)
    check("S2c_safe log(wp) finite for finite wp > 0", log_ax(wv) + [fin(wv), z3.fpGT(wv, fp(0.0)), N(fin(log(wv)))],
          "unsat")

    # S3 / S9
    sdf, dc = z3.Ints("S3_sumDocFreq S3_docCount")
    q = z3.fpDiv(RNE, i2d(sdf), i2d(dc))
    check("S3_safe sumDocFreq/docCount", [dc >= 1, sdf >= dc, sdf <= 2 ** 50, N(fin(q))], "unsat")
    check("S3_trig docCount=0 (unreachable: Terms non-null => docCount>=1)", [dc == 0, sdf == 0, z3.fpIsNaN(q)], "sat")

    # S4: SimpleNaiveBayesDocumentClassifier: result / tokenizedText.length with zero tokens
    ln = z3.Int("S4_len")
    result0 = fp(0.0)  # loop over zero tokens leaves result = 0d
    r4 = z3.fpDiv(RNE, result0, i2d(ln))
    prior4, boost = z3.Consts("S4_prior S4_boost", F)
    field_score = z3.fpAdd(RNE, prior4, z3.fpMul(RNE, r4, boost))
    check("S4_trig zero tokens -> 0.0/0 = NaN field score (REACHABLE)",
          [ln == 0, fin(prior4), fin(boost), z3.fpGT(boost, fp(0.0)), z3.fpIsNaN(field_score)], "sat")
    check("S4_nan_for_every_class (field score NaN regardless of class prior)",
          [ln == 0, fin(prior4), fin(boost), z3.fpGT(boost, fp(0.0)), N(z3.fpIsNaN(field_score))], "unsat")
    sumv = z3.Const("S4_sum", F)
    r4b = z3.fpDiv(RNE, sumv, i2d(ln))
    check("S4_safe len>=1 and finite sum -> finite", [ln >= 1, ln <= 2 ** 31 - 1, fin(sumv), N(fin(r4b))], "unsat")

    # S5: BM25NB log(score), score is a positive finite float widened to double
    sc = z3.Const("S5_score", z3.Float32())
    scd = z3.fpFPToFP(RNE, sc, F)
    check("S5_safe log(positive finite float)", log_ax(scd) + [N(z3.fpIsNaN(sc)), N(z3.fpIsInf(sc)),
                                                                z3.fpGT(sc, z3.FPVal(0.0, z3.Float32())),
                                                                N(fin(log(scd)))], "unsat")

    # S7: KNN score / maxScore with 0 < score <= maxScore
    s7, m7 = z3.Consts("S7_score S7_max", z3.Float32())
    d7 = z3.fpDiv(RNE, z3.fpFPToFP(RNE, s7, F), z3.fpFPToFP(RNE, m7, F))
    f32 = lambda v: z3.FPVal(v, z3.Float32())
    check("S7_safe score/maxScore in (0,1]", [z3.fpGT(s7, f32(0.0)), z3.fpLEQ(s7, m7), N(z3.fpIsInf(m7)),
                                              N(z3.fpIsNaN(s7)),
                                              N(A(fin(d7), z3.fpGT(d7, fp(0.0)), z3.fpLEQ(d7, fp(1.0))))], "unsat")
    check("S7_trig maxScore=0 (unreachable: MUST WildcardQuery scores a constant 1.0)",
          [s7 == f32(0.0), m7 == f32(0.0), z3.fpIsNaN(d7)], "sat")

    # S10: avg classification time with counter == 0
    tm = z3.Const("S10_time", F)
    cnt = z3.Int("S10_counter")
    avg = z3.fpDiv(RNE, tm, i2d(cnt))
    check("S10_trig counter=0 -> NaN or Inf avg time (REACHABLE: every assignClass returned null/null class)",
          [cnt == 0, fin(tm), z3.fpGEQ(tm, fp(0.0)), N(fin(avg))], "sat")

    ok = all(results)
    print(f"{sum(results)}/{len(results)} as expected -> {'ALL_EXPECTED' if ok else 'SOME_UNEXPECTED'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

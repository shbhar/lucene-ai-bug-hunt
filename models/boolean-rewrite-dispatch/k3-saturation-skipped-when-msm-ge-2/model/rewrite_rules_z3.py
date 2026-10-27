"""
Z3 model of BooleanQuery.rewrite rules, BooleanWeight.scorerSupplier simplifications and the
BooleanScorerSupplier dispatch preconditions (Lucene main @ ea57360912ed).

Encoding. A document is abstracted by the match status of each clause. Because the rules are about
clause GROUPS (all MUST, all FILTER, any MUST_NOT, how many SHOULD match), each group is summarised
by symbolic quantities that are unbounded integers (LIA), so the proofs hold for every number of
clauses, every multiplicity and every minimumNumberShouldMatch value, not only for small bounds:

  allM, allF : Bool   all MUST / all FILTER clauses match
  anyN       : Bool   some MUST_NOT clause matches
  nM, nF, nS : Int>=0 number of MUST / FILTER / SHOULD clauses (SHOULD counted with multiplicity)
  cS         : Int    number of matching SHOULD clauses, 0 <= cS <= nS
  msm        : Int    minimumNumberShouldMatch (any Int; the documented domain is msm >= 0)

Reference semantics (javadoc of BooleanQuery.Builder#setMinimumNumberShouldMatch and
BooleanClause.Occur):
  matches = (nM+nF+nS > 0) & allM & allF & !anyN & cS >= msm & (nM+nF > 0 | cS >= 1)
  score   = sum of scores of matching MUST and SHOULD clauses (FILTER, MUST_NOT score 0)

Every obligation is checked as UNSAT of (preconditions & original != rewritten). A SAT answer is a
counterexample and is printed; the run then exits non-zero unless the obligation is a mutant whose
SAT is expected (vacuity probes: removing a guard from the real rule must make the proof fail).

file:line mapping (BooleanQuery.java unless noted)
  R1  empty -> MatchNoDocs                                   :269-271
  R2  pure negative -> MatchNoDocs                           :274-276
  R3  single clause unwrapping (SHOULD/MUST/FILTER, msm 0/1) :279-297
  R4  clause rewrites to MatchNoDocs: drop SHOULD/MUST_NOT,
      MUST/FILTER -> MatchNoDocs                              :317-329
  R5  duplicate FILTER / MUST_NOT removal                    :344-362
  R6  FILTER in MUST_NOT, MUST(unwrapped) in MUST_NOT,
      MatchAll in MUST_NOT -> MatchNoDocs (#16667)           :365-385
  R7  drop MatchAll FILTER if |F|>1 or |M|>0; drop FILTER=MUST:388-408
  R8  FILTER also SHOULD -> MUST, msm := max(0, msm - j)     :411-436
  R9  SHOULD dedup when msm <= 1                             :442-447, deduplicateClauses :714-758
  R10 MUST dedup                                             :450-455
  R11 single MUST MatchAll + FILTER -> CSQ(filters)          :459-504
  R12 flatten nested pure disjunctions when msm <= 1         :507-528
  R13 inline required/prohibited clauses of MUST/FILTER BQ   :532-568
  R14 |SHOULD| < msm -> MatchNoDocs ; |SHOULD| == msm > 0 ->
      all SHOULD become MUST                                 :572-589
  R15 inline SHOULD clauses of the only MUST clause,
      msm := max(1, inner msm)                               :592-609
  N1  rewriteNoScoring: drop SHOULD if msm == 0 and req      :214-259
  W1  BooleanWeight: null SHOULD scorer dropped              BooleanWeight.java:297-308
  W2  |SHOULD| == msm -> SHOULD become MUST, msm := 0        BooleanWeight.java:312-317
  W3  |SHOULD| < msm -> null                                 BooleanWeight.java:324-329
  W4  !needsScores & msm == 0 & req -> drop SHOULD           BooleanWeight.java:331-336
  P1  BooleanScorerSupplier constructor preconditions        BooleanScorerSupplier.java:46-64
  D1  dispatch: optionalBulkScorer single SHOULD shortcut     BooleanScorerSupplier.java:311-313
  D2  dispatch: BooleanScorer(minShouldMatch=max(1,msm))      BooleanScorerSupplier.java:337
  D3  dispatch: getInternal opt() of a single SHOULD          BooleanScorerSupplier.java:574-576
  D4  dispatch: conjunction(req, opt) for msm > 0             BooleanScorerSupplier.java:166-173
  D5  dispatch: ReqOptSumScorer for msm == 0                  BooleanScorerSupplier.java:174-183
"""
import sys
from z3 import (And, Bool, BoolVal, If, Implies, Int, Not, Or, Real, Solver, Sum, sat, unsat,
                set_param)

set_param("timeout", 60000)

RESULTS = []


def matches(nM, nF, nS, allM, allF, anyN, cS, msm):
    return And(nM + nF + nS > 0, allM, allF, Not(anyN), cS >= msm, Or(nM + nF > 0, cS >= 1))


def maxi(a, b):
    return If(a >= b, a, b)


def check(name, pre, orig, rew, expect_sat=False, show=()):
    s = Solver()
    s.add(pre)
    s.add(orig != rew)
    r = s.check()
    ok = (r == unsat) if not expect_sat else (r == sat)
    status = "PROVED" if r == unsat else ("COUNTEREXAMPLE" if r == sat else "UNKNOWN")
    line = f"{name:<60} {status:<15} {'(expected, mutant)' if expect_sat else ''}"
    if r == sat:
        m = s.model()
        line += "  model: " + ", ".join(f"{v}={m.eval(v, model_completion=True)}" for v in show)
    print(line)
    RESULTS.append((name, status, ok))
    return r


def ints(*names):
    return [Int(n) for n in names]


# common symbols
nM, nF, nN, nS, cS, msm = ints("nM", "nF", "nN", "nS", "cS", "msm")
allM, allF, anyN = Bool("allM"), Bool("allF"), Bool("anyN")
base = And(nM >= 0, nF >= 0, nN >= 0, nS >= 0, cS >= 0, cS <= nS,
           Implies(nM == 0, allM), Implies(nF == 0, allF), Implies(nN == 0, Not(anyN)))
DOC = msm >= 0  # documented domain of minimumNumberShouldMatch
orig = matches(nM, nF, nS, allM, allF, anyN, cS, msm)

# ---------------------------------------------------------------- R1, R2
check("R1 empty BooleanQuery matches nothing", And(base, nM == 0, nF == 0, nS == 0, nN == 0),
      orig, BoolVal(False))
check("R2 pure negative matches nothing", And(base, nM == 0, nF == 0, nS == 0, nN > 0),
      orig, BoolVal(False))

# ---------------------------------------------------------------- R3 single clause
q = Bool("q")
for occ, pre, rew in [
    ("SHOULD msm=1", And(nS == 1, nM == 0, nF == 0, nN == 0, msm == 1, cS == If(q, 1, 0)), q),
    ("SHOULD msm=0", And(nS == 1, nM == 0, nF == 0, nN == 0, msm == 0, cS == If(q, 1, 0)), q),
    ("MUST msm=0", And(nS == 0, nM == 1, nF == 0, nN == 0, msm == 0, allM == q), q),
    ("FILTER msm=0", And(nS == 0, nM == 0, nF == 1, nN == 0, msm == 0, allF == q), q),
]:
    check(f"R3 single {occ} -> clause itself", And(base, pre), orig, rew)
# mutant: single MUST with msm=1 must NOT be unwrapped (the code requires msm == 0)
check("R3m single MUST msm=1 unwrapped (mutant)",
      And(base, nS == 0, nM == 1, nF == 0, nN == 0, msm == 1, allM == q), orig, q, expect_sat=True,
      show=(q,))

# ---------------------------------------------------------------- R4 a clause rewrites to MatchNoDocs
# SHOULD MatchNoDocs dropped: nS -> nS-1, cS unchanged (it never matches)
check("R4 drop MatchNoDocs SHOULD", And(base, DOC, nS >= 1, cS <= nS - 1),
      orig, matches(nM, nF, nS - 1, allM, allF, anyN, cS, msm))
check("R4 drop MatchNoDocs SHOULD (any msm)", And(base, nS >= 1, cS <= nS - 1),
      orig, matches(nM, nF, nS - 1, allM, allF, anyN, cS, msm))
check("R4 MUST MatchNoDocs -> MatchNoDocs", And(base, nM >= 1, Not(allM)), orig, BoolVal(False))
# MUST_NOT MatchNoDocs dropped: anyN is the OR of the others
anyN2 = Bool("anyN_rest")
check("R4 drop MatchNoDocs MUST_NOT", And(base, nN >= 1, anyN == anyN2), orig,
      matches(nM, nF, nS, allM, allF, anyN2, cS, msm))

# ---------------------------------------------------------------- R5 duplicate FILTER/MUST_NOT
# all(F) with duplicates == all(set F) (idempotence); we model via counts: nF' <= nF, same truth
nF2, nN2 = ints("nF_set", "nN_set")
check("R5 dedup FILTER/MUST_NOT", And(base, nF2 >= 1, nF2 <= nF, nN2 <= nN, Implies(nN > 0, nN2 >= 1)),
      orig, matches(nM, nF2, nS, allM, allF, anyN, cS, msm))

# ---------------------------------------------------------------- R6 (#16667)
x = Bool("x")  # match of the shared query
check("R6 FILTER also MUST_NOT -> MatchNoDocs", And(base, Implies(allF, x), Implies(x, anyN)),
      orig, BoolVal(False))
check("R6 MUST(unwrapped Boost/CSQ) also MUST_NOT -> MatchNoDocs",
      And(base, Implies(allM, x), Implies(x, anyN)), orig, BoolVal(False))
check("R6 MUST_NOT MatchAll -> MatchNoDocs", And(base, anyN == True), orig, BoolVal(False))
# mutant: a SHOULD clause also in MUST_NOT does NOT make the query empty
check("R6m SHOULD also MUST_NOT -> MatchNoDocs (mutant)",
      And(base, nS >= 2, Implies(x, anyN)), orig, BoolVal(False), expect_sat=True, show=(nS, cS, x))

# ---------------------------------------------------------------- R7 remove MatchAll / MUST-equal FILTER
# removing one FILTER that always matches: nF -> nF-1, allF unchanged; guard |F|>1 | |M|>0
check("R7 drop MatchAll FILTER under guard", And(base, DOC, nF >= 1, Or(nF > 1, nM > 0)),
      orig, matches(nM, nF - 1, nS, allM, allF, anyN, cS, msm))
check("R7m drop MatchAll FILTER without guard (mutant)", And(base, DOC, nF == 1, nM == 0, nS >= 1),
      orig, matches(nM, nF - 1, nS, allM, allF, anyN, cS, msm), expect_sat=True,
      show=(nS, cS, msm))
# FILTER equal to a MUST clause: dropping the FILTER keeps nM > 0
check("R7 drop FILTER equal to a MUST", And(base, DOC, nF >= 1, nM >= 1),
      orig, matches(nM, nF - 1, nS, allM, allF, anyN, cS, msm))

# ---------------------------------------------------------------- R8 FILTER also SHOULD -> MUST
# k distinct shared queries, j >= k SHOULD occurrences of them; X = all shared queries match.
j, cO, nO = ints("j", "cO", "nO")  # other SHOULD clauses
X, allFo = Bool("X"), Bool("allF_other")
pre8 = And(base, j >= 1, nO >= 0, cO >= 0, cO <= nO, nS == j + nO,
           Implies(X, cS == j + cO), Implies(Not(X), And(cS >= cO, cS <= j + cO - 1)),
           allF == And(X, allFo), nF >= 1)
rew8 = matches(nM + j, nF - 1, nO, allM, allFo, anyN, cO, maxi(0, msm - j))
# note: allM for the rewritten query must also require X (the j new MUST clauses)
rew8 = And(rew8, X)
check("R8 FILTER&SHOULD -> MUST, msm-=j (msm>=0)", And(pre8, DOC), orig, rew8)
check("R8 FILTER&SHOULD -> MUST, msm-=j (any msm)", pre8, orig, rew8)
rew8m = And(matches(nM + j, nF - 1, nO, allM, allFo, anyN, cO, maxi(0, msm - 1)), X)
check("R8m msm decremented once per query instead of per occurrence (mutant)", And(pre8, DOC),
      orig, rew8m, expect_sat=True, show=(j, msm, cO, nO))

# ---------------------------------------------------------------- R9 SHOULD dedup (msm <= 1)
cSet, nSet = ints("cSet", "nSet")
pre9 = And(base, nSet >= 1, nSet < nS, cSet >= 0, cSet <= nSet, cSet <= cS,
           (cSet == 0) == (cS == 0))
rew9 = matches(nM, nF, nSet, allM, allF, anyN, cSet, msm)
check("R9 SHOULD dedup when msm<=1", And(pre9, msm <= 1), orig, rew9)
check("R9m SHOULD dedup when msm<=2 (mutant)", And(pre9, msm <= 2), orig, rew9, expect_sat=True,
      show=(msm, cS, cSet))
# score: sum_i m_i*s_i*[q_i] == sum_i w(m_i)*s_i*[q_i] with w(m)=m (default Similarity, BM25 k3<0)
m1, m2 = ints("m1", "m2")
s1, s2 = Real("s1"), Real("s2")
b1, b2 = Bool("b1"), Bool("b2")
lin = lambda m: m  # Similarity#computeQueryTermWeight default
check("R9 SHOULD dedup score (linear query-term weight)", And(m1 >= 1, m2 >= 1, s1 >= 0, s2 >= 0),
      If(b1, m1 * s1, 0) + If(b2, m2 * s2, 0), If(b1, lin(m1) * s1, 0) + If(b2, lin(m2) * s2, 0))

# ---------------------------------------------------------------- R10 MUST dedup
nMset = Int("nMset")
check("R10 MUST dedup (matches)", And(base, DOC, nMset >= 1, nMset < nM),
      orig, matches(nMset, nF, nS, allM, allF, anyN, cS, msm))

# ---------------------------------------------------------------- R11 MatchAll MUST + FILTER -> CSQ
bst, sShould = Real("boost"), Real("sumShould")
# original: MUST MatchAll (always matches) + FILTERs + SHOULDs ; rewritten: MUST CSQ(FILTERs, MUST_NOTs)
check("R11 MatchAll MUST + FILTER -> MUST CSQ (matches)", And(base, DOC, nM == 1, allM, nF >= 1),
      orig, matches(1, 0, nS, And(allF, Not(anyN)), BoolVal(True), BoolVal(False), cS, msm))
check("R11 score boost*1 + sum(SHOULD)", And(bst >= 0, sShould >= 0), bst * 1 + sShould,
      bst + sShould)

# ---------------------------------------------------------------- R12 flatten nested disjunctions
# inner pure disjunctions D1, D2 (msm_i <= 1): inner match <=> c_i >= 1. Outer counts each inner
# as one clause; flattened counts each inner clause.
c1, c2, n1, n2, cOt, nOt = ints("c1", "c2", "n1", "n2", "cOt", "nOt")
pre12 = And(base, c1 >= 0, c2 >= 0, n1 >= 2, n2 >= 2, c1 <= n1, c2 <= n2, cOt >= 0, cOt <= nOt,
            nS == nOt + 2, cS == cOt + If(c1 >= 1, 1, 0) + If(c2 >= 1, 1, 0))
rew12 = matches(nM, nF, nOt + n1 + n2, allM, allF, anyN, cOt + c1 + c2, msm)
check("R12 flatten pure disjunctions when msm<=1", And(pre12, msm <= 1), orig, rew12)
check("R12m flatten when msm<=2 (mutant)", And(pre12, msm <= 2), orig, rew12, expect_sat=True,
      show=(msm, c1, c2, cOt))

# ---------------------------------------------------------------- R13 inline required BQ
iM, iF, iN = Bool("iAllM"), Bool("iAllF"), Bool("iAnyN")
inM, inF, inN = ints("inM", "inF", "inN")
pre13 = And(base, inM >= 0, inF >= 0, inN >= 0, inM + inF >= 1,
            Implies(inM == 0, iM), Implies(inF == 0, iF), Implies(inN == 0, Not(iN)))
inner13 = And(iM, iF, Not(iN))  # inner BQ (msm 0, no SHOULD, has a positive clause)
oM = Bool("outerAllMOther")
# the inner BQ is one of the outer MUST clauses
orig13 = matches(nM, nF, nS, And(oM, inner13), allF, anyN, cS, msm)
rew13 = matches(nM - 1 + inM, nF + inF, nS, And(oM, iM), And(allF, iF), Or(anyN, iN), cS, msm)
check("R13 inline MUST BQ(msm0, no SHOULD)", And(pre13, DOC, nM >= 1, Implies(nM == 1, oM)),
      orig13, rew13)

# ---------------------------------------------------------------- R14
check("R14 |SHOULD| < msm -> MatchNoDocs", And(base, nS < msm), orig, BoolVal(False))
check("R14 |SHOULD| == msm > 0 -> all SHOULD MUST", And(base, nS == msm, msm > 0),
      orig, matches(nM + nS, nF, 0, And(allM, cS == nS), allF, anyN, 0, 0))

# ---------------------------------------------------------------- R15 inline SHOULD of the only MUST
cI, nI, msmI = ints("cI", "nI", "msmI")
pre15 = And(base, nS == 0, cS == 0, nM == 1, cI >= 0, cI <= nI, nI >= 1, msm <= 0)
innerMatch = matches(0, 0, nI, BoolVal(True), BoolVal(True), BoolVal(False), cI, msmI)
orig15 = matches(1, nF, 0, innerMatch, allF, anyN, 0, msm)
rew15 = matches(0, nF, nI, BoolVal(True), allF, anyN, cI, maxi(1, msmI))
check("R15 inline SHOULD of the only MUST, msm=max(1,inner)", pre15, orig15, rew15)
rew15m = matches(0, nF, nI, BoolVal(True), allF, anyN, cI, msmI)
check("R15m without max(1, .) (mutant)", And(pre15, msmI >= 0), orig15, rew15m, expect_sat=True,
      show=(nF, cI, msmI))

# ---------------------------------------------------------------- N1 rewriteNoScoring
check("N1 drop SHOULD when msm==0 and a required clause exists", And(base, msm == 0, nM + nF > 0),
      orig, matches(0, nM + nF, 0, BoolVal(True), And(allM, allF), anyN, 0, 0))

# ---------------------------------------------------------------- W1..W4 BooleanWeight.scorerSupplier
nS_seg, cS_seg = ints("nS_seg", "cS_seg")  # SHOULD clauses that have a scorer on this segment
preW = And(base, DOC, nS_seg >= 0, nS_seg <= nS, cS_seg == cS, cS_seg <= nS_seg)
segQ = matches(nM, nF, nS_seg, allM, allF, anyN, cS_seg, msm)
check("W1 null SHOULD scorers dropped", preW, orig, segQ)
check("W2 |SHOULD| == msm -> MUST, msm:=0", And(preW, nS_seg == msm),
      segQ, matches(nM + nS_seg, nF, 0, And(allM, cS_seg == nS_seg), allF, anyN, 0, 0))
check("W3 |SHOULD| < msm -> no scorer", And(preW, nS_seg < msm), segQ, BoolVal(False))
check("W4 no scores, msm==0, required -> drop SHOULD", And(preW, msm == 0, nM + nF > 0),
      segQ, matches(nM, nF, 0, allM, allF, anyN, 0, 0))

# ---------------------------------------------------------------- P1 BooleanScorerSupplier preconditions
# After W1..W4, the constructor's IllegalArgumentException conditions must be unreachable:
#   msm < 0 | (msm != 0 & msm >= |SHOULD|) | (!needsScores & msm==0 & |SHOULD|>0 & req>0) | no positive
needsScores = Bool("needsScores")
nS1 = If(nS_seg == msm, 0, nS_seg)
nM1 = If(nS_seg == msm, nM + nS_seg, nM)
msm1 = If(nS_seg == msm, 0, msm)
returnsNull = Or(And(nF == 0, nM1 == 0, nS1 == 0), nS1 < msm1)
nS2 = If(And(Not(needsScores), msm1 == 0, nM1 + nF > 0), 0, nS1)
iae = Or(msm1 < 0, And(msm1 != 0, msm1 >= nS2),
         And(Not(needsScores), msm1 == 0, nS2 > 0, nM1 + nF > 0), nS2 + nM1 + nF == 0)
s = Solver()
s.add(nS_seg >= 0, nM >= 0, nF >= 0, msm >= 0, Not(returnsNull), iae)
r = s.check()
print(f"{'P1 constructor IAE unreachable for msm >= 0':<60} {'PROVED' if r == unsat else 'COUNTEREXAMPLE'}")
RESULTS.append(("P1 msm>=0", "PROVED" if r == unsat else "CEX", r == unsat))
s = Solver()
s.add(nS_seg >= 0, nM >= 0, nF >= 0, Not(returnsNull), iae)
r = s.check()
line = f"{'P1n constructor IAE reachable for msm < 0 (outside doc domain)':<60} "
if r == sat:
    m = s.model()
    line += "COUNTEREXAMPLE  model: " + ", ".join(f"{v}={m.eval(v, model_completion=True)}"
                                                    for v in (msm, nS_seg, nM, nF))
    line += "  -> replayed by harness/src/.../NegativeMsmProbe.java (IAE at search, count() succeeds)"
else:
    line += "PROVED"
print(line)
RESULTS.append(("P1n msm<0 reachable (informational)", "CEX" if r == sat else "PROVED", True))

# ---------------------------------------------------------------- D1..D5 dispatch obligations
# D1: optionalBulkScorer is only reached with no required clauses; with 1 SHOULD and msm<=1 the
# disjunction equals its single clause.
check("D1 single SHOULD bulk scorer (no required, msm<=1)",
      And(base, nM == 0, nF == 0, nS == 1, msm <= 1, msm >= 0), orig,
      And(cS == 1, Not(anyN)))
# D2: BooleanScorer counts freq >= max(1, msm) for pure disjunctions
check("D2 BooleanScorer threshold max(1,msm)", And(base, DOC, nM == 0, nF == 0, nS >= 2),
      orig, And(cS >= maxi(1, msm), Not(anyN)))
# D3: getInternal pure disjunction with a single SHOULD returns that clause: needs msm == 0 (P1)
check("D3 opt() single SHOULD (msm < |SHOULD| = 1)", And(base, nM == 0, nF == 0, nS == 1, msm >= 0,
      msm < nS), orig, And(cS == 1, Not(anyN)))
# D4/D5: mixed required/optional
check("D4 conj(req, opt(msm)) for msm>0", And(base, nM + nF > 0, nS >= 2, msm > 0, msm < nS),
      orig, And(allM, allF, Not(anyN), cS >= msm))
check("D5 ReqOptSum (req matches, opt only scores) for msm==0",
      And(base, nM + nF > 0, nS >= 1, msm == 0), orig, And(allM, allF, Not(anyN)))

bad = [r for r in RESULTS if not r[2]]
print(f"\nobligations={len(RESULTS)} failed={len(bad)}")
for b in bad:
    print("  FAILED:", b)
sys.exit(1 if bad else 0)

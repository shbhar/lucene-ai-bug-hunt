# Score-composition algebra of Lucene Monitor's QueryDecomposer under sum-resolve, over the reals (Z3).
#
# Question per decomposition rule R: the Monitor runs each part P_i of decompose(Q) separately and
# ScoringMatch.resolve / ExplainingMatch.resolve ADD the parts' scores. Does sum_i score(P_i) == score(Q)
# for every document that Q matches? SAT on "matched and not equal" = rule does NOT preserve the score
# (model printed); UNSAT = rule preserves the score over the reals (float rounding is out of scope here:
# the TV harness compares with a relative tolerance).
#
# Mapping table (model element -> Java line, checkout a9779ef3c193):
#   resolve = sum                -> monitor/ScoringMatch.java:42-45 (match1.getScore() + match2.getScore())
#                                   monitor/ExplainingMatch.java:44-52 (sum of explanation values)
#   addMatch -> resolve per part -> monitor/CandidateMatcher.java:65-74
#   R_DISJ  pure SHOULD          -> monitor/QueryDecomposer.java:88-90, 103 (each SHOULD clause decomposed)
#   R_DMAX  DisjunctionMaxQuery  -> monitor/QueryDecomposer.java:46-52 (disjuncts decomposed, tie ignored)
#           DisMax score          -> core/search/DisjunctionMaxQuery.java:239 (max + otherSum * tie), :63-66 tie in [0,1]
#   R_MUST1 single MUST, no SHOULD -> monitor/QueryDecomposer.java:85-86, 99-101
#   R_FILT1 single FILTER, no SHOULD -> monitor/QueryDecomposer.java:85-86 (FILTER put with MUST), 99-101
#           FILTER contributes 0  -> core/search/BooleanClause.java (FILTER: "does not participate in scoring")
#   R_EXCL  MUST_NOT exclusions   -> monitor/QueryDecomposer.java:105-116 (+part -ex...)
#   R_BOOST BoostQuery(b, Q)      -> monitor/QueryDecomposer.java:61-69 (BoostQuery(part, b))
#   R_DUP   equal parts collapsed -> monitor/QueryDecomposer.java:42 (Set<Query>), :80 LinkedHashSet, :82 HashSet
#           duplicate clauses sum -> core/search/BooleanQuery.java:438-455, 714-760 (boost sum / computeQueryTermWeight)
#   R_MSM   msm > 1 -> not decomposed -> monitor/QueryDecomposer.java:78
#
# Run: python3 -I score_algebra.py
from z3 import Reals, Real, Bools, Bool, Solver, And, Or, Not, If, Sum, sat, unsat, Implies

results = []


def check(name, java, constraints, claim_equal_expected):
    s = Solver()
    s.set("timeout", 60000)
    s.add(*constraints)
    r = s.check()
    preserved = r == unsat
    status = "PRESERVES score" if preserved else ("BREAKS score" if r == sat else "UNKNOWN")
    line = f"{name:<44} {status:<16} ({java})"
    if r == sat:
        m = s.model()
        line += "\n    witness: " + ", ".join(f"{d.name()}={m[d]}" for d in sorted(m.decls(), key=lambda d: d.name()))
    ok = (preserved == claim_equal_expected) and r != __import__("z3").unknown
    results.append((name, status, ok))
    print(line + ("" if ok else "    <-- differs from the plan's expectation"))


def nonneg(*xs):
    return [x >= 0 for x in xs]


def mx(*xs):
    m = xs[0]
    for x in xs[1:]:
        m = If(x > m, x, m)
    return m


# Part scores s_i >= 0 when the part matches; m_i = part matches.
s1, s2, s3 = Reals("s1 s2 s3")
m1, m2, m3 = Bools("m1 m2 m3")
t, b = Reals("tie boost")


def eff(m, s):
    return If(m, s, 0)


# R_DISJ: BooleanQuery(SHOULD p1, SHOULD p2, SHOULD p3), msm <= 1. Score = sum of matching clauses.
orig = eff(m1, s1) + eff(m2, s2) + eff(m3, s3)
dec = eff(m1, s1) + eff(m2, s2) + eff(m3, s3)
check("R_DISJ pure disjunction", "QueryDecomposer.java:88-90", nonneg(s1, s2, s3) + [Or(m1, m2, m3), orig != dec], True)

# R_DMAX: DisjunctionMaxQuery(p1, p2, p3; tie). Score = max + tie * (sum - max).
mxv = mx(eff(m1, s1), eff(m2, s2), eff(m3, s3))
sm = eff(m1, s1) + eff(m2, s2) + eff(m3, s3)
orig = mxv + t * (sm - mxv)
dec = sm
base = nonneg(s1, s2, s3) + [t >= 0, t <= 1, Or(m1, m2, m3)]
check("R_DMAX dismax (any tie)", "QueryDecomposer.java:46-52", base + [orig != dec], False)
# DMAX preserved iff tie == 1 or at most one matching disjunct with a positive score:
check("R_DMAX tie == 1", "QueryDecomposer.java:46-52", base + [t == 1, orig != dec], True)
one = Or(And(m1, Not(m2), Not(m3)), And(m2, Not(m1), Not(m3)), And(m3, Not(m1), Not(m2)))
check("R_DMAX exactly one matching disjunct", "QueryDecomposer.java:46-52", base + [one, orig != dec], True)
# Converse: tie < 1 and two matching disjuncts with positive scores ALWAYS breaks it.
# Query asks for orig == dec; UNSAT means "never equal" (printed as PRESERVES = 'the equality query is UNSAT').
check("R_DMAX tie<1, >=2 positive matches: EQUALITY query", "QueryDecomposer.java:46-52",
      base + [t < 1, m1, m2, s1 > 0, s2 > 0, orig == dec], True)  # expect UNSAT: always differs

# R_MUST1: BooleanQuery(MUST p1) alone: score(Q) = score(p1).
check("R_MUST1 single MUST", "QueryDecomposer.java:99-101", nonneg(s1) + [m1, s1 != s1], True)

# R_FILT1: BooleanQuery(FILTER p1) alone: score(Q) = 0 (FILTER does not score); decomposed part = p1 scores s1.
orig = 0
dec = s1
check("R_FILT1 single FILTER, no SHOULD", "QueryDecomposer.java:85-86,99-101", nonneg(s1) + [m1, orig != dec], False)
check("R_FILT1 holds only when the clause scores 0", "QueryDecomposer.java:85-86", nonneg(s1) + [m1, s1 == 0, orig != dec], True)

# R_FILT1 + exclusions: BooleanQuery(FILTER p1, MUST_NOT e) -> part '+p1 -e' scores s1, Q scores 0.
e = Bool("e")
check("R_FILT1 + MUST_NOT", "QueryDecomposer.java:85-86,105-116", nonneg(s1) + [m1, Not(e), 0 != s1], False)

# R_EXCL: BooleanQuery(SHOULD p1, SHOULD p2, MUST_NOT e): parts '+p_i -e'; MUST_NOT does not score.
orig = If(e, 0, eff(m1, s1) + eff(m2, s2))
dec = eff(And(m1, Not(e)), s1) + eff(And(m2, Not(e)), s2)
check("R_EXCL disjunction with exclusions", "QueryDecomposer.java:105-116", nonneg(s1, s2) + [Or(m1, m2), Not(e), orig != dec], True)

# R_BOOST: BoostQuery(b, SHOULD p1 SHOULD p2), b > 0: b*(s1+s2) vs b*s1 + b*s2 (exact over the reals).
orig = b * (eff(m1, s1) + eff(m2, s2))
dec = b * eff(m1, s1) + b * eff(m2, s2)
check("R_BOOST boost of a disjunction", "QueryDecomposer.java:61-69", nonneg(s1, s2) + [b > 0, Or(m1, m2), orig != dec], True)
# R_BOOST over DisMax inherits R_DMAX (b * dismax vs b * sum):
mxv = mx(eff(m1, s1), eff(m2, s2))
orig = b * (mxv + t * (eff(m1, s1) + eff(m2, s2) - mxv))
dec = b * (eff(m1, s1) + eff(m2, s2))
check("R_BOOST of a DisMax", "QueryDecomposer.java:61-69 + 46-52", nonneg(s1, s2) + [b > 0, t >= 0, t <= 1, m1, m2, orig != dec], False)

# R_DUP: BooleanQuery(SHOULD p1, SHOULD p1) (or nested disjunctions sharing a part): BooleanQuery sums the
# duplicate (boost 2 via computeQueryTermWeight(2) = 2 for the default BM25, k3 < 0); the Set keeps p1 once.
w2 = Real("w2")  # computeQueryTermWeight(2); default BM25 (k3<0) and Similarity default: 2
orig = w2 * eff(m1, s1)
dec = eff(m1, s1)
check("R_DUP duplicate SHOULD part (w2 = 2)", "QueryDecomposer.java:42,80", nonneg(s1) + [w2 == 2, m1, orig != dec], False)
# Nested: (p1 SHOULD p2) SHOULD (p1 SHOULD p3): score 2*s1 + s2 + s3 ; parts {p1, p2, p3}: s1 + s2 + s3
orig = 2 * eff(m1, s1) + eff(m2, s2) + eff(m3, s3)
dec = eff(m1, s1) + eff(m2, s2) + eff(m3, s3)
check("R_DUP nested disjunctions sharing a part", "QueryDecomposer.java:80,89", nonneg(s1, s2, s3) + [Or(m1, m2, m3), orig != dec], False)
check("R_DUP holds iff the shared part does not match / scores 0", "QueryDecomposer.java:80,89",
      nonneg(s1, s2, s3) + [Or(Not(m1), s1 == 0), Or(m1, m2, m3), orig != dec], True)

# R_MSM: msm > 1 -> singleton(Q): trivially preserved.
check("R_MSM msm>1 not decomposed", "QueryDecomposer.java:78", nonneg(s1) + [m1, s1 != s1], True)

# ---- Mutant / vacuity checks: Luwak's original resolve kept the MAX of the parts
# (flaxsearch/luwak ScoringMatcher.resolve: match1.getScore() < match2.getScore() ? match2 : match1).
# Under max-resolve the verdicts must flip: pure disjunction breaks, DisMax with tie 0 is preserved.
print("-- mutant: max-resolve (Luwak) instead of sum-resolve")
orig = eff(m1, s1) + eff(m2, s2)
dec = mx(eff(m1, s1), eff(m2, s2))
check("MUTANT max-resolve: pure disjunction", "luwak ScoringMatcher.resolve", nonneg(s1, s2) + [Or(m1, m2), orig != dec], False)
mxv = mx(eff(m1, s1), eff(m2, s2))
orig = mxv + 0 * (eff(m1, s1) + eff(m2, s2) - mxv)
check("MUTANT max-resolve: DisMax tie 0", "luwak ScoringMatcher.resolve", nonneg(s1, s2) + [Or(m1, m2), orig != dec], True)
orig = mxv + t * (eff(m1, s1) + eff(m2, s2) - mxv)
check("MUTANT max-resolve: DisMax tie in (0,1]", "luwak ScoringMatcher.resolve", nonneg(s1, s2) + [t > 0, t <= 1, m1, m2, orig != dec], False)

print()
bad = [r for r in results if not r[2]]
print(f"{len(results)} obligations, {len(bad)} differ from the plan's expectation")

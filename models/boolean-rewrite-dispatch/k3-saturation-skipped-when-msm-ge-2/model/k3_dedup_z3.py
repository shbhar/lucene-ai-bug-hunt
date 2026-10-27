"""
Z3 obligations for the k3 query-term weight hook (Similarity#computeQueryTermWeight, #15818) through
the BooleanQuery.rewrite paths that a query with one duplicated SHOULD term "a a b" can take.

Contract (BM25Similarity k3 javadoc): a term that appears qtf times in the query gets the boost
w(qtf) = ((k3 + 1) * qtf) / (k3 + qtf) instead of qtf. For k3 >= 0, w(2) = 2(k3+1)/(k3+2), so
1 <= w(2) < 2. For a document that matches every clause, the documented score is w(2)*sa + sb,
independent of minimumNumberShouldMatch (msm only decides WHICH documents match).

Rewrite paths, read from BooleanQuery.java on main @ ea57360912ed:
  msm <= 1 : SHOULD dedup (:442-447 -> deduplicateClauses :714-758)  -> a^w(2) b        score w2*sa+sb
  msm == 2 : no dedup (guard msm <= 1), |SHOULD| = 3 != msm (:577)   -> a a b (linear)  score 2*sa+sb
  msm == 3 : |SHOULD| == msm -> all MUST (:577-588), MUST dedup (:450) -> +a^w(2) +b    score w2*sa+sb
  fix      : msm >= 2 keeps n occurrences, each boosted by w(n)/n       -> a^(w2/2) a^(w2/2) b
"""
import sys
from z3 import And, Real, Solver, sat, unsat, Or

sa, sb, k3 = Real("sa"), Real("sb"), Real("k3")
w2 = 2 * (k3 + 1) / (k3 + 2)
pre = And(sa > 0, sb >= 0, k3 >= 0)

score = {
    1: w2 * sa + sb,
    2: 2 * sa + sb,
    3: w2 * sa + sb,
}
fixed2 = 2 * (w2 / 2) * sa + sb
documented = w2 * sa + sb

ok = True


def q(name, formula, expect):
    global ok
    s = Solver()
    s.add(pre, formula)
    r = s.check()
    res = "SAT" if r == sat else ("UNSAT" if r == unsat else "UNKNOWN")
    good = (r == sat) if expect == "sat" else (r == unsat)
    ok &= good
    line = f"{name:<75} {res:<6} expected {expect.upper():<6} {'ok' if good else 'MISMATCH'}"
    if r == sat:
        m = s.model()
        line += f"  model: k3={m[k3]}, sa={m[sa]}, sb={m[sb]}"
    print(line)


q("msm=1 path equals the documented k3 score", score[1] != documented, "unsat")
q("msm=3 path equals the documented k3 score", score[3] != documented, "unsat")
q("msm=2 path differs from the documented k3 score (the bug)", score[2] != documented, "sat")
q("msm=2 scores strictly higher than msm=1 AND msm=3 (non-monotone in msm)",
  And(score[2] > score[1], score[2] > score[3]), "sat")
q("msm=2 can be lower than the documented score (never: w(2) <= 2)", score[2] < documented, "unsat")
# control: with k3 disabled the weight is linear, w(2) = 2, and every path agrees
q("control, k3 disabled (w(2)=2): msm=2 path equals the documented score", (2 * sa + sb) != (2 * sa + sb), "unsat")
q("fix: per-occurrence boost w(2)/2 restores the documented score", fixed2 != documented, "unsat")
sys.exit(0 if ok else 1)

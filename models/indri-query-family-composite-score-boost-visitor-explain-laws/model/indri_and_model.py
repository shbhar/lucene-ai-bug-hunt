"""
Z3 model of IndriAndQuery matching, IndriAndWeight.explain and IndriQuery.visit
(Lucene main a9779ef3c193), checked against two contracts:

  (C1) explain/score consistency: Explanation.isMatch() of IndexSearcher.explain(q, d) agrees with
       whether d is a hit of q (Weight.explain javadoc; the test framework's CheckHits/QueryUtils
       contract that a non-hit is explained as a non-match and a hit as a match with the hit's score).
  (C2) BooleanClause.Occur semantics (BooleanClause.java:30-62): MUST/FILTER clauses must match,
       MUST_NOT clauses must not match, SHOULD clauses are optional (at least one SHOULD must match
       when there are no required clauses), as implemented by BooleanQuery.
  (C3) Query.visit (Query.java:88-93): "Recurse through the query tree, visiting any child queries";
       IndexSearcher's cumulative clause check (IndexSearcher.java:891-932) counts the leaves the
       visitor reaches.

Mapping table (model element -> Java line, Lucene main-a9779ef):
  hit(d)            = OR_i m_i            IndriAndWeight.java:47-65 (getScorer: every clause's scorer,
                                          whatever its Occur) + IndriDisjunctionScorer.java:36-41
                                          (DisjunctionDISIApproximation over all sub-scorers)
  present_i                               IndriAndWeight.java:51-53 (w.scorer(context) != null, i.e.
                                          the clause has a match somewhere in the segment); m_i -> present_i
  required(o)       = o in {MUST,FILTER}  BooleanClause.java (Occur.isRequired / record isRequired())
  fail              = OR_i (!m_i & req_i) IndriAndWeight.java:84-90
  explainMatch      = !fail & anyPresent  IndriAndWeight.java:92-104 (scorer(context) != null -> match)
  explainAssertErr  = !fail & anyPresent & !hit   IndriAndWeight.java:98-99 (advance(doc) lands past doc;
                                          'assert advanced == doc' fires under -ea; under -da the
                                          explanation is a match with the score of the doc it landed on)
  ref(d)            (C2)                  BooleanQuery / BooleanWeight semantics
  visitCount(Indri) = 1                   IndriQuery.java:71-74 (visitor.visitLeaf(this))
  (C4) two-phase clauses:
  a_i  (approximation of clause i matches d; a_i >= m_i; a_i = m_i unless clause i is two-phase)
                                          DisiWrapper.java:69-77 (approximation = twoPhaseView.approximation())
  hitTP(d) = OR_i m_i            if #present <= 1   IndriAndWeight.java:60-63 (a single sub-scorer is returned as is,
                                                    two-phase view intact)
           = OR_i a_i            otherwise           IndriDisjunctionScorer.java:36-47 (iterator() = disjunction of
                                                    approximations; no twoPhaseIterator() override, Scorer.java:58)
  contract: IndriAndQuery.java:22 "matches documents matching combinations of subqueries" and Scorer.java:34
            "a DocIdSetIterator over matching documents": hit(d) -> OR_i m_i

Fix models checked for (C1):
  FIX_ADV:  explainMatch' = !fail & anyPresent & hit      (check advanced == doc; else noMatch)
  FIX_PR463: explainMatch'' = anyPresent (no isRequired check; open PR #463's explain, unchecked advance kept)
  FIX_BOTH: explainMatch''' = anyPresent & hit = hit      (drop isRequired + check advance)

Bound: N clauses (default 4); the model is propositional, so a bound of 4 covers every combination of
(occur, matches, present) per clause for up to 4 clauses; the TV harness checks the real jar up to 3
clauses x 3 terms x 4 occurs x 8 documents (harness/TestIndriAndBoundedTV.java).

Run: python3 -I model/indri_and_model.py
"""

import itertools
import json
import sys

from z3 import And, Bool, BoolVal, If, Implies, Int, Not, Or, Solver, Sum, sat, unsat

OCCURS = ["SHOULD", "MUST", "FILTER", "MUST_NOT"]  # encoded 0..3


def build(n):
    occ = [Int(f"occ{i}") for i in range(n)]
    m = [Bool(f"m{i}") for i in range(n)]  # clause i matches doc d
    p = [Bool(f"p{i}") for i in range(n)]  # clause i has a scorer in the segment
    dom = []
    for i in range(n):
        dom += [occ[i] >= 0, occ[i] <= 3, Implies(m[i], p[i])]
    req = [Or(occ[i] == 1, occ[i] == 2) for i in range(n)]
    should = [occ[i] == 0 for i in range(n)]
    mustnot = [occ[i] == 3 for i in range(n)]
    hit = Or(*m)
    any_present = Or(*p)
    fail = Or(*[And(Not(m[i]), req[i]) for i in range(n)])
    explain_match = And(Not(fail), any_present)
    explain_assert = And(Not(fail), any_present, Not(hit))
    has_req = Or(*req)
    ref = And(
        And(*[Implies(req[i], m[i]) for i in range(n)]),
        And(*[Implies(mustnot[i], Not(m[i])) for i in range(n)]),
        Or(has_req, Or(*[And(should[i], m[i]) for i in range(n)])),
    )
    return dict(
        occ=occ, m=m, p=p, dom=dom, req=req, should=should, mustnot=mustnot, hit=hit,
        any_present=any_present, fail=fail, explain_match=explain_match,
        explain_assert=explain_assert, ref=ref,
        fix_adv=And(Not(fail), any_present, hit),
        fix_pr463=any_present,
        fix_both=And(any_present, hit),
    )


def check(name, n, extra, goal, expect):
    """goal is the property violation; expect 'sat' (counterexample exists) or 'unsat' (proved)."""
    M = build(n)
    s = Solver()
    s.add(*M["dom"])
    for e in extra(M):
        s.add(e)
    s.add(goal(M))
    r = s.check()
    out = {"query": name, "n": n, "result": str(r), "expected": expect}
    if r == sat:
        mod = s.model()
        out["witness"] = [
            {
                "occur": OCCURS[mod.eval(M["occ"][i]).as_long()],
                "matches_doc": bool(mod.eval(M["m"][i], model_completion=True)),
                "present_in_segment": bool(mod.eval(M["p"][i], model_completion=True)),
            }
            for i in range(n)
        ]
        out["hit"] = bool(mod.eval(M["hit"], model_completion=True))
        out["explain_match"] = bool(mod.eval(M["explain_match"], model_completion=True))
        out["ref"] = bool(mod.eval(M["ref"], model_completion=True))
    out["ok"] = str(r) == expect
    return out


def two_phase_queries(n):
    """C4: SHOULD-only clauses; some clauses two-phase (approximation a_i may exceed m_i)."""
    out = []
    for name, extra, goal, expect in [
        ("C4 hit of a doc that matches no subquery (two-phase clause, >= 2 present scorers)",
         lambda V: [], lambda V: And(V["hitTP"], Not(Or(*V["m"]))), "sat"),
        ("C4 impossible with a single present scorer (proof)",
         lambda V: [V["atMostOnePresent"]], lambda V: And(V["hitTP"], Not(Or(*V["m"]))), "unsat"),
        ("C4 impossible when no clause is two-phase (a_i == m_i) (proof)",
         lambda V: [V["exact"]], lambda V: And(V["hitTP"], Not(Or(*V["m"]))), "unsat"),
        ("C4 fix (disjunction TwoPhaseIterator checking sub-matches): hit == OR m_i (proof)",
         lambda V: [], lambda V: V["hitFix"] != Or(*V["m"]), "unsat"),
    ]:
        m = [Bool(f"m{i}") for i in range(n)]
        a = [Bool(f"a{i}") for i in range(n)]
        p = [Bool(f"p{i}") for i in range(n)]
        s = Solver()
        for i in range(n):
            s.add(Implies(m[i], a[i]), Implies(a[i], p[i]))
        npres = Sum(*[If(p[i], 1, 0) for i in range(n)])
        V = dict(m=m, a=a, p=p,
                 hitTP=If(npres <= 1, Or(*m), Or(*a)),
                 hitFix=Or(*m),
                 atMostOnePresent=npres <= 1,
                 exact=And(*[a[i] == m[i] for i in range(n)]))
        for e in extra(V):
            s.add(e)
        s.add(goal(V))
        r = s.check()
        o = {"query": name, "n": n, "result": str(r), "expected": expect, "ok": str(r) == expect}
        if r == sat:
            mod = s.model()
            o["witness"] = [{"matches_doc": bool(mod.eval(m[i], model_completion=True)),
                             "approximation_matches_doc": bool(mod.eval(a[i], model_completion=True)),
                             "present_in_segment": bool(mod.eval(p[i], model_completion=True))} for i in range(n)]
        out.append(o)
    return out


def classify_families(n):
    """Enumerate all (occur, m, p) assignments (the model is finite) and group the C1 violations."""
    fam = {}
    for occs in itertools.product(range(4), repeat=n):
        for ms in itertools.product([False, True], repeat=n):
            for ps in itertools.product([False, True], repeat=n):
                if any(ms[i] and not ps[i] for i in range(n)):
                    continue
                hit = any(ms)
                req = [o in (1, 2) for o in occs]
                fail = any((not ms[i]) and req[i] for i in range(n))
                em = (not fail) and any(ps)
                if em != hit:
                    key = "F1 non-hit explained as match (unchecked advance)" if em else \
                          "F2 hit explained as non-match (explain honours isRequired, scorer does not)"
                    fam[key] = fam.get(key, 0) + 1
    return fam


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    res = []
    none = lambda M: []
    should_only = lambda M: [o == 0 for o in M["occ"]]
    # C1: explain vs hits
    res.append(check("C1 explain.isMatch != hit (any occur)", n, none,
                     lambda M: M["explain_match"] != M["hit"], "sat"))
    res.append(check("C1-F1 non-hit explained as match, SHOULD-only clauses", n, should_only,
                     lambda M: And(M["explain_match"], Not(M["hit"])), "sat"))
    res.append(check("C1-F2 hit explained as non-match, SHOULD-only clauses (expect impossible)", n,
                     should_only, lambda M: And(M["hit"], Not(M["explain_match"])), "unsat"))
    res.append(check("C1-F2 hit explained as non-match, some MUST/FILTER clause", n,
                     lambda M: [Or(*M["req"])], lambda M: And(M["hit"], Not(M["explain_match"])), "sat"))
    res.append(check("C1-F1 needs a clause present in the segment but no clause matching (proof)", n, none,
                     lambda M: And(M["explain_match"], Not(M["hit"]),
                                   Not(And(M["any_present"], Not(Or(*M["m"]))))), "unsat"))
    # fixes
    res.append(check("FIX_ADV (check advanced==doc): C1 violation, SHOULD-only (expect proved)", n,
                     should_only, lambda M: M["fix_adv"] != M["hit"], "unsat"))
    res.append(check("FIX_ADV: C1 violation with MUST clauses remains (F2)", n, none,
                     lambda M: M["fix_adv"] != M["hit"], "sat"))
    res.append(check("FIX_PR463 (drop isRequired, keep unchecked advance): C1 violation remains (F1)", n,
                     none, lambda M: M["fix_pr463"] != M["hit"], "sat"))
    res.append(check("FIX_BOTH: C1 violation for any occur (expect proved)", n, none,
                     lambda M: M["fix_both"] != M["hit"], "unsat"))
    # C2: occur semantics vs BooleanQuery reference
    res.append(check("C2 hit != ref, SHOULD-only (expect proved equal)", n, should_only,
                     lambda M: M["hit"] != M["ref"], "unsat"))
    res.append(check("C2 widening: hit & !ref (some clause not SHOULD)", n, none,
                     lambda M: And(M["hit"], Not(M["ref"])), "sat"))
    res.append(check("C2 narrowing: ref & !hit (expect impossible: union is a superset)", n, none,
                     lambda M: And(M["ref"], Not(M["hit"])), "unsat"))
    res.append(check("C2 widening needs a MUST/FILTER clause that misses or a MUST_NOT clause that matches (proof)",
                     n, none,
                     lambda M: And(M["hit"], Not(M["ref"]),
                                   Not(Or(*[Or(And(M["req"][i], Not(M["m"][i])),
                                               And(M["mustnot"][i], M["m"][i])) for i in range(n)]))),
                     "unsat"))
    res.append(check("C2 lone MUST_NOT clause: hit & !ref", 1, lambda M: [M["occ"][0] == 3],
                     lambda M: And(M["hit"], Not(M["ref"])), "sat"))
    res += two_phase_queries(n)
    fam = classify_families(min(n, 3))
    summary = {"model": "indri_and_model.py", "n": n, "queries": res,
               "c1_violation_families_by_enumeration_n3": fam,
               "all_as_expected": all(r["ok"] for r in res)}
    print(json.dumps(summary, indent=1))
    return 0 if summary["all_as_expected"] else 1


if __name__ == "__main__":
    sys.exit(main())

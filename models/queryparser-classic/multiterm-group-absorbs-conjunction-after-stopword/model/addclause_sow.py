"""Z3 proof: with an analyzer that analyses each word on its own (no multi-word synonyms), the classic
QueryParser builds queries with the same match set for splitOnWhitespace=true and =false, for every clause
sequence of length <= N, both default operators, every modifier/conjunction, and stopwords anywhere.

Model element -> Java (lucene/queryparser/src/java/org/apache/lucene/queryparser/classic/):
  item i = one grammar clause: conj[i] (CONJ_NONE/AND/OR), mods[i] (MOD_NONE/REQ/NOT), bare[i] (a plain <TERM>
           without field, boost, fuzzy, wildcard), nul[i] (the word analyses to no token), v[i] (it matches the doc)
                                                         QueryParser.jj:264-295 (Query), 297-314 (Clause)
  addClause(...)                                         QueryParserBase.java:346-390 (transcribed literally)
  MultiTerm grouping (sow=false): a maximal run k..j (j>k) of bare items with CONJ_NONE/MOD_NONE, where every
           item k+1..j is followed by a token allowed by allowedPostMultiTerm (not AND/OR; a following bare item
           is a TERM, allowed); the run's text is analysed once: query = op-combination of its non-null words
                                                         QueryParser.jj:376-409, QueryParser.jj:166-171
           added with addClause(CONJ_NONE, MOD_NONE)     QueryParser.jj:405 (since #16446; before:
                                                         addMultiTermClauses, flattening)
  BooleanQuery semantics: all MUST, no MUST_NOT, and (some MUST or some SHOULD matching); empty -> no match.
Mutation/vacuity: dropping the "not followed by AND/OR" condition must make the proof fail (sat).
"""
import json
import sys
from z3 import And, Bool, BoolVal, If, Implies, Int, Not, Or, Solver, sat, unsat

CN, CA, CO = 0, 1, 2           # CONJ_NONE, CONJ_AND, CONJ_OR
MN, MR, MX = 0, 1, 2           # MOD_NONE, MOD_REQ, MOD_NOT
MUST, SHOULD, NOT_, ABSENT = 0, 1, 2, 3


def add_clause(occ, n_before, conj, mods, isnull, andop):
    """occ: list of Z3 Int expressions (occur of existing clauses, ABSENT if not present). Returns new list
    (one more entry for the new clause). Literal transcription of QueryParserBase.addClause."""
    occ = list(occ)
    # index of the last present clause
    last_idx = None
    lastpos = [BoolVal(False)] * len(occ)
    for j in range(len(occ)):
        is_last = And(occ[j] != ABSENT, *[occ[t] == ABSENT for t in range(j + 1, len(occ))])
        lastpos[j] = is_last
    for j in range(len(occ)):
        # if (clauses.size() > 0 && conj == CONJ_AND) { if !prohibited -> MUST }
        occ[j] = If(And(lastpos[j], conj == CA, occ[j] != NOT_), MUST, occ[j])
    for j in range(len(occ)):
        # if (clauses.size() > 0 && operator == AND && conj == CONJ_OR) { if !prohibited -> SHOULD }
        occ[j] = If(And(lastpos[j], andop, conj == CO, occ[j] != NOT_), SHOULD, occ[j])
    prohibited = mods == MX
    required_or = Or(mods == MR, And(conj == CA, Not(prohibited)))
    required_and = And(Not(prohibited), conj != CO)
    required = If(andop, required_and, required_or)
    new = If(isnull, ABSENT, If(prohibited, NOT_, If(required, MUST, SHOULD)))
    return occ + [new]


def bq_match(occ, vals):
    any_must = Or([o == MUST for o in occ]) if occ else BoolVal(False)
    musts = And([Implies(o == MUST, v) for o, v in zip(occ, vals)]) if occ else BoolVal(True)
    nots = And([Implies(o == NOT_, Not(v)) for o, v in zip(occ, vals)]) if occ else BoolVal(True)
    should = Or([And(o == SHOULD, v) for o, v in zip(occ, vals)]) if occ else BoolVal(False)
    return And(musts, nots, Or(any_must, should))


def build(N, mutant=False):
    conj = [Int(f"conj{i}") for i in range(N)]
    mods = [Int(f"mods{i}") for i in range(N)]
    bare = [Bool(f"bare{i}") for i in range(N)]
    nul = [Bool(f"null{i}") for i in range(N)]
    v = [Bool(f"v{i}") for i in range(N)]
    andop = Bool("andop")
    length = Int("len")
    cons = [length >= 1, length <= N, conj[0] == CN]
    for i in range(N):
        cons += [conj[i] >= 0, conj[i] <= 2, mods[i] >= 0, mods[i] <= 2]
    # sow=true: every item is its own clause (MultiTerm adds one clause per word with CONJ_NONE/MOD_NONE,
    # which is what the item already has)
    def run_sow_true(L):
        occ, vals = [], []
        for i in range(L):
            occ = add_clause(occ, i, conj[i], mods[i], nul[i], andop)
            vals.append(v[i])
        return bq_match(occ, vals)

    # sow=false: group runs.  plain[i]: item can be part of a MultiTerm run
    def run_sow_false(L):
        plain = [And(bare[i], conj[i] == CN, mods[i] == MN) for i in range(L)]
        # follow_ok[i]: the token after item i is allowed after a MultiTerm term (not AND/OR); end of input allowed
        follow_ok = [(Not(Or(conj[i + 1] == CA, conj[i + 1] == CO)) if i + 1 < L else BoolVal(True)) for i in range(L)]
        if mutant:
            follow_ok = [BoolVal(True)] * L
        # in_run_next[i]: item i+1 joins the run that contains item i
        joins = [And(plain[i], plain[i + 1], follow_ok[i + 1]) if i + 1 < L else BoolVal(False) for i in range(L)]
        # Build clause list: walk items; Z3 expressions for the grouping (start of a run = not joined from the left)
        occ, vals = [], []
        for i in range(L):
            joined_left = joins[i - 1] if i > 0 else BoolVal(False)
            # the run starting at i extends to j while joins hold; compute group value and nullness
            # group members: i..j where joins[i..j-1] all true and (j == L-1 or joins[j] false)
            grp_val_and = BoolVal(True)
            grp_val_or = BoolVal(False)
            grp_null = BoolVal(True)
            cont = BoolVal(True)
            for j in range(i, L):
                member = cont
                grp_val_and = And(grp_val_and, Implies(And(member, Not(nul[j])), v[j]))
                grp_val_or = Or(grp_val_or, And(member, Not(nul[j]), v[j]))
                grp_null = And(grp_null, Implies(member, nul[j]))
                cont = And(cont, joins[j]) if j + 1 < L else BoolVal(False)
            is_run = joins[i]                      # a run of length >= 2 starts here (if not joined_left)
            gval = If(andop, grp_val_and, grp_val_or)
            start = Not(joined_left)
            # item i contributes a clause only if it starts a group (single item or run start)
            c_conj = If(is_run, CN, conj[i])
            c_mods = If(is_run, MN, mods[i])
            c_null = Or(Not(start), If(is_run, grp_null, nul[i]))
            c_val = If(is_run, gval, v[i])
            occ = add_clause(occ, i, c_conj, c_mods, c_null, andop)
            vals.append(c_val)
        return bq_match(occ, vals)

    results = []
    for L in range(1, N + 1):
        results.append((L, run_sow_true(L), run_sow_false(L)))
    return dict(cons=cons, results=results, length=length, andop=andop, conj=conj, mods=mods, bare=bare, nul=nul, v=v)


def check(N, mutant):
    M = build(N, mutant)
    out = {}
    for L, t, f in M["results"]:
        s = Solver()
        s.add(M["cons"])
        s.add(t != f)
        r = s.check()
        w = None
        if r == sat:
            mo = s.model()
            w = {"andop": bool(mo.eval(M["andop"], model_completion=True)),
                 "items": [{"conj": mo.eval(M["conj"][i], model_completion=True).as_long(),
                            "mods": mo.eval(M["mods"][i], model_completion=True).as_long(),
                            "bare": bool(mo.eval(M["bare"][i], model_completion=True)),
                            "null": bool(mo.eval(M["nul"][i], model_completion=True)),
                            "v": bool(mo.eval(M["v"][i], model_completion=True))} for i in range(L)]}
        out[L] = {"result": str(r), "witness": w}
    return out


if __name__ == "__main__":
    N = int(sys.argv[1])
    rep = {"proof": check(N, False), "mutant_no_follow_restriction": check(N, True)}
    for k, val in rep.items():
        print(k, {L: x["result"] for L, x in val.items()})
    json.dump(rep, open(sys.argv[2], "w"), indent=1)

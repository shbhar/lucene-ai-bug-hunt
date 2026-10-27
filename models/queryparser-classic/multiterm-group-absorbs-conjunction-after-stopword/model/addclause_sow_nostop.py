"""Variant of addclause_sow.py: no stopwords (every item analyses to a token). Claim: splitOnWhitespace=true and
=false give the same match set for every clause sequence of length <= N (see addclause_sow.py for the mapping)."""
import json, sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from addclause_sow import build
from z3 import Solver, Not
N = int(sys.argv[1]); out = {}
M = build(N, False)
for L, t, f in M["results"]:
    s = Solver(); s.add(M["cons"]); s.add([Not(x) for x in M["nul"]]); s.add(t != f)
    out[L] = str(s.check())
Mm = build(N, True); mut = {}
for L, t, f in Mm["results"]:
    s = Solver(); s.add(Mm["cons"]); s.add([Not(x) for x in Mm["nul"]]); s.add(t != f)
    mut[L] = str(s.check())
print("no-stopword proof:", out); print("no-stopword mutant (no follow restriction):", mut)
json.dump({"proof": out, "mutant": mut}, open(sys.argv[2], "w"), indent=1)

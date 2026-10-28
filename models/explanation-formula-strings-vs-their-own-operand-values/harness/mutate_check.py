# Vacuity check for eval_sweep.py: inject one fault per check type into a clean sweep (the sweep run
# with the three fix sketches shadowed, whose only remaining group is the documented LMDirichlet
# clamp), run the evaluator per mutant and require the mutant's group to appear.
# Usage: python3 -I mutate_check.py sweep-fix.jsonl eval_sweep.py workdir
import json, subprocess, sys, os
src, evaluator, work = sys.argv[1:4]
lines = open(src, encoding="utf-8").read().splitlines()
MUTANTS = [
    ("sum node value x1.01", lambda n: n["d"] == "sum of:" and n["k"], lambda n: n.update(v=n["v"] * 1.01)),
    ("product child x1.05", lambda n: n["d"].endswith("product of:") and n["k"], lambda n: n["k"][0].update(v=n["k"][0]["v"] * 1.05)),
    ("dismax node x1.01", lambda n: n["d"].startswith("max plus"), lambda n: n.update(v=n["v"] * 1.01)),
    ("max node x1.01 (value != 0)", lambda n: n["d"] == "max of:" and float(n["v"]) != 0, lambda n: n.update(v=n["v"] * 1.01)),
    ("BM25 idf formula node x1.01", lambda n: n["d"].startswith("idf, computed as log(1 + (N - n + 0.5)"), lambda n: n.update(v=n["v"] * 1.01)),
    ("FeatureField saturation k child +1", lambda n: n["d"].startswith("Saturation function on the feat"), lambda n: n["k"][1].update(v=n["k"][1]["v"] + 1)),
    ("label n (docFreq) +1 in BasicModelIn", lambda n: n["d"].startswith("BasicModelIn, computed"), lambda n: [k.update(v=k["v"] + 1) for k in n["k"] if k["d"].startswith("n, ")]),
    ("StableTfl tr node x1.01", lambda n: n["d"].startswith("tr, term rarity, computed as log"), lambda n: n.update(v=n["v"] * 1.01)),
    ("Bayesian beta child +0.1", lambda n: n["d"].startswith("sigmoid calibration, computed"), lambda n: n["k"][2].update(v=n["k"][2]["v"] + 0.1)),
    ("distance pivot child x2 (value != origin)", lambda n: n["d"].startswith("Distance score, computed as weight * pivotDistance / (pivotDistance + abs(value") and n["k"][2]["v"] != n["k"][3]["v"], lambda n: n["k"][1].update(v=n["k"][1]["v"] * 2)),
]
def find(n, pred):
    if pred(n): return n
    for k in n["k"]:
        f = find(k, pred)
        if f: return f
    return None
ok = 0
base = subprocess.run([sys.executable, "-I", evaluator, src], capture_output=True, text=True).stdout
base_total = [l for l in base.splitlines() if l.startswith("mismatch groups")][0]
print("baseline:", base_total)
for name, pred, mut in MUTANTS:
    out = []; done = False
    for ln in lines:
        rec = json.loads(ln)
        if not done:
            n = find(rec["expl"], pred)
            if n is not None:
                mut(n); done = True; ln = json.dumps(rec)
        out.append(ln)
    p = os.path.join(work, "mutant.jsonl")
    open(p, "w", encoding="utf-8").write("\n".join(out) + "\n")
    res = subprocess.run([sys.executable, "-I", evaluator, p], capture_output=True, text=True).stdout
    total = [l for l in res.splitlines() if l.startswith("mismatch groups")][0]
    killed = done and total != base_total
    ok += killed
    print("%-40s applied=%s killed=%s (%s)" % (name, done, killed, total))
os.remove(os.path.join(work, "mutant.jsonl"))
print("killed %d of %d mutants" % (ok, len(MUTANTS)))

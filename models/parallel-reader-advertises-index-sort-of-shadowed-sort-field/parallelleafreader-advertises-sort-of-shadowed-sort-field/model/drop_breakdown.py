"""Breakdown of the structures where FIX_OWNER drops a sort that the SMT leg proved always true (UNSAT).
Reads compositions-main.jsonl and smt-report.json's per-structure verdicts by re-deriving the key; run after
violation_smt.py with its --dump of verdicts (verdicts.jsonl)."""
import json, sys
from collections import Counter
verdicts = {}
for line in open(sys.argv[2]):
    v = json.loads(line); verdicts[v["key"]] = v["res"]
def parse_owner(o):
    return (-1, None) if o == "-1" else (int(o[:-1]), o[-1])
cnt = Counter(); ex = {}
sidecar_ok = Counter()
for line in open(sys.argv[1]):
    c = json.loads(line); leaf = c["leaf"]
    if not leaf["accepted"] or leaf["sort"] == "none": continue
    adv = leaf["sort"]; readers = c["readers"]
    owners = {"s": parse_owner(leaf["own_s"]), "t": parse_owner(leaf["own_t"])}
    key = json.dumps([readers, adv, {f: owners[f] for f in adv}], sort_keys=True)
    res = verdicts[key]
    keep = any(rd["sort"] == adv and all(owners[f][0] in (-1, r) for f in adv) for r, rd in enumerate(readers))
    # the javadoc's sidecar use: every sort field owned by a reader that declares the sort, the other readers
    # unsorted and carrying none of the sort fields
    declaring = [r for r, rd in enumerate(readers) if rd["sort"] == adv]
    if declaring and all(owners[f][0] in declaring + [-1] for f in adv) and len({owners[f][0] for f in adv if owners[f][0] >= 0}) <= 1:
        sidecar_ok["single-declaring-owner: kept" if keep else "single-declaring-owner: DROPPED"] += 1
    if res != "unsat" or keep: continue
    fam = []
    for f in adv:
        pos, kind = owners[f]
        if pos < 0: fam.append(f + ":unowned")
        elif readers[pos]["sort"] != adv: fam.append(f + ":owner-undeclared-" + kind)
        else: fam.append(f + ":owner-declares-r" + str(pos))
    owner_set = {owners[f][0] for f in adv if owners[f][0] >= 0 and readers[owners[f][0]]["sort"] == adv}
    if all(("undeclared-p" in x) or ("unowned" in x) or ("declares" in x) for x in fam) and any("undeclared-p" in x for x in fam):
        cls = "shadowed by a non-doc-values field (served constant) and the order happens to hold"
    elif len(owner_set) > 1 and not any("undeclared" in x for x in fam):
        cls = "split across several readers that all declare the sort, order happens to hold"
    else:
        cls = "other: " + ",".join(fam)
    cnt[cls] += 1; ex.setdefault(cls, c)
print(json.dumps({"dropped_but_true_by_family": cnt, "single_declaring_owner_structures": sidecar_ok}, indent=1))
for k, v in ex.items(): print("EXAMPLE", k, json.dumps(v))

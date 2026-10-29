"""Compare the patched ParallelLeafReader (shadow-src run of CompositionEnum) with the FIX_OWNER predicate and the
SMT verdicts: the patched class must advertise exactly the original sort when FIX_OWNER holds and none otherwise,
must never advertise a sort the SMT leg showed can be violated, and must accept/reject exactly what main does."""
import json, sys
from collections import Counter
def parse_owner(o):
    return (-1, None) if o == "-1" else (int(o[:-1]), o[-1])
verd = {}
for l in open(sys.argv[3]):
    v = json.loads(l); verd[v["key"]] = v["res"]
st = Counter()
for lm, lf in zip(open(sys.argv[1]), open(sys.argv[2])):
    m, f = json.loads(lm), json.loads(lf)
    assert m["readers"] == f["readers"]
    for view in ("leaf", "composite"):
        a, b = m[view], f[view]
        if a["accepted"] != b["accepted"]:
            st[view + ":acceptance_changed"] += 1; continue
        if not a["accepted"]:
            st[view + ":rejected_both"] += 1; continue
        if a["sort"] == "none":
            st[view + ":no_sort_both" if b["sort"] == "none" else view + ":fix_added_sort"] += 1; continue
        owners = {"s": parse_owner(a["own_s"]), "t": parse_owner(a["own_t"])}
        readers = m["readers"]; adv = a["sort"]
        keep = any(rd["sort"] == adv and all(owners[x][0] in (-1, r) for x in adv) for r, rd in enumerate(readers))
        expect = adv if keep else "none"
        st[view + (":matches_FIX_OWNER" if b["sort"] == expect else ":DIFFERS_from_FIX_OWNER")] += 1
        key = json.dumps([readers, adv, {x: owners[x] for x in adv}], sort_keys=True)
        if b["sort"] != "none" and verd[key] == "sat":
            st[view + ":FIX_ADVERTISES_VIOLABLE_SORT"] += 1
        if b["sort"] == "none" and verd[key] == "sat":
            st[view + ":fix_drops_violable_sort"] += 1
        if b["sort"] == "none" and verd[key] == "unsat":
            st[view + ":fix_drops_true_sort(conservative)"] += 1
print(json.dumps(st, indent=1, sort_keys=True))

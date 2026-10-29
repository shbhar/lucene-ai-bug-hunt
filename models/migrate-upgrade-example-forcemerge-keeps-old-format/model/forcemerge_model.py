#!/usr/bin/env python3
"""Decision model: does `new IndexWriter(dir, config).forceMerge(1)` leave every segment at Version.LATEST?

Derived from code reading of main 645d4fe14a20. The model only uses pre-state features (segment count,
deletions, compound-file flag versus the policy's compound-file decision, versions). It is checked against
the observations of ForceMergeUpgradeMatrix.java on the stock jar (run-645d4fe-da/run.log), and compared
with the MIGRATE.md 11.0 claim "Rewrites all segments to latest format" (prediction: always True).

file:line mapping (lucene/core/src/java/org/apache/lucene/index/ unless noted)
| model element                         | Java                                                                  |
|---------------------------------------|-----------------------------------------------------------------------|
| every segment is an original          | IndexWriter.java:2252-2256 forceMerge: segmentsToMerge.put(info, TRUE)|
| TMP: eligible = originals, no size cut| TieredMergePolicy.java:770-827 (maxSegmentCount==1 -> maxMergeBytes   |
|   at maxSegmentCount 1                |   = Long.MAX_VALUE, line 790-791)                                     |
| TMP: foundDeletes                     | TieredMergePolicy.java:812-817                                        |
| TMP: lone original, no deletes ->     | TieredMergePolicy.java:835-847 (segmentsToMerge.get(infoZero) != null |
|   "already merged" (no merge at all)  |   short-circuits; isMerged() is never consulted for an original)      |
| TMP: otherwise one merge of all       | TieredMergePolicy.java:865-872                                        |
| LMP: lone original -> isMerged(seg)   | LogMergePolicy.java:181-203, 348-354, 375-381                         |
| isMerged(seg) = no deletes AND cfs    | MergePolicy.java:750-758; codecs/CompoundFormat.java:163-180          |
|   decision == segment's cfs flag      |                                                                       |
| UpgradeIndexMergePolicy rewrites every| UpgradeIndexMergePolicy.java:68-70 (shouldUpgradeSegment), 80-137    |
|   segment whose version != LATEST     |   findForcedMerges (delegate merges + leftover OneMerge, line 136)    |
| IndexUpgrader = UpgradeIndexMergePolicy| IndexUpgrader.java:166-196 upgrade(): policy at 180, forceMerge(1) 196|

Simplification (stated, checked by the observations): for LogMergePolicy the compound-file decision of a
small lone segment under default settings is "use CFS" (CompoundFormat defaults: shouldUseCompoundFile=true,
the segment is far below every threshold), so cfs_mismatch == (segment is not CFS).
"""
import json
import sys


def predict(policy, segs):
    """True iff forceMerge(1) leaves every segment at the latest version."""
    latest = "11.0.0"
    old = [s for s in segs if s["ver"] != latest]
    if not old:
        return True
    n = len(segs)
    dels = any(s["del"] > 0 for s in segs)
    if policy in ("Upgrade(TMP)", "IndexUpgrader"):
        return True
    if policy in ("default(TMP)", "TieredMergePolicy"):
        # n >= 2 -> one merge of all; n == 1 -> merged only if it has deletions
        return n >= 2 or dels
    if policy in ("LogByteSizeMergePolicy", "LogDocMergePolicy"):
        cfs_mismatch = (n == 1) and (segs[0]["cfs"] is False)
        return n >= 2 or dels or cfs_mismatch
    raise ValueError(policy)


def main(path):
    rows = [json.loads(l) for l in open(path) if l.startswith('{"state"')]
    model_ok = claim_ok = 0
    model_bad, claim_bad = [], []
    for r in rows:
        obs = r["all_latest_after"]
        p = predict(r["policy"], r["before"])
        if p == obs:
            model_ok += 1
        else:
            model_bad.append((r["state"], r["policy"], p, obs))
        if obs is True:
            claim_ok += 1
        else:
            claim_bad.append((r["state"], r["policy"]))
    print(f"observations: {len(rows)}")
    print(f"model agrees: {model_ok}/{len(rows)}; disagreements: {model_bad}")
    print(f"MIGRATE claim ('always rewritten') holds: {claim_ok}/{len(rows)}; fails on {len(claim_bad)}:")
    for s, p in claim_bad:
        print(f"  {s:24s} {p}")
    return 0 if not model_bad else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))

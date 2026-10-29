"""SMT model (z3py, exact Java int/long wrap via 32/64-bit bit-vectors) of the 'offset + limit' and 'limit + 1'
size computations in lucene/grouping on main a9779ef3c193.

Model element -> Java line (paths relative to lucene/grouping/src/java/org/apache/lucene/search/grouping/):
  S1  FirstPassGroupingCollectorManager.java:92/95 validation (groupOffset >= 0, topNGroups >= 1),
      :110 newCollector passes groupOffset + topNGroups (int)
  S1b FirstPassGroupingCollector.java:97 validation (topNGroups >= 1), :116 getComparator(topNGroups + 1) (int),
      :121 HashMap.newHashMap(topNGroups)
  S2  TopGroupsCollectorManager.java:107 newCollector passes withinGroupOffset + maxDocsPerGroup (int);
      no validation in the manager; the collector's TopScoreDocCollectorManager/TopFieldCollectorManager
      (core TopScoreDocCollectorManager.java:62, TopFieldCollectorManager.java:62) require numHits > 0
  S3  GroupingSearch.java:235 groupByDocBlock passes groupDocsOffset + groupDocsLimit (int) as maxDocsPerGroup;
      BlockGroupingCollectorManager.java:125 requires maxDocsPerGroup >= 1
  S4  BlockGroupingCollectorManager.java:112/116 validation, :143 newCollector passes groupOffset + topNGroups (int);
      BlockGroupingCollector.java:213 requires topNGroups >= 1
  S5  TopGroups.java:241/247 merge passes docOffset + docTopN (int) to TopDocs.merge(topN, ...);
      core TopDocs.java:313 allocates new ScoreDoc[Math.min(size, availHitCount - start)] (negative size -> NASE)
  S6  SearchGroup.java:281 maxQueueSize = offset + topN (int); :294 new ArrayList<>(topN) (pre-sized by topN)
  S7  TopGroupsCollector.java:190 Math.min(scoreDocs.length, withinGroupOffset + maxDocsPerGroup) (int)

Documented limits used as preconditions: IndexWriter.MAX_DOCS = 2^31 - 129 (maxDoc of any reader);
ArrayUtil.MAX_ARRAY_LENGTH = 2^31 - 1 - 16 (= 2147483631 on a 64-bit JVM: 'maxSize must be >= 0 and < 2147483631').

Queries:
  Q1..Q7  for each site: exists (offset, limit) accepted by the API's own validation such that the int sum wraps
          (SAT expected; the solver returns the minimal witness under a minimisation of offset+limit).
  Q8      exact wrap region: for offset >= 0, limit >= 1 the int sum wraps  <=>  offset + limit >= 2^31 (math ints).
          Proven by UNSAT of the negation over 32-bit vectors.
  P1      proposed fix (cap offset and limit at maxDoc before they reach the collectors):
             offset' = min(offset, maxDoc);  limit' = max(1, min(limit, maxDoc - offset'))
          for every maxDoc in [1, MAX_DOCS] and every number of groups G in [0, maxDoc]:
            (a) offset' + limit' and offset' + limit' + 1 never wrap as Java ints (offset' + limit' <= maxDoc + 1),
            (b) the slice returned equals the slice with the caller's values in exact arithmetic:
                |[offset, offset+limit) n [0, G)| == |[offset', offset'+limit') n [0, G)|,
            (c) 1 <= limit' <= maxDoc (valid for the managers' >= 1 checks; allocation bounded by the index,
                as IndexSearcher.search caps numHits at maxDoc).
          Proven by UNSAT of the negation (64-bit bit-vectors, no overflow possible in the encoding).
  V1      vacuity: the same P1 query with the cap replaced by the unfixed int sum must be SAT (counterexample).
  V2, V3  mutants of the fix (cap the limit at maxDoc but ignore the offset; do not cap the offset) must be SAT.
"""
import json
import sys
import time

from z3 import (BitVec, BitVecVal, If, Optimize, Solver, And, Or, Not, ULT, SignExt, Extract, sat, unsat,
                ZeroExt)

INT_MIN = -(2 ** 31)
INT_MAX = 2 ** 31 - 1
MAX_DOCS = 2 ** 31 - 129
MAX_ARRAY_LENGTH = 2147483631  # exclusive bound in PriorityQueue / LongHeap messages on main

results = {}


def i32(name):
    return BitVec(name, 32)


def to64(x):
    return SignExt(32, x)


def wraps_add(a, b):
    """Java int a + b wraps iff the exact 64-bit sum differs from the sign-extended 32-bit sum."""
    return to64(a + b) != to64(a) + to64(b)


def site_query(name, pre, sum_expr_parts, extra=None):
    o, l = sum_expr_parts
    opt = Optimize()
    opt.add(pre)
    opt.add(wraps_add(o, l))
    if extra is not None:
        opt.add(extra)
    # minimal witness: minimise the exact sum, then the offset
    opt.minimize(to64(o) + to64(l))
    opt.minimize(to64(o))
    t = time.time()
    r = opt.check()
    entry = {"query": name, "result": str(r), "secs": round(time.time() - t, 3)}
    if r == sat:
        m = opt.model()
        ov = m.eval(o).as_signed_long()
        lv = m.eval(l).as_signed_long()
        s = ((ov + lv + 2 ** 31) % 2 ** 32) - 2 ** 31
        entry.update({"offset": ov, "limit": lv, "java_int_sum": s})
    results[name] = entry
    print(json.dumps(entry))


# S1: FirstPassGroupingCollectorManager(groupOffset >= 0, topNGroups >= 1) -> newCollector(groupOffset + topNGroups)
go, tn = i32("groupOffset"), i32("topNGroups")
site_query("Q1_FirstPassManager_offset_plus_topN", And(go >= 0, tn >= 1), (go, tn))

# S1b: FirstPassGroupingCollector(topNGroups >= 1) -> getComparator(topNGroups + 1)
one = BitVecVal(1, 32)
site_query("Q1b_FirstPassCollector_topN_plus_1", tn >= 1, (tn, one))

# S2: TopGroupsCollectorManager (no validation; GroupingSearch passes groupDocsOffset/groupDocsLimit unchanged)
#     restrict to the values the core collectors would accept separately: offset >= 0, limit >= 1
wo, md = i32("withinGroupOffset"), i32("maxDocsPerGroup")
site_query("Q2_TopGroupsManager_offset_plus_max", And(wo >= 0, md >= 1), (wo, md))

# S3: GroupingSearch.groupByDocBlock: groupDocsOffset + groupDocsLimit (GroupingSearch validates neither)
do_, dl = i32("groupDocsOffset"), i32("groupDocsLimit")
site_query("Q3_GroupingSearch_block_docs_offset_plus_limit", And(do_ >= 0, dl >= 1), (do_, dl))

# S4: BlockGroupingCollectorManager validation then groupOffset + topNGroups
site_query("Q4_BlockManager_offset_plus_topN", And(go >= 0, tn >= 1), (go, tn))

# S5: TopGroups.merge(docOffset, docTopN): javadoc 'which document to start from' / 'how many top documents to keep'
dof, dtn = i32("docOffset"), i32("docTopN")
site_query("Q5_TopGroups_merge_docOffset_plus_docTopN", And(dof >= 0, dtn >= 1), (dof, dtn))

# S6: SearchGroup.merge(offset, topN)
so, st = i32("offset"), i32("topN")
site_query("Q6_SearchGroup_merge_offset_plus_topN", And(so >= 0, st >= 1), (so, st))
# S6 second failure mode, without any overflow: ArrayList(topN) pre-sized by the caller's topN.
s = Solver()
s.add(st >= 1, so == 0, Not(wraps_add(so, st)), st > BitVecVal(MAX_ARRAY_LENGTH - 1, 32))
r = s.check()
e = {"query": "Q6b_SearchGroup_merge_presize_exceeds_vm_array_limit_offset0", "result": str(r)}
if r == sat:
    e["topN"] = s.model().eval(st).as_signed_long()
results[e["query"]] = e
print(json.dumps(e))

# S7: TopGroupsCollector.getTopGroups(withinGroupOffset) Math.min(len, withinGroupOffset + maxDocsPerGroup)
site_query("Q7_TopGroupsCollector_getTopGroups_offset_plus_max", And(wo >= 0, md >= 1), (wo, md))

# Q8: exact wrap region for non-negative offset and positive limit
a, b = i32("a"), i32("b")
s = Solver()
exact = to64(a) + to64(b)
s.add(a >= 0, b >= 1)
s.add(wraps_add(a, b) != (exact >= BitVecVal(2 ** 31, 64)))
r = s.check()
results["Q8_wrap_region_iff_sum_ge_2^31"] = {"result": str(r), "expect": "unsat"}
print(json.dumps({"query": "Q8_wrap_region_iff_sum_ge_2^31", "result": str(r), "expect": "unsat"}))


# P1: proposed fix. All arithmetic in 64-bit bit-vectors (values are < 2^33, so no 64-bit wrap is possible).
def min64(x, y):
    return If(x < y, x, y)


def max64(x, y):
    return If(x > y, x, y)


def slice_len(off, lim, G):
    """|[off, off+lim) n [0, G)| in exact arithmetic (all 64-bit, operands < 2^33)."""
    return max64(BitVecVal(0, 64), min64(G, off + lim) - off)


def fix_query(name, use_fix, mutant=None):
    o32, l32, m32, g32 = i32("o"), i32("l"), i32("maxDoc"), i32("G")
    o, l, m, G = to64(o32), to64(l32), to64(m32), to64(g32)
    s = Solver()
    s.add(o32 >= 0, l32 >= 1, m32 >= 1, m32 <= MAX_DOCS, g32 >= 0, g32 <= m32)
    one64 = BitVecVal(1, 64)
    if use_fix:
        # offset' = Math.min(offset, maxDoc); limit' = Math.max(1, Math.min(limit, maxDoc - offset'))
        off2 = min64(o, m) if mutant != "M2_no_offset_cap" else o
        lim2 = max64(one64, min64(l, m - off2) if mutant != "M1_cap_limit_at_maxDoc_only" else min64(l, m))
        java_end = to64(Extract(31, 0, off2) + Extract(31, 0, lim2))  # offset' + limit' as Java int
        java_end_p1 = to64(Extract(31, 0, off2) + Extract(31, 0, lim2) + 1)  # FirstPassGroupingCollector's + 1
        no_wrap = And(java_end == off2 + lim2, java_end_p1 == off2 + lim2 + one64, java_end <= m + one64)
        bounded = And(lim2 >= one64, lim2 <= m)
    else:
        off2 = o
        lim2 = l
        java_end = to64(o32 + l32)  # the unfixed int sum as Java computes it
        no_wrap = java_end == o + l
        bounded = lim2 <= m
    same = slice_len(o, l, G) == slice_len(off2, lim2, G)
    s.add(Not(And(no_wrap, same, bounded)))
    t = time.time()
    r = s.check()
    entry = {"query": name, "result": str(r), "secs": round(time.time() - t, 3),
             "expect": "unsat" if (use_fix and mutant is None) else "sat"}
    if r == sat:
        mdl = s.model()
        entry["cex"] = {k: mdl.eval(v).as_signed_long() for k, v in (("o", o32), ("l", l32), ("maxDoc", m32),
                                                                      ("G", g32))}
    results[name] = entry
    print(json.dumps(entry))


fix_query("P1_fix_cap_window_at_maxDoc_preserves_slice_and_never_wraps", True)
fix_query("V1_vacuity_unfixed_int_sum_has_counterexample", False)
fix_query("V2_mutant_cap_limit_at_maxDoc_only_has_counterexample", True, "M1_cap_limit_at_maxDoc_only")
fix_query("V3_mutant_no_offset_cap_has_counterexample", True, "M2_no_offset_cap")

ok = (all(results[k]["result"] == "sat" for k in results if k.startswith("Q") and k != "Q8_wrap_region_iff_sum_ge_2^31")
      and results["Q8_wrap_region_iff_sum_ge_2^31"]["result"] == "unsat"
      and results["P1_fix_cap_window_at_maxDoc_preserves_slice_and_never_wraps"]["result"] == "unsat"
      and results["V1_vacuity_unfixed_int_sum_has_counterexample"]["result"] == "sat"
      and results["V2_mutant_cap_limit_at_maxDoc_only_has_counterexample"]["result"] == "sat"
      and results["V3_mutant_no_offset_cap_has_counterexample"]["result"] == "sat")
out = sys.argv[1] if len(sys.argv) > 1 else None
if out:
    with open(out, "w") as f:
        json.dump({"all_as_expected": ok, "results": results}, f, indent=1)
print("ALL_AS_EXPECTED", ok)

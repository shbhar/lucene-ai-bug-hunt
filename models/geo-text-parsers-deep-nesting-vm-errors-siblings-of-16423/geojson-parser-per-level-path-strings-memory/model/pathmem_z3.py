"""path-string memory model of SimpleGeoJSONPolygonParser (main a9779ef3c193), with Z3 queries.

Model element -> Java (lucene/core/src/java/org/apache/lucene/geo/SimpleGeoJSONPolygonParser.java):
  path_i        the String parameter `path` of the i-th live parseArray/parseObject frame
                (parseObject: newPath = path + "." + key, lines 139-152; parseArray: path + ".[]", lines 285-288)
  live frames   every frame on the recursion chain keeps its own path_i reachable until it returns, so at the
                deepest point all d strings are live at once (no frame drops its parameter early)
  sizeof(L)     a Latin-1 String of L chars on a 64-bit JVM with compressed oops and compact strings:
                24-byte String object + byte[] (16-byte header + L, rounded up to 8) >= 40 + L bytes
  per array lvl one ArrayList (24 bytes) created by parseArray (line 277); its elementData is the shared empty
                array until the first add, which happens only after the child returns
  input family  F1, a valid Feature (javadoc: a Polygon 'optionally embedded under a "type: Feature"'):
                {"type":"Feature","geometry":{<valid polygon>},"properties":{"<K chars>": [^d ]^d }}
                path_1 = "properties." + key (11 + K chars), path_i = path_1 + ".[]" * (i-1)
Lower bound on the live heap of the parse at its deepest point (bytes):
  LB(d, K) = sum_{i=1..d} (40 + 11 + K + 3(i-1)) + 24 d  =  d (75 + K) + 3 d (d - 1) / 2
Input length: n(d, K) = C0 + K + 2 d + 4, C0 = len('{"type":"Feature",...,"properties":{"').

Queries (all integer arithmetic, exact):
  Q1 main:          min n  s.t. d <= 1000 (no StackOverflowError at depth 1000 on any stack in the sweep) and
                    LB(d, K) > 1 GiB                     -> SAT, witness (d, K, n)
  Q2 depth guard:   a #16423-style nesting limit D = 1024 (ParseException beyond D levels): exists an input the
                    guarded parser accepts (d <= D) with n <= 2 MiB and LB(d, K) > 1 GiB  -> SAT: a depth guard
                    alone does not bound the memory
  Q3 path-free fix: the parser keeps a constant-size context instead of the path (<= 32 bytes per frame, generous)
                    plus the ArrayList: UB = n (input) + 2 K (key builder + key) + 56 d; exists n <= 2 MiB with
                    UB > 1 GiB  -> UNSAT (UB <= 31 n)
  Q4 vacuity:       Q3 with the path term put back must be SAT again (the solver can find the main-branch witness)
"""
import json, sys
from z3 import Int, Optimize, Solver, If, sat, unsat

RING = "[[[100.0,0.0],[101.0,0.0],[101.0,1.0],[100.0,1.0],[100.0,0.0]]]"
PREFIX = '{"type":"Feature","geometry":{"type":"Polygon","coordinates":' + RING + '},"properties":{"'
C0 = len(PREFIX)
GiB = 1 << 30
MiB = 1 << 20

def LB(d, K):
    return d * (75 + K) + 3 * d * (d - 1) / 2

def n_of(d, K):
    return C0 + K + 2 * d + 4

out = {"C0": C0}
# Q1
d, K = Int("d"), Int("K")
o = Optimize()
o.add(d >= 1, d <= 1000, K >= 1, K <= 64 * MiB)
o.add(d * (75 + K) * 2 + 3 * d * (d - 1) > 2 * GiB)  # 2*LB > 2*GiB, avoids division
h = o.minimize(n_of(d, K))
r = o.check()
m = o.model()
w = {"d": m[d].as_long(), "K": m[K].as_long()}
w["n"] = n_of(w["d"], w["K"]); w["LB_bytes"] = LB(w["d"], w["K"])
out["Q1_main_min_input_for_1GiB_at_depth_le_1000"] = {"result": str(r), "witness": w}
# Q2
D = 1024
s = Solver()
d2, K2 = Int("d2"), Int("K2")
live = If(d2 < D, d2, D)
s.add(d2 >= 1, d2 <= D, K2 >= 1, n_of(d2, K2) <= 2 * MiB)  # an input the guarded parser ACCEPTS
s.add(live * (75 + K2) * 2 + 3 * live * (live - 1) > 2 * GiB)
r2 = s.check()
w2 = None
if r2 == sat:
    mm = s.model(); w2 = {"d": mm[d2].as_long(), "K": mm[K2].as_long()}; w2["n"] = n_of(w2["d"], w2["K"])
    lv = min(w2["d"], D); w2["LB_bytes"] = LB(lv, w2["K"])
out["Q2_depth_guard_1024_input_le_2MiB_LB_gt_1GiB"] = {"result": str(r2), "witness": w2}
# Q3
s3 = Solver()
d3, K3 = Int("d3"), Int("K3")
n3 = n_of(d3, K3)
s3.add(d3 >= 1, K3 >= 1, n3 <= 2 * MiB)
s3.add(n3 + 2 * K3 + 56 * d3 > GiB)
r3 = s3.check()
out["Q3_path_free_fix_input_le_2MiB_UB_gt_1GiB"] = {"result": str(r3)}
# Q4 vacuity: same constraints as Q3 but with the path term restored must be SAT
s4 = Solver()
d4, K4 = Int("d4"), Int("K4")
n4 = n_of(d4, K4)
s4.add(d4 >= 1, d4 <= 1000, K4 >= 1, n4 <= 2 * MiB)
s4.add((n4 + 2 * K4 + 56 * d4) * 2 + d4 * (75 + K4) * 2 + 3 * d4 * (d4 - 1) > 2 * GiB)
r4 = s4.check()
out["Q4_vacuity_path_term_restored"] = {"result": str(r4)}
# closed-form predictions for the measured points (heap-threshold-main-a9779ef.jsonl)
out["predictions_MiB"] = {f"d={dd},K={kk}": round((LB(dd, kk) + C0 + kk + 2 * dd + 4 + 2 * kk) / MiB, 1)
                         for dd, kk in [(250, MiB), (500, MiB // 2), (1000, MiB // 4), (100, MiB), (1000, 0)]}
ok = r == sat and r2 == sat and r3 == unsat and r4 == sat
out["all_expected"] = ok
print(json.dumps(out, indent=1))
sys.exit(0 if ok else 1)

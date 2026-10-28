"""Dataflow model of maxConn (M) through HNSW merge-time graph reuse, checked with Z3.

Question: for which (M_old, M_new) can a reusing merge (a) throw 'No growth is allowed' or (b) write
a segment whose recorded M is too small for its stored neighbour lists (reader overflow)?
Each code version is modelled by WHERE each of three components takes its M from:

  M_cap  : capacity of every NeighborArray of the merged OnHeapHnswGraph
           (level 0: 2*M_cap + 1, upper levels: M_cap + 1)
  M_b    : the builder's M, which bounds forward neighbour selection of inserted nodes
           (level 0: 2*M_b, upper: M_b)
  M_meta : the M written to .vem; the reader allocates int[2*M_meta] per seek

and whether reused (copied) lists are pruned on copy.

File:line table (branch_10_6 head 4ae946e5a9b = branch_10x head 657014b3c57 for these files; 10.5.2
has the same lines at the offsets shown in brackets; main = a9779ef3c193):

  model element                    | 10.x line                                         | main line
  ---------------------------------+---------------------------------------------------+---------------------------------
  M_cap = initializerGraph.maxConn | InitializedHnswGraphBuilder.java:135,261 [10.5.2:128] | InitializedHnswGraphBuilder same
  capacity 2*M+1 / M+1             | OnHeapHnswGraph.java:80-81 (nsize, nsize0)        | same
  copy = addOutOfOrder, no prune   | InitializedHnswGraphBuilder.java:366-378          | same
  M_b (MergingHnswGraphBuilder)    | MergingHnswGraphBuilder.java:74 super(..M..)      | HnswGraphBuilder.java:180 M = hnsw.maxConn()
                                   |   M from IncrementalHnswGraphMerger.java:186 (configured) |
  M_b (concurrent)                 | ConcurrentHnswMerger.java:115 (configured M)      | ConcurrentHnswMerger: no M passed
  forward limit 2*M_b / M_b        | HnswGraphBuilder.java:443, loop :527              | same
  forward add throws at capacity   | NeighborArray.java:68-71 addInOrder               | same
  reverse link prunes at capacity  | NeighborArray.java:119-130 addAndEnsureDiversity  | same
  writeGraph scratch 2*graph.maxConn | Lucene99HnswVectorsWriter.java:567              | same
  M_meta = configured M            | Lucene99HnswVectorsWriter.java:625 [10.5.2:584]   | :627 graph.maxConn()
  reader buffer int[2*M_meta]      | Lucene99HnswVectorsReader.java:572, read :594-598 | same

Abstraction: D0 = number of level-0 candidates the diversity heuristic accepts for one inserted node
(data dependent, 0 <= D0 <= C0 = number of level-0 candidates); S = largest degree of a copied
level-0 list in the reused graph (0 <= S <= 2*M_old). The model asks whether a bad outcome is
POSSIBLE for some data; the executed sweep (harness/) checks which cases real data reaches.
Upper levels are analogous (M instead of 2*M) and are omitted for brevity.
Documented limits: 1 <= M <= 512 (Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN),
1 <= beamWidth <= 3200 (MAXIMUM_BEAM_WIDTH). Level-0 candidate count C0 <= beamWidth.
"""

import sys
from z3 import And, Int, If, Not, Or, Solver, sat, unsat


def model(cfg):
    M_old, M_new, beam, D0, S = Int("M_old"), Int("M_new"), Int("beam"), Int("D0"), Int("S")
    s = Solver()
    s.add(1 <= M_old, M_old <= 512, 1 <= M_new, M_new <= 512, 1 <= beam, beam <= 3200)
    s.add(0 <= S, S <= 2 * M_old)  # copied list from a graph written with M_old
    if cfg["skip_reuse_on_mismatch"]:
        s.add(M_old == M_new)  # reuse happens only when the Ms agree
    M_cap = M_old if cfg["cap"] == "old" else M_new
    M_b = M_old if cfg["builder"] == "old" else M_new
    M_meta = M_old if cfg["meta"] == "old" else M_new
    cap0 = 2 * M_cap + 1
    fwd0 = 2 * M_b
    s.add(0 <= D0, D0 <= beam)  # accepted candidates <= candidates <= beam width
    # (a1) forward selection of an inserted node: the loop adds while size < fwd0 (HnswGraphBuilder:527);
    #      addInOrder throws when size == cap0 (NeighborArray:70-71)
    fwd_throw = And(fwd0 >= cap0, D0 >= cap0)
    # (a2) copying a reused list of size S into capacity cap0 without pruning (Initialized..:377)
    copy_throw = S >= cap0 if not cfg["prune_on_copy"] else False
    S_copied = S if not cfg["prune_on_copy"] else If(S > 2 * M_cap, 2 * M_cap, S)
    # (b) stored degree after the merge: copied lists keep <= cap0-1 (reverse links prune at
    #     capacity, NeighborArray:122-129); inserted nodes keep min(D0, fwd0, cap0-1)
    new_deg = If(D0 < fwd0, D0, fwd0)
    new_deg = If(new_deg < cap0 - 1, new_deg, cap0 - 1)
    stored = If(S_copied > new_deg, S_copied, new_deg)
    merge_throws = Or(fwd_throw, copy_throw)
    # a segment is only written (and later read) when the merge did not throw
    read_overflow = And(Not(merge_throws), stored > 2 * M_meta)
    return s, (M_old, M_new, beam, D0, S), merge_throws, read_overflow


CONFIGS = {
    # current 10.x (10.4.0 .. 10.5.2, branch_10_6, branch_10x): #15003 backport 3e024479d35
    "10x_current": dict(cap="old", builder="new", meta="new", prune_on_copy=False, skip_reuse_on_mismatch=False),
    # main: #14097 (builder M from graph) + #14121 (meta M from graph)
    "main": dict(cap="old", builder="old", meta="old", prune_on_copy=False, skip_reuse_on_mismatch=False),
    # 9.10 .. 10.3 (before the #15003 backport): everything from the configured M, copy without prune
    "pre_10_4": dict(cap="new", builder="new", meta="new", prune_on_copy=False, skip_reuse_on_mismatch=False),
    # fix option A for 10.x: port #14121 (meta) and make the 10.x builders take M from the graph
    "fixA_port_14121_builderM": dict(cap="old", builder="old", meta="old", prune_on_copy=False, skip_reuse_on_mismatch=False),
    # fix option B (as phrased in the plan): capacity follows the configured M, no other change
    "fixB_cap_follows_builder": dict(cap="new", builder="new", meta="new", prune_on_copy=False, skip_reuse_on_mismatch=False),
    # fix option B': capacity follows the configured M AND copied lists are pruned to 2*M_new
    "fixB2_cap_new_prune_copy": dict(cap="new", builder="new", meta="new", prune_on_copy=True, skip_reuse_on_mismatch=False),
    # fix option C: skip a reuse candidate whose maxConn differs (hnsw-graph-format's fix.diff)
    "fixC_skip_reuse": dict(cap="old", builder="new", meta="new", prune_on_copy=False, skip_reuse_on_mismatch=True),
}


def check(name, cfg, out):
    res = {}
    for prop in ("merge_throws", "read_overflow"):
        s, (M_old, M_new, beam, D0, S), mt, ro = model(cfg)
        target = mt if prop == "merge_throws" else ro
        s.push()
        s.add(target)
        r = s.check()
        wit = None
        if r == sat:
            m = s.model()
            wit = {str(v): m.eval(v).as_long() for v in (M_old, M_new, beam, D0, S)}
        s.pop()
        # region checks: the property can only fail when raising / lowering
        reg = {}
        for rel, c in (("raise", M_new > M_old), ("same", M_new == M_old), ("lower", M_new < M_old)):
            s.push()
            s.add(target, c)
            reg[rel] = str(s.check())
            s.pop()
        res[prop] = (str(r), wit, reg)
        print(f"{name:28s} {prop:14s} {str(r):6s} regions={reg} witness={wit}", file=out)
    return res


if __name__ == "__main__":
    out = sys.stdout
    print("config                       property       result regions(raise/same/lower) witness", file=out)
    allres = {n: check(n, c, out) for n, c in CONFIGS.items()}
    # expectations (assertions on the model itself, so a modelling change that flips them is visible)
    e = allres
    assert e["main"]["merge_throws"][0] == "unsat" and e["main"]["read_overflow"][0] == "unsat"
    assert e["10x_current"]["merge_throws"][2] == {"raise": "sat", "same": "unsat", "lower": "unsat"}
    assert e["10x_current"]["read_overflow"][2] == {"raise": "unsat", "same": "unsat", "lower": "sat"}
    assert e["pre_10_4"]["merge_throws"][2] == {"raise": "unsat", "same": "unsat", "lower": "sat"}
    assert e["pre_10_4"]["read_overflow"][0] == "unsat"
    assert e["fixA_port_14121_builderM"]["merge_throws"][0] == "unsat"
    assert e["fixA_port_14121_builderM"]["read_overflow"][0] == "unsat"
    assert e["fixB_cap_follows_builder"]["merge_throws"][0] == "sat"  # reintroduces the pre-10.4 crash
    assert e["fixB_cap_follows_builder"]["read_overflow"][0] == "unsat"
    assert e["fixB2_cap_new_prune_copy"]["merge_throws"][0] == "unsat"
    assert e["fixB2_cap_new_prune_copy"]["read_overflow"][0] == "unsat"
    assert e["fixC_skip_reuse"]["merge_throws"][0] == "unsat" and e["fixC_skip_reuse"]["read_overflow"][0] == "unsat"
    print("all model expectations hold", file=out)

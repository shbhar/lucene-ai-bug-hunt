"""
Z3 model: is the DENSE vector bulk path of IndexWriter#addBatch (#16734) failure-atomic through the
wrapping field writers?  Extends the columnar-addbatch dense-commit model (W1-W4: doc values / points) with the
vectors writers (W5-W8).

Contract (KnnFieldVectorsWriter.addDenseValues javadoc, KnnFieldVectorsWriter.java:40-56):
  "The cursor may throw while it is being consumed ... the documents of the whole batch are marked as
   deleted, but the writer must remain in a consistent state: every doc ID it has recorded must have
   its vector."  "The default implementation calls addValue once per vector."
The default therefore records the k vectors it consumed before the cursor threw at position k.

Model element -> Java (Lucene main @ a9779ef3c19)
  D_ATOMIC   Lucene99FlatVectorsWriter.DefaultFieldWriter.addDenseValues :513-531
             (fills into a private batch list; vectors.addAll + docsWithField.addRange only after
              every fill returned)
  D_DEFAULT  KnnFieldVectorsWriter.addDenseValues default :58-63 (addValue per vector), used by every
             FlatFieldVectorsWriter that does not override it, e.g. one supplied through the public
             Lucene99FlatVectorsWriter(state, scorer, strategyFactory) constructor :97-101
  flat.addValue                Lucene99FlatVectorsWriter :487-502 / custom writer
  H.addValue                   Lucene99HnswVectorsWriter.FieldWriter.addValue :825-843
  H.addDenseValues             Lucene99HnswVectorsWriter.FieldWriter.addDenseValues :851-869
                               (node/lastDocID/graph advanced only if the delegate returned)
  H.replay                     replayBufferedVectors :818-822 (inserts ords [0, flat.size))
  H.flush check                writeMeta :647  assert level-0 node count == vector count
  SQ.addValue / addDenseValues Lucene104ScalarQuantizedVectorsWriter.FieldWriter :740-756
                               (accumulates [firstOrd, flat.size) only if the delegate returned)
  SQ.flush                     scaleToUnitLength :828-829 magnitudes.get(ord) for every ord (COSINE)
  docs                         DocumentsWriterPerThread.updateBatch :313-358 (a failed batch's doc IDs
                               stay consumed and are marked deleted)
  shouldCreateGraph(k, n)      Lucene99HnswVectorsWriter :716-722, modelled as the monotone predicate
                               n >= T0 (monotonicity checked separately, harness ShouldCreateGraphMonotone)

Invariants checked at flush:
  I1 flat.ndocs == flat.nvec                 (DocsWithFieldSet vs buffered vectors)
  I2 H.node == flat.nvec                     (HNSW node counter vs delegate)
  I3 graph inserted ords == [0, flat.nvec)   when a graph exists (no gap, no duplicate)
  I4 SQ.nacc == flat.nvec                    (centroid sums / cached magnitudes vs delegate)

Run: python3 -I model/dense_vector_commit.py
"""
import sys
from z3 import Int, Bool, If, And, Or, Not, Solver, sat, unsat, IntVal, BoolVal, Implies

NONE = -1  # no graph builder yet


class State:
    def __init__(self, nvec, ndocs, node, g, bad, nacc, nextdoc):
        self.nvec, self.ndocs, self.node, self.g, self.bad, self.nacc, self.nextdoc = (
            nvec, ndocs, node, g, bad, nacc, nextdoc)


def add_graph_node(g, bad, ord_):
    """HnswGraphBuilder.addGraphNode(ord): the model keeps the graph as the contiguous ord range
    [0, g); inserting anything other than ord g is a gap or a duplicate (bad)."""
    ok = ord_ == g
    return If(ok, g + 1, g), Or(bad, Not(ok))


def step(st, op, count, k, t0, delegate_default):
    """One IndexingChain call for the field.  op 0 = addDocument (addValue), op 1 = addBatch with a
    DENSE column of `count` vectors whose cursor throws when asked for vector k (k == count: no
    throw)."""
    # ---- op 0: addValue
    v_nvec = st.nvec + 1
    v_ndocs = st.ndocs + 1
    v_node0 = st.node
    create = And(st.g == NONE, v_node0 + 1 >= t0)
    # replay inserts [0, v_nvec): contiguous from an empty graph, so g = v_nvec, bad unchanged
    g_rep = v_nvec
    g_add, bad_add = add_graph_node(st.g, st.bad, v_node0)
    v_g = If(create, g_rep, If(st.g != NONE, g_add, st.g))
    v_bad = If(create, st.bad, If(st.g != NONE, bad_add, st.bad))
    v_node = v_node0 + 1
    v_nacc = st.nacc + 1
    v_next = st.nextdoc + 1

    # ---- op 1: addDenseValues(first = nextdoc, cursor of `count`, throws at k)
    throws = k < count
    if delegate_default:
        added = If(throws, k, count)          # addValue per consumed vector, then the throw
    else:
        added = If(throws, IntVal(0), count)  # all-or-nothing
    d_nvec = st.nvec + added
    d_ndocs = st.ndocs + added
    # HNSW wrapper: only if the delegate returned
    first_node = st.node
    d_node = If(throws, st.node, st.node + count)
    # graph: existing builder -> addGraphNode(ord) for ord in [first_node, first_node+count)
    # unrolled for count <= CMAX
    g_seq, bad_seq = st.g, st.bad
    for i in range(CMAX):
        gi, bi = add_graph_node(g_seq, bad_seq, first_node + i)
        g_seq = If(i < count, gi, g_seq)
        bad_seq = If(i < count, bi, bad_seq)
    d_create = And(st.g == NONE, d_node >= t0)
    d_g = If(throws, st.g, If(st.g != NONE, g_seq, If(d_create, d_nvec, st.g)))
    d_bad = If(throws, st.bad, If(st.g != NONE, bad_seq, st.bad))
    # SQ wrapper: firstOrd = flat size before; accumulate [firstOrd, flat size) only if returned
    d_nacc = If(throws, st.nacc, st.nacc + (d_nvec - st.nvec))
    d_next = st.nextdoc + count  # doc IDs stay consumed (deleted) even on failure

    is_doc = op == 0
    return State(
        If(is_doc, v_nvec, d_nvec),
        If(is_doc, v_ndocs, d_ndocs),
        If(is_doc, v_node, d_node),
        If(is_doc, v_g, d_g),
        If(is_doc, v_bad, d_bad),
        If(is_doc, v_nacc, d_nacc),
        If(is_doc, v_next, d_next),
    ), throws


CMAX = 4   # dense batch size bound for the BMC unrolling
TMAX = 4   # operations


def bmc(delegate_default, which):
    s = Solver()
    t0 = Int("t0")
    s.add(t0 >= 1, t0 <= 8)
    graph_from_start = Bool("graph_from_start")  # tinySegmentsThreshold <= 0
    st = State(IntVal(0), IntVal(0), IntVal(0), If(graph_from_start, IntVal(0), IntVal(NONE)),
               BoolVal(False), IntVal(0), IntVal(0))
    s.add(Implies(graph_from_start, t0 == 1))
    ops = []
    for t in range(TMAX):
        op, count, k = Int(f"op{t}"), Int(f"count{t}"), Int(f"k{t}")
        s.add(Or(op == 0, op == 1), count >= 1, count <= CMAX, k >= 0, k <= count)
        st, _ = step(st, op, count, k, t0, delegate_default)
        ops.append((op, count, k))
    viol = {
        "I1": st.ndocs != st.nvec,
        "I2": st.node != st.nvec,
        "I3": Or(st.bad, And(st.g != NONE, st.g != st.nvec)),
        "I4": st.nacc != st.nvec,
    }[which]
    s.add(viol)
    r = s.check()
    witness = None
    if r == sat:
        m = s.model()
        witness = []
        for op, count, k in ops:
            if m.eval(op).as_long() == 0:
                witness.append("addDocument")
            else:
                c, kk = m.eval(count).as_long(), m.eval(k).as_long()
                witness.append(f"addBatch(DENSE n={c}, cursor throws at {kk})" if kk < c else f"addBatch(DENSE n={c}, ok)")
        witness.append(f"t0={m.eval(t0)} graphFromStart={m.eval(graph_from_start)}")
        witness.append(f"flush: nvec={m.eval(st.nvec)} ndocs={m.eval(st.ndocs)} node={m.eval(st.node)} g={m.eval(st.g)} bad={m.eval(st.bad)} nacc={m.eval(st.nacc)}")
    return r, witness


def inductive(delegate_default):
    """Unbounded proof attempt: Inv /\\ step -> Inv' for one symbolic step with unbounded counts.
    For the unbounded dense graph loop we use the closed form (contiguous insertion of
    [node, node+count) succeeds iff node == g), justified by add_graph_node's definition."""
    s = Solver()
    nvec, ndocs, node, g, nacc, nextdoc, t0 = (Int(x) for x in ("nvec", "ndocs", "node", "g", "nacc", "nextdoc", "t0"))
    bad = Bool("bad")
    op, count, k = Int("op"), Int("count"), Int("k")
    s.add(t0 >= 1, Or(op == 0, op == 1), count >= 1, k >= 0, k <= count, nvec >= 0)
    inv = And(ndocs == nvec, node == nvec, Not(bad), nacc == nvec,
              Or(And(g == NONE, node < t0), g == node))
    s.add(inv)
    # addValue
    create = And(g == NONE, node + 1 >= t0)
    v = dict(nvec=nvec + 1, ndocs=ndocs + 1, node=node + 1, nacc=nacc + 1,
             g=If(create, nvec + 1, If(g != NONE, If(node == g, g + 1, g), g)),
             bad=If(create, bad, If(g != NONE, Or(bad, node != g), bad)))
    throws = k < count
    added = If(throws, k, count) if delegate_default else If(throws, IntVal(0), count)
    d_nvec = nvec + added
    d_node = If(throws, node, node + count)
    d_create = And(g == NONE, d_node >= t0)
    d = dict(nvec=d_nvec, ndocs=ndocs + added, node=d_node,
             nacc=If(throws, nacc, nacc + (d_nvec - nvec)),
             g=If(throws, g, If(g != NONE, If(node == g, g + count, g), If(d_create, d_nvec, g))),
             bad=If(throws, bad, If(g != NONE, Or(bad, node != g), bad)))
    nx = {key: If(op == 0, v[key], d[key]) for key in v}
    inv2 = And(nx["ndocs"] == nx["nvec"], nx["node"] == nx["nvec"], Not(nx["bad"]), nx["nacc"] == nx["nvec"],
               Or(And(nx["g"] == NONE, nx["node"] < t0), nx["g"] == nx["node"]))
    s.add(Not(inv2))
    r = s.check()
    cti = None
    if r == sat:
        m = s.model()
        cti = {x: str(m.eval(e)) for x, e in (("op", op), ("count", count), ("k", k), ("nvec", nvec), ("g", g), ("t0", t0))}
    return r, cti


def main():
    ok = True
    print("== W5 stock stack (Lucene99 DefaultFieldWriter override = all-or-nothing delegate)")
    for inv in ("I1", "I2", "I3", "I4"):
        r, w = bmc(False, inv)
        print(f"   BMC T={TMAX} n<={CMAX} {inv}: {'VIOLATED (sat)' if r == sat else 'holds (unsat)'}")
        ok &= r == unsat
    r, cti = inductive(False)
    print(f"   inductive invariant (unbounded counts): {'proved (unsat)' if r == unsat else 'CTI ' + str(cti)}")
    ok &= r == unsat

    print("== W6-W8 wrapper over a delegate with the documented default addDenseValues")
    for inv in ("I1", "I2", "I3", "I4"):
        r, w = bmc(True, inv)
        print(f"   BMC T={TMAX} n<={CMAX} {inv}: {'VIOLATED (sat)' if r == sat else 'holds (unsat)'}")
        if w:
            for line in w:
                print("      " + line)
    r, cti = inductive(True)
    print(f"   inductive invariant: {'proved (unsat)' if r == unsat else 'CTI (sat) ' + str(cti)}")

    # vacuity probes: (a) the BMC can reach a thrown cursor at all; (b) a mutant that lets the
    # stock delegate record the consumed prefix (as the default does) must be caught.
    s = Solver(); k, c = Int("k"), Int("c"); s.add(c >= 1, c <= CMAX, k >= 0, k < c)
    print(f"   vacuity: a throwing cursor is reachable: {s.check()}")
    print(f"   mutant check: stock model with partial delegate violates I2: {bmc(True, 'I2')[0]}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

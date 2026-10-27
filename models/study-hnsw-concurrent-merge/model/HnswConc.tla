------------------------------ MODULE HnswConc ------------------------------
(***************************************************************************)
(* Design-level model of Lucene's concurrent HNSW graph construction      *)
(* (HnswConcurrentMergeBuilder + HnswGraphBuilder.addGraphNodeInternal)   *)
(* and of the concurrent link repair of a reused graph                    *)
(* (HnswConcurrentMergeBuilder.repairDisconnectedNodes ->                 *)
(*  HnswGraphBuilder.fixDisconnectedNodes / addConnections).              *)
(*                                                                         *)
(* Derived from apache/lucene main 7cecf79523 (2026-10-21).  The mapping  *)
(* from actions to file:line is in ../README.md (section "Mapping").      *)
(*                                                                         *)
(* Abstractions                                                            *)
(*  - vectors: an explicit similarity table Sim (integers scaled by 2, so  *)
(*    Math.nextUp(s) is s+1 and is exact); self-similarity is maximal.    *)
(*  - each beam-search iteration (pop + graphSeek-copy under read lock +  *)
(*    scoring the unvisited friends) is one atomic step, exactly the      *)
(*    granularity of MergeSearcher.graphSeek (copy under HnswLock.read).  *)
(*  - each write critical section (updateNeighbor under HnswLock.write,   *)
(*    and the repair self-lock) is either one atomic step (SPLIT=FALSE,   *)
(*    sound by Lipton reduction since no critical section touches any     *)
(*    other shared state) or acquire / body+release (SPLIT=TRUE) to check *)
(*    deadlock freedom with stripe collisions.                            *)
(*  - selectAndLinkDiverse on a NEW node is unlocked in the code; it is   *)
(*    modeled as SelBegin/SelEnd with a ghost marking the array "being    *)
(*    written without lock"; any other access in that window is flagged.  *)
(*  - batch claiming: batch size 1 (any worker may claim the next node).  *)
(*  - per-worker UpdateableRandomVectorScorer state "ord" is modeled      *)
(*    exactly: every setScoringOrdinal call site updates it.              *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS
  N,            \* nodes are 0..N-1
  W,            \* workers are 1..W
  M,            \* HnswGraphBuilder.M (maxConn on levels > 0; level 0 uses 2M)
  BEAM,         \* beamWidth
  NLOCKS,       \* HnswLock.NUM_LOCKS (512 in the code; small here to force stripe collisions)
  SPLIT,        \* model lock acquire and release as separate steps
  FIX_SCORER,   \* candidate fix: reset scorer ordinal to the inserted node before a retry
  FIX_DESCENT,  \* candidate fix: addConnections descends to the BEST candidate (not popNode=worst)
  SimChoices,   \* set of similarity tables [Nodes \X Nodes -> Int]
  LevelChoices, \* set of level assignments [Nodes -> Nat]
  InitChoices,  \* set of initial (pruned) graphs: [Nodes -> Seq of Seq(Nodes)] (copied neighbor lists), or {} graph
  RepairD,      \* [levels -> Seq(Nodes)] disconnected nodes per level (repair phase), <<>> if none
  Order,        \* sequence of nodes inserted by the concurrent workers (non-initialized nodes)
  PRINT_FINAL   \* print the final graph's edge sets (used to enumerate sequential outcomes)

Nodes   == 0..(N-1)
Workers == 1..W
NONE    == -1
NaN     == -77777          \* stands for Float.NaN (score unknown, set by copyAndPruneGraphStructure)
NegInf  == -100000         \* Float.NEGATIVE_INFINITY
MaxSize(l) == IF l = 0 THEN 2*M + 1 ELSE M + 1   \* OnHeapHnswGraph nsize0 / nsize
MaxConn(l) == IF l = 0 THEN 2*M ELSE M           \* addDiverseNeighbors maxConnOnLevel
Stripe(l, n) == (l * 31 + n) % NLOCKS            \* HnswLock.hash(level,node) % NUM_LOCKS
Range(s) == {s[i] : i \in DOMAIN s}
Min(a, b) == IF a < b THEN a ELSE b
Max(a, b) == IF a > b THEN a ELSE b

VARIABLES
  sim,      \* the similarity table (constant during a behavior)
  lvl,      \* node -> top level (constant during a behavior)
  arr,      \* arr[n][l] = NeighborArray record or NullNA (srt = -1); l in 0..glen[n]-1
  glen,     \* graph[n].length (0 means graph[n] == null)
  sizeCtr,  \* OnHeapHnswGraph.size
  entry,    \* OnHeapHnswGraph.entryNode (AtomicReference<EntryNode>)
  ord,      \* per-worker scorer ordinal (UpdateableRandomVectorScorer.setScoringOrdinal)
  pc,       \* per-worker program counter
  lv,       \* per-worker locals
  claim,    \* workProgress (next index into Order)
  phase,    \* "repair" | "insert" | "end"
  repLevel, \* level currently being repaired
  repClaim, \* repairProgress (next index into RepairD[repLevel])
  wr,       \* write-lock holder per stripe (0 = free)
  unl,      \* ghost: set of <<level, node, worker>> arrays being written without lock
  err,      \* ghost: set of code-level failures (exceptions / failed Java asserts)
  wq,       \* ghost: set of <<worker, node, query>> searches run with the wrong scorer ordinal
  desc      \* ghost: addConnections descent steps that did not move to the best candidate

vars == <<sim, lvl, arr, glen, sizeCtr, entry, ord, pc, lv, claim, phase, repLevel, repClaim,
          wr, unl, err, wq, desc>>

MaxL == 3
Levels == 0..MaxL

-----------------------------------------------------------------------------
(* NeighborArray (descending order, as created by OnHeapHnswGraph.addNode) *)
EmptyNA == [n |-> <<>>, s |-> <<>>, srt |-> 0]
NullNA  == [n |-> <<>>, s |-> <<>>, srt |-> -1]   \* graph[n][l] == null
Size(a) == Len(a.n)
AddOutOfOrder(a, nd, sc) == [n |-> Append(a.n, nd), s |-> Append(a.s, sc), srt |-> a.srt]
AddInOrder(a, nd, sc)    == [n |-> Append(a.n, nd), s |-> Append(a.s, sc), srt |-> a.srt + 1]
AddInOrderErr(a, sc, l) ==
  (IF Size(a) # a.srt THEN {"addInOrder after addOutOfOrder"} ELSE {})
  \cup (IF Size(a) >= MaxSize(l) THEN {"addInOrder: No growth is allowed"} ELSE {})
  \cup (IF Size(a) > 0 /\ a.s[Size(a)] < sc THEN {"addInOrder: incorrect order"} ELSE {})

RemoveIndex(a, i) ==   \* i is 1-based; NeighborArray.removeIndex / removeLast
  IF i = Size(a)
  THEN [n |-> SubSeq(a.n, 1, i - 1), s |-> SubSeq(a.s, 1, i - 1), srt |-> Min(a.srt, i - 1)]
  ELSE [n |-> SubSeq(a.n, 1, i - 1) \o SubSeq(a.n, i + 1, Size(a)),
        s |-> SubSeq(a.s, 1, i - 1) \o SubSeq(a.s, i + 1, Size(a)),
        srt |-> IF i <= a.srt THEN a.srt - 1 ELSE a.srt]

\* NeighborArray.sort: insert each unsorted element (in index order) at the right-most
\* insertion point of the sorted prefix (descSortFindRightMostInsertionPoint).  NaN scores are
\* computed with the scorer, whose ordinal is the owner at this point.
RECURSIVE SortRec(_, _, _, _, _)
SortRec(pn, ps, rn, rs, owner) ==
  IF rn = <<>> THEN <<pn, ps>>
  ELSE LET nd == Head(rn)
           sc == IF Head(rs) = NaN THEN sim[owner, nd] ELSE Head(rs)
           p  == Cardinality({i \in DOMAIN ps : ps[i] >= sc})
       IN SortRec(SubSeq(pn, 1, p) \o <<nd>> \o SubSeq(pn, p + 1, Len(pn)),
                  SubSeq(ps, 1, p) \o <<sc>> \o SubSeq(ps, p + 1, Len(ps)),
                  Tail(rn), Tail(rs), owner)
SortNA(a, owner) ==
  LET r == SortRec(SubSeq(a.n, 1, a.srt), SubSeq(a.s, 1, a.srt),
                   SubSeq(a.n, a.srt + 1, Size(a)), SubSeq(a.s, a.srt + 1, Size(a)), owner)
  IN [n |-> r[1], s |-> r[2], srt |-> Size(a)]
\* final (1-based, ascending) positions of the previously unsorted nodes
RECURSIVE AscSeq(_)
AscSeq(S) == IF S = {} THEN <<>> ELSE LET m == CHOOSE x \in S : \A y \in S : x <= y
                                      IN <<m>> \o AscSeq(S \ {m})
Unchecked(a, sorted) ==
  AscSeq({i \in DOMAIN sorted.n : sorted.n[i] \in Range(SubSeq(a.n, a.srt + 1, Size(a)))})

\* NeighborArray.isWorstNonDiverse; the scorer ordinal is a.n[i]
IsWorst(a, i, unc, cur) ==
  IF i = unc[cur] THEN \E j \in 1..(i - 1) : sim[a.n[i], a.n[j]] >= a.s[i]
  ELSE \E c \in 1..cur : sim[a.n[i], a.n[unc[c]]] >= a.s[i]
\* NeighborArray.findWorstNonDiverse -> <<index (1-based), final scorer ordinal>>
RECURSIVE FW(_, _, _, _, _)
FW(a, unc, i, cur, lo) ==
  IF i < 2 \/ cur < 1 THEN <<Size(a), lo>>
  ELSE IF IsWorst(a, i, unc, cur) THEN <<i, a.n[i]>>
  ELSE FW(a, unc, i - 1, IF i = unc[cur] THEN cur - 1 ELSE cur, a.n[i])

\* NeighborArray.addAndEnsureDiversity -> <<newArray, scorerOrdinal, errors>>
AddDiverse(a, nd, sc, owner, l, ord0) ==
  IF Size(a) >= MaxSize(l) THEN <<a, ord0, {"addOutOfOrder: No growth is allowed"}>>
  ELSE LET a1 == AddOutOfOrder(a, nd, sc) IN
       IF Size(a1) < MaxSize(l) THEN <<a1, ord0, {}>>
       ELSE LET a2  == SortNA(a1, owner)
                unc == Unchecked(a1, a2)
                r   == FW(a2, unc, Size(a2), Len(unc), owner)
            IN <<RemoveIndex(a2, r[1]), r[2], {}>>

\* HnswGraphBuilder.updateNeighbor
UpdateNeighbor(a, x, sc, nbr, l, rep, ord0) ==
  IF rep /\ x \in Range(a.n) THEN <<a, ord0, {}>>
  ELSE AddDiverse(a, x, sc, nbr, l, ord0)

\* HnswGraphBuilder.selectAndLinkDiverse: cand is the scratch array in ASCENDING score order
\* (popToScratch), iterated from the best.  Returns <<own array, mask (set of 1-based indices),
\* scorer ordinal, errors>>.  diversityCheck(cScore, neighbors, scorer@cNode) is
\* "no selected neighbor scores >= cScore against cNode".
RECURSIVE SL(_, _, _, _, _, _, _, _, _)
SL(x, a, cand, i, mask, lo, l, rep, e) ==
  IF i < 1 \/ Size(a) >= MaxConn(l) THEN <<a, mask, lo, e>>
  ELSE LET c  == cand[i][1]
           cs == cand[i][2]
       IN IF c = x THEN SL(x, a, cand, i - 1, mask, lo, l, rep, e)
          ELSE IF \A j \in 1..Size(a) : sim[c, a.n[j]] < cs
               THEN IF rep
                    THEN IF c \in Range(a.n)
                         THEN SL(x, a, cand, i - 1, mask \cup {i}, c, l, rep, e)
                         ELSE SL(x, AddOutOfOrder(a, c, cs), cand, i - 1, mask \cup {i}, c, l, rep,
                                 e \cup (IF Size(a) >= MaxSize(l) THEN {"addOutOfOrder: No growth"} ELSE {}))
                    ELSE SL(x, AddInOrder(a, c, cs), cand, i - 1, mask \cup {i}, c, l, rep,
                            e \cup AddInOrderErr(a, cs, l))
               ELSE SL(x, a, cand, i - 1, mask, c, l, rep, e)

-----------------------------------------------------------------------------
(* Search helpers (HnswGraphSearcher.searchLevel with a GraphBuilderKnnCollector) *)
Better(p, q) == p[2] > q[2] \/ (p[2] = q[2] /\ p[1] < q[1])   \* NeighborQueue tie-break: smaller id wins
Best(S)  == CHOOSE p \in S : \A q \in S : p = q \/ Better(p, q)
Worst(S) == CHOOSE p \in S : \A q \in S : p = q \/ Better(q, p)
MinComp(res, k) == IF Cardinality(res) >= k THEN Worst(res)[2] ELSE NegInf
NextUp(s) == IF s = NegInf THEN NegInf + 1 ELSE s + 1
\* results.collect -> insertWithOverflow
Collect(res, p, k) ==
  IF Cardinality(res) < k THEN <<res \cup {p}, TRUE>>
  ELSE IF Better(p, Worst(res)) THEN <<(res \ {Worst(res)}) \cup {p}, TRUE>>
  ELSE <<res, FALSE>>
RECURSIVE TopK(_, _, _)
TopK(S, res, k) == IF S = {} THEN res ELSE LET p == Best(S) IN TopK(S \ {p}, Collect(res, p, k)[1], k)
RECURSIVE SortAscRec(_)
SortAscRec(S) == IF S = {} THEN <<>> ELSE LET p == Worst(S) IN <<p>> \o SortAscRec(S \ {p})

\* process the friend list of one popped candidate (bulk-scored, then in array order)
RECURSIVE Proc(_, _, _, _)
Proc(fr, st, q, k) ==
  IF fr = <<>> THEN st
  ELSE LET f == Head(fr) IN
       IF f \in st.vis THEN Proc(Tail(fr), st, q, k)
       ELSE LET sc == sim[q, f]
                st1 == [st EXCEPT !.vis = @ \cup {f}]
            IN IF sc >= st.minAcc
               THEN LET cr == Collect(st.res, <<f, sc>>, k)
                        nm == NextUp(MinComp(cr[1], k))
                        st2 == [st1 EXCEPT !.cands = @ \cup {<<f, sc>>}, !.res = cr[1],
                                           !.minAcc = IF cr[2] THEN nm ELSE @,
                                           !.explore = IF cr[2] /\ nm > st.minAcc THEN TRUE ELSE @]
                    IN Proc(Tail(fr), st2, q, k)
               ELSE Proc(Tail(fr), st1, q, k)

-----------------------------------------------------------------------------
Exists(n, l)    == glen[n] > l                        \* OnHeapHnswGraph.nodeExistAtLevel
Alloc(n, l)     == glen[n] > l /\ arr[n][l].srt # -1  \* graph[n][l] != null
NodesAt(l)      == {n \in Nodes : Exists(n, l)}
OthersUnl(l, n, w) == \E u \in unl : u[1] = l /\ u[2] = n /\ u[3] # w

NoSearch == [level |-> 0, k |-> 0, cands |-> {}, res |-> {}, vis |-> {}, minAcc |-> 0,
             explore |-> FALSE, ret |-> "none"]
Idle == [x |-> NONE, nl |-> 0, cm |-> 0, lu |-> 0, eps |-> {}, li |-> 0,
         scratch |-> [l \in Levels |-> <<>>], mask |-> {}, ui |-> 0, rep |-> FALSE,
         s |-> NoSearch, retry |-> FALSE]

InitArr(g) == [n \in Nodes |-> [l \in Levels |->
                 IF l < Len(g[n]) THEN [n |-> g[n][l + 1], s |-> [i \in DOMAIN g[n][l + 1] |-> NaN], srt |-> 0]
                 ELSE NullNA]]
\* entry as set by copyAndPruneGraphStructure: first surviving node at the top surviving level
InitEntry(g) == IF \A n \in Nodes : Len(g[n]) = 0 THEN [node |-> NONE, level |-> 1]
                ELSE LET top == CHOOSE t \in Levels : (\E n \in Nodes : Len(g[n]) = t + 1)
                                                     /\ \A n \in Nodes : Len(g[n]) <= t + 1
                     IN [node |-> CHOOSE n \in Nodes : Len(g[n]) = top + 1 /\
                                    \A m \in Nodes : Len(g[m]) = top + 1 => n <= m,
                         level |-> top]

Init ==
  /\ sim \in SimChoices
  /\ \E g \in InitChoices :
       /\ arr = InitArr(g)
       /\ glen = [n \in Nodes |-> Len(g[n])]
       /\ sizeCtr = Cardinality({n \in Nodes : Len(g[n]) > 0})
       /\ entry = InitEntry(g)
       /\ lvl \in {f \in LevelChoices : \A n \in Nodes : Len(g[n]) > 0 => f[n] = Len(g[n]) - 1}
  /\ ord = [w \in Workers |-> NONE]
  /\ pc = [w \in Workers |-> "idle"]
  /\ lv = [w \in Workers |-> Idle]
  /\ claim = 1
  /\ phase = IF RepairD = <<>> THEN "insert" ELSE "repair"
  /\ repLevel = IF RepairD = <<>> THEN 0 ELSE Len(RepairD) - 1
  /\ repClaim = 1
  /\ wr = [i \in 0..(NLOCKS - 1) |-> 0]
  /\ unl = {}
  /\ err = {}
  /\ wq = {}
  /\ desc = {}

-----------------------------------------------------------------------------
(* Starting a search: scoreEntryPoints with the CURRENT scorer ordinal ord[w] *)
StartSearch(w, l, k, eps, ret) ==
  LET q == ord[w]
      cs == {<<e, sim[q, e]>> : e \in eps}
      res == TopK(cs, {}, k)
  IN /\ lv' = [lv EXCEPT ![w].s = [level |-> l, k |-> k, cands |-> cs, res |-> res, vis |-> eps,
                                   minAcc |-> NextUp(MinComp(res, k)), explore |-> TRUE, ret |-> ret]]
     /\ pc' = [pc EXCEPT ![w] = "sStep"]
     /\ wq' = IF q # lv[w].x THEN wq \cup {<<w, lv[w].x, q>>} ELSE wq

\* one iteration of the searchLevel while-loop (or loop exit)
SStep(w) ==
  /\ pc[w] = "sStep"
  /\ LET s == lv[w].s IN
     IF s.cands = {} \/ (Best(s.cands)[2] < s.minAcc
                         /\ ~(s.explore /\ NextUp(Best(s.cands)[2]) = s.minAcc))
     THEN /\ pc' = [pc EXCEPT ![w] = s.ret]
          /\ UNCHANGED <<lv, err>>
     ELSE LET top == Best(s.cands)
              t == top[1]
          IN /\ wr[Stripe(s.level, t)] = 0 \/ wr[Stripe(s.level, t)] = w   \* HnswLock.read (blocks on a writer)
             /\ err' = err \cup (IF ~Alloc(t, s.level) THEN {"graphSeek on unallocated array"} ELSE {})
                           \cup (IF OthersUnl(s.level, t, w) THEN {"read of array being written without lock"} ELSE {})
             /\ LET fr == IF Alloc(t, s.level) THEN arr[t][s.level].n ELSE <<>>
                    s1 == [s EXCEPT !.cands = @ \ {top},
                                    !.explore = IF top[2] < s.minAcc THEN FALSE ELSE @]
                IN lv' = [lv EXCEPT ![w].s = Proc(fr, s1, ord[w], s.k)]
             /\ UNCHANGED pc
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, wq, desc>>

-----------------------------------------------------------------------------
(* Insertion: ConcurrentMergeWorker.run -> HnswGraphBuilder.addGraphNode -> addGraphNodeInternal *)
Claim(w) ==
  /\ phase = "insert" /\ pc[w] = "idle" /\ claim <= Len(Order)
  /\ LET x == Order[claim] IN
     /\ ord' = [ord EXCEPT ![w] = x]                              \* addGraphNode: scorer.setScoringOrdinal(node)
     /\ lv' = [lv EXCEPT ![w] = [Idle EXCEPT !.x = x, !.nl = lvl[x], !.li = lvl[x]]]
  /\ claim' = claim + 1
  /\ pc' = [pc EXCEPT ![w] = "addNode"]
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, phase, repLevel, repClaim, wr, unl, err, wq, desc>>

AddNode(w) ==   \* hnsw.addNode(level, node), from the top level down
  /\ pc[w] = "addNode"
  /\ LET x == lv[w].x  l == lv[w].li IN
     /\ glen' = [glen EXCEPT ![x] = IF @ = 0 THEN lv[w].nl + 1 ELSE @]
     /\ sizeCtr' = IF glen[x] = 0 THEN sizeCtr + 1 ELSE sizeCtr
     /\ arr' = [arr EXCEPT ![x][l] = EmptyNA]
     /\ IF l = 0 THEN /\ pc' = [pc EXCEPT ![w] = "trySet"] /\ UNCHANGED lv
                 ELSE /\ lv' = [lv EXCEPT ![w].li = l - 1] /\ UNCHANGED pc
  /\ UNCHANGED <<sim, lvl, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err, wq, desc>>

TrySet(w) ==    \* hnsw.trySetNewEntryNode(node, nodeLevel)
  /\ pc[w] = "trySet"
  /\ IF entry.node = NONE
     THEN /\ entry' = [node |-> lv[w].x, level |-> lv[w].nl]
          /\ pc' = [pc EXCEPT ![w] = "idle"] /\ lv' = [lv EXCEPT ![w] = Idle]
     ELSE /\ pc' = [pc EXCEPT ![w] = "readMax"] /\ UNCHANGED <<entry, lv>>
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, ord, claim, phase, repLevel, repClaim, wr, unl, err, wq, desc>>

ReadMax(w) ==   \* curMaxLevel = hnsw.numLevels() - 1
  /\ pc[w] = "readMax"
  /\ lv' = [lv EXCEPT ![w].cm = entry.level]
  /\ ord' = IF FIX_SCORER /\ lv[w].retry THEN [ord EXCEPT ![w] = lv[w].x] ELSE ord
  /\ pc' = [pc EXCEPT ![w] = "readEntry"]
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, claim, phase, repLevel, repClaim, wr, unl, err, wq, desc>>

ReadEntry(w) == \* eps = {hnsw.entryNode()}; then the first searchLevel
  /\ pc[w] = "readEntry"
  /\ LET cm == lv[w].cm  nl == lv[w].nl  e == {entry.node} IN
     IF cm > nl
     THEN StartSearch(w, cm, 1, e, "upperRet")
     ELSE StartSearch(w, Min(nl, cm), BEAM, e, "beamRet")
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err, desc>>

\* lv[w].s.level holds the level just searched
UpperRet(w) ==  \* eps[0] = candidates.popNode() (k = 1 => the best); next level
  /\ pc[w] = "upperRet"
  /\ LET l == lv[w].s.level  e == {Best(lv[w].s.res)[1]}  nl == lv[w].nl  cm == lv[w].cm IN
     IF l - 1 > nl THEN StartSearch(w, l - 1, 1, e, "upperRet")
     ELSE StartSearch(w, Min(nl, cm), BEAM, e, "beamRet")
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err, desc>>

BeamRet(w) ==   \* eps = popUntilNearestKNodes(); scratchPerLevel[i] = popToScratch (ascending)
  /\ pc[w] = "beamRet"
  /\ LET l == lv[w].s.level  res == lv[w].s.res  sc == SortAscRec(res)
         e == {p[1] : p \in res}
     IN IF l - 1 >= lv[w].lu
        THEN /\ LET q == ord[w]  cs == {<<z, sim[q, z]>> : z \in e}  r == TopK(cs, {}, BEAM) IN
                lv' = [lv EXCEPT ![w].scratch[l] = sc,
                                 ![w].s = [level |-> l - 1, k |-> BEAM, cands |-> cs, res |-> r, vis |-> e,
                                           minAcc |-> NextUp(MinComp(r, BEAM)), explore |-> TRUE,
                                           ret |-> "beamRet"]]
             /\ pc' = [pc EXCEPT ![w] = "sStep"]
             /\ wq' = IF ord[w] # lv[w].x THEN wq \cup {<<w, lv[w].x, ord[w]>>} ELSE wq
        ELSE /\ lv' = [lv EXCEPT ![w].scratch[l] = sc, ![w].li = lv[w].lu, ![w].s = NoSearch]
             /\ pc' = [pc EXCEPT ![w] = "selBegin"]
             /\ UNCHANGED wq
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err, desc>>

SelBegin(w) ==  \* non-repair selectAndLinkDiverse runs WITHOUT a lock on the node's own array
  /\ pc[w] = "selBegin"
  /\ unl' = unl \cup {<<lv[w].li, lv[w].x, w>>}
  /\ pc' = [pc EXCEPT ![w] = "selEnd"]
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, lv, claim, phase, repLevel, repClaim, wr, err, wq, desc>>

SelEnd(w) ==
  /\ pc[w] = "selEnd"
  /\ LET x == lv[w].x  l == lv[w].li  cand == lv[w].scratch[l]
         r == SL(x, arr[x][l], cand, Len(cand), {}, ord[w], l, FALSE, {})
     IN /\ arr' = [arr EXCEPT ![x][l] = r[1]]
        /\ ord' = [ord EXCEPT ![w] = r[3]]
        /\ lv' = [lv EXCEPT ![w].mask = r[2], ![w].ui = 1, ![w].rep = FALSE]
        /\ err' = err \cup r[4]
  /\ unl' = unl \ {<<lv[w].li, lv[w].x, w>>}
  /\ pc' = [pc EXCEPT ![w] = "upd"]
  /\ UNCHANGED <<sim, lvl, glen, sizeCtr, entry, claim, phase, repLevel, repClaim, wr, wq, desc>>

\* the reciprocal-link loop of addDiverseNeighbors (shared by insertion and repair)
NextMasked(w) == {i \in lv[w].mask : i >= lv[w].ui}
UpdBody(w) ==
  LET x == lv[w].x  l == lv[w].li  i == CHOOSE j \in NextMasked(w) : \A k \in NextMasked(w) : j <= k
      nbr == lv[w].scratch[l][i][1]  sc == lv[w].scratch[l][i][2]
      r == UpdateNeighbor(arr[nbr][l], x, sc, nbr, l, lv[w].rep, ord[w])
  IN /\ arr' = [arr EXCEPT ![nbr][l] = r[1]]
     /\ ord' = [ord EXCEPT ![w] = r[2]]
     /\ err' = err \cup r[3] \cup (IF OthersUnl(l, nbr, w) THEN {"write to array being written without lock"} ELSE {})
                          \cup (IF ~Alloc(nbr, l) THEN {"updateNeighbor on unallocated array"} ELSE {})
     /\ lv' = [lv EXCEPT ![w].ui = i + 1]
Upd(w) ==
  /\ pc[w] = "upd"
  /\ IF NextMasked(w) = {}
     THEN /\ UNCHANGED <<arr, ord, err, wr>>
          /\ IF lv[w].rep
             THEN /\ pc' = [pc EXCEPT ![w] = "idle"] /\ lv' = [lv EXCEPT ![w] = Idle]
             ELSE IF lv[w].li < Min(lv[w].nl, lv[w].cm)
             THEN /\ lv' = [lv EXCEPT ![w].li = @ + 1, ![w].mask = {}, ![w].ui = 0]
                  /\ pc' = [pc EXCEPT ![w] = "selBegin"]
             ELSE LET lu2 == Min(lv[w].nl, lv[w].cm) + 1 IN
                  IF lu2 = lv[w].nl + 1
                  THEN /\ pc' = [pc EXCEPT ![w] = "idle"] /\ lv' = [lv EXCEPT ![w] = Idle]
                  ELSE /\ lv' = [lv EXCEPT ![w].lu = lu2, ![w].mask = {}, ![w].ui = 0,
                                           ![w].scratch = [l \in Levels |-> <<>>]]
                       /\ pc' = [pc EXCEPT ![w] = "promote"]
     ELSE IF SPLIT
          THEN LET i == CHOOSE j \in NextMasked(w) : \A k \in NextMasked(w) : j <= k
                   st == Stripe(lv[w].li, lv[w].scratch[lv[w].li][i][1])
               IN /\ wr[st] = 0
                  /\ wr' = [wr EXCEPT ![st] = w]
                  /\ pc' = [pc EXCEPT ![w] = "updBody"]
                  /\ UNCHANGED <<arr, ord, err, lv>>
          ELSE /\ UpdBody(w) /\ UNCHANGED <<pc, wr>>
  /\ UNCHANGED <<sim, lvl, glen, sizeCtr, entry, claim, phase, repLevel, repClaim, unl, wq, desc>>
UpdRelease(w) ==
  /\ pc[w] = "updBody"
  /\ UpdBody(w)
  /\ wr' = [i \in DOMAIN wr |-> IF wr[i] = w THEN 0 ELSE wr[i]]
  /\ pc' = [pc EXCEPT ![w] = "upd"]
  /\ UNCHANGED <<sim, lvl, glen, sizeCtr, entry, claim, phase, repLevel, repClaim, unl, wq, desc>>

Promote(w) ==   \* hnsw.tryPromoteNewEntryNode(node, nodeLevel, curMaxLevel) + the ISE check
  /\ pc[w] = "promote"
  /\ IF entry.level = lv[w].cm
     THEN /\ entry' = [node |-> lv[w].x, level |-> lv[w].nl]
          /\ pc' = [pc EXCEPT ![w] = "idle"] /\ lv' = [lv EXCEPT ![w] = Idle]
          /\ UNCHANGED err
     ELSE /\ err' = err \cup (IF entry.level + 1 = lv[w].cm + 1 THEN {"ISE: not able to promote"} ELSE {})
          /\ pc' = [pc EXCEPT ![w] = "readMax"] /\ lv' = [lv EXCEPT ![w].retry = TRUE]
          /\ UNCHANGED entry
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, ord, claim, phase, repLevel, repClaim, wr, unl, wq, desc>>

-----------------------------------------------------------------------------
(* Repair: repairDisconnectedNodes -> fixDisconnectedNodes / addConnections, level by level *)
RClaim(w) ==
  /\ phase = "repair" /\ pc[w] = "idle" /\ repClaim <= Len(RepairD[repLevel + 1])
  /\ LET x == RepairD[repLevel + 1][repClaim] IN
     /\ ord' = [ord EXCEPT ![w] = x]                     \* scorer.setScoringOrdinal(node)
     /\ lv' = [lv EXCEPT ![w] = [Idle EXCEPT !.x = x, !.li = repLevel, !.rep = TRUE]]
  /\ repClaim' = repClaim + 1
  /\ pc' = [pc EXCEPT ![w] = "rSnap"]
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, claim, phase, repLevel, wr, unl, err, wq, desc>>

RSnap(w) ==     \* entryPoints = copy of existing neighbors under HnswLock.read
  /\ pc[w] = "rSnap"
  /\ LET x == lv[w].x  l == lv[w].li  e == Range(arr[x][l].n) IN
     /\ wr[Stripe(l, x)] = 0
     /\ IF e # {}
        THEN StartSearch(w, l, BEAM, e, "rLink")
        ELSE \* addConnections: eps = {hnsw.entryNode()}; descend from hnsw.numLevels() - 1
             IF entry.level > l
             THEN StartSearch(w, entry.level, BEAM, {entry.node}, "acRet")
             ELSE StartSearch(w, l, BEAM, {entry.node}, "rLink")
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err, desc>>

ACRet(w) ==     \* addConnections descent: eps[0] = candidates.popNode() on a k=beamWidth collector
  /\ pc[w] = "acRet"
  /\ LET l == lv[w].s.level  res == lv[w].s.res
         nxt == IF FIX_DESCENT THEN Best(res)[1] ELSE Worst(res)[1]
     IN /\ desc' = IF Worst(res)[1] # Best(res)[1] /\ ~FIX_DESCENT THEN desc \cup {<<lv[w].x, l>>} ELSE desc
        /\ IF l - 1 > lv[w].li THEN StartSearch(w, l - 1, BEAM, {nxt}, "acRet")
           ELSE StartSearch(w, lv[w].li, BEAM, {nxt}, "rLink")
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, wr, unl, err>>

RLink(w) ==     \* popToScratch; addDiverseNeighbors(level, node, scratch, scorer, isLinkRepair=true)
  /\ pc[w] = "rLink"
  /\ LET l == lv[w].li IN
     /\ lv' = [lv EXCEPT ![w].scratch[l] = SortAscRec(lv[w].s.res), ![w].s = NoSearch]
     /\ IF SPLIT
        THEN /\ wr[Stripe(l, lv[w].x)] = 0
             /\ wr' = [wr EXCEPT ![Stripe(l, lv[w].x)] = w]     \* selfLock = hnswLock.write(level, node)
             /\ pc' = [pc EXCEPT ![w] = "rSel"]
        ELSE /\ pc' = [pc EXCEPT ![w] = "rSel"] /\ UNCHANGED wr
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, claim, phase, repLevel, repClaim, unl, err, wq, desc>>

RSel(w) ==      \* selectAndLinkDiverse(isLinkRepair=true) under the self write lock
  /\ pc[w] = "rSel"
  /\ LET x == lv[w].x  l == lv[w].li  cand == lv[w].scratch[l]
         r == SL(x, arr[x][l], cand, Len(cand), {}, ord[w], l, TRUE, {})
     IN /\ SPLIT \/ wr[Stripe(l, x)] = 0
        /\ arr' = [arr EXCEPT ![x][l] = r[1]]
        /\ ord' = [ord EXCEPT ![w] = r[3]]
        /\ lv' = [lv EXCEPT ![w].mask = r[2], ![w].ui = 1]
        /\ err' = err \cup r[4]
  /\ wr' = IF SPLIT THEN [i \in DOMAIN wr |-> IF wr[i] = w THEN 0 ELSE wr[i]] ELSE wr
  /\ pc' = [pc EXCEPT ![w] = "upd"]
  /\ UNCHANGED <<sim, lvl, glen, sizeCtr, entry, claim, phase, repLevel, repClaim, unl, wq, desc>>

\* taskExecutor.invokeAll barrier between repair levels, then the insertion phase
RepairBarrier ==
  /\ phase = "repair"
  /\ repClaim > Len(RepairD[repLevel + 1])
  /\ \A w \in Workers : pc[w] = "idle"
  /\ IF repLevel > 0 THEN /\ repLevel' = repLevel - 1 /\ repClaim' = 1 /\ UNCHANGED phase
                     ELSE /\ phase' = "insert" /\ UNCHANGED <<repLevel, repClaim>>
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, pc, lv, claim, wr, unl, err, wq, desc>>

EdgeView == [n \in Nodes |-> [l \in Levels |-> IF Alloc(n, l) THEN Range(arr[n][l].n) ELSE {}]]
Finish ==
  /\ phase = "insert" /\ claim > Len(Order) /\ \A w \in Workers : pc[w] = "idle"
  /\ (PRINT_FINAL => PrintT(<<"FINAL", EdgeView>>))
  /\ phase' = "end"
  /\ UNCHANGED <<sim, lvl, arr, glen, sizeCtr, entry, ord, pc, lv, claim, repLevel, repClaim, wr, unl, err, wq, desc>>

Done == phase = "end" /\ UNCHANGED vars

Next ==
  \/ \E w \in Workers :
       \/ Claim(w) \/ AddNode(w) \/ TrySet(w) \/ ReadMax(w) \/ ReadEntry(w)
       \/ SStep(w) \/ UpperRet(w) \/ BeamRet(w) \/ SelBegin(w) \/ SelEnd(w)
       \/ Upd(w) \/ UpdRelease(w) \/ Promote(w)
       \/ RClaim(w) \/ RSnap(w) \/ ACRet(w) \/ RLink(w) \/ RSel(w)
  \/ RepairBarrier \/ Finish \/ Done

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
(* Properties *)
NoCodeError == err = {}                         \* no exception / failed Java assert on any path
ScorerQueryOK == wq = {}                        \* every search uses the inserted/repaired node as query
DescentGreedy == desc = {}                      \* addConnections descends to the best candidate

AllocNodes == {<<n, l>> \in Nodes \X Levels : Alloc(n, l)}
NoSelfLoop == \A p \in AllocNodes : p[1] \notin Range(arr[p[1]][p[2]].n)
NoDuplicate == \A p \in AllocNodes : LET a == arr[p[1]][p[2]] IN
                 \A i, j \in DOMAIN a.n : i # j => a.n[i] # a.n[j]
SizeBound == \A p \in AllocNodes : Size(arr[p[1]][p[2]]) <= MaxConn(p[2])
RefsExist == \A p \in AllocNodes : \A m \in Range(arr[p[1]][p[2]].n) : Alloc(m, p[2])
SortedPrefix == \A p \in AllocNodes : LET a == arr[p[1]][p[2]] IN
                  /\ a.srt <= Size(a)
                  /\ \A i \in 1..a.srt : a.s[i] # NaN
                  /\ \A i \in 1..(a.srt - 1) : a.s[i] >= a.s[i + 1]
\* NeighborArray stores "the neighbors of a node and their mutual scores"
ScoreBookkeeping == \A p \in AllocNodes : LET a == arr[p[1]][p[2]] IN
                      \A i \in DOMAIN a.n : a.s[i] = NaN \/ a.s[i] = sim[p[1], a.n[i]]
EntryOK == entry.node # NONE => /\ Exists(entry.node, entry.level)
                                 /\ Alloc(entry.node, entry.level)
SizeCounter == sizeCtr = Cardinality({n \in Nodes : glen[n] > 0})
\* "add all the outgoing links first before adding an incoming link": a node that is
\* discoverable (has an in-edge) at level l is not still being linked without lock at l
Published == \A p \in AllocNodes : \A m \in Range(arr[p[1]][p[2]].n) :
               ~(\E u \in unl : u[1] = p[2] /\ u[2] = m)

\* at the end of the build
TopLevelIsEntryLevel == phase = "end" =>
   /\ entry.node # NONE
   /\ \A n \in Nodes : glen[n] > 0 => glen[n] - 1 <= entry.level
DownwardClosed == phase = "end" => \A n \in Nodes : \A l \in Levels : Exists(n, l) => Alloc(n, l)
AllInserted == phase = "end" => \A n \in Nodes : glen[n] = lvl[n] + 1
Linked == phase = "end" =>
   \A l \in Levels : \A n \in NodesAt(l) : Cardinality(NodesAt(l)) > 1 => Size(arr[n][l]) > 0
RECURSIVE Reach(_, _)
Reach(S, l) == LET S2 == S \cup UNION {Range(arr[n][l].n) : n \in S} IN IF S2 = S THEN S ELSE Reach(S2, l)
\* every node at level l is reachable from the level-l entry point (descend greedily is not modeled:
\* we use "reachable from the entry node at its top level, then from all reached nodes below")
ReachableFromEntry == phase = "end" =>
   \A l \in 0..entry.level : NodesAt(l) \subseteq Reach({entry.node}, l)
\* the final graph equals the graph of SOME sequential insertion order (SeqViews is generated)
SeqViews == {}
SerializableView == phase = "end" => EdgeView \in SeqViews
RepairLinked == (phase # "repair") =>
   \A l \in Levels : \A n \in NodesAt(l) :
      (Cardinality(NodesAt(l)) > 1 /\ RepairD # <<>> /\ l < Len(RepairD) /\ n \in Range(RepairD[l + 1]))
         => Size(arr[n][l]) > 0
=============================================================================

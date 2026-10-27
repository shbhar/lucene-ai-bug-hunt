---------------------------- MODULE KnnRewrite ----------------------------
(***************************************************************************)
(* Model of AbstractKnnVectorQuery.rewrite (Lucene main @ ea57360912ed):   *)
(* per-leaf exact/approximate decision, optimistic per-leaf k, TopDocs     *)
(* merge with ties by doc id, the re-entry ("phase 2") pass, and a query   *)
(* timeout that can fire at any point.                                     *)
(*                                                                         *)
(* Model element            -> Java (lucene/core/.../search/)              *)
(*  PLK[l]                  -> perLeafTopKCalculation, AbstractKnnVector-  *)
(*                             Query.java:343-347 (used :276 and :327)     *)
(*  ExactLeaf(l)            -> exactSearch :360-414 (top-k, ties doc asc)  *)
(*  Phase1Leaf              -> getLeafResults :244-303, Optimistic         *)
(*                             manager :309-335 (collector k = PLK[l])     *)
(*  filtered fallback rule  -> :282-302 (cost<=PLK exact; approx kept iff  *)
(*                             EQUAL_TO and size>=PLK, or timed out)       *)
(*  Merge(R)                -> mergeLeafResults/TopDocs.merge :428-430     *)
(*  Reenter                 -> rewrite :120-152 (EQUAL_TO, min score test) *)
(*  Phase2Leaf              -> ReentrantKnnCollectorManager :433-476,      *)
(*                             seeds = phase-1 hits; HNSW scores seeds     *)
(*                             unless already early-terminated             *)
(*                             (HnswGraphSearcher.searchLevel :294-297)    *)
(*  perLeaf[l] := ...       -> runSearchTasks :222-224 REPLACES the entry  *)
(*  to (timeout flag)       -> QueryTimeout.shouldExit, monotone           *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Leaves,      \* leaf ordinals
          LeafDocs,    \* [Leaves -> SUBSET Docs]: accepted (live, filtered, with vector) docs
          Score,       \* [Docs -> Nat]
          K,           \* the query's k
          PLK,         \* [Leaves -> Nat]: perLeafTopK (>= 1)
          Filtered,    \* TRUE iff a non-trivial filter is present
          TimeoutOn    \* TRUE iff the timeout may fire

Docs == UNION {LeafDocs[l] : l \in Leaves}
Min(a, b) == IF a < b THEN a ELSE b
Better(a, b) == Score[a] > Score[b] \/ (Score[a] = Score[b] /\ a < b)
TopN(S, n) == CHOOSE T \in SUBSET S :
                 /\ Cardinality(T) = Min(n, Cardinality(S))
                 /\ \A a \in T, b \in S \ T : Better(a, b)
CountGE(S, v) == Cardinality({d \in S : Score[d] >= v})
MinScore(S) == CHOOSE v \in {Score[d] : d \in S} : \A d \in S : Score[d] >= v
Merge(R) == TopN(UNION {R[l] : l \in Leaves}, K)
Cost(l) == Cardinality(LeafDocs[l])

VARIABLES pc, todo, R, rel, path, to, M1, rel1, reentered

vars == <<pc, todo, R, rel, path, to, M1, rel1, reentered>>

Init == /\ pc = "phase1"
        /\ todo = Leaves
        /\ R = [l \in Leaves |-> {}]
        /\ rel = [l \in Leaves |-> "EQ"]
        /\ path = [l \in Leaves |-> "none"]
        /\ to = FALSE
        /\ M1 = {}
        /\ rel1 = "EQ"
        /\ reentered = {}

\* The timeout may fire at any time before the rewrite finishes.
Fire == /\ TimeoutOn /\ ~to /\ pc # "done"
        /\ to' = TRUE
        /\ UNCHANGED <<pc, todo, R, rel, path, M1, rel1, reentered>>

\* One leaf search. ck = collector k. seeds = entry points that HNSW scores first.
\* Without a timeout the visited set S is any superset of the seeds (approximate search);
\* with a timeout the search may stop anywhere, so S is any subset; the result is TopN(S, ck).
LeafOutcomes(l, ck, seeds) ==
  IF Filtered /\ Cost(l) <= PLK[l]
  THEN IF to THEN {[res |-> T, r |-> "GE", p |-> "exact"] : T \in SUBSET LeafDocs[l]}
                    \cup {[res |-> TopN(LeafDocs[l], K), r |-> "EQ", p |-> "exact"]}
       ELSE {[res |-> TopN(LeafDocs[l], K), r |-> "EQ", p |-> "exact"]}
  ELSE LET approx == IF to
                     THEN {[res |-> TopN(S, ck), r |-> "GE", p |-> "approx"] : S \in SUBSET LeafDocs[l]}
                     ELSE {[res |-> TopN(S, ck), r |-> r0, p |-> "approx"] :
                              S \in {S2 \in SUBSET LeafDocs[l] : seeds \subseteq S2},
                              r0 \in IF Filtered THEN {"EQ", "GE"} ELSE {"EQ"}}
       IN IF ~Filtered THEN approx
          ELSE {IF (o.r = "EQ" /\ Cardinality(o.res) >= PLK[l]) \/ to
                THEN o
                ELSE [res |-> TopN(LeafDocs[l], K), r |-> "EQ", p |-> "fallback"] : o \in approx}

Phase1Leaf == /\ pc = "phase1" /\ todo # {}
              /\ \E l \in todo : \E o \in LeafOutcomes(l, PLK[l], {}) :
                    /\ R' = [R EXCEPT ![l] = o.res]
                    /\ rel' = [rel EXCEPT ![l] = o.r]
                    /\ path' = [path EXCEPT ![l] = o.p]
                    /\ todo' = todo \ {l}
              /\ UNCHANGED <<pc, to, M1, rel1, reentered>>

EndPhase1 == /\ pc = "phase1" /\ todo = {}
             /\ LET M == Merge(R)
                    r == IF \E l \in Leaves : rel[l] = "GE" THEN "GE" ELSE "EQ"
                    ree == IF M # {} /\ Cardinality(Leaves) > 1 /\ r = "EQ"
                           THEN {l \in Leaves : R[l] # {} /\ MinScore(R[l]) >= MinScore(M)}
                           ELSE {}
                IN /\ M1' = M /\ rel1' = r /\ reentered' = ree
                   /\ todo' = ree
                   /\ pc' = IF ree = {} THEN "done" ELSE "phase2"
             /\ UNCHANGED <<R, rel, path, to>>

Phase2Leaf == /\ pc = "phase2" /\ todo # {}
              /\ \E l \in todo : \E o \in LeafOutcomes(l, K, R[l]) :
                    /\ R' = [R EXCEPT ![l] = o.res]     \* replaces the phase-1 hits
                    /\ rel' = [rel EXCEPT ![l] = o.r]
                    /\ path' = [path EXCEPT ![l] = o.p]
                    /\ todo' = todo \ {l}
              /\ UNCHANGED <<pc, to, M1, rel1, reentered>>

EndPhase2 == /\ pc = "phase2" /\ todo = {}
             /\ pc' = "done"
             /\ UNCHANGED <<todo, R, rel, path, to, M1, rel1, reentered>>

Done == pc = "done" /\ UNCHANGED vars   \* terminal stuttering

Next == Fire \/ Phase1Leaf \/ EndPhase1 \/ Phase2Leaf \/ EndPhase2 \/ Done
Spec == Init /\ [][Next]_vars

Final == Merge(R)
Accepted == UNION {LeafDocs[l] : l \in Leaves}

\* P1 soundness: never a doc outside the accepted set (filter, live, has vector).
Sound == Final \subseteq Accepted
\* P2 exactness: if every leaf took the exact path in every pass and no timeout fired,
\* the result is the global top-k with ties by doc id.
AllExactTopK == (pc = "done" /\ ~to /\ \A l \in Leaves : path[l] = "exact")
                   => Final = TopN(Accepted, K)
\* P3 no-loss: once a complete (EQUAL_TO) first pass found M1, the re-entry pass must not make the
\* answer worse: for every score level v, Final has at least as many hits >= v as M1.
NoPhase1Loss == (pc = "done" /\ rel1 = "EQ") => \A v \in {Score[d] : d \in M1} : CountGE(Final, v) >= CountGE(M1, v)
\* Vacuity probes (must be violated): re-entry happens; a timeout happens with re-entry.
ProbeNoReentry == reentered = {}
ProbeNotAllExactDone == ~(pc = "done" /\ \A l \in Leaves : path[l] = "exact")
\* Model mutant: merge keeps only K-1 hits (must violate AllExactTopK when the antecedent is reachable).
MutantFinal == TopN(UNION {R[l] : l \in Leaves}, K - 1)
AllExactTopKMutant == (pc = "done" /\ ~to /\ \A l \in Leaves : path[l] = "exact") => MutantFinal = TopN(Accepted, K)
=============================================================================

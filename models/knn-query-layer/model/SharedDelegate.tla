--------------------------- MODULE SharedDelegate --------------------------
(***************************************************************************)
(* Two searches share one PatienceKnnVectorQuery instance whose delegate   *)
(* is a SeededKnnVectorQuery (Lucene main @ ea57360912ed).                 *)
(*                                                                         *)
(* Model element        -> Java (lucene/core/.../search/)                  *)
(*  Assign(t)           -> PatienceKnnVectorQuery.rewrite :296-311: the    *)
(*                         field `delegate` (:45, non-final) is set to a   *)
(*                         new SeededKnnVectorQuery holding a seed Weight  *)
(*                         created on the caller's IndexSearcher           *)
(*  Use(t)              -> per-leaf approximateSearch :197-204 reads the   *)
(*                         field again; SeededKnnVectorQuery$Seeded-       *)
(*                         CollectorManager :321 runs seedWeight on the    *)
(*                         caller's leaf                                   *)
(*  Fixed = TRUE        -> the rewrite keeps the seeded delegate in a      *)
(*                         local/new query instead of the shared field     *)
(***************************************************************************)
EXTENDS Naturals
CONSTANTS Threads, NLeaves, Fixed
VARIABLES delegate, local, pc, leaf, used
vars == <<delegate, local, pc, leaf, used>>

Init == /\ delegate = "none"
        /\ local = [t \in Threads |-> "none"]
        /\ pc = [t \in Threads |-> "assign"]
        /\ leaf = [t \in Threads |-> 1]
        /\ used = [t \in Threads |-> {}]

Assign(t) == /\ pc[t] = "assign"
             /\ IF Fixed THEN /\ local' = [local EXCEPT ![t] = t] /\ UNCHANGED delegate
                         ELSE /\ delegate' = t /\ UNCHANGED local
             /\ pc' = [pc EXCEPT ![t] = "search"]
             /\ UNCHANGED <<leaf, used>>

Use(t) == /\ pc[t] = "search"
          /\ used' = [used EXCEPT ![t] = @ \cup {IF Fixed THEN local[t] ELSE delegate}]
          /\ IF leaf[t] = NLeaves
             THEN /\ pc' = [pc EXCEPT ![t] = "done"] /\ UNCHANGED leaf
             ELSE /\ leaf' = [leaf EXCEPT ![t] = @ + 1] /\ UNCHANGED pc
          /\ UNCHANGED <<delegate, local>>

Done == (\A t \in Threads : pc[t] = "done") /\ UNCHANGED vars

Next == (\E t \in Threads : Assign(t) \/ Use(t)) \/ Done
Spec == Init /\ [][Next]_vars

\* Each search must run only the seed Weight that was created on its own searcher/reader.
OwnWeightOnly == \A t \in Threads : used[t] \subseteq {t}
=============================================================================

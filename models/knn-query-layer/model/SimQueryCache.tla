--------------------------- MODULE SimQueryCache ---------------------------
(***************************************************************************)
(* A query cache keyed on (query, segment core), as LRUQueryCache is, in   *)
(* front of a per-segment result that depends on more than the core.       *)
(*                                                                         *)
(* Model element        -> Java (lucene/core/.../search/)                  *)
(*  Key = core          -> LRUQueryCache$CachingWrapperWeight.scorerSupp-  *)
(*                         lier :812-883: get/putIfAbsent(query,           *)
(*                         getCoreCacheHelper())                           *)
(*  Cacheable           -> AbstractVectorSimilarityQuery.java:252-255      *)
(*                         isCacheable(ctx) == true                        *)
(*  SegResult(live,to)  -> AbstractVectorSimilarityQuery.scorerSupplier    *)
(*                         :186-250: the doc set is computed with the      *)
(*                         reader's liveDocs (:188, :196, :204, :219) and  *)
(*                         is cut short by the searcher's QueryTimeout     *)
(*                         (:154-156, :239-243)                            *)
(*  readers r1, r2      -> two point-in-time readers sharing one segment   *)
(*                         core (an NRT reopen after a delete)             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets
CONSTANTS Cacheable
Readers == {"old", "new"}
Live == [r \in Readers |-> IF r = "old" THEN {1, 2, 3} ELSE {2, 3}]   \* doc 1 deleted in "new"
\* Matching docs of the segment among the given live docs; a timed-out search returns a subset.
SegResult(live, to) == IF to THEN {} ELSE live
VARIABLES hasEntry, cache, served
vars == <<hasEntry, cache, served>>
Init == hasEntry = FALSE /\ cache = {} /\ served = <<>>

Search(r, to) ==
  LET val == IF hasEntry THEN cache ELSE SegResult(Live[r], to)
      \* the search loop applies the reader's own live docs after the cache
      out == val \cap Live[r]
  IN /\ hasEntry' = (hasEntry \/ Cacheable)
     /\ cache' = IF ~hasEntry /\ Cacheable THEN val ELSE cache
     /\ served' = [reader |-> r, timedOut |-> to, out |-> out]

Next == \E r \in Readers, to \in BOOLEAN : Search(r, to)
Spec == Init /\ [][Next]_vars

\* A search that did not time out must see exactly its own reader's matches.
Correct == (served # <<>> /\ ~served.timedOut) => served.out = SegResult(Live[served.reader], FALSE)
=============================================================================

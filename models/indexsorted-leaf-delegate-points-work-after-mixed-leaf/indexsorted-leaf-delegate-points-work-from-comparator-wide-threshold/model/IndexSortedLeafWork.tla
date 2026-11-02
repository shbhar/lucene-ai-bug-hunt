------------------------- MODULE IndexSortedLeafWork -------------------------
(***************************************************************************)
(* Per-leaf call protocol between TopFieldCollector's leaf collector, the  *)
(* IndexSortedLeafFieldComparator wrapper (#16857) and NumericComparator's *)
(* PointsCompetitiveDISIBuilder, for a sorted top-k search over NumLeaves  *)
(* segments in one slice (one thread). Each leaf is either "N" (normal:    *)
(* the search sort is not a prefix of its index sort) or "ET" (it is, so   *)
(* the collector terminates it early by the index sort).                   *)
(* Extends sort-leafstart-hitsthreshold-before-setscorer-points-intersect-16857's SortLeafStart.tla (same abstraction of cost and values). *)
(*                                                                         *)
(* Model element -> Java line (main e1faeba08bd unless noted):             *)
(*   NewLeaf(k,s)   TopFieldCollector.java:50-62; :55 getLeafComparator ->  *)
(*                  FieldValueHitQueue.java:177-194 wraps the comparator   *)
(*                  in IndexSortedLeafFieldComparator iff canEarlyTerminate *)
(*                  (kind "ET"); :56-59 ctor call setHitsThresholdReached  *)
(*                  when totalHitsRelation is GREATER_THAN_OR_EQUAL_TO.    *)
(*                  NumericComparator.java:315 iteratorCost = -1 per leaf. *)
(*                  645d4fe TopFieldCollector.java:69-74: ET leaf calls    *)
(*                  disableSkipping() -> NumericComparator.java:110-116    *)
(*                  (645d4fe) competitiveDISIBuilder = null.               *)
(*   wrapper        IndexSortedLeafFieldComparator.java:54-56 setBottom and *)
(*                  :81-84 setScorer always forwarded to the delegate;     *)
(*                  :91-97 setHitsThresholdReached forwarded only if       *)
(*                  paging; :58-69 compareBottom terminates the leaf on    *)
(*                  the first non-competitive doc once its own flag is set.*)
(*   SetScorer      TopFieldCollector.java:114-123 -> NumericComparator    *)
(*                  .java:146-151 -> 345-355 (cost := scorer cost, then     *)
(*                  updateCompetitiveIterator, only if cost == -1).        *)
(*   Collect(c)     TopFieldCollector.java:64-77 countHit (:74 first       *)
(*                  crossing), :79-92 thresholdCheck, :94-100 / :102-112   *)
(*                  collectCompetitiveHit / collectAnyHit -> setBottom     *)
(*                  (NumericComparator.java:131-137).                      *)
(*   Upd            NumericComparator.java:222-247: gate 'hitsThresholdReached'*)
(*                  (:223, a field of the top-level comparator, :59) and    *)
(*                  'leafTopSet || queueFull' (:226, queueFull :60 also    *)
(*                  comparator-wide); past the gates the builder walks the *)
(*                  BKD tree for the 8x estimate and may intersect          *)
(*                  (:426-446): that is the "points work" counted here.    *)
(* Variants:                                                               *)
(*   "p645"     645d4fe (disableSkipping on ET leaves, no ctor call)        *)
(*   "main"     e1faeba08bd                                                *)
(*   "fixA"     main + LFV-1057 fix A (doUpdate returns while cost == -1)   *)
(*   "fixB"     main + LFV-1057 fix B (ctor call moved into setScorer)      *)
(*   "perleaf"  main + per-leaf flag: NumericLeafComparator records its own *)
(*              setHitsThresholdReached and :223 tests that flag            *)
(*   "perleafA" perleaf + fix A;   "perleafB" perleaf + fix B              *)
(* Abstracted: missing values (:231-233), sampling (:235-240), values of    *)
(* bottom/top; cost is -1 (sentinel) or 1 (known).                         *)
(***************************************************************************)
EXTENDS Integers

CONSTANTS Variant, NumLeaves, MaxCollect, NumHitsSet, ThresholdSet

VARIABLES leaf, kind, phase, rel, totalHits, queueFull, htr, cost, hasScorer,
          leafFlag, wrapHtr, updInLeaf, etWork, blindUpdates, collected,
          NumHits, Threshold, Paging

vars == <<leaf, kind, phase, rel, totalHits, queueFull, htr, cost, hasScorer,
          leafFlag, wrapHtr, updInLeaf, etWork, blindUpdates, collected,
          NumHits, Threshold, Paging>>
params == <<NumHits, Threshold, Paging>>

PerLeaf == Variant \in {"perleaf", "perleafA", "perleafB"}
GuardA  == Variant \in {"fixA", "perleafA"}
CallInSetScorer == Variant \in {"fixB", "perleafB"}
CtorCall == Variant \in {"main", "fixA", "perleaf", "perleafA"}

Eligible(h, qf) == h /\ (Paging \/ qf)

(* updateCompetitiveIterator on the current leaf's builder; k = leaf kind,   *)
(* h = comparator-wide flag, lf = per-leaf flag after the call               *)
Upd(k, h, lf, qf, c) ==
  IF Variant = "p645" /\ k = "ET" THEN [cost |-> c, blind |-> 0, upd |-> FALSE]
  ELSE IF ~Eligible(IF PerLeaf THEN lf ELSE h, qf)
       THEN [cost |-> c, blind |-> 0, upd |-> FALSE]
  ELSE IF c = -1
       THEN IF GuardA
            THEN [cost |-> c, blind |-> 0, upd |-> FALSE]
            ELSE [cost |-> 1, blind |-> 1, upd |-> TRUE]
       ELSE [cost |-> c, blind |-> 0, upd |-> TRUE]
NoUpd(c) == [cost |-> c, blind |-> 0, upd |-> FALSE]

(* does a setHitsThresholdReached on the leaf comparator reach the delegate?  *)
(* 645d4fe has no wrapper: the call reaches NumericLeafComparator (setting the *)
(* comparator-wide flag) but the ET leaf's builder is null, so Upd is a no-op. *)
Reaches(k) == k = "N" \/ Paging \/ Variant = "p645"

EtWork(k, r1, r2) == IF k = "ET" /\ ~Paging
                     THEN (IF r1.upd THEN 1 ELSE 0) + (IF r2.upd THEN 1 ELSE 0)
                     ELSE 0

Init ==
  /\ leaf = 0 /\ kind = "N" /\ phase = "between" /\ rel = "EQ" /\ totalHits = 0
  /\ queueFull = FALSE /\ htr = FALSE /\ cost = -1 /\ hasScorer = FALSE
  /\ leafFlag = FALSE /\ wrapHtr = FALSE /\ updInLeaf = FALSE /\ etWork = 0
  /\ blindUpdates = 0 /\ collected = 0
  /\ NumHits \in NumHitsSet /\ Threshold \in ThresholdSet /\ Paging \in BOOLEAN

NewLeaf(k, s) ==
  /\ phase = "between" /\ leaf < NumLeaves
  /\ LET call == CtorCall /\ rel = "GTE"
         reach == call /\ Reaches(k)
         lf == reach
         h == htr \/ reach
         r == IF reach THEN Upd(k, h, lf, queueFull, -1) ELSE NoUpd(-1)
     IN /\ leaf' = leaf + 1
        /\ kind' = k
        /\ phase' = IF s THEN "created" ELSE "between"
        /\ hasScorer' = s
        /\ cost' = r.cost
        /\ htr' = h
        /\ leafFlag' = lf
        /\ wrapHtr' = (k = "ET" /\ call)
        /\ updInLeaf' = r.upd
        /\ etWork' = etWork + EtWork(k, r, NoUpd(0))
        /\ blindUpdates' = blindUpdates + r.blind
        /\ collected' = 0
        /\ UNCHANGED <<rel, totalHits, queueFull>>
        /\ UNCHANGED params

SetScorer ==
  /\ phase = "created"
  /\ LET r1 == IF cost = -1 THEN Upd(kind, htr, leafFlag, queueFull, 1) ELSE NoUpd(cost)
         call == CallInSetScorer /\ rel = "GTE"
         reach == call /\ Reaches(kind)
         lf == leafFlag \/ reach
         h == htr \/ reach
         r2 == IF reach THEN Upd(kind, h, lf, queueFull, r1.cost) ELSE NoUpd(r1.cost)
     IN /\ cost' = r2.cost
        /\ htr' = h
        /\ leafFlag' = lf
        /\ wrapHtr' = (wrapHtr \/ (kind = "ET" /\ call))
        /\ updInLeaf' = (updInLeaf \/ r1.upd \/ r2.upd)
        /\ etWork' = etWork + EtWork(kind, r1, r2)
        /\ blindUpdates' = blindUpdates + r1.blind + r2.blind
        /\ phase' = "scored"
        /\ UNCHANGED <<leaf, kind, rel, totalHits, queueFull, hasScorer, collected>>
        /\ UNCHANGED params

Collect(competitive) ==
  /\ phase = "scored" /\ collected < MaxCollect
  /\ LET th == totalHits + 1
         crossing == rel = "EQ" /\ th > Threshold
         reach == crossing /\ Reaches(kind)
         lf1 == leafFlag \/ reach
         h1 == htr \/ reach
         r1 == IF reach THEN Upd(kind, h1, lf1, queueFull, cost) ELSE NoUpd(cost)
         wh == wrapHtr \/ (kind = "ET" /\ crossing)
         \* ET leaf: a non-competitive doc on a full queue ends the leaf once the
         \* wrapper knows the threshold (645d4fe: once totalHits > threshold)
         stop == kind = "ET" /\ queueFull /\ ~competitive
                 /\ (IF Variant = "p645" THEN th > Threshold ELSE wh)
         bottomCall == (~queueFull /\ th >= NumHits) \/ (queueFull /\ competitive)
         r2 == IF bottomCall THEN Upd(kind, h1, lf1, TRUE, r1.cost) ELSE NoUpd(r1.cost)
     IN /\ totalHits' = th
        /\ rel' = IF crossing THEN "GTE" ELSE rel
        /\ htr' = h1
        /\ leafFlag' = lf1
        /\ wrapHtr' = wh
        /\ queueFull' = (queueFull \/ th >= NumHits)
        /\ cost' = r2.cost
        /\ updInLeaf' = (updInLeaf \/ r1.upd \/ r2.upd)
        /\ etWork' = etWork + EtWork(kind, r1, r2)
        /\ blindUpdates' = blindUpdates + r1.blind + r2.blind
        /\ collected' = collected + 1
        /\ phase' = IF stop THEN "between" ELSE phase
        /\ UNCHANGED <<leaf, kind, hasScorer>>
        /\ UNCHANGED params

EndLeaf ==
  /\ phase = "scored"
  /\ phase' = "between"
  /\ UNCHANGED <<leaf, kind, rel, totalHits, queueFull, htr, cost, hasScorer,
                 leafFlag, wrapHtr, updInLeaf, etWork, blindUpdates, collected>>
  /\ UNCHANGED params

Done == phase = "between" /\ leaf = NumLeaves /\ UNCHANGED vars

Next == \/ \E k \in {"N", "ET"}, s \in BOOLEAN : NewLeaf(k, s)
        \/ SetScorer
        \/ \E c \in BOOLEAN : Collect(c)
        \/ EndLeaf
        \/ Done

Spec == Init /\ [][Next]_vars

TypeOK == /\ cost \in {-1, 1} /\ rel \in {"EQ", "GTE"} /\ kind \in {"N", "ET"}
          /\ phase \in {"between", "created", "scored"}

(* C1, the claim (IndexSortedLeafFieldComparator.java:31-33): when not paging, *)
(* a leaf terminated early by its index sort does no points work.             *)
NoETWork == etWork = 0

(* P1, no lost pruning: once a normal leaf has its scorer and the comparator  *)
(* can skip by the comparator-wide state, an update has run in it.            *)
FirstUpdateNotLostN == (phase = "scored" /\ kind = "N" /\ Eligible(htr, queueFull)) => updInLeaf

(* P2, paging keeps the delegate's skipping in ET leaves (the wrapper's reason *)
(* to forward when paging, :31-32).                                           *)
PagingETUpdates == (phase = "scored" /\ kind = "ET" /\ Paging /\ Variant # "p645"
                    /\ Eligible(htr, queueFull)) => updInLeaf

(* S1, LFV-1057: no points read before the leaf's scorer cost is known *)
NoBlindUpdate == blindUpdates = 0

(* vacuity probes: must be violated (reachable) in every variant *)
NeverETAfterThreshold == ~(phase = "scored" /\ kind = "ET" /\ ~Paging /\ htr /\ queueFull)
NeverPagingETEligible == ~(phase = "scored" /\ kind = "ET" /\ Paging /\ htr)
=============================================================================

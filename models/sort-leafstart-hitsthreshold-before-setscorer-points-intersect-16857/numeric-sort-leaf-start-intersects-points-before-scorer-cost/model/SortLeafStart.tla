--------------------------- MODULE SortLeafStart ---------------------------
(***************************************************************************)
(* Per-leaf call protocol between TopFieldCollector's leaf collector and   *)
(* NumericComparator's PointsCompetitiveDISIBuilder, for a sorted top-k    *)
(* search over NumLeaves segments, one search thread.                      *)
(*                                                                         *)
(* Model element -> Java line (upstream main 716d6f216f5 = #16857, unless  *)
(* noted; 645d4fe = the code under test before #16857):                    *)
(*   NewLeaf          TopFieldCollector.java:51-62 (ctor); lines 56-59:    *)
(*                    'if (totalHitsRelation == GREATER_THAN_OR_EQUAL_TO)  *)
(*                    comparator.setHitsThresholdReached()'.  645d4fe      *)
(*                    TopFieldCollector.java:52-75 has no such call.       *)
(*                    NumericComparator.java:315 'iteratorCost = -1' for   *)
(*                    the builder of every new leaf.                       *)
(*   hasScorer=FALSE  IndexSearcher.java:836-843 (645d4fe): getLeafCollector *)
(*                    runs before weight.scorerSupplier(ctx), which is     *)
(*                    null when the query matches nothing in the leaf, so  *)
(*                    setScorer/collect never happen for that leaf.        *)
(*   SetScorer        TopFieldCollector.java:115-123 -> NumericComparator  *)
(*                    .java:146-151 -> 345-355 (cost := scorer cost, then  *)
(*                    updateCompetitiveIterator, only if cost == -1).      *)
(*   Collect          TopFieldCollector.java:64-77 countHit (line 74       *)
(*                    setHitsThresholdReached on the first crossing), then *)
(*                    96-111 collectCompetitiveHit / collectAnyHit ->      *)
(*                    setBottom (NumericComparator.java:132-137).          *)
(*   Upd              NumericComparator.java:222-247 guards (htr, topSet   *)
(*                    or queueFull), then doUpdateCompetitiveIterator      *)
(*                    397-446: threshold = iteratorCost >>> 3 (line 426);  *)
(*                    with iteratorCost = -1 the threshold is 2^61-1, the  *)
(*                    selectivity estimate is always below it and the BKD  *)
(*                    tree is intersected (441), then iteratorCost :=      *)
(*                    newIterator.cost() (444) so SetScorer skips (347).   *)
(*   htr, queueFull   NumericComparator.java:59-60: fields of the top-level *)
(*                    comparator, shared by all leaves of one search.      *)
(*   leafHtr          IndexSortedLeafFieldComparator.hitsThresholdReached  *)
(*                    (per leaf; the reason #16857 added the ctor call).   *)
(* Variants: "p645" = 645d4fe, "main" = 716d6f216f5, "fixA" = main plus    *)
(*   'if (iteratorCost == -1) return;' at the top of                       *)
(*   doUpdateCompetitiveIterator, "fixB" = main with the ctor call moved   *)
(*   into setScorer after comparator.setScorer.                            *)
(* Abstracted: missing values (231-233), update sampling (235-240), the    *)
(* values of bottom/top; cost is -1 (sentinel) or 1 (known).               *)
(***************************************************************************)
EXTENDS Integers

CONSTANTS Variant, NumLeaves, MaxCollect, NumHitsSet, ThresholdSet

(* numHits, totalHitsThreshold and paging (searchAfter) are chosen once per run *)

VARIABLES leaf, phase, rel, totalHits, queueFull, htr, cost, hasScorer,
          leafHtr, updInLeaf, collected, blindUpdates, NumHits, Threshold, Paging

vars == <<leaf, phase, rel, totalHits, queueFull, htr, cost, hasScorer,
          leafHtr, updInLeaf, collected, blindUpdates, NumHits, Threshold, Paging>>
params == <<NumHits, Threshold, Paging>>

Eligible(h, qf) == h /\ (Paging \/ qf)

(* updateCompetitiveIterator on the current leaf's builder *)
Upd(h, qf, c) ==
  IF ~Eligible(h, qf) THEN [cost |-> c, blind |-> 0, upd |-> FALSE]
  ELSE IF c = -1
       THEN IF Variant = "fixA"
            THEN [cost |-> c, blind |-> 0, upd |-> FALSE]   \* guard returns
            ELSE [cost |-> 1, blind |-> 1, upd |-> TRUE]    \* intersect, cost unknown
       ELSE [cost |-> c, blind |-> 0, upd |-> TRUE]         \* estimate with known cost
NoUpd(c) == [cost |-> c, blind |-> 0, upd |-> FALSE]

Init ==
  /\ leaf = 0 /\ phase = "between" /\ rel = "EQ" /\ totalHits = 0
  /\ queueFull = FALSE /\ htr = FALSE /\ cost = -1 /\ hasScorer = FALSE
  /\ leafHtr = FALSE /\ updInLeaf = FALSE /\ collected = 0 /\ blindUpdates = 0
  /\ NumHits \in NumHitsSet /\ Threshold \in ThresholdSet /\ Paging \in BOOLEAN

NewLeaf(s) ==
  /\ phase = "between" /\ leaf < NumLeaves
  /\ LET ctorCall == Variant \in {"main", "fixA"} /\ rel = "GTE"
         r == IF ctorCall THEN Upd(TRUE, queueFull, -1) ELSE NoUpd(-1)
     IN /\ leaf' = leaf + 1
        /\ phase' = IF s THEN "created" ELSE "between"   \* no scorer: leaf ends
        /\ hasScorer' = s
        /\ cost' = r.cost
        /\ htr' = (htr \/ ctorCall)
        /\ leafHtr' = ctorCall
        /\ updInLeaf' = r.upd
        /\ blindUpdates' = blindUpdates + r.blind
        /\ collected' = 0
        /\ UNCHANGED <<rel, totalHits, queueFull>>
        /\ UNCHANGED params

SetScorer ==
  /\ phase = "created"
  /\ LET r1 == IF cost = -1 THEN Upd(htr, queueFull, 1) ELSE NoUpd(cost)
         fixBCall == Variant = "fixB" /\ rel = "GTE"
         r2 == IF fixBCall THEN Upd(TRUE, queueFull, r1.cost) ELSE NoUpd(r1.cost)
     IN /\ cost' = r2.cost
        /\ htr' = (htr \/ fixBCall)
        /\ leafHtr' = (leafHtr \/ fixBCall)
        /\ updInLeaf' = (updInLeaf \/ r1.upd \/ r2.upd)
        /\ blindUpdates' = blindUpdates + r1.blind + r2.blind
        /\ phase' = "scored"
        /\ UNCHANGED <<leaf, rel, totalHits, queueFull, hasScorer, collected>>
        /\ UNCHANGED params

Collect(competitive) ==
  /\ phase = "scored" /\ collected < MaxCollect
  /\ LET th == totalHits + 1
         crossing == rel = "EQ" /\ th > Threshold
         r1 == IF crossing THEN Upd(TRUE, queueFull, cost) ELSE NoUpd(cost)
         h1 == htr \/ crossing
         bottomCall == (~queueFull /\ th >= NumHits) \/ (queueFull /\ competitive)
         r2 == IF bottomCall THEN Upd(h1, TRUE, r1.cost) ELSE NoUpd(r1.cost)
     IN /\ totalHits' = th
        /\ rel' = IF crossing THEN "GTE" ELSE rel
        /\ htr' = h1
        /\ leafHtr' = (leafHtr \/ crossing)
        /\ queueFull' = (queueFull \/ th >= NumHits)
        /\ cost' = r2.cost
        /\ updInLeaf' = (updInLeaf \/ r1.upd \/ r2.upd)
        /\ blindUpdates' = blindUpdates + r1.blind + r2.blind
        /\ collected' = collected + 1
        /\ UNCHANGED <<leaf, phase, hasScorer>>
        /\ UNCHANGED params

EndLeaf ==
  /\ phase = "scored"
  /\ phase' = "between"
  /\ UNCHANGED <<leaf, rel, totalHits, queueFull, htr, cost, hasScorer,
                 leafHtr, updInLeaf, collected, blindUpdates>>
  /\ UNCHANGED params

Done == phase = "between" /\ leaf = NumLeaves /\ UNCHANGED vars

Next == \/ \E s \in BOOLEAN : NewLeaf(s)
        \/ SetScorer
        \/ \E c \in BOOLEAN : Collect(c)
        \/ EndLeaf
        \/ Done

Spec == Init /\ [][Next]_vars

TypeOK == /\ cost \in {-1, 1} /\ rel \in {"EQ", "GTE"}
          /\ phase \in {"between", "created", "scored"}

(* S1, the claim: the competitive points set is never materialised before the  *)
(* leaf's scorer cost is known (the 8x selectivity check is never bypassed).    *)
NoBlindUpdate == blindUpdates = 0

(* P1, no lost first update: once a leaf has its scorer and the comparator can  *)
(* skip (threshold reached, queue full or paging), an update has run in it.     *)
FirstUpdateNotLost == (phase = "scored" /\ Eligible(htr, queueFull)) => updInLeaf

(* P2, #16857's reason for the ctor call: an index-sorted leaf comparator knows  *)
(* the threshold was reached before its first collect. Not applicable to p645.  *)
LeafKnowsThreshold == (Variant # "p645" /\ phase = "scored" /\ rel = "GTE") => leafHtr

(* vacuity probes: these must be violated (reachable) in every variant *)
NeverEligibleScored == ~(phase = "scored" /\ Eligible(htr, queueFull) /\ leaf > 1)
NeverScorelessLeafAfterThreshold == ~(phase = "between" /\ hasScorer = FALSE /\ leaf > 1 /\ rel = "GTE")
=============================================================================

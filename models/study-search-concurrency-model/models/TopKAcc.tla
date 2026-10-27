------------------------------ MODULE TopKAcc ------------------------------
(***************************************************************************)
(* Design-level model of concurrent top-k collection with a shared        *)
(* minimum competitive score: TopScoreDocCollectorManager +               *)
(* TopScoreDocCollector + MaxScoreAccumulator + IndexSearcher slices /     *)
(* intra-segment partitions, and the final TopDocs.merge.                  *)
(* Lucene main 7cecf79523, lucene/core/src/java/org/apache/lucene/search/ *)
(*                                                                         *)
(* Mapping:                                                                *)
(*  heap / topScore      TopScoreDocCollector.java:36,79-80,130-135        *)
(*  collect              :93-128 (count, periodic global check, after,     *)
(*                       non-competitive, competitive)                     *)
(*  updateGlobal         :137-152 (nextUp iff docBase >= docId(acc))       *)
(*  updateMin            :154-172 (nextUp(topScore), accumulate(topCode))  *)
(*  setScorer            :83-91                                            *)
(*  threshold            TopScoreDocCollectorManager.java:72-73            *)
(*  acc                  MaxScoreAccumulator.java:28-43 (LongAccumulator    *)
(*                       max, codes from DocScoreEncoder.java:31-41)        *)
(*  slices / order       IndexSearcher.java:1004-1031 (partitions sorted by *)
(*                       docBase then minDocId), :801-808                   *)
(*  scorer pruning       Scorable#setMinCompetitiveScore: docs with score  *)
(*                       < minScore MAY be skipped (nondeterministic here)  *)
(*  merge                TopDocs.java:216-217 (score desc, doc asc)         *)
(*                                                                         *)
(* Scores are positive integers scaled by 2 so that nextUp(x) = x + 1 has  *)
(* no representable score strictly between x and x + 1, like Math.nextUp.  *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS
  NDocs,      \* docs 0..NDocs-1, all match the query
  ScoreVals,  \* possible (unscaled) scores, e.g. {1, 2}
  Leaves,     \* Leaves[i] = [base |-> b, len |-> n], contiguous, in doc order
  Slices,     \* Slices[c] = << [leaf |-> i, lo |-> local, hi |-> local], ... >>
  K,          \* numHits
  T,          \* totalHitsThreshold as passed to the manager
  ModMask,    \* MaxScoreAccumulator.modInterval (2^k - 1)
  After,      \* [s |-> unscaled score, d |-> doc]; s = 0 means no "after" (first page)
  TimeoutPossible \* IndexSearcher#setTimeout: QueryTimeout may start returning true (monotone)

Docs == 0..NDocs - 1
Thr == IF T > K THEN T ELSE K
NC == Len(Slices)
Cs == 1..NC
NoAcc == -1
NegInf == -2            \* score of the sentinel (Float.NEGATIVE_INFINITY)
BIG == 1000             \* code = score * BIG + (BIG - 1 - doc)
Enc(s, d) == s * BIG + (BIG - 1 - d)
CodeScore(c) == c \div BIG
CodeDoc(c) == BIG - 1 - (c % BIG)
NextUp(x) == x + 1

VARIABLES score, heap, th, rel, minc, pi, pos, started, acc, tout

vars == <<score, heap, th, rel, minc, pi, pos, started, acc, tout>>

Better(a, b) == a.s > b.s \/ (a.s = b.s /\ a.d < b.d)
TopKOf(S) == { x \in S : Cardinality({ y \in S : Better(y, x) }) < K }
Worst(S) == CHOOSE x \in S : \A y \in S : y = x \/ Better(y, x)
\* heap top: the worst of K entries, or the sentinel while not full
TopScore(c) == IF Cardinality(heap[c]) < K THEN NegInf ELSE Worst(heap[c]).s
TopCode(c) == LET w == Worst(heap[c]) IN Enc(w.s, w.d)

Part(c) == Slices[c][pi[c]]
Base(c) == Leaves[Part(c).leaf].base
Hi(c) == IF Part(c).hi > Leaves[Part(c).leaf].len THEN Leaves[Part(c).leaf].len ELSE Part(c).hi

Init ==
  /\ score \in [Docs -> { 2 * v : v \in ScoreVals }]
  /\ heap = [c \in Cs |-> {}]
  /\ th = [c \in Cs |-> 0]
  /\ rel = [c \in Cs |-> "EQ"]
  /\ minc = [c \in Cs |-> 0]
  /\ pi = [c \in Cs |-> 1]
  /\ pos = [c \in Cs |-> Slices[c][1].lo]
  /\ started = [c \in Cs |-> FALSE]
  /\ acc = NoAcc
  /\ tout = FALSE

UseAcc == TRUE   \* totalHitsThreshold != Integer.MAX_VALUE in all configurations here

\* ---- updateGlobalMinCompetitiveScore (:137-152) as a function of the read acc value ----
GlobalNew(c, a, m) ==
  IF a = NoAcc THEN m
  ELSE LET s0 == CodeScore(a)
           s == IF Base(c) >= CodeDoc(a) THEN NextUp(s0) ELSE s0
       IN IF s > m THEN s ELSE m
\* ---- updateMinCompetitiveScore (:154-172): returns [m, acc, gte] ----
LocalNew(c, h, hits, m, a) ==
  IF hits > Thr
  THEN LET ts == IF Cardinality(h) < K THEN NegInf ELSE Worst(h).s
           l == NextUp(ts)
       IN IF l > m
          THEN [m |-> l, acc |-> LET tc == Enc(Worst(h).s, Worst(h).d)
                                 IN IF tc > a THEN tc ELSE a, gte |-> TRUE]
          ELSE [m |-> m, acc |-> a, gte |-> FALSE]
  ELSE [m |-> m, acc |-> a, gte |-> FALSE]

StartLeaf(c) ==
  /\ pi[c] <= Len(Slices[c]) /\ ~started[c]
  /\ started' = [started EXCEPT ![c] = TRUE]
  /\ pos' = [pos EXCEPT ![c] = Part(c).lo]
  \* setScorer: new LeafCollector, minCompetitiveScore = 0
  /\ LET g == GlobalNew(c, acc, 0) IN
       /\ minc' = [minc EXCEPT ![c] = g]
       /\ rel' = [rel EXCEPT ![c] = IF g > 0 THEN "GTE" ELSE @]
  /\ UNCHANGED <<score, heap, th, pi, acc, tout>>

EndLeaf(c) ==
  /\ started[c] /\ pos[c] >= Hi(c)
  /\ started' = [started EXCEPT ![c] = FALSE]
  /\ pi' = [pi EXCEPT ![c] = @ + 1]
  /\ UNCHANGED <<score, heap, th, rel, minc, pos, acc, tout>>

\* the scorer skips doc (allowed only if its score < the scorer's min competitive score)
Skip(c) ==
  /\ started[c] /\ pos[c] < Hi(c)
  /\ score[Base(c) + pos[c]] < minc[c]
  /\ pos' = [pos EXCEPT ![c] = @ + 1]
  /\ UNCHANGED <<score, heap, th, rel, minc, pi, started, acc, tout>>

Collect(c) ==
  /\ started[c] /\ pos[c] < Hi(c)
  /\ LET d == Base(c) + pos[c]
         sc == score[d]
         hits == th[c] + 1
         \* periodic global check (:99-101)
         m1 == IF hits % (ModMask + 1) = 0 THEN GlobalNew(c, acc, minc[c]) ELSE minc[c]
         gte1 == m1 > minc[c]
         prevPage == After.s > 0 /\ (sc > 2 * After.s \/ (sc = 2 * After.s /\ d <= After.d))
         ts == TopScore(c)
         newHeap == IF Cardinality(heap[c]) < K THEN heap[c] \cup {[s |-> sc, d |-> d]}
                    ELSE (heap[c] \ {Worst(heap[c])}) \cup {[s |-> sc, d |-> d]}
     IN /\ th' = [th EXCEPT ![c] = hits]
        /\ pos' = [pos EXCEPT ![c] = @ + 1]
        /\ IF prevPage
           THEN \* :103-111 hit collected on a previous page
                LET r == IF rel[c] = "EQ" /\ ~gte1 THEN LocalNew(c, heap[c], hits, m1, acc)
                         ELSE [m |-> m1, acc |-> acc, gte |-> FALSE]
                IN /\ minc' = [minc EXCEPT ![c] = r.m]
                   /\ acc' = r.acc
                   /\ rel' = [rel EXCEPT ![c] = IF gte1 \/ r.gte THEN "GTE" ELSE @]
                   /\ UNCHANGED heap
           ELSE IF sc <= ts
           THEN \* :113-124 not competitive
                LET r == IF hits = Thr + 1 THEN LocalNew(c, heap[c], hits, m1, acc)
                         ELSE [m |-> m1, acc |-> acc, gte |-> FALSE]
                IN /\ minc' = [minc EXCEPT ![c] = r.m]
                   /\ acc' = r.acc
                   /\ rel' = [rel EXCEPT ![c] = IF gte1 \/ r.gte THEN "GTE" ELSE @]
                   /\ UNCHANGED heap
           ELSE \* :130-135 competitive
                LET r == LocalNew(c, newHeap, hits, m1, acc)
                IN /\ heap' = [heap EXCEPT ![c] = newHeap]
                   /\ minc' = [minc EXCEPT ![c] = r.m]
                   /\ acc' = r.acc
                   /\ rel' = [rel EXCEPT ![c] = IF gte1 \/ r.gte THEN "GTE" ELSE @]
  /\ UNCHANGED <<score, pi, started, tout>>

\* the QueryTimeout starts returning true (QueryTimeoutImpl is monotone)
TimeoutFires == TimeoutPossible /\ ~tout /\ tout' = TRUE
                /\ UNCHANGED <<score, heap, th, rel, minc, pi, pos, started, acc>>
\* TimeLimitingBulkScorer.java:69-72 checks shouldExit() before each chunk and throws
\* TimeExceededException; IndexSearcher.java:851-858 catches it, sets partialResult and
\* finishes the leaf; the slice goes on with its next partition
TimeExceeded(c) ==
  /\ tout /\ started[c] /\ pos[c] < Hi(c)
  /\ pos' = [pos EXCEPT ![c] = Hi(c)]
  /\ UNCHANGED <<score, heap, th, rel, minc, pi, started, acc, tout>>

AllDone == \A c \in Cs : pi[c] > Len(Slices[c])
Terminate == AllDone /\ UNCHANGED vars

Next == \/ \E c \in Cs : StartLeaf(c) \/ EndLeaf(c) \/ Skip(c) \/ Collect(c) \/ TimeExceeded(c)
        \/ TimeoutFires
        \/ Terminate

Spec == Init /\ [][Next]_vars

(* ---------------- properties (checked on the reduced result) ---------------- *)
Eligible == { [s |-> score[d], d |-> d] : d \in
               { x \in Docs : After.s = 0 \/ ~(score[x] > 2 * After.s \/ (score[x] = 2 * After.s /\ x <= After.d)) } }
Merged == TopKOf(UNION { heap[c] : c \in Cs })
RECURSIVE SumTh(_)
SumTh(S) == IF S = {} THEN 0 ELSE LET c == CHOOSE c \in S : TRUE IN th[c] + SumTh(S \ {c})
Total == SumTh(Cs)
Rel == IF \E c \in Cs : rel[c] = "GTE" THEN "GTE" ELSE "EQ"

\* concurrent top-k (with pruning) == exact sequential top-k, including doc-id tie-breaks
TopKCorrect == AllDone => Merged = TopKOf(Eligible)
\* TotalHits contract (TopScoreDocCollectorManager javadoc :37-55)
EqMeansExact == AllDone => (Rel = "EQ" => Total = NDocs)
AccurateUpToThreshold == AllDone => (NDocs <= Thr => (Total = NDocs /\ Rel = "EQ"))
LowerBoundAboveThreshold == AllDone => (Rel = "GTE" => Total > Thr)
NeverOvercount == Total <= NDocs
=============================================================================

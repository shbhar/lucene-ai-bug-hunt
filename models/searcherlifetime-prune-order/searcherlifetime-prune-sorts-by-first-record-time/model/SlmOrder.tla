---------------------------- MODULE SlmOrder ----------------------------
(***************************************************************************)
(* Model of SearcherLifetimeManager record/prune ordering (Lucene main     *)
(* 645d4fe14a20, lucene/core/src/java/org/apache/lucene/search/            *)
(* SearcherLifetimeManager.java).                                          *)
(*                                                                         *)
(* Model element            -> Java                                        *)
(*   cur / Refresh           -> SearcherManager.maybeRefresh: a new reader  *)
(*                              with a higher DirectoryReader.getVersion() *)
(*   Acquire(r)              -> SearcherManager.acquire() by request r      *)
(*   Record(r)               -> SLM.record (:141-165): a tracker is created *)
(*                              only if the version is not tracked yet;    *)
(*                              recordTimeSec = nanoTime at creation (:103)*)
(*   rt[v]                   -> SearcherTracker.recordTimeSec (:94,:103)    *)
(*   clock / Tick            -> System.nanoTime(); every Record reads a     *)
(*                              strictly later time than the previous one  *)
(*   Ord (Impl="stock")      -> Collections.sort by compareTo (:106-110):   *)
(*                              descending recordTimeSec                   *)
(*   Age(i)                  -> prune loop (:244-263): first = 0, else     *)
(*                              now - recordTimeSec of the previous tracker*)
(*                              in the sorted list (also when it was pruned*)
(*                              :263 runs after the if)                    *)
(*   Prune(m)                -> prune(new PruneByAge(m)) (:211-225,:234)    *)
(*   Impl="fixVersion"       -> fix A: compareTo by version descending      *)
(*   Impl="fixVersionMin"    -> fix B: fix A + age from the EARLIEST record *)
(*                              time among all newer trackers              *)
(*   Impl="fixHybrid"        -> fix C: fix B, but never older than the     *)
(*                              tracker's own first record (a token just   *)
(*                              returned keeps a full window)              *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Req, MaxV, MaxT, MaxAge, Impl

Versions == 1..MaxV
Max(S) == CHOOSE x \in S : \A y \in S : y <= x
Min(S) == CHOOSE x \in S : \A y \in S : x <= y

VARIABLES cur, clock, acq, tracked, rt,
          pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst, lateRecord

vars == <<cur, clock, acq, tracked, rt, pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst, lateRecord>>

Init ==
  /\ cur = 1
  /\ clock = 1
  /\ acq = [r \in Req |-> 0]
  /\ tracked = {}
  /\ rt = [v \in Versions |-> 0]
  /\ pruned_once = FALSE
  /\ lTracked = {} /\ lRt = [v \in Versions |-> 0] /\ lPruned = {}
  /\ lNow = 0 /\ lM = 0 /\ lFirst = 0
  /\ lateRecord = FALSE

Refresh ==
  /\ cur < MaxV
  /\ cur' = cur + 1
  /\ UNCHANGED <<clock, acq, tracked, rt, pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst, lateRecord>>

Acquire(r) ==
  /\ acq[r] = 0
  /\ acq' = [acq EXCEPT ![r] = cur]
  /\ UNCHANGED <<cur, clock, tracked, rt, pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst, lateRecord>>

\* record + release of the request; a new tracker only if the version is untracked
Record(r) ==
  /\ acq[r] # 0
  /\ clock < MaxT
  /\ LET v == acq[r] IN
       IF v \in tracked
         THEN /\ UNCHANGED <<clock, tracked, rt, lateRecord>>
         ELSE /\ clock' = clock + 1
              /\ tracked' = tracked \cup {v}
              /\ rt' = [rt EXCEPT ![v] = clock + 1]
              /\ lateRecord' = (lateRecord \/ (\E u \in tracked : u > v))
  /\ acq' = [acq EXCEPT ![r] = 0]
  /\ UNCHANGED <<cur, pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst>>

Tick ==
  /\ clock < MaxT
  /\ clock' = clock + 1
  /\ UNCHANGED <<cur, acq, tracked, rt, pruned_once, lTracked, lRt, lPruned, lNow, lM, lFirst, lateRecord>>

\* sort key: larger key = earlier in the list passed to the Pruner
Key(v) == IF Impl = "stock" THEN rt[v] ELSE v
Rank(v) == 1 + Cardinality({u \in tracked : Key(u) > Key(v)})
N == Cardinality(tracked)
Ord(i) == CHOOSE v \in tracked : Rank(v) = i
Age(i) ==
  IF i = 1 THEN 0
  ELSE IF Impl = "fixVersionMin"
         THEN clock - Min({rt[Ord(j)] : j \in 1..(i-1)})
  ELSE IF Impl = "fixHybrid"
         THEN clock - Max({rt[Ord(i)], Min({rt[Ord(j)] : j \in 1..(i-1)})})
         ELSE clock - rt[Ord(i-1)]

Prune(m) ==
  /\ tracked # {}
  /\ LET P == {Ord(i) : i \in {j \in 1..N : Age(j) > m}} IN
       /\ tracked' = tracked \ P
       /\ lPruned' = P
  /\ pruned_once' = TRUE
  /\ lTracked' = tracked /\ lRt' = rt /\ lNow' = clock /\ lM' = m /\ lFirst' = Ord(1)
  /\ UNCHANGED <<cur, clock, acq, rt, lateRecord>>

Next ==
  \/ Refresh \/ Tick
  \/ \E r \in Req : Acquire(r) \/ Record(r)
  \/ \E m \in 0..MaxAge : Prune(m)

Spec == Init /\ [][Next]_vars

TypeOK ==
  /\ cur \in Versions /\ clock \in 1..MaxT
  /\ tracked \subseteq Versions
  /\ \A v \in tracked : rt[v] \in 1..MaxT

\* distinct record times among tracked versions (so the stock sort has no ties)
DistinctRt == \A u, v \in tracked : u # v => rt[u] # rt[v]

\* prune javadoc :228-229 "passed to the Pruner in sorted (newest to oldest IndexSearcher) order"
NewestFirst == pruned_once => lFirst = Max(lTracked)

\* PruneByAge javadoc :207-208 "drops any searcher older by more than the specified seconds,
\* than the newest searcher": the newest tracked searcher is never dropped
LiveKept == pruned_once => Max(lTracked) \notin lPruned

\* Pruner.doPrune ageSec :201 "how much time has passed since this searcher was the current (live)
\* searcher": a kept older searcher was superseded (a newer version was recorded) at most m ago
RetentionBound ==
  pruned_once =>
    \A v \in lTracked \ lPruned :
      (\E u \in lTracked : u > v) =>
         lNow - Min({lRt[u] : u \in {w \in lTracked : w > v}}) <= lM

\* a searcher is pruned only after maxAge has passed since its first record (the token returned by
\* that record keeps a full follow-on window); the stock order has this property for first records
TokenWindow == pruned_once => \A v \in lPruned : lNow - lRt[v] > lM

\* fix C's retention: a kept older searcher was superseded, and first recorded, at most m ago
RetentionBoundOwn ==
  pruned_once =>
    \A v \in lTracked \ lPruned :
      (\E u \in lTracked : u > v) =>
         lNow - Max({lRt[v], Min({lRt[u] : u \in {w \in lTracked : w > v}})}) <= lM

\* vacuity probes (must be violated)
ProbeNoPrune == ~(pruned_once /\ lPruned # {})
ProbeNoLateRecord == ~lateRecord
=============================================================================

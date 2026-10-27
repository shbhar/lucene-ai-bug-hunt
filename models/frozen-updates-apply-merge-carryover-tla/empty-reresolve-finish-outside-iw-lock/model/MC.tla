---- MODULE MC ----
EXTENDS FrozenApplyMerge, TLC
\* 3 docs in 3 initial segments; doc 4 is flushed during the run into s4
MCSegOf == (1 :> "s1") @@ (2 :> "s2") @@ (3 :> "s3") @@ (4 :> "s4")
MCSegOf3 == (1 :> "s1") @@ (2 :> "s2") @@ (3 :> "s3")
MCSegOf2 == (1 :> "s1") @@ (2 :> "s2")
MCKeyShared == (1 :> "a") @@ (2 :> "a") @@ (3 :> "b") @@ (4 :> "a")
MCKeyShared3 == (1 :> "a") @@ (2 :> "a") @@ (3 :> "b")
MCKeyDistinct3 == (1 :> "a") @@ (2 :> "b") @@ (3 :> "a")
MCKey2 == (1 :> "a") @@ (2 :> "b")
\* merge s1+s2 -> m, then m+s3 -> m2 (cascade while m still carries pending updates)
PlanCascade == << [src |-> {"s1", "s2"}, dst |-> "m"], [src |-> {"m", "s3"}, dst |-> "m2"] >>
\* merge s1+s2 -> m, then m+s4 (a segment flushed during the run) -> m2
PlanFlushed == << [src |-> {"s1", "s2"}, dst |-> "m"], [src |-> {"m", "s4"}, dst |-> "m2"] >>
\* merge s1+s2 -> m only
PlanOne == << [src |-> {"s1", "s2"}, dst |-> "m"] >>
\* merge s1+s4 -> m first (waitApplyForMerge must wait for the packets older than s4)
PlanFlushFirst == << [src |-> {"s1", "s4"}, dst |-> "m"], [src |-> {"m", "s2"}, dst |-> "m2"] >>
\* scenario (b): merge s1+s2 -> m (may end 100% deleted and dropped), then merge the survivors s3+s4
PlanDropThen == << [src |-> {"s1", "s2"}, dst |-> "m"], [src |-> {"s3", "s4"}, dst |-> "m2"] >>
MCKeyDrop == (1 :> "a") @@ (2 :> "a") @@ (3 :> "b") @@ (4 :> "b")
\* minimal shape for the empty-iteration window: m (s1+s2) dropped, then s3 merged alone
PlanDropThenS3 == << [src |-> {"s1", "s2"}, dst |-> "m"], [src |-> {"s3"}, dst |-> "m2"] >>
====

---------------------------- MODULE ParallelMatcherLifecycle ----------------------------
(***************************************************************************)
(* Unit executor-pluggable-behaviour-sweep. Lifecycle of ParallelMatcher's worker tasks on a pluggable   *)
(* ThreadPoolExecutor, run with TLC deadlock checking ON: every reachable  *)
(* state with no enabled action must be a clean end state (caller returned *)
(* or threw AND every accepted worker finished). A reported deadlock is a  *)
(* caller blocked forever or a worker left blocked forever.                *)
(*                                                                         *)
(* Model element -> Java (lucene/monitor/.../ParallelMatcher.java @a9779ef) *)
(*   Submit / Reject / CallerRuns  -> ctor :69-72  executor.submit(mw)      *)
(*       (pool: ThreadPoolExecutor with P threads, queue capacity Q,       *)
(*        AbortPolicy -> RejectedExecutionException, CallerRunsPolicy ->   *)
(*        rejected task runs on the submitting thread)                     *)
(*   Produce                       -> matchQuery :80 queue.put(task)        *)
(*   FailAfterCreate               -> Monitor.match :222-225: an exception  *)
(*       thrown by queryIndex.search between createMatcher and finish      *)
(*   FinishPut                     -> doFinish :94-96 queue.put(END) x W    *)
(*   FinishGet                     -> doFinish :98-99 future.get()          *)
(*   WorkerTake                    -> MatcherWorker.call :127 queue.take()  *)
(*   Fix (mutant of a candidate fix): on any failure path the caller puts  *)
(*       one END per accepted worker (releases them); it does not change   *)
(*       CallerRuns, where the worker runs inside the submit call itself.  *)
(* Internal queue: LinkedBlockingQueue(1024); capacity CAP here (>= NT+W   *)
(* so puts never block in these scopes).                                   *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS W,        \* threads argument of ParallelMatcher.factory
          P,        \* pool threads
          Q,        \* pool queue capacity (0 = SynchronousQueue)
          NT,       \* candidate queries matched
          Policy,   \* "Abort" | "CallerRuns"
          FailAfterCreate, \* BOOLEAN
          Fix       \* BOOLEAN

Workers == 1..W

VARIABLES pc,        \* caller: "submit","produce","finishPut","finishGet","returned","threw","inWorker"
          submitted, \* workers handed to the executor so far
          wst,       \* worker state: "none","queued","pool","caller","done","rejected"
          poolQ,     \* pool queue (Seq of worker ids)
          q,         \* ParallelMatcher internal queue (Seq of "T"/"E")
          produced,  \* tasks put so far
          ends       \* END markers put so far

vars == <<pc, submitted, wst, poolQ, q, produced, ends>>

busyThreads == Cardinality({w \in Workers : wst[w] = "pool"})
accepted == {w \in Workers : wst[w] \in {"queued","pool","caller","done"}}

Init == /\ pc = "submit" /\ submitted = 0 /\ wst = [w \in Workers |-> "none"]
        /\ poolQ = <<>> /\ q = <<>> /\ produced = 0 /\ ends = 0

\* caller-side failure path (exception escapes the ctor or Monitor.match)
FailPath(newWst) ==
  /\ pc' = "threw"
  /\ (IF Fix
        THEN q' = q \o [i \in 1..Cardinality({w \in Workers : newWst[w] \in {"queued","pool","caller"}}) |-> "E"]
        ELSE q' = q)

Submit ==
  /\ pc = "submit" /\ submitted < W
  /\ LET w == submitted + 1 IN
     \/ /\ busyThreads < P
        /\ wst' = [wst EXCEPT ![w] = "pool"] /\ submitted' = w
        /\ UNCHANGED <<pc, poolQ, q, produced, ends>>
     \/ /\ busyThreads = P /\ Len(poolQ) < Q
        /\ wst' = [wst EXCEPT ![w] = "queued"] /\ poolQ' = Append(poolQ, w) /\ submitted' = w
        /\ UNCHANGED <<pc, q, produced, ends>>
     \/ /\ busyThreads = P /\ Len(poolQ) = Q /\ Policy = "Abort"
        /\ wst' = [wst EXCEPT ![w] = "rejected"] /\ submitted' = w
        /\ FailPath(wst')
        /\ UNCHANGED <<poolQ, produced, ends>>
     \/ /\ busyThreads = P /\ Len(poolQ) = Q /\ Policy = "CallerRuns"
        /\ wst' = [wst EXCEPT ![w] = "caller"] /\ submitted' = w /\ pc' = "inWorker"
        /\ UNCHANGED <<poolQ, q, produced, ends>>

SubmitDone == pc = "submit" /\ submitted = W /\ pc' = "produce"
              /\ UNCHANGED <<submitted, wst, poolQ, q, produced, ends>>

Produce == pc = "produce" /\ produced < NT /\ q' = Append(q, "T") /\ produced' = produced + 1
           /\ UNCHANGED <<pc, submitted, wst, poolQ, ends>>

ProduceDone == pc = "produce" /\ produced = NT
  /\ (IF FailAfterCreate THEN FailPath(wst) ELSE (pc' = "finishPut" /\ q' = q))
  /\ UNCHANGED <<submitted, wst, poolQ, produced, ends>>

FinishPut == pc = "finishPut"
  /\ (IF ends < W THEN (q' = Append(q, "E") /\ ends' = ends + 1 /\ pc' = pc)
                  ELSE (pc' = "finishGet" /\ q' = q /\ ends' = ends))
  /\ UNCHANGED <<submitted, wst, poolQ, produced>>

FinishGet == pc = "finishGet" /\ \A w \in Workers : wst[w] \in {"done","rejected"}
  /\ pc' = "returned" /\ UNCHANGED <<submitted, wst, poolQ, q, produced, ends>>

\* a running worker takes the head of the internal queue (blocks when empty)
WorkerTake(w) ==
  /\ wst[w] \in {"pool","caller"} /\ Len(q) > 0
  /\ q' = Tail(q)
  /\ (IF Head(q) = "T"
        THEN UNCHANGED <<wst, poolQ, pc>>
        ELSE \* END: worker returns; its pool thread picks the next queued worker
             (IF wst[w] = "pool" /\ Len(poolQ) > 0
                THEN (/\ wst' = [wst EXCEPT ![w] = "done", ![Head(poolQ)] = "pool"]
                      /\ poolQ' = Tail(poolQ) /\ pc' = pc)
                ELSE (/\ wst' = [wst EXCEPT ![w] = "done"] /\ poolQ' = poolQ
                      /\ pc' = (IF wst[w] = "caller" THEN "submit" ELSE pc))))
  /\ UNCHANGED <<submitted, produced, ends>>

CleanEnd == pc \in {"returned","threw"} /\ \A w \in Workers : wst[w] \in {"none","done","rejected"}

Next == \/ Submit \/ SubmitDone \/ Produce \/ ProduceDone \/ FinishPut \/ FinishGet
        \/ \E w \in Workers : WorkerTake(w)
        \/ (CleanEnd /\ UNCHANGED vars)   \* clean end states may stutter; anything else stuck = deadlock

Spec == Init /\ [][Next]_vars

TypeOK == pc \in {"submit","produce","finishPut","finishGet","returned","threw","inWorker"}
          /\ wst \in [Workers -> {"none","queued","pool","caller","done","rejected"}]
=============================================================================

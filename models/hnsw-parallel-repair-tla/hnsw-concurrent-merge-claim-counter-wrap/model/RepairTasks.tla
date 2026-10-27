------------------------------ MODULE RepairTasks ------------------------------
(***************************************************************************)
(* Task protocol of the parallel HNSW merge (Lucene main @ ea57360912ed):  *)
(* per-level repair tasks that claim batches of disconnected nodes through *)
(* an AtomicInteger, one TaskExecutor.invokeAll per level (the barrier),   *)
(* abort propagation through the abort check and invokeAll's cancel, the   *)
(* serial rebalance, then the insertion phase (one task per worker that    *)
(* claims batches of ordinals through a second AtomicInteger).             *)
(*                                                                         *)
(* The graph mutations inside one node's repair are NOT modelled here     *)
(* (the study's HnswConc.tla models them exactly and found the lock        *)
(* protocol safe); this model abstracts one node's repair to one atomic    *)
(* "process" step, which is sound for the properties checked below        *)
(* because those are about which nodes are processed, when, by how many    *)
(* tasks, and what is still running when invokeAll / build return.        *)
(*                                                                         *)
(* Java int arithmetic is modelled exactly at a reduced width IntBits      *)
(* (two's complement wrap); the 32-bit instance of the same arithmetic is  *)
(* checked separately with Z3 (../smt/q_claim_wrap.py).                    *)
(*                                                                         *)
(* Model element -> Java (all paths under lucene/core/src/java/org/apache/  *)
(* lucene/):                                                               *)
(*  RepairLevel loop, top down        util/hnsw/HnswConcurrentMergeBuilder.java:170-201 *)
(*  taskCount = min(W, ceilDiv(total,B))                     ...:179       *)
(*  repairProgress.getAndAdd(B), from < total, length        ...:188-189   *)
(*  fixDisconnectedNodes: maybeAbort() per node    util/hnsw/HnswGraphBuilder.java:725-730 *)
(*  invokeAll: fork count-1 'work' runnables  search/TaskExecutor.java:83-96 *)
(*  executor rejection -> run on caller                 TaskExecutor.java:56-66 *)
(*  caller loop over taskId                             TaskExecutor.java:101-108 *)
(*  collectResults: get() each future in order          TaskExecutor.java:112-128 *)
(*  Task.run CAS startedOrCancelled                     TaskExecutor.java:161-166 *)
(*  Task.setException then cancelAll                    TaskExecutor.java:168-172 *)
(*  Task.cancel CAS, set(null)                          TaskExecutor.java:174-189 *)
(*  rebalanceGraph() (serial, polls the abort check)  util/hnsw/InitializedHnswGraphBuilder.java:435-485 *)
(*  insertion: workers[i].run(maxOrd) via invokeAll   HnswConcurrentMergeBuilder.java:135-144 *)
(*  getStartPos: workProgress.getAndAdd(batchSize), start < maxOrd  ...:305-312 *)
(*  run loop: end = min(maxOrd, start+batch); addVectors   ...:294-302   *)
(*  addGraphNode skips initialized nodes; else polls abort  ...:315-320, HnswGraphBuilder.java:305-307 *)
(*                                                                         *)
(* Mutants (vacuity evidence): MutFutureCancel = plain FutureTask.cancel   *)
(* (a running task is marked cancelled, get() throws CancellationException *)
(* at once); MutNoBarrier = the caller does not wait for the level's      *)
(* futures; MutLe = "from <= total" (an equivalent mutant: the batch is   *)
(* then empty); MutNoMin = length = B without the Math.min.               *)
(***************************************************************************)
EXTENDS Naturals, Integers, Sequences, FiniteSets, TLC

CONSTANTS
  W,          \* number of merge workers (numMergeWorkers)
  Totals,     \* sequence: Totals[l] = #flagged nodes at level l-1 (0 = null entry)
  B,          \* REPAIR_BATCH_SIZE
  MaxOrd,     \* maxOrd for the insertion phase
  B2,         \* insertion batchSize
  E,          \* executor threads (0 = executor rejects everything -> caller runs)
  AbortOn,    \* TRUE: the environment may raise the merge abort at any time
  IntBits,    \* width of Java int in this instance
  MutFutureCancel, MutNoBarrier, MutLe, MutNoMin

Half == 2^(IntBits - 1)
Wrap(x) == ((x + Half) % (2 * Half)) - Half          \* Java int wrap
CeilDiv(a, b) == (a + b - 1) \div b
Min(a, b) == IF a < b THEN a ELSE b
NL == Len(Totals)
Threads == 0..E                                        \* 0 = the merge (caller) thread
Phases == {"repair", "insert"}

\* invokeAll instances: one per non-null repair level (top down), then one for insertion
RepairLevels == [k \in 1..Cardinality({l \in 1..NL : Totals[l] > 0}) |->
                  CHOOSE l \in 1..NL : Totals[l] > 0 /\
                    Cardinality({m \in 1..NL : Totals[m] > 0 /\ m >= l}) = k]
NI == Len(RepairLevels) + 1
Insts == 1..NI
InstPhase(i) == IF i < NI THEN "repair" ELSE "insert"
InstLevel(i) == IF i < NI THEN RepairLevels[i] ELSE 0
Count(i) == IF i < NI THEN Min(W, CeilDiv(Totals[RepairLevels[i]], B)) ELSE W
Total(i) == IF i < NI THEN Totals[RepairLevels[i]] ELSE MaxOrd
Batch(i) == IF i < NI THEN B ELSE B2
MaxCount == W

VARIABLES
  abort,      \* merge aborted (sticky)
  mpc,        \* main control: "start","fork","loop","collect","rebal","done","failed"
  cur,        \* current invokeAll instance of the main thread
  forked,     \* work runnables submitted for cur
  cidx,       \* collectResults index
  cexc,       \* collectResults saw an exception
  taskId,     \* taskId[i] (TaskExecutor local AtomicInteger)
  prog,       \* prog[i] = repairProgress / workProgress
  fut,        \* fut[i][t] \in {"new","run","ok","exc","cnl"}
  soc,        \* soc[i][t] = startedOrCancelled
  pending,    \* pending[i][t]: setException done, cancelAll not yet
  queue,      \* executor work queue: sequence of instance ids
  tpc,        \* thread pc: "idle","claim","node","cnl"
  tinst, ttask, tpos, tend,  \* thread's current task and batch cursor
  tret,       \* caller only: where to return after running a task inline
  processed,  \* processed[l][n] for repair; processed[0][n] for insertion (n in 0..MaxOrd-1)
  err         \* set of error strings

vars == <<abort, mpc, cur, forked, cidx, cexc, taskId, prog, fut, soc, pending, queue,
          tpc, tinst, ttask, tpos, tend, tret, processed, err>>

NodeIdx(l) == IF l = 0 THEN 0..(MaxOrd - 1) ELSE 0..(Totals[l] - 1)

Init ==
  /\ abort = FALSE
  /\ mpc = IF NI = 1 THEN "rebal" ELSE "start"
  /\ cur = 1
  /\ forked = 0
  /\ cidx = 0
  /\ cexc = FALSE
  /\ taskId = [i \in Insts |-> 0]
  /\ prog = [i \in Insts |-> 0]
  /\ fut = [i \in Insts |-> [t \in 0..(MaxCount - 1) |-> "new"]]
  /\ soc = [i \in Insts |-> [t \in 0..(MaxCount - 1) |-> FALSE]]
  /\ pending = [i \in Insts |-> [t \in 0..(MaxCount - 1) |-> FALSE]]
  /\ queue = <<>>
  /\ tpc = [th \in Threads |-> "idle"]
  /\ tinst = [th \in Threads |-> 0]
  /\ ttask = [th \in Threads |-> 0]
  /\ tpos = [th \in Threads |-> 0]
  /\ tend = [th \in Threads |-> 0]
  /\ tret = "none"
  /\ processed = [l \in 0..NL |-> [n \in NodeIdx(l) |-> 0]]
  /\ err = {}

Done(i, t) == fut[i][t] \in {"ok", "exc", "cnl"}

\* ---- environment: abort can be raised at any time ----
RaiseAbort == AbortOn /\ abort = FALSE /\ abort' = TRUE /\ UNCHANGED <<mpc, cur, forked, cidx, cexc,
  taskId, prog, fut, soc, pending, queue, tpc, tinst, ttask, tpos, tend, tret, processed, err>>

\* ---- a thread starts running task t of instance i (after taking id = t) ----
\* Task.run: if startedOrCancelled.compareAndSet(false,true) -> run callable, else no-op
StartTask(th, i, t) ==
  IF soc[i][t] = FALSE
  THEN /\ soc' = [soc EXCEPT ![i][t] = TRUE]
       /\ fut' = [fut EXCEPT ![i][t] = "run"]
       /\ tpc' = [tpc EXCEPT ![th] = "claim"]
       /\ tinst' = [tinst EXCEPT ![th] = i]
       /\ ttask' = [ttask EXCEPT ![th] = t]
  ELSE /\ UNCHANGED <<soc, fut>>
       /\ tpc' = [tpc EXCEPT ![th] = "idle"]
       /\ UNCHANGED <<tinst, ttask>>

\* ---- claim a batch (repair: HnswConcurrentMergeBuilder.java:188-189; insert: 305-312, 298) ----
Claim(th) ==
  /\ tpc[th] = "claim"
  /\ LET i == tinst[th]
         t == ttask[th]
         from == prog[i]
         tot == Total(i)
         ok == IF MutLe /\ InstPhase(i) = "repair" THEN from <= tot ELSE from < tot
     IN /\ prog' = [prog EXCEPT ![i] = Wrap(prog[i] + Batch(i))]
        /\ IF ok
           THEN LET e == IF InstPhase(i) = "repair"
                         THEN IF MutNoMin THEN Wrap(from + Batch(i))
                              ELSE Wrap(from + Min(Batch(i), Wrap(tot - from)))
                         ELSE Min(tot, Wrap(from + Batch(i)))
                IN /\ tpos' = [tpos EXCEPT ![th] = from]
                   /\ tend' = [tend EXCEPT ![th] = e]
                   /\ tpc' = [tpc EXCEPT ![th] = "node"]
                   /\ UNCHANGED <<fut, err>>
           ELSE \* callable returns normally
                /\ fut' = [fut EXCEPT ![i][t] = "ok"]
                /\ tpc' = [tpc EXCEPT ![th] = IF th = 0 THEN "ret" ELSE "idle"]
                /\ UNCHANGED <<tpos, tend, err>>
  /\ UNCHANGED <<abort, mpc, cur, forked, cidx, cexc, taskId, soc, pending, queue, tinst, ttask,
                 tret, processed>>

\* ---- process one node (poll abort, then repair/insert it) ----
Node(th) ==
  /\ tpc[th] = "node"
  /\ LET i == tinst[th]
         t == ttask[th]
         p == tpos[th]
         l == InstLevel(i)
     IN IF p >= tend[th]
        THEN /\ tpc' = [tpc EXCEPT ![th] = "claim"]
             /\ UNCHANGED <<fut, pending, processed, tpos, err>>
        ELSE IF p < 0 \/ p >= Total(i)
        THEN \* IntsRef / addGraphNode with an out-of-range index: AIOOBE in Java
             /\ err' = err \cup {"index out of range: " \o ToString(p)}
             /\ fut' = [fut EXCEPT ![i][t] = "exc"]
             /\ pending' = [pending EXCEPT ![i][t] = TRUE]
             /\ tpc' = [tpc EXCEPT ![th] = "cnl"]
             /\ UNCHANGED <<processed, tpos>>
        ELSE IF abort
        THEN \* maybeAbort throws MergeAbortedException -> FutureTask.setException
             /\ fut' = [fut EXCEPT ![i][t] = "exc"]
             /\ pending' = [pending EXCEPT ![i][t] = TRUE]
             /\ tpc' = [tpc EXCEPT ![th] = "cnl"]
             /\ UNCHANGED <<processed, tpos, err>>
        ELSE /\ processed' = [processed EXCEPT ![l][p] = @ + 1]
             /\ tpos' = [tpos EXCEPT ![th] = p + 1]
             /\ UNCHANGED <<fut, pending, tpc, err>>
  /\ UNCHANGED <<abort, mpc, cur, forked, cidx, cexc, taskId, prog, soc, queue, tinst, ttask,
                 tend, tret>>

\* ---- Task.setException -> cancelAll(futures) (TaskExecutor.java:168-189) ----
CancelAll(th) ==
  /\ tpc[th] = "cnl"
  /\ LET i == tinst[th]
         t == ttask[th]
     IN /\ IF MutFutureCancel
           THEN \* plain FutureTask.cancel(false): marks NEW-or-running tasks cancelled
                fut' = [tt \in Insts |-> IF tt = i
                          THEN [k \in 0..(MaxCount-1) |-> IF k < Count(i) /\ fut[i][k] \in {"new","run"}
                                                         THEN "cnl" ELSE fut[i][k]]
                          ELSE fut[tt]]
           ELSE fut' = [tt \in Insts |-> IF tt = i
                          THEN [k \in 0..(MaxCount-1) |-> IF k < Count(i) /\ soc[i][k] = FALSE
                                                         THEN "cnl" ELSE fut[i][k]]
                          ELSE fut[tt]]
        /\ soc' = [soc EXCEPT ![i] = [k \in 0..(MaxCount-1) |-> IF k < Count(i) THEN TRUE ELSE soc[i][k]]]
        /\ pending' = [pending EXCEPT ![i][t] = FALSE]
        /\ tpc' = [tpc EXCEPT ![th] = IF th = 0 THEN "ret" ELSE "idle"]
        \* FutureTask.setException publishes the outcome before Lucene's cancelAll runs, so the
        \* caller may already have returned; then every future of the group must be done
        \* (cancelAll is a no-op), else a task could start after invokeAll returned
        /\ err' = IF mpc \in {"done", "failed"} /\ \E k \in 0..(Count(i)-1) : ~Done(i, k)
                  THEN err \cup {"cancelAll after return found a live future"} ELSE err
  /\ UNCHANGED <<abort, mpc, cur, forked, cidx, cexc, taskId, prog, queue, tinst, ttask, tpos, tend,
                 tret, processed>>

\* ---- executor thread: take a work runnable: id = taskId.getAndIncrement(); if id < count run ----
ExecTake(th) ==
  /\ th # 0
  /\ tpc[th] = "idle"
  /\ queue # <<>>
  /\ LET i == Head(queue)
         id == taskId[i]
     IN /\ queue' = Tail(queue)
        /\ taskId' = [taskId EXCEPT ![i] = id + 1]
        /\ IF id < Count(i)
           THEN StartTask(th, i, id)
           ELSE UNCHANGED <<soc, fut, tpc, tinst, ttask>>
  /\ UNCHANGED <<abort, mpc, cur, forked, cidx, cexc, prog, pending, tpos, tend, tret, processed, err>>

\* ---- main thread ----
MainStart ==
  /\ mpc = "start"
  /\ tpc[0] = "idle"
  /\ forked' = 0
  /\ cidx' = 0
  /\ cexc' = FALSE
  /\ mpc' = IF Count(cur) > 1 THEN "fork" ELSE "loop"
  /\ UNCHANGED <<abort, cur, taskId, prog, fut, soc, pending, queue, tpc, tinst, ttask, tpos, tend,
                 tret, processed, err>>

\* fork count-1 work runnables (TaskExecutor.java:89-96); with E = 0 the executor rejects and
\* the wrapper runs the runnable on the caller (TaskExecutor.java:56-66)
MainFork ==
  /\ mpc = "fork"
  /\ tpc[0] = "idle"
  /\ IF forked < Count(cur) - 1
     THEN /\ forked' = forked + 1
          /\ IF E > 0
             THEN /\ queue' = Append(queue, cur)
                  /\ UNCHANGED <<taskId, soc, fut, tpc, tinst, ttask, tret>>
             ELSE LET id == taskId[cur]
                  IN /\ taskId' = [taskId EXCEPT ![cur] = id + 1]
                     /\ tret' = "fork"
                     /\ IF id < Count(cur)
                        THEN StartTask(0, cur, id)
                        ELSE UNCHANGED <<soc, fut, tpc, tinst, ttask>>
                     /\ UNCHANGED queue
          /\ UNCHANGED mpc
     ELSE /\ mpc' = "loop"
          /\ UNCHANGED <<forked, queue, taskId, soc, fut, tpc, tinst, ttask, tret>>
  /\ UNCHANGED <<abort, cur, cidx, cexc, prog, pending, tpos, tend, processed, err>>

\* caller loop (TaskExecutor.java:101-108)
MainLoop ==
  /\ mpc = "loop"
  /\ tpc[0] = "idle"
  /\ LET id == taskId[cur]
     IN /\ taskId' = [taskId EXCEPT ![cur] = id + 1]
        /\ IF id < Count(cur)
           THEN /\ StartTask(0, cur, id)
                /\ tret' = IF id >= Count(cur) - 1 THEN "collect" ELSE "loop"
                /\ UNCHANGED mpc
           ELSE /\ mpc' = "collect"
                /\ UNCHANGED <<soc, fut, tpc, tinst, ttask, tret>>
  /\ UNCHANGED <<abort, cur, forked, cidx, cexc, prog, pending, queue, tpos, tend, processed, err>>

\* return from a task the caller ran inline
MainRet ==
  /\ tpc[0] = "ret"
  /\ tpc' = [tpc EXCEPT ![0] = "idle"]
  /\ mpc' = IF tret = "collect" THEN "collect" ELSE mpc
  /\ UNCHANGED <<abort, cur, forked, cidx, cexc, taskId, prog, fut, soc, pending, queue, tinst, ttask,
                 tpos, tend, tret, processed, err>>

\* collectResults: future.get() in order; blocks until done (TaskExecutor.java:112-128)
MainCollect ==
  /\ mpc = "collect"
  /\ tpc[0] = "idle"
  /\ IF cidx < Count(cur)
     THEN /\ (MutNoBarrier \/ Done(cur, cidx))
          /\ cidx' = cidx + 1
          /\ cexc' = (cexc \/ fut[cur][cidx] = "exc")
          /\ UNCHANGED <<mpc, cur, err>>
     ELSE \* assert assertAllFuturesCompleted(futures)
          /\ err' = IF \E k \in 0..(Count(cur)-1) : ~Done(cur, k) /\ ~MutNoBarrier
                    THEN err \cup {"invokeAll returned with a running task"} ELSE err
          /\ IF cexc
             THEN /\ mpc' = "failed" /\ UNCHANGED cur
             ELSE IF cur < NI - 1
                  THEN /\ cur' = cur + 1 /\ mpc' = "start"
                  ELSE IF cur = NI - 1
                       THEN /\ mpc' = "rebal" /\ UNCHANGED cur
                       ELSE /\ mpc' = "done" /\ UNCHANGED cur
          /\ UNCHANGED cidx /\ UNCHANGED cexc
  /\ UNCHANGED <<abort, forked, taskId, prog, fut, soc, pending, queue, tpc, tinst, ttask, tpos, tend,
                 tret, processed>>

\* serial rebalance (polls abort at least once when something is promoted; abstracted to one poll)
MainRebal ==
  /\ mpc = "rebal"
  /\ tpc[0] = "idle"
  /\ IF abort THEN mpc' = "failed" ELSE (mpc' = "start" /\ cur' = NI)
  /\ UNCHANGED <<abort, forked, cidx, cexc, taskId, prog, fut, soc, pending, queue, tpc, tinst, ttask,
                 tpos, tend, tret, processed, err>>
  /\ (abort => UNCHANGED cur)

\* the repair levels may all be null: then the build goes straight to rebalance/insert
Next ==
  \/ RaiseAbort
  \/ MainStart \/ MainFork \/ MainLoop \/ MainRet \/ MainCollect \/ MainRebal
  \/ \E th \in Threads : Claim(th) \/ Node(th) \/ CancelAll(th) \/ ExecTake(th)
  \/ (mpc \in {"done", "failed"} /\ \A th \in Threads : tpc[th] = "idle" /\ queue = <<>> /\ UNCHANGED vars)

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)
FairSpec == Init /\ [][Next]_vars
            /\ WF_vars(MainStart \/ MainFork \/ MainLoop \/ MainRet \/ MainCollect \/ MainRebal)
            /\ \A th \in Threads : WF_vars(Claim(th) \/ Node(th) \/ CancelAll(th) \/ ExecTake(th))

\* ------------------------------ properties ------------------------------
NoError == err = {}
AtMostOnce == \A l \in 0..NL : \A n \in NodeIdx(l) : processed[l][n] <= 1
ExactlyOnceOnSuccess ==
  mpc \in {"rebal", "done"} =>
    \A l \in 1..NL : \A n \in NodeIdx(l) : processed[l][n] = 1
InsertExactlyOnceOnSuccess == mpc = "done" => \A n \in NodeIdx(0) : processed[0][n] = 1
\* level barrier: while a thread works on a node of repair level l, every node of every
\* higher level has been processed exactly once
Barrier ==
  \A th \in Threads :
    (tpc[th] = "node" /\ InstPhase(tinst[th]) = "repair") =>
      \A l2 \in (InstLevel(tinst[th]) + 1)..NL : \A n \in NodeIdx(l2) : processed[l2][n] = 1
\* insertion starts only after all repair is complete
RepairBeforeInsert ==
  \A th \in Threads :
    (tpc[th] = "node" /\ InstPhase(tinst[th]) = "insert") =>
      \A l2 \in 1..NL : \A n \in NodeIdx(l2) : processed[l2][n] = 1
\* when build() returns or throws, no task of any instance is still running
\* (a thread may still be inside Task.setException -> cancelAll, which only touches futures;
\*  CancelAll checks that those are all done by then)
NoStraggler ==
  mpc \in {"done", "failed"} =>
    \A th \in Threads : tpc[th] \notin {"claim", "node"}
FailOnlyOnAbort == (mpc = "failed") => (abort \/ err # {})
Terminates == <>(mpc \in {"done", "failed"})
=============================================================================

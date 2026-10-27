------------------------------ MODULE TaskExec ------------------------------
(***************************************************************************)
(* Design-level model of org.apache.lucene.search.TaskExecutor (main      *)
(* 7cecf79523), lucene/core/src/java/org/apache/lucene/search/            *)
(* TaskExecutor.java, including nested invokeAll from inside a task,      *)
(* a bounded executor queue that rejects (-> run on the submitting        *)
(* thread, :57-67), task exceptions + cancelAll (:175-198), and an        *)
(* interrupt of the thread that called invokeAll (:121-125).              *)
(*                                                                         *)
(* Mapping:                                                                *)
(*  Submit      :91-101  executor.execute(work) x (count-1); rejected ->  *)
(*                       work.run() inline (:61-66)                        *)
(*  WorkerTake  :93-97   work: id = taskId.getAndIncrement(); run if <    *)
(*  Claim       :107-114 caller loop; break after the last id             *)
(*  Task.run    :169-173 startedOrCancelled CAS                            *)
(*  FinishTask  :175-179 setException -> cancelAll(futures)                *)
(*  cancel      :181-197 CAS; set(null) if not started                     *)
(*  Collect     :118-135 for each future in list order: get();             *)
(*                       InterruptedException -> remember, continue (!)    *)
(*                       assert all futures done (:130); rethrow first     *)
(*  FutureTask.get: blocks iff not done; throws InterruptedException if   *)
(*                  the interrupt flag is set while not done (JDK)         *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS
  NTasks,     \* NTasks[j] = number of callables of invocation j (invocation 1 = top level)
  Child,      \* Child[j][t] = invocation started by task t of invocation j from inside the task, or 0
  NumWorkers, \* executor threads
  QCap,       \* executor queue capacity; a full queue rejects (TaskExecutor runs the work inline)
  MayThrow,   \* set of <<j, t>> whose callable may throw
  Interrupts, \* number of interrupts that may be delivered to the top-level caller thread
  TEFix       \* TRUE: on InterruptedException remember it but keep waiting for the same future

Invs == DOMAIN NTasks
TasksOf(j) == 1..NTasks[j]
Caller == 0
Workers == 1..NumWorkers
Threads == {Caller} \cup Workers
Done == {"ok", "exc", "cancelled"}

VARIABLES
  tstate,    \* [j][t] -> "new" | "running" | "ok" | "exc" | "cancelled"
  taskId,    \* [j] -> next id to claim (AtomicInteger, 1-based)
  queue,     \* FIFO of work items (invocation ids)
  stk,       \* [thread] -> stack of frames
  intr,      \* [thread] -> interrupt flag
  ibudget,   \* remaining interrupts
  ret,       \* [j] -> "no" | "ok" | "exc"   (how invokeAll(j) completed)
  retRunning,\* [j] -> tasks of j still running when invokeAll(j) completed
  reported   \* [j] -> tasks whose exception invokeAll(j) observed

vars == <<tstate, taskId, queue, stk, intr, ibudget, ret, retRunning, reported>>

InvFrame(j) == [kind |-> "inv", inv |-> j, pc |-> "submit", r |-> IF NTasks[j] > 1 THEN NTasks[j] - 1 ELSE 0,
                k |-> 1, last |-> 0, iexc |-> FALSE]
TaskFrame(j, t) == [kind |-> "task", inv |-> j, t |-> t, phase |-> "start", cexc |-> FALSE]

Init ==
  /\ tstate = [j \in Invs |-> [t \in TasksOf(j) |-> "new"]]
  /\ taskId = [j \in Invs |-> 1]
  /\ queue = <<>>
  /\ stk = [th \in Threads |-> IF th = Caller THEN <<InvFrame(1)>> ELSE <<>>]
  /\ intr = [th \in Threads |-> FALSE]
  /\ ibudget = Interrupts
  /\ ret = [j \in Invs |-> "no"]
  /\ retRunning = [j \in Invs |-> {}]
  /\ reported = [j \in Invs |-> {}]

Top(th) == stk[th][Len(stk[th])]
SetTop(th, f) == stk' = [stk EXCEPT ![th] = [@ EXCEPT ![Len(@)] = f]]
Pop(s) == SubSeq(s, 1, Len(s) - 1)

\* claim id of invocation j and run it on thread th (Task.run CAS)
\* returns the new stack for th given the (already updated) caller frame f
RunOrSkip(th, j, id, f) ==
  IF id <= NTasks[j] /\ tstate[j][id] = "new"
  THEN /\ tstate' = [tstate EXCEPT ![j][id] = "running"]
       /\ stk' = [stk EXCEPT ![th] = Append([@ EXCEPT ![Len(@)] = f], TaskFrame(j, id))]
  ELSE /\ UNCHANGED tstate
       /\ stk' = [stk EXCEPT ![th] = [@ EXCEPT ![Len(@)] = f]]

\* ---------------- invokeAll frame ----------------
Submit(th) ==
  LET f == Top(th) IN
  /\ f.kind = "inv" /\ f.pc = "submit"
  /\ IF f.r = 0
     THEN /\ SetTop(th, [f EXCEPT !.pc = "claim"])
          /\ UNCHANGED <<tstate, taskId, queue>>
     ELSE IF Len(queue) < QCap
          THEN /\ queue' = Append(queue, f.inv)
               /\ SetTop(th, [f EXCEPT !.r = @ - 1])
               /\ UNCHANGED <<tstate, taskId>>
          ELSE \* RejectedExecutionException -> r.run() on the submitting thread
               /\ taskId' = [taskId EXCEPT ![f.inv] = @ + 1]
               /\ RunOrSkip(th, f.inv, taskId[f.inv], [f EXCEPT !.r = @ - 1])
               /\ UNCHANGED queue
  /\ UNCHANGED <<intr, ibudget, ret, retRunning, reported>>

Claim(th) ==
  LET f == Top(th)
      j == f.inv IN
  /\ f.kind = "inv" /\ f.pc = "claim"
  /\ taskId' = [taskId EXCEPT ![j] = @ + 1]
  /\ IF taskId[j] > NTasks[j]
     THEN /\ SetTop(th, [f EXCEPT !.pc = "collect"])
          /\ UNCHANGED tstate
     ELSE RunOrSkip(th, j, taskId[j],
                    [f EXCEPT !.pc = IF taskId[j] = NTasks[j] THEN "collect" ELSE "claim"])
  /\ UNCHANGED <<queue, intr, ibudget, ret, retRunning, reported>>

\* future.get() for future k of invocation j, in list order
Collect(th) ==
  LET f == Top(th)
      j == f.inv IN
  /\ f.kind = "inv" /\ f.pc = "collect" /\ f.k <= NTasks[j]
  /\ \/ /\ tstate[j][f.k] \in Done                       \* get() returns / throws ExecutionException
        /\ reported' = IF tstate[j][f.k] = "exc" THEN [reported EXCEPT ![j] = @ \cup {f.k}] ELSE reported
        /\ SetTop(th, [f EXCEPT !.k = @ + 1, !.iexc = @ \/ tstate[j][f.k] = "exc"])
        /\ UNCHANGED intr
     \/ /\ tstate[j][f.k] \notin Done /\ intr[th]          \* InterruptedException: remembered, loop goes on
        /\ intr' = [intr EXCEPT ![th] = FALSE]
        /\ SetTop(th, [f EXCEPT !.k = IF TEFix THEN @ ELSE @ + 1, !.iexc = TRUE])
        /\ UNCHANGED reported
     \* otherwise: blocked in FutureTask.awaitDone
  /\ UNCHANGED <<tstate, taskId, queue, ibudget, ret, retRunning>>

\* end of collectResults: return or throw; pop the frame
Return(th) ==
  LET f == Top(th)
      j == f.inv
      s == Pop(stk[th]) IN
  /\ f.kind = "inv" /\ f.pc = "collect" /\ f.k > NTasks[j]
  /\ ret' = [ret EXCEPT ![j] = IF f.iexc THEN "exc" ELSE "ok"]
  /\ retRunning' = [retRunning EXCEPT ![j] = {t \in TasksOf(j) : tstate[j][t] = "running"}]
  /\ stk' = [stk EXCEPT ![th] =
               IF s = <<>> THEN s
               ELSE [s EXCEPT ![Len(s)] = [@ EXCEPT !.phase = "after", !.cexc = f.iexc]]]
  /\ UNCHANGED <<tstate, taskId, queue, intr, ibudget, reported>>

\* ---------------- task frame ----------------
TaskStart(th) ==
  LET f == Top(th) IN
  /\ f.kind = "task" /\ f.phase = "start"
  /\ Child[f.inv][f.t] # 0
  /\ stk' = [stk EXCEPT ![th] = Append([@ EXCEPT ![Len(@)] = [f EXCEPT !.phase = "nested"]],
                                       InvFrame(Child[f.inv][f.t]))]
  /\ UNCHANGED <<tstate, taskId, queue, intr, ibudget, ret, retRunning, reported>>

FinishTask(th) ==
  LET f == Top(th)
      j == f.inv
      t == f.t IN
  /\ f.kind = "task"
  /\ \/ f.phase = "after"
     \/ f.phase = "start" /\ Child[j][t] = 0
  /\ \E exc \in (IF f.cexc \/ <<j, t>> \in MayThrow THEN {f.cexc, TRUE} ELSE {FALSE}) :
       \* a nested invokeAll that threw propagates (f.cexc forces exc)
       LET e == exc \/ f.cexc IN
       tstate' = [tstate EXCEPT ![j] =
                    [u \in TasksOf(j) |-> IF u = t THEN (IF e THEN "exc" ELSE "ok")
                                          ELSE IF e /\ tstate[j][u] = "new" THEN "cancelled"
                                          ELSE tstate[j][u]]]
  /\ stk' = [stk EXCEPT ![th] = Pop(@)]
  /\ UNCHANGED <<taskId, queue, intr, ibudget, ret, retRunning, reported>>

\* ---------------- executor workers ----------------
WorkerTake(w) ==
  /\ stk[w] = <<>>
  /\ queue # <<>>
  /\ LET j == Head(queue) IN
     /\ queue' = Tail(queue)
     /\ taskId' = [taskId EXCEPT ![j] = @ + 1]
     /\ LET id == taskId[j] IN
        IF id <= NTasks[j] /\ tstate[j][id] = "new"
        THEN /\ tstate' = [tstate EXCEPT ![j][id] = "running"]
             /\ stk' = [stk EXCEPT ![w] = <<TaskFrame(j, id)>>]
        ELSE UNCHANGED <<tstate, stk>>
  /\ UNCHANGED <<intr, ibudget, ret, retRunning, reported>>

\* Thread.interrupt() of the application thread that called the top-level invokeAll
Interrupt ==
  /\ ibudget > 0
  /\ ret[1] = "no"
  /\ ibudget' = ibudget - 1
  /\ intr' = [intr EXCEPT ![Caller] = TRUE]
  /\ UNCHANGED <<tstate, taskId, queue, stk, ret, retRunning, reported>>

Finished == stk[Caller] = <<>> /\ \A w \in Workers : stk[w] = <<>>
Terminate == Finished /\ queue = <<>> /\ UNCHANGED vars

Next ==
  \/ \E th \in Threads : stk[th] # <<>> /\
        (Submit(th) \/ Claim(th) \/ Collect(th) \/ Return(th) \/ TaskStart(th) \/ FinishTask(th))
  \/ \E w \in Workers : WorkerTake(w)
  \/ Interrupt
  \/ Terminate

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

(* ---------------- properties ---------------- *)
\* TaskExecutor.java:130 + javadoc (:71-80) + the intent stated at :184-189:
\* invokeAll never completes while one of its tasks is still running
NoRunningAfterReturn == \A j \in Invs : ret[j] # "no" => retRunning[j] = {}

\* every task exception is surfaced by its invokeAll (thrown or suppressed), and an invokeAll
\* that returned normally had no failing task
NoLostException ==
  \A j \in Invs : ret[j] # "no" =>
     /\ \A t \in TasksOf(j) : tstate[j][t] = "exc" => t \in reported[j]
     /\ (ret[j] = "ok" => \A t \in TasksOf(j) : tstate[j][t] # "exc")

\* an invokeAll that returned normally ran every callable (none cancelled, none skipped)
OkMeansAllRan ==
  \A j \in Invs : ret[j] = "ok" => \A t \in TasksOf(j) : tstate[j][t] = "ok"

TopLevelReturns == <>(ret[1] # "no")
=============================================================================

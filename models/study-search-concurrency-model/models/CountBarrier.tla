---------------------------- MODULE CountBarrier ----------------------------
(***************************************************************************)
(* Design-level model of IndexSearcher.count(Query) /                      *)
(* IndexSearcher.search(Query, TotalHitCountCollectorManager) when the     *)
(* searcher uses intra-segment partitions, executed by                     *)
(* TaskExecutor.invokeAll.  Lucene main 7cecf79523.                        *)
(*                                                                         *)
(* Mapping (file:line, all under lucene/core/src/java/org/apache/lucene/): *)
(*  emap            TotalHitCountCollectorManager.java:75 earlyTerminatedMap*)
(*  Register        :110-115  get + putIfAbsent of a new CompletableFuture *)
(*  CountOutcome    :119-125  super.getLeafCollector -> Weight.count(ctx)  *)
(*                  TotalHitCountCollector.java:48-53                      *)
(*                  "count"  : count != -1 -> += whole-leaf count, CTE     *)
(*                  "nocount": count == -1 -> collect own partition        *)
(*                  "throw"  : count() throws (IOException, Runtime...)    *)
(*                             -> future is NEVER completed (no finally)   *)
(*  WaitFuture      :130-133  earlyTerminated.get() (blocks, no timeout)   *)
(*  Reduce          :86-99    clears the map, sums collectors              *)
(*  Submit          TaskExecutor.java:91-101  fork count-1 work items      *)
(*  WorkerTake      TaskExecutor.java:93-97   taskId.getAndIncrement       *)
(*  CallerClaim     TaskExecutor.java:107-114 caller runs tasks itself     *)
(*  FinishExc       TaskExecutor.java:175-179 setException -> cancelAll    *)
(*                  :181-197 cancel() only affects not-yet-started tasks   *)
(*  Collect         TaskExecutor.java:118-135 future.get() in list order   *)
(*  slices/tasks    IndexSearcher.java:760-793 one task per LeafSlice      *)
(*  partitions      IndexSearcher.java:801-808 partitions in slice order   *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS
  Plan,        \* Plan[s] = <<[leaf |-> l, m |-> matches], ...>> partitions of slice s, in order
  NumWorkers,  \* executor pool size (>= 0)
  Outcomes,    \* Outcomes[q][l] subset of {"count","nocount","throw"} for search q, leaf l
  NumSearches, \* sequential searches that reuse the same collector manager
  Fix          \* "none" (main) | "exc" (completeExceptionally) | "excremove" (+ remove the entry)
               \* | "perweight" (completeExceptionally + map keyed by (Weight, leaf): fresh per search)

NT == Len(Plan)
Tasks == 1..NT
Workers == 1..NumWorkers
CallerId == NumWorkers + 1   \* runner id of the thread that called invokeAll
Parts == UNION { { <<s, j>> : j \in DOMAIN Plan[s] } : s \in Tasks }
Leaves == { Plan[p[1]][p[2]].leaf : p \in Parts }

RECURSIVE SumParts(_)
SumParts(S) == IF S = {} THEN 0
               ELSE LET x == CHOOSE x \in S : TRUE
                    IN Plan[x[1]][x[2]].m + SumParts(S \ {x})
PartsOf(l) == { p \in Parts : Plan[p[1]][p[2]].leaf = l }
LeafMatches(l) == SumParts(PartsOf(l))
TrueCount == SumParts(Parts)
RECURSIVE SumF(_, _)
SumF(f, X) == IF X = {} THEN 0 ELSE LET x == CHOOSE x \in X : TRUE IN f[x] + SumF(f, X \ {x})
\* TotalHitCountCollectorManager.hasSegmentPartitions (:52-62)
HasPartitions == \E l \in Leaves : Cardinality(PartsOf(l)) > 1

Done == {"ok", "exc", "cancelled"}

VARIABLES
  q,        \* current search (1..NumSearches)
  emap,     \* leaf -> "absent" | "pending" | "true" | "false"
  tstate,   \* task -> "new" | "running" | "ok" | "exc" | "cancelled"
  tpos,     \* task -> index of the partition being searched
  tcnt,     \* task -> TotalHitCountCollector.totalHits
  treg,     \* task -> TRUE iff it registered the future for its current leaf and must call count()
  runner,   \* task -> 0 (not started) | worker id | CallerId
  taskId,   \* TaskExecutor's AtomicInteger (1-based)
  queue,    \* pending work items in the executor queue
  wrun,     \* worker -> task (0 = idle)
  cpc,      \* caller pc
  ck,       \* collect loop index
  results   \* sequence of [exc |-> BOOLEAN, count |-> Nat]

vars == <<q, emap, tstate, tpos, tcnt, treg, runner, taskId, queue, wrun, cpc, ck, results>>

Init ==
  /\ q = 1
  /\ emap = [l \in Leaves |-> "absent"]
  /\ tstate = [t \in Tasks |-> "new"]
  /\ tpos = [t \in Tasks |-> 1]
  /\ tcnt = [t \in Tasks |-> 0]
  /\ treg = [t \in Tasks |-> FALSE]
  /\ runner = [t \in Tasks |-> 0]
  /\ taskId = 1
  /\ queue = 0
  /\ wrun = [w \in Workers |-> 0]
  /\ cpc = "submit"
  /\ ck = 1
  /\ results = <<>>

\* the thread that ran task t becomes free (TaskExecutor.java:96 / :109-113)
FreeRunner(t) ==
  IF runner[t] = CallerId
  THEN /\ cpc' = IF t = NT THEN "collect" ELSE "claim"
       /\ UNCHANGED wrun
  ELSE /\ wrun' = [wrun EXCEPT ![runner[t]] = 0]
       /\ UNCHANGED cpc

FinishOk(t) ==
  /\ tstate' = [tstate EXCEPT ![t] = "ok"]
  /\ FreeRunner(t)

\* FutureTask.setException then cancelAll: tasks not started yet become cancelled (set(null))
FinishExc(t) ==
  /\ tstate' = [u \in Tasks |-> IF u = t THEN "exc"
                               ELSE IF tstate[u] = "new" THEN "cancelled" ELSE tstate[u]]
  /\ FreeRunner(t)

Submit ==
  /\ cpc = "submit"
  /\ queue' = IF NT > 1 THEN NT - 1 ELSE 0
  /\ cpc' = "claim"
  /\ UNCHANGED <<q, emap, tstate, tpos, tcnt, treg, runner, taskId, wrun, ck, results>>

CallerClaim ==
  /\ cpc = "claim"
  /\ taskId' = taskId + 1
  /\ IF taskId > NT
     THEN /\ cpc' = "collect" /\ UNCHANGED <<tstate, runner>>
     ELSE IF tstate[taskId] = "new"
          THEN /\ tstate' = [tstate EXCEPT ![taskId] = "running"]
               /\ runner' = [runner EXCEPT ![taskId] = CallerId]
               /\ cpc' = "run"
          ELSE /\ cpc' = IF taskId = NT THEN "collect" ELSE "claim"
               /\ UNCHANGED <<tstate, runner>>
  /\ UNCHANGED <<q, emap, tpos, tcnt, treg, queue, wrun, ck, results>>

WorkerTake(w) ==
  /\ wrun[w] = 0
  /\ queue > 0
  /\ queue' = queue - 1
  /\ taskId' = taskId + 1
  /\ IF taskId <= NT /\ tstate[taskId] = "new"
     THEN /\ tstate' = [tstate EXCEPT ![taskId] = "running"]
          /\ runner' = [runner EXCEPT ![taskId] = w]
          /\ wrun' = [wrun EXCEPT ![w] = taskId]
     ELSE UNCHANGED <<tstate, runner, wrun>>
  /\ UNCHANGED <<q, emap, tpos, tcnt, treg, cpc, ck, results>>

\* One step of the slice search of task t (IndexSearcher.search(partitions,..) / searchLeaf)
TaskStep(t) ==
  /\ tstate[t] = "running"
  /\ IF tpos[t] > Len(Plan[t])
     THEN /\ FinishOk(t)
          /\ UNCHANGED <<q, emap, tpos, tcnt, treg, runner, taskId, queue, ck, results>>
     ELSE LET l == Plan[t][tpos[t]].leaf
              m == Plan[t][tpos[t]].m IN
          IF ~HasPartitions
          THEN \* plain TotalHitCountCollector (TotalHitCountCollector.java:47-54)
               \E o \in Outcomes[q][l] :
                 IF o \in {"throw", "scorethrow"}
                 THEN /\ FinishExc(t)
                      /\ UNCHANGED <<q, emap, tpos, tcnt, treg, runner, taskId, queue, ck, results>>
                 ELSE /\ tcnt' = [tcnt EXCEPT ![t] = @ + (IF o = "count" THEN LeafMatches(l) ELSE m)]
                      /\ tpos' = [tpos EXCEPT ![t] = @ + 1]
                      /\ UNCHANGED <<q, emap, tstate, treg, runner, taskId, queue, wrun, cpc, ck, results>>
          ELSE IF treg[t]
          THEN \* first arriver: super.getLeafCollector(context) -> weight.count(context)
               \E o \in Outcomes[q][l] :
                 CASE o = "count" ->
                        /\ emap' = [emap EXCEPT ![l] = "true"]        \* complete(true); CTE
                        /\ tcnt' = [tcnt EXCEPT ![t] = @ + LeafMatches(l)]
                        /\ tpos' = [tpos EXCEPT ![t] = @ + 1]
                        /\ treg' = [treg EXCEPT ![t] = FALSE]
                        /\ UNCHANGED <<q, tstate, runner, taskId, queue, wrun, cpc, ck, results>>
                   [] o = "nocount" ->
                        /\ emap' = [emap EXCEPT ![l] = "false"]       \* complete(false); collect
                        /\ tcnt' = [tcnt EXCEPT ![t] = @ + m]
                        /\ tpos' = [tpos EXCEPT ![t] = @ + 1]
                        /\ treg' = [treg EXCEPT ![t] = FALSE]
                        /\ UNCHANGED <<q, tstate, runner, taskId, queue, wrun, cpc, ck, results>>
                   [] o = "scorethrow" ->  \* count()==-1: complete(false), then scoring throws
                        /\ emap' = [emap EXCEPT ![l] = "false"]
                        /\ treg' = [treg EXCEPT ![t] = FALSE]
                        /\ FinishExc(t)
                        /\ UNCHANGED <<q, tpos, tcnt, runner, taskId, queue, ck, results>>
                   [] o = "throw" ->       \* main: future left incomplete (no catch/finally)
                        /\ treg' = [treg EXCEPT ![t] = FALSE]
                        /\ emap' = IF Fix = "none" THEN emap ELSE [emap EXCEPT ![l] = "failed"]
                        /\ FinishExc(t)
                        /\ UNCHANGED <<q, tpos, tcnt, runner, taskId, queue, ck, results>>
          ELSE CASE emap[l] = "absent" ->                              \* putIfAbsent wins
                      /\ emap' = [emap EXCEPT ![l] = "pending"]
                      /\ treg' = [treg EXCEPT ![t] = TRUE]
                      /\ UNCHANGED <<q, tstate, tpos, tcnt, runner, taskId, queue, wrun, cpc, ck, results>>
                 [] emap[l] = "true" ->                                \* get() == true -> CTE
                      /\ tpos' = [tpos EXCEPT ![t] = @ + 1]
                      /\ UNCHANGED <<q, emap, tstate, tcnt, treg, runner, taskId, queue, wrun, cpc, ck, results>>
                 [] emap[l] = "false" ->                               \* get() == false -> collect
                      /\ tcnt' = [tcnt EXCEPT ![t] = @ + m]
                      /\ tpos' = [tpos EXCEPT ![t] = @ + 1]
                      /\ UNCHANGED <<q, emap, tstate, treg, runner, taskId, queue, wrun, cpc, ck, results>>
                 [] emap[l] = "failed" /\ Fix # "perweight" ->         \* fixed model: get() throws
                      /\ FinishExc(t)
                      /\ UNCHANGED <<q, emap, tpos, tcnt, treg, runner, taskId, queue, ck, results>>
                 [] emap[l] = "failed" /\ Fix = "perweight" ->         \* patch: waiter early-terminates
                      /\ tpos' = [tpos EXCEPT ![t] = @ + 1]            \* (the search fails anyway)
                      /\ UNCHANGED <<q, emap, tstate, tcnt, treg, runner, taskId, queue, wrun, cpc, ck, results>>
                 [] OTHER -> FALSE  \* emap[l] = "pending": blocked in CompletableFuture.get()

Collect ==
  /\ cpc = "collect"
  /\ IF ck <= NT
     THEN /\ tstate[ck] \in Done                     \* future.get() blocks until done
          /\ ck' = ck + 1
          /\ UNCHANGED <<q, emap, tstate, tpos, tcnt, treg, runner, taskId, queue, wrun, cpc, results>>
     ELSE LET exc == \E t \in Tasks : tstate[t] = "exc"
              tot == SumF(tcnt, Tasks)
          IN /\ results' = Append(results, [exc |-> exc, count |-> IF exc THEN 0 ELSE tot])
             \* reduce() is only called when invokeAll returned normally (IndexSearcher.java:792-793)
             /\ emap' = IF ~exc /\ HasPartitions THEN [l \in Leaves |-> "absent"]
                        ELSE IF Fix = "excremove"
                             THEN [l \in Leaves |-> IF emap[l] = "failed" THEN "absent" ELSE emap[l]]
                             ELSE emap
             /\ cpc' = "returned"
             /\ UNCHANGED <<q, tstate, tpos, tcnt, treg, runner, taskId, queue, wrun, ck>>

\* the same manager instance is reused for the next search (TotalHitCountCollectorManager.java:87-93)
NextSearch ==
  /\ cpc = "returned"
  /\ q < NumSearches
  /\ \A w \in Workers : wrun[w] = 0
  /\ q' = q + 1
  /\ tstate' = [t \in Tasks |-> "new"]
  /\ tpos' = [t \in Tasks |-> 1]
  /\ tcnt' = [t \in Tasks |-> 0]
  /\ treg' = [t \in Tasks |-> FALSE]
  /\ runner' = [t \in Tasks |-> 0]
  /\ taskId' = 1
  /\ queue' = 0          \* stale work items of the previous invokeAll are no-ops
  /\ cpc' = "submit"
  /\ ck' = 1
  /\ emap' = IF Fix = "perweight" THEN [l \in Leaves |-> "absent"] ELSE emap
  /\ UNCHANGED <<wrun, results>>

Finished == cpc = "returned" /\ q = NumSearches
Terminate == Finished /\ UNCHANGED vars

Next ==
  \/ Submit \/ CallerClaim \/ Collect \/ NextSearch
  \/ \E w \in Workers : WorkerTake(w)
  \/ \E t \in Tasks : TaskStep(t)
  \/ Terminate

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

(* ---------------- properties ---------------- *)
TypeOK ==
  /\ emap \in [Leaves -> {"absent", "pending", "true", "false", "failed"}]
  /\ tstate \in [Tasks -> {"new", "running", "ok", "exc", "cancelled"}]

\* every search that returns normally returns the true hit count
CountCorrect == \A i \in DOMAIN results : results[i].exc = FALSE => results[i].count = TrueCount

\* a search whose Weight#count can never throw must not fail
NoSpuriousFailure ==
  \A i \in DOMAIN results :
    results[i].exc => \E l \in Leaves : Outcomes[i][l] \cap {"throw", "scorethrow"} # {}

\* invokeAll never returns while one of its tasks is still running (TaskExecutor.java:130, :186)
NoRunningAfterReturn == cpc = "returned" => \A t \in Tasks : tstate[t] # "running"

\* liveness: every search eventually returns (checked as PROPERTY, and via TLC deadlock detection)
AllSearchesReturn == <>Finished
=============================================================================

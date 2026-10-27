---------------------------- MODULE OfflineSortProto ----------------------------
(***************************************************************************)
(* Protocol model of org.apache.lucene.util.OfflineSorter.sort(String)     *)
(* (Lucene main @ ea57360912ed): partition reads gated by the              *)
(* partitionsInRAM semaphore, SortPartitionTask / MergePartitionsTask      *)
(* futures run on an ExecutorService, the levelCounts merge cascade, the   *)
(* final merge, and temp-file cleanup on failure.                          *)
(*                                                                         *)
(* Record contents are not modelled (the per-partition sort and the k-way  *)
(* merge are checked by the differential harness on the real jar); this    *)
(* model checks deadlock freedom, temp-file cleanup, permit accounting and *)
(* that every partition reaches the result exactly once.                   *)
(*                                                                         *)
(* Mapping (model element -> OfflineSorter.java line):                     *)
(*   MAcquire          readPartition: partitionsInRAM.acquire()      :431  *)
(*   MRead fault       readPartition catch -> release                :479  *)
(*   MRead empty       sort: part.count == 0 -> release; break  :301-306   *)
(*   MRead submit      sort: segments.add(exec.submit(job))          :311  *)
(*   MCascade          sort: while (levelCounts[l] == maxTempFiles) :318   *)
(*   Merge(segs)       mergePartitions: last maxTempFiles segs  :384-401   *)
(*   MFinal            sort: while (segments.size() > 1)        :337-339   *)
(*   MWait             getPartition(segments.get(0)) / empty file :342-351 *)
(*   MFooter           CodecUtil.checkFooter(is.in)                  :360  *)
(*   MCleanup          catch: deleteFilesSuppressingExceptions(      *)
(*                       trackingDir.getCreatedFiles())         :363-369   *)
(*   S0 / S1           SortPartitionTask.call create/write; finally  *)
(*                       release                                :629-657   *)
(*   MWaitDep          MergePartitionsTask: getPartition(dep) loop :685-687*)
(*   MCreate           createTempOutput for merged output            :700  *)
(*   MOpen             openChecksumInput(segment.fileName)           :707  *)
(*   MMerge            PQ merge + footers                       :723-745   *)
(*   MDel              IOUtils.deleteFiles(dir, inputs)         :749-753   *)
(*   W = 0             exec == null -> SameThreadExecutorService :247       *)
(*   LIFO = FALSE      ThreadPoolExecutor + LinkedBlockingQueue (FIFO)     *)
(*                     as in TestOfflineSorter.randomExecutorServiceOrNull *)
(* All deletions go through the TrackingDirectoryWrapper, so the set of    *)
(* existing temp files equals trackingDir.getCreatedFiles().               *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS NPMax,     \* max non-empty partitions in the input (np is chosen in Init)
          MaxTemp,   \* maxTempFiles (>= 2)
          MaxPIR,    \* maxPartitionsInRAM (>= 1)
          W,         \* executor threads; 0 = SameThreadExecutorService (exec == null)
          LIFO,      \* executor queue discipline
          Faults,    \* max injected failures (IOException / corruption / interrupt)
          Reject     \* allow exec.submit to throw RejectedExecutionException (counts as a fault)

MaxTasks == IF NPMax = 0 THEN 1 ELSE 2 * NPMax
TaskIds == 1..MaxTasks
EmptyFile == MaxTasks + 1          \* the empty result file written when there are no partitions
Workers == IF W = 0 THEN {0} ELSE 1..W
Levels == 0..(NPMax + 1)

VARIABLES np,       \* non-empty partitions in this input
          exf,      \* TRUE: input ends exactly on a partition boundary (one extra empty read)
          mpc,      \* main thread pc
          part,     \* index of next partition to read (1..np+1)
          exh,      \* last read partition was exhausted
          permits,  \* semaphore permits
          lc,       \* levelCounts
          cl,       \* current cascade level
          segs,     \* List<Future<Partition>> segments
          tk,       \* task table
          nextId,
          queue,    \* executor queue
          wk,       \* worker -> running task (0 = idle)
          files,    \* existing temp files (= trackingDir.getCreatedFiles())
          budget,   \* remaining injectable faults
          result

vars == <<np, exf, mpc, part, exh, permits, lc, cl, segs, tk, nextId, queue, wk, files, budget, result>>

NoTaskRec == [kind |-> "none", deps |-> << >>, st |-> "none", pc |-> "none", k |-> 0]

Init ==
  /\ np \in 0..NPMax /\ exf \in BOOLEAN
  /\ mpc = "acquire" /\ part = 1 /\ exh = FALSE /\ permits = MaxPIR
  /\ lc = [l \in Levels |-> 0] /\ cl = 0 /\ segs = << >>
  /\ tk = [t \in TaskIds |-> NoTaskRec] /\ nextId = 1
  /\ queue = << >> /\ wk = [w \in Workers |-> 0]
  /\ files = {} /\ budget = Faults /\ result = 0

Done(t) == tk[t].st \in {"ok", "fail"}

\* SameThreadExecutorService runs a submitted task inside submit(); the main thread
\* takes no other step until it is finished.
MainMayStep == W = 0 => (queue = << >> /\ wk[0] = 0)

\* --- submission ------------------------------------------------------------
\* Submit task id nextId with kind/deps; RejectedExecutionException optional.
Enqueue(t) == queue' = Append(queue, t)

MergeDeps == IF Len(segs) > MaxTemp
               THEN SubSeq(segs, Len(segs) - MaxTemp + 1, Len(segs))
               ELSE segs
MergeKeep == IF Len(segs) > MaxTemp THEN SubSeq(segs, 1, Len(segs) - MaxTemp) ELSE << >>

\* --- main thread ---------------------------------------------------------
MAcquire ==
  /\ mpc = "acquire" /\ MainMayStep
  /\ \/ /\ permits > 0
        /\ permits' = permits - 1 /\ mpc' = "read"
        /\ UNCHANGED budget
     \/ /\ budget > 0             \* InterruptedException while blocked in acquire
        /\ budget' = budget - 1 /\ mpc' = "cleanup" /\ UNCHANGED permits
  /\ UNCHANGED <<part, exh, lc, cl, segs, tk, nextId, queue, wk, files, result>>

MRead ==
  /\ mpc = "read" /\ MainMayStep
  /\ \/ /\ budget > 0            \* corrupt input / IOException while reading the partition
        /\ budget' = budget - 1 /\ permits' = permits + 1 /\ mpc' = "cleanup"
        /\ UNCHANGED <<part, exh, lc, segs, tk, nextId, queue>>
     \/ /\ part > np              \* read returned 0 items
        /\ permits' = permits + 1 /\ mpc' = "final"
        /\ UNCHANGED <<part, exh, lc, segs, tk, nextId, queue, budget>>
     \/ /\ part <= np
        /\ LET t == nextId IN
           \/ /\ Reject /\ budget > 0   \* exec.submit throws; permit is NOT released
              /\ budget' = budget - 1 /\ mpc' = "cleanup"
              /\ UNCHANGED <<part, exh, lc, segs, tk, nextId, queue, permits>>
           \/ /\ tk' = [tk EXCEPT ![t] = [kind |-> "S", deps |-> << >>, st |-> "queued", pc |-> "s0", k |-> 0]]
              /\ Enqueue(t) /\ nextId' = nextId + 1
              /\ segs' = Append(segs, t)
              /\ lc' = [lc EXCEPT ![0] = @ + 1]
              /\ exh' = (part = np /\ ~exf)
              /\ part' = part + 1
              /\ mpc' = "cascade"
              /\ UNCHANGED <<permits, budget>>
  /\ UNCHANGED <<cl, wk, files, result>>

\* one mergePartitions call (submit of a MergePartitionsTask)
DoMerge ==
  LET t == nextId IN
  /\ tk' = [tk EXCEPT ![t] = [kind |-> "M", deps |-> MergeDeps, st |-> "queued", pc |-> "wait", k |-> 1]]
  /\ Enqueue(t) /\ nextId' = nextId + 1
  /\ segs' = Append(MergeKeep, t)

MCascade ==
  /\ mpc = "cascade" /\ MainMayStep
  /\ IF lc[cl] = MaxTemp
       THEN \/ /\ DoMerge
               /\ lc' = [lc EXCEPT ![cl + 1] = @ + 1, ![cl] = 0]
               /\ cl' = cl + 1
               /\ UNCHANGED <<mpc, budget>>
            \/ /\ Reject /\ budget > 0
               /\ budget' = budget - 1 /\ mpc' = "cleanup"
               /\ UNCHANGED <<tk, queue, nextId, segs, lc, cl>>
       ELSE /\ cl' = 0
            /\ mpc' = IF exh THEN "final" ELSE "acquire"
            /\ UNCHANGED <<tk, queue, nextId, segs, lc, budget>>
  /\ UNCHANGED <<part, exh, permits, wk, files, result>>

MFinal ==
  /\ mpc = "final" /\ MainMayStep
  /\ IF Len(segs) > 1
       THEN \/ /\ DoMerge /\ UNCHANGED <<mpc, budget>>
            \/ /\ Reject /\ budget > 0
               /\ budget' = budget - 1 /\ mpc' = "cleanup"
               /\ UNCHANGED <<tk, queue, nextId, segs>>
       ELSE /\ mpc' = "wait" /\ UNCHANGED <<tk, queue, nextId, segs, budget>>
  /\ UNCHANGED <<part, exh, permits, lc, cl, wk, files, result>>

MWait ==
  /\ mpc = "wait" /\ MainMayStep
  /\ \/ /\ segs = << >>
        /\ files' = files \cup {EmptyFile} /\ result' = EmptyFile /\ mpc' = "footer"
        /\ UNCHANGED budget
     \/ /\ segs # << >> /\ Done(segs[1])
        /\ IF tk[segs[1]].st = "ok"
             THEN result' = segs[1] /\ mpc' = "footer"
             ELSE result' = 0 /\ mpc' = "cleanup"
        /\ UNCHANGED <<files, budget>>
     \/ /\ budget > 0            \* interrupt while blocked in future.get()
        /\ budget' = budget - 1 /\ mpc' = "cleanup"
        /\ UNCHANGED <<files, result>>
  /\ UNCHANGED <<part, exh, permits, lc, cl, segs, tk, nextId, queue, wk>>

MFooter ==
  /\ mpc = "footer" /\ MainMayStep
  /\ \/ /\ budget > 0            \* checkFooter(is.in) fails (corrupt input)
        /\ budget' = budget - 1 /\ mpc' = "cleanup"
     \/ /\ mpc' = "done" /\ UNCHANGED budget
  /\ UNCHANGED <<part, exh, permits, lc, cl, segs, tk, nextId, queue, wk, files, result>>

MCleanup ==
  /\ mpc = "cleanup" /\ MainMayStep
  /\ files' = {}                 \* delete every file trackingDir has created so far
  /\ mpc' = "failed"
  /\ UNCHANGED <<part, exh, permits, lc, cl, segs, tk, nextId, queue, wk, budget, result>>

\* --- executor workers ------------------------------------------------------
Take(w) ==
  /\ wk[w] = 0 /\ queue # << >>
  /\ LET t == IF LIFO THEN queue[Len(queue)] ELSE Head(queue) IN
     /\ wk' = [wk EXCEPT ![w] = t]
     /\ queue' = IF LIFO THEN SubSeq(queue, 1, Len(queue) - 1) ELSE Tail(queue)
     /\ tk' = [tk EXCEPT ![t].st = "run"]
  /\ UNCHANGED <<mpc, part, exh, permits, lc, cl, segs, nextId, files, budget, result>>

Finish(w, t, s) ==
  /\ tk' = [tk EXCEPT ![t].st = s, ![t].pc = "end"]
  /\ wk' = [wk EXCEPT ![w] = 0]

StepS(w) ==
  LET t == wk[w] IN
  /\ t # 0 /\ tk[t].kind = "S"
  /\ \/ /\ tk[t].pc = "s0"
        /\ \/ /\ budget > 0  \* createTempOutput fails: no file; finally releases the permit
              /\ budget' = budget - 1 /\ Finish(w, t, "fail") /\ permits' = permits + 1
              /\ UNCHANGED files
           \/ /\ files' = files \cup {t} /\ tk' = [tk EXCEPT ![t].pc = "s1"]
              /\ UNCHANGED <<budget, wk, permits>>
     \/ /\ tk[t].pc = "s1"
        /\ \/ /\ budget > 0  \* write fails: file stays, permit released
              /\ budget' = budget - 1 /\ Finish(w, t, "fail") /\ permits' = permits + 1
           \/ /\ Finish(w, t, "ok") /\ permits' = permits + 1 /\ UNCHANGED budget
        /\ UNCHANGED files
  /\ UNCHANGED <<mpc, part, exh, lc, cl, segs, nextId, queue, result>>

StepM(w) ==
  LET t == wk[w]
      d == tk[t].deps
  IN
  /\ t # 0 /\ tk[t].kind = "M"
  /\ \/ /\ tk[t].pc = "wait"        \* getPartition(dep) in dep order
        /\ Done(d[tk[t].k])
        /\ IF tk[d[tk[t].k]].st = "fail"
             THEN Finish(w, t, "fail")
             ELSE /\ tk' = IF tk[t].k = Len(d)
                             THEN [tk EXCEPT ![t].pc = "create", ![t].k = 1]
                             ELSE [tk EXCEPT ![t].k = @ + 1]
                  /\ UNCHANGED wk
        /\ UNCHANGED <<files, budget>>
     \/ /\ tk[t].pc = "create"
        /\ \/ /\ budget > 0 /\ budget' = budget - 1 /\ Finish(w, t, "fail") /\ UNCHANGED files
           \/ /\ files' = files \cup {t} /\ tk' = [tk EXCEPT ![t].pc = "open"]
              /\ UNCHANGED <<budget, wk>>
     \/ /\ tk[t].pc = "open"        \* open each input; NoSuchFileException if already deleted
        /\ IF d[tk[t].k] \notin files
             THEN Finish(w, t, "fail") /\ UNCHANGED budget
             ELSE \/ /\ budget > 0 /\ budget' = budget - 1 /\ Finish(w, t, "fail")
                  \/ /\ tk' = IF tk[t].k = Len(d)
                                THEN [tk EXCEPT ![t].pc = "merge"]
                                ELSE [tk EXCEPT ![t].k = @ + 1]
                     /\ UNCHANGED <<budget, wk>>
        /\ UNCHANGED files
     \/ /\ tk[t].pc = "merge"
        /\ \/ /\ budget > 0 /\ budget' = budget - 1 /\ Finish(w, t, "fail")
           \/ /\ tk' = [tk EXCEPT ![t].pc = "del"] /\ UNCHANGED <<budget, wk>>
        /\ UNCHANGED files
     \/ /\ tk[t].pc = "del"         \* IOUtils.deleteFiles: deletes all it can, then throws
        /\ LET inputs == {d[i] : i \in 1..Len(d)} IN
           /\ files' = files \ inputs
           /\ IF inputs \subseteq files THEN Finish(w, t, "ok") ELSE Finish(w, t, "fail")
        /\ UNCHANGED budget
  /\ UNCHANGED <<mpc, part, exh, permits, lc, cl, segs, nextId, queue, result>>

Quiescent == mpc \in {"done", "failed"} /\ queue = << >> /\ \A w \in Workers : wk[w] = 0

NextCore ==
  \/ MAcquire \/ MRead \/ MCascade \/ MFinal \/ MWait \/ MFooter \/ MCleanup
  \/ \E w \in Workers : Take(w) \/ StepS(w) \/ StepM(w)

Next == UNCHANGED <<np, exf>> /\ NextCore
\* Terminal stuttering only when the whole system is quiescent; any other state with
\* no enabled action is reported by TLC as a deadlock.
Spec == Init /\ [][Next]_vars
NextT == Next \/ (Quiescent /\ UNCHANGED vars)
SpecT == Init /\ [][NextT]_vars

\* --- properties ---------------------------------------------------------------
TypeOK == permits \in 0..MaxPIR /\ nextId \in 1..(MaxTasks + 1)

\* every task id appears as an input of at most one merge, and segments are distinct
DepsOf(t) == {tk[t].deps[i] : i \in 1..Len(tk[t].deps)}
NoSharedInputs ==
  \A a, b \in TaskIds : (a # b /\ tk[a].kind = "M" /\ tk[b].kind = "M") => DepsOf(a) \cap DepsOf(b) = {}

RECURSIVE Leaves(_)
Leaves(t) == IF tk[t].kind = "S" THEN {t}
             ELSE UNION {Leaves(tk[t].deps[i]) : i \in 1..Len(tk[t].deps)}
STasks == {t \in TaskIds : tk[t].kind = "S"}

\* mirrors the assert at OfflineSorter.java:355-356 (reached only on the success path)
AssertCreatedIsResult == mpc = "footer" => files = {result}

\* success: every partition reaches the result exactly once (merge trees are disjoint)
ExactlyOnce == (mpc = "done" /\ result # EmptyFile) => (Leaves(result) = STasks /\ Cardinality(STasks) = np)

SuccessClean  == (Quiescent /\ mpc = "done")   => files = {result}
FailureClean  == (Quiescent /\ mpc = "failed") => files = {}
PermitsBack   == Quiescent => permits = MaxPIR

\* --- vacuity probes (each must be VIOLATED in a non-vacuous configuration) ---------
ProbeNeverDone      == mpc # "done"
ProbeNeverLevel2    == lc[2] = 0                     \* a cascaded (level-1 -> level-2) merge happened
ProbeNeverFailed    == mpc # "failed"
ProbeNeverParallel  == Cardinality({w \in Workers : wk[w] # 0}) < 2
ProbeNeverBlockedAcquire == ~(mpc = "acquire" /\ permits = 0)
=============================================================================

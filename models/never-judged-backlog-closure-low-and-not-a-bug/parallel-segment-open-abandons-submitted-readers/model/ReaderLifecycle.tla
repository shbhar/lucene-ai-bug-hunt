--------------------------- MODULE ReaderLifecycle ---------------------------
(***************************************************************************)
(* Reader / core reference-count protocol of Lucene main @ ea57360912ed.   *)
(*                                                                         *)
(* Objects (fixed universe, small scope):                                  *)
(*   commit A = segments {1,2};  commit B = segments {1,2,3}               *)
(*   seg 1 unchanged in B  -> reopen REUSES SegmentReader "sr1A" (incRef)  *)
(*   seg 2 new deletes in B-> reopen makes "sr2B" SHARING core 2 (incRef)  *)
(*   seg 3 new in B        -> reopen makes "sr3B" with a NEW core 3         *)
(*   DirectoryReaders "r1" (commit A, opened in Init, owned by app1) and   *)
(*   "r2" (commit B, built by the reopen).  Optional FilterDirectoryReader *)
(*   wrappers "w1" (over r1) and "w2" (built by the reopen over r2).       *)
(*   Optional FilterCodecReader "fcr" over sr1A (LFV-235 sanity check).    *)
(*                                                                         *)
(* Model element -> Java (lucene/core/src/java/org/apache/lucene/...)      *)
(*   RcInc/TryInc     index/IndexReader.java:175-205 (CAS loop, >0 only)   *)
(*   DecStart/DecEnd  index/IndexReader.java:215-232 (decrementAndGet; at  *)
(*                    0: closed=true, doClose(), then listeners (finally)) *)
(*   Close            index/IndexReader.java:351-356 (closed flag, once)   *)
(*   SR doClose       index/SegmentReader.java:214-225 (core.decRef)       *)
(*   core incRef      index/SegmentCoreReaders.java:156-164 (ACE at 0)     *)
(*   core decRef      index/SegmentCoreReaders.java:167-180 (close+notify  *)
(*                    when decrementAndGet()==0; no underflow check)       *)
(*   SR listeners     index/SegmentReader.java:315-338 (ensureOpen; add)   *)
(*   core listeners   index/SegmentReader.java:349-362 (SR.ensureOpen,     *)
(*                    then core set add) + SegmentCoreReaders.java:191-205 *)
(*   DR doClose       index/StandardDirectoryReader.java:548-568           *)
(*                    (applyToAll decRef of every sub)                     *)
(*   reopen           index/StandardDirectoryReader.java:507-511 ->        *)
(*                    open(Directory,SegmentInfos,oldReaders,..):174-184   *)
(*                    createSegmentReaders:187-247, createOrReuse:287-361  *)
(*     seq branch     :232-244 try/catch decRefWhileSuppressingException   *)
(*     exec branch    :195-228 submit loop, then get() loop, cleanup only  *)
(*                    of non-null collected readers                        *)
(*     ctor           :182-183 NO try/catch (contrast: open(...):97-105)   *)
(*   wrapper reopen   index/FilterDirectoryReader.java:107-114 (wrap the   *)
(*                    new inner reader; no cleanup if wrapping throws)     *)
(*   wrapper close    index/FilterDirectoryReader.java:162-164 (in.close)  *)
(*   FCR doClose      index/FilterCodecReader.java:124-126 (in.doClose())  *)
(*   FutureTask.get   JDK: returns a completed result even if interrupted; *)
(*                    throws InterruptedException only while not done     *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS
  MODE,        \* "seq" | "exec"   (executor passed to openIfChanged or not)
  FAULT,       \* "none" | "newSrFail3" | "newSrFail2" | "rejectAt2" | "rejectAt3"
               \* | "interrupt3" | "interrupt2" | "ctorThrow" | "wrapThrow"
  WRAPPED,     \* TRUE: app reopens a FilterDirectoryReader w1 over r1
  WITH_APP2,   \* TRUE: a second application thread acquires/releases r1
  WITH_FCR,    \* TRUE: LFV-235 scenario (FilterCodecReader over sr1A)
  FCR_FIX,     \* TRUE: FilterCodecReader.doClose calls in.close() (fix)
  MUTANT       \* vacuity mutants: "none" | "noIncRefOnReuse" | "noSubDecRef"
               \* | "noExecCleanup" | "doubleNotify" | "fixAll" (candidate fixes)

SRS   == {"sr1A", "sr2A", "sr2B", "sr3B"}
CORES == {1, 2, 3}
DRS   == {"r1", "r2"}
WRAPS == {"w1", "w2"}
FCRS  == {"fcr"}
RDR   == SRS \cup DRS \cup WRAPS \cup FCRS
coreOf == [s \in SRS |-> CASE s = "sr1A" -> 1 [] s = "sr2A" -> 2
                          [] s = "sr2B" -> 2 [] s = "sr3B" -> 3]
\* sub-readers of each DirectoryReader after a successful (re)open
subsA == {"sr1A", "sr2A"}
subsB == {"sr1A", "sr2B", "sr3B"}
\* task order of createSegmentReaders: i = sis.size()-1 .. 0, i.e. seg 3, 2, 1
TASKS == <<3, 2, 1>>
\* what task "seg k" produces
srFor(k) == CASE k = 1 -> "sr1A" [] k = 2 -> "sr2B" [] k = 3 -> "sr3B"

THREADS == {"app1", "app2", "worker"}

VARIABLES
  rc,          \* reader refCount (IndexReader.refCount)
  closedFlag,  \* IndexReader.closed (set by close() and at refCount 0)
  doClosed,    \* number of times doClose() ran per reader (should be <= 1)
  coreRc,      \* SegmentCoreReaders.ref
  coreOpen,    \* core files open (TRUE between creation and close)
  coreCloses,  \* number of times a core's files were closed
  lis,         \* registered closed listeners: set of <<kind, target>>
  notified,    \* function listener -> number of onClose calls
  holds,       \* holds[t][x] = references thread t owns on reader x
  pc,          \* per-thread program counter
  task,        \* exec mode: task state per segment: "none","sub","done","fail"
  got,         \* readers collected by the caller (createSegmentReaders 'readers')
  abandoned,   \* tasks whose future the caller gave up on
  err,         \* app1's reopen ended with an exception
  app2steps

vars == <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified,
          holds, pc, task, got, abandoned, err, app2steps>>

(* ---------------- helpers: atomic cascades of close() ------------------ *)
\* Closing cascades are executed atomically: no lock is involved, but the
\* properties here are about ref counts and the order inside one thread.

\* listeners attached to x
LisOf(kind, x) == {l \in lis : l[1] = kind /\ l[2] = x}

Init ==
  /\ rc = [x \in RDR |-> IF x \in {"r1", "sr1A", "sr2A"} \/ (WRAPPED /\ x = "w1") THEN 1 ELSE 0]
  /\ closedFlag = [x \in RDR |-> FALSE]
  /\ doClosed = [x \in RDR |-> 0]
  /\ coreRc = [c \in CORES |-> IF c \in {1, 2} THEN 1 ELSE 0]
  /\ coreOpen = [c \in CORES |-> c \in {1, 2}]
  /\ coreCloses = [c \in CORES |-> 0]
  /\ lis = {}
  /\ notified = [l \in ({"core"} \X CORES) \cup ({"rdr"} \X RDR) |-> 0]
  /\ holds = [t \in THREADS |-> [x \in RDR |->
                IF t = "app1" /\ ((WRAPPED /\ x = "w1") \/ (~WRAPPED /\ x = "r1")) THEN 1 ELSE 0]]
  /\ pc = [t \in THREADS |-> CASE t = "app1" -> IF WITH_FCR THEN "fcr_wrap" ELSE "reopen"
                               [] t = "app2" -> IF WITH_APP2 THEN "acquire" ELSE "done"
                               [] t = "worker" -> "idle"]
  /\ task = [k \in {1, 2, 3} |-> "none"]
  /\ got = {}
  /\ abandoned = {}
  /\ err = FALSE
  /\ app2steps = 0

(* The reopen of commit B creates or reuses the reader of segment k.
   Returns the new rc/coreRc/coreOpen as a record; ok = FALSE when it throws
   (AlreadyClosedException from incRef or an injected new-reader failure). *)
CreateOrReuse(k, r, cr, co, failNew) ==
  CASE k = 1 ->   \* oldReader.incRef(): tryIncRef, else ensureOpen() throws ACE
          IF r["sr1A"] > 0 THEN [ok |-> TRUE, rc |-> [r EXCEPT !["sr1A"] = @ + (IF MUTANT = "noIncRefOnReuse" THEN 0 ELSE 1)], crc |-> cr, co |-> co]
          ELSE [ok |-> FALSE, rc |-> r, crc |-> cr, co |-> co]
    [] k = 2 ->   \* new SegmentReader(si, sr, ...): core.incRef(), ACE at 0; an I/O failure
                  \* in initFieldInfos/initDocValuesProducer runs doClose() (core.decRef): net 0
          IF failNew THEN [ok |-> FALSE, rc |-> r, crc |-> cr, co |-> co]
          ELSE IF cr[2] > 0
          THEN [ok |-> TRUE, rc |-> [r EXCEPT !["sr2B"] = 1], crc |-> [cr EXCEPT ![2] = @ + 1], co |-> co]
          ELSE [ok |-> FALSE, rc |-> r, crc |-> cr, co |-> co]
    [] k = 3 ->   \* new SegmentReader(si, ...): new core; on failure the ctor closes what it opened
          IF failNew THEN [ok |-> FALSE, rc |-> r, crc |-> cr, co |-> co]
          ELSE [ok |-> TRUE, rc |-> [r EXCEPT !["sr3B"] = 1], crc |-> [cr EXCEPT ![3] = 1],
                co |-> [co EXCEPT ![3] = TRUE]]

(* SegmentReader decRef cascade on state record s; returns new record.
   s has fields rc, closedFlag, doClosed, crc, co, cc, notified *)
SrDec(s, x) ==
  LET r0 == s.rc[x] - 1 IN
  IF s.rc[x] <= 0 THEN [s EXCEPT !.ace = TRUE]   \* decRef on closed reader: ACE
  ELSE IF r0 > 0 THEN [s EXCEPT !.rc[x] = r0]
  ELSE \* refCount 0: closed=true; doClose(): core.decRef(); then reader listeners
       LET c == coreOf[x]
           c0 == s.crc[c] - 1
       IN [s EXCEPT !.rc[x] = 0, !.closedFlag[x] = TRUE, !.doClosed[x] = @ + 1,
                    !.crc[c] = c0,
                    !.co[c] = IF c0 = 0 THEN FALSE ELSE @,
                    !.cc[c] = IF c0 = 0 THEN @ + 1 ELSE @,
                    !.notified = [l \in DOMAIN @ |->
                        IF (l = <<"rdr", x>> /\ <<"rdr", x>> \in lis)
                           \/ (c0 = 0 /\ l = <<"core", c>> /\ <<"core", c>> \in lis)
                        THEN @[l] + 1 ELSE @[l]]]

\* FilterCodecReader.doClose(): in.doClose() (stock) -- runs SR doClose directly
SrDoCloseDirect(s, x) ==
  LET c == coreOf[x]
      c0 == s.crc[c] - 1
  IN [s EXCEPT !.doClosed[x] = @ + 1,
               !.crc[c] = c0,
               !.co[c] = IF c0 = 0 THEN FALSE ELSE @,
               !.cc[c] = IF c0 = 0 THEN @ + 1 ELSE @,
               !.notified = [l \in DOMAIN @ |->
                   IF (c0 = 0 /\ l = <<"core", c>> /\ <<"core", c>> \in lis)
                   THEN @[l] + 1 ELSE @[l]]]

SubsOf(d) == IF d = "r1" THEN subsA ELSE subsB

\* applyToAll over a set: fold
RECURSIVE FoldDec(_, _)
FoldDec(s, S) == IF S = {} THEN s ELSE LET x == CHOOSE y \in S : TRUE IN FoldDec(SrDec(s, x), S \ {x})

DrDec(s, d) ==
  IF s.rc[d] <= 0 THEN [s EXCEPT !.ace = TRUE]
  ELSE IF s.rc[d] > 1 THEN [s EXCEPT !.rc[d] = @ - 1]
  ELSE LET s1 == [s EXCEPT !.rc[d] = 0, !.closedFlag[d] = TRUE, !.doClosed[d] = @ + 1]
           s2 == IF MUTANT = "noSubDecRef" THEN s1 ELSE FoldDec(s1, SubsOf(d))
       IN [s2 EXCEPT !.notified = [l \in DOMAIN @ |->
              IF l = <<"rdr", d>> /\ <<"rdr", d>> \in lis
              THEN @[l] + (IF MUTANT = "doubleNotify" THEN 2 ELSE 1) ELSE @[l]]]

WrapDec(s, w) ==
  IF s.rc[w] <= 0 THEN [s EXCEPT !.ace = TRUE]
  ELSE IF s.rc[w] > 1 THEN [s EXCEPT !.rc[w] = @ - 1]
  ELSE LET inner == IF w = "w1" THEN "r1" ELSE "r2"
           s1 == [s EXCEPT !.rc[w] = 0, !.closedFlag[w] = TRUE, !.doClosed[w] = @ + 1]
       IN \* FilterDirectoryReader.doClose(): in.close() -- once per inner (closed flag)
          IF s1.closedFlag[inner] THEN s1
          ELSE LET s2 == DrDec(s1, inner) IN [s2 EXCEPT !.closedFlag[inner] = TRUE]

FcrDec(s) ==
  IF s.rc["fcr"] <= 0 THEN [s EXCEPT !.ace = TRUE]
  ELSE IF s.rc["fcr"] > 1 THEN [s EXCEPT !.rc["fcr"] = @ - 1]
  ELSE LET s1 == [s EXCEPT !.rc["fcr"] = 0, !.closedFlag["fcr"] = TRUE, !.doClosed["fcr"] = @ + 1]
       IN IF FCR_FIX THEN (IF s1.closedFlag["sr1A"] THEN s1
                           ELSE LET s2 == SrDec(s1, "sr1A") IN [s2 EXCEPT !.closedFlag["sr1A"] = TRUE])
          ELSE SrDoCloseDirect(s1, "sr1A")

St == [rc |-> rc, closedFlag |-> closedFlag, doClosed |-> doClosed, crc |-> coreRc,
       co |-> coreOpen, cc |-> coreCloses, notified |-> notified, ace |-> FALSE]

Apply(s) == /\ rc' = s.rc /\ closedFlag' = s.closedFlag /\ doClosed' = s.doClosed
            /\ coreRc' = s.crc /\ coreOpen' = s.co /\ coreCloses' = s.cc
            /\ notified' = s.notified

\* close(): decRef only if !closed (IndexReader.java:351-356)
CloseOf(s, x) ==
  IF s.closedFlag[x] THEN s
  ELSE LET s1 == CASE x \in DRS -> DrDec(s, x) [] x \in WRAPS -> WrapDec(s, x)
                   [] x \in SRS -> SrDec(s, x) [] x = "fcr" -> FcrDec(s)
       IN [s1 EXCEPT !.closedFlag[x] = TRUE]

(* ------------------------------ app1 ---------------------------------- *)
\* LFV-235 sanity scenario: wrap sr1A in a FilterCodecReader, incRef sr1A,
\* close the wrapper, then go on with the reopen (which reuses sr1A).
FcrWrap == /\ pc["app1"] = "fcr_wrap"
           /\ rc' = [rc EXCEPT !["fcr"] = 1, !["sr1A"] = @ + 1]
           /\ holds' = [holds EXCEPT !["app1"]["fcr"] = 1]
           /\ pc' = [pc EXCEPT !["app1"] = "fcr_close"]
           /\ UNCHANGED <<closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified,
                          task, got, abandoned, err, app2steps>>
FcrClose == /\ pc["app1"] = "fcr_close"
            /\ LET s == CloseOf(St, "fcr") IN Apply(s)
            /\ holds' = [holds EXCEPT !["app1"]["fcr"] = 0]
            /\ pc' = [pc EXCEPT !["app1"] = "reopen"]
            /\ UNCHANGED <<lis, task, got, abandoned, err, app2steps>>

\* register a core listener (via sr1A: SR.ensureOpen() then add) and a reader
\* listener on r1 before reopening (LRUQueryCache-style)
Reopen_Seq ==
  /\ pc["app1"] = "reopen" /\ MODE = "seq"
  /\ LET c3 == CreateOrReuse(3, rc, coreRc, coreOpen, FAULT = "newSrFail3")
         c2 == CreateOrReuse(2, c3.rc, c3.crc, c3.co, FAULT = "newSrFail2")
         c1 == CreateOrReuse(1, c2.rc, c2.crc, c2.co, FALSE)
         s3 == [St EXCEPT !.rc = c3.rc, !.crc = c3.crc, !.co = c3.co]
         s2 == [St EXCEPT !.rc = c2.rc, !.crc = c2.crc, !.co = c2.co]
     IN IF ~c3.ok THEN \* catch: decRefWhileSuppressingException(readers): nothing created yet
          /\ err' = TRUE /\ pc' = [pc EXCEPT !["app1"] = "finish"] /\ got' = got
          /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
        ELSE IF ~c2.ok THEN \* readers = [_, _, sr3B]: decRef sr3B
          /\ Apply(SrDec(s3, "sr3B"))
          /\ err' = TRUE /\ pc' = [pc EXCEPT !["app1"] = "finish"] /\ got' = got
        ELSE IF ~c1.ok THEN                       \* readers = [_, sr2B, sr3B]
          /\ Apply(SrDec(SrDec(s2, "sr3B"), "sr2B"))
          /\ err' = TRUE /\ pc' = [pc EXCEPT !["app1"] = "finish"] /\ got' = got
        ELSE
          /\ rc' = c1.rc /\ coreRc' = c1.crc /\ coreOpen' = c1.co
          /\ UNCHANGED <<closedFlag, doClosed, coreCloses, notified, err>>
          /\ got' = {"sr1A", "sr2B", "sr3B"}
          /\ pc' = [pc EXCEPT !["app1"] = "ctor"]
  /\ UNCHANGED <<lis, holds, task, abandoned, app2steps>>

\* exec mode: the submit loop (one task per step, order 3,2,1)
Submit(i) ==
  /\ pc["app1"] = ("submit" \o ToString(i)) /\ MODE = "exec"
  /\ LET k == TASKS[i] IN
     IF (FAULT = "rejectAt2" /\ i = 2) \/ (FAULT = "rejectAt3" /\ i = 3)
     THEN \* RejectedExecutionException escapes createSegmentReaders: no cleanup
          \* (fixAll: wait for the submitted tasks, then release what they produced)
          /\ err' = TRUE
          /\ pc' = [pc EXCEPT !["app1"] = IF MUTANT = "fixAll" THEN "drain" ELSE "finish"]
          /\ abandoned' = IF MUTANT = "fixAll" THEN {} ELSE {TASKS[j] : j \in 1..(i-1)}
          /\ UNCHANGED task
     ELSE /\ task' = [task EXCEPT ![k] = "sub"]
          /\ pc' = [pc EXCEPT !["app1"] = IF i = 3 THEN "get1" ELSE ("submit" \o ToString(i + 1))]
          /\ UNCHANGED <<err, abandoned>>
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, holds,
                 got, app2steps>>

StartExec == /\ pc["app1"] = "reopen" /\ MODE = "exec"
             /\ pc' = [pc EXCEPT !["app1"] = "submit1"]
             /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified,
                            holds, task, got, abandoned, err, app2steps>>

\* worker runs one submitted task
Work(k) ==
  /\ task[k] = "sub"
  /\ LET c == CreateOrReuse(k, rc, coreRc, coreOpen, (FAULT = "newSrFail3" /\ k = 3)
                                                     \/ (FAULT = "newSrFail2" /\ k = 2)) IN
     /\ rc' = c.rc /\ coreRc' = c.crc /\ coreOpen' = c.co
     /\ task' = [task EXCEPT ![k] = IF c.ok THEN "done" ELSE "fail"]
  /\ UNCHANGED <<closedFlag, doClosed, coreCloses, lis, notified, holds, pc, got, abandoned, err,
                 app2steps>>

\* caller: futures.get(i).get()
Get(i) ==
  /\ pc["app1"] = ("get" \o ToString(i))
  /\ LET k == TASKS[i]
         intr == (FAULT = "interrupt3" /\ k = 3) \/ (FAULT = "interrupt2" /\ k = 2)
     IN \/ /\ task[k] \in {"done", "fail"}       \* completed: get() returns / throws ExecutionException
           /\ got' = IF task[k] = "done" THEN got \cup {srFor(k)} ELSE got
           /\ err' = (err \/ task[k] = "fail" \/ (MUTANT = "fixAll" /\ intr))
           /\ UNCHANGED abandoned
        \/ /\ task[k] = "sub" /\ intr /\ MUTANT # "fixAll"  \* still running and caller interrupted
           /\ abandoned' = abandoned \cup {k} /\ err' = TRUE
           /\ UNCHANGED got
  /\ pc' = [pc EXCEPT !["app1"] = IF i = 3 THEN "after_get" ELSE ("get" \o ToString(i + 1))]
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, holds,
                 task, app2steps>>

\* fixAll only: after a rejected submit, wait for the submitted tasks and release their results
Drain ==
  /\ pc["app1"] = "drain"
  /\ \A k \in {1, 2, 3} : task[k] # "sub"
  /\ LET s1 == FoldDec(St, {srFor(k) : k \in {j \in {1, 2, 3} : task[j] = "done"}}) IN Apply(s1)
  /\ pc' = [pc EXCEPT !["app1"] = "finish"]
  /\ UNCHANGED <<lis, holds, task, got, abandoned, err, app2steps>>

\* if (firstException != null) decRefWhileSuppressingException(firstException, readers)
AfterGet ==
  /\ pc["app1"] = "after_get"
  /\ IF err
     THEN LET s1 == IF MUTANT = "noExecCleanup" THEN St ELSE FoldDec(St, got) IN
          /\ Apply(s1) /\ got' = {} /\ pc' = [pc EXCEPT !["app1"] = "finish"]
     ELSE /\ pc' = [pc EXCEPT !["app1"] = "ctor"] /\ got' = got
          /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
  /\ UNCHANGED <<lis, holds, task, abandoned, err, app2steps>>

\* new StandardDirectoryReader(...): the leaf sorter / maxDoc check may throw (no cleanup)
Ctor ==
  /\ pc["app1"] = "ctor"
  /\ IF FAULT = "ctorThrow"
     THEN /\ err' = TRUE /\ pc' = [pc EXCEPT !["app1"] = "finish"] /\ got' = {}
          /\ IF MUTANT = "fixAll" THEN Apply(FoldDec(St, got))   \* fix: decRef the new readers
             ELSE UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
     ELSE /\ rc' = [rc EXCEPT !["r2"] = 1] /\ got' = {} /\ UNCHANGED err
          /\ pc' = [pc EXCEPT !["app1"] = IF WRAPPED THEN "wrap" ELSE "own"]
          /\ UNCHANGED <<closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
  /\ UNCHANGED <<lis, holds, task, abandoned, app2steps>>

\* FilterDirectoryReader.wrapDirectoryReader(newInner): doWrapDirectoryReader may throw
Wrap ==
  /\ pc["app1"] = "wrap"
  /\ IF FAULT = "wrapThrow"
     THEN /\ err' = TRUE /\ pc' = [pc EXCEPT !["app1"] = "finish"]
          /\ IF MUTANT = "fixAll" THEN Apply(CloseOf(St, "r2"))   \* fix: close the new inner reader
             ELSE UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
     ELSE /\ rc' = [rc EXCEPT !["w2"] = 1] /\ UNCHANGED err
          /\ pc' = [pc EXCEPT !["app1"] = "own"]
          /\ UNCHANGED <<closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified>>
  /\ UNCHANGED <<lis, holds, task, got, abandoned, app2steps>>

\* the application now owns the returned reader
Own ==
  /\ pc["app1"] = "own"
  /\ holds' = [holds EXCEPT !["app1"][IF WRAPPED THEN "w2" ELSE "r2"] = 1]
  /\ pc' = [pc EXCEPT !["app1"] = "finish"]
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, task, got,
                 abandoned, err, app2steps>>

\* app1 closes everything it owns (old reader always, new reader if returned)
Finish ==
  /\ pc["app1"] = "finish"
  /\ \E x \in RDR : holds["app1"][x] > 0
       /\ LET s == CloseOf(St, x) IN
          /\ Apply(s)
          /\ holds' = [holds EXCEPT !["app1"][x] = 0]
  /\ UNCHANGED <<pc, lis, task, got, abandoned, err, app2steps>>
FinishDone ==
  /\ pc["app1"] = "finish" /\ \A x \in RDR : holds["app1"][x] = 0
  /\ pc' = [pc EXCEPT !["app1"] = "done"]
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, holds,
                 task, got, abandoned, err, app2steps>>

(* ------------------------------ app2 ---------------------------------- *)
\* SearcherManager-style user: tryIncRef(r1) (or w1); if it succeeds register
\* listeners, read, then decRef.  Runs concurrently with app1's reopen/close.
App2Target == IF WRAPPED THEN "w1" ELSE "r1"
App2Acquire ==
  /\ pc["app2"] = "acquire"
  /\ IF rc[App2Target] > 0
     THEN /\ rc' = [rc EXCEPT ![App2Target] = @ + 1]
          /\ holds' = [holds EXCEPT !["app2"][App2Target] = 1]
          /\ pc' = [pc EXCEPT !["app2"] = "addlis_check"]
     ELSE /\ pc' = [pc EXCEPT !["app2"] = "done"] /\ UNCHANGED <<rc, holds>>
  /\ UNCHANGED <<closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, task, got,
                 abandoned, err, app2steps>>
\* SegmentReader.coreCacheHelper.addClosedListener: ensureOpen() of sr1A, then add (2 steps)
App2AddLisCheck ==
  /\ pc["app2"] = "addlis_check"
  /\ IF rc["sr1A"] > 0 THEN pc' = [pc EXCEPT !["app2"] = "addlis_add"]
     ELSE pc' = [pc EXCEPT !["app2"] = "release"]     \* ACE
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, lis, notified, holds, task,
                 got, abandoned, err, app2steps>>
App2AddLisAdd ==
  /\ pc["app2"] = "addlis_add"
  /\ lis' = lis \cup {<<"core", 1>>, <<"rdr", "r1">>}
  /\ pc' = [pc EXCEPT !["app2"] = "release"]
  /\ UNCHANGED <<rc, closedFlag, doClosed, coreRc, coreOpen, coreCloses, notified, holds, task, got,
                 abandoned, err, app2steps>>
App2Release ==
  /\ pc["app2"] = "release"
  /\ LET s == IF App2Target = "w1" THEN WrapDec(St, "w1") ELSE DrDec(St, "r1") IN Apply(s)
  /\ holds' = [holds EXCEPT !["app2"][App2Target] = 0]
  /\ pc' = [pc EXCEPT !["app2"] = "done"]
  /\ UNCHANGED <<lis, task, got, abandoned, err, app2steps>>

AllDone == pc["app1"] = "done" /\ pc["app2"] = "done" /\ \A k \in {1,2,3} : task[k] # "sub"

Next ==
  \/ FcrWrap \/ FcrClose
  \/ Reopen_Seq \/ StartExec \/ \E i \in 1..3 : Submit(i) \/ Get(i)
  \/ AfterGet \/ Drain \/ Ctor \/ Wrap \/ Own \/ Finish \/ FinishDone
  \/ \E k \in {1, 2, 3} : Work(k)
  \/ App2Acquire \/ App2AddLisCheck \/ App2AddLisAdd \/ App2Release
  \/ (AllDone /\ UNCHANGED vars)   \* terminal stutter (no deadlock report at quiescence)


Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

(* ----------------------------- properties ----------------------------- *)
TypeOK == \A x \in RDR : rc[x] >= 0

\* every reader a thread owns a reference to is fully usable: the reader is
\* open and the cores of all its segment readers are open
UsableSR(s) == rc[s] > 0 /\ coreOpen[coreOf[s]]
Usable(x) ==
  CASE x \in SRS -> UsableSR(x)
    [] x \in DRS -> rc[x] > 0 /\ \A s \in SubsOf(x) : UsableSR(s)
    [] x = "w1" -> rc["w1"] > 0 /\ rc["r1"] > 0 /\ \A s \in subsA : UsableSR(s)
    [] x = "w2" -> rc["w2"] > 0 /\ rc["r2"] > 0 /\ \A s \in subsB : UsableSR(s)
    [] x = "fcr" -> rc["fcr"] > 0 /\ UsableSR("sr1A")
NoUseAfterClose == \A t \in THREADS, x \in RDR : holds[t][x] > 0 => Usable(x)

\* doClose / core close at most once; listeners at most once
CloseOnce == /\ \A x \in RDR : doClosed[x] <= 1
             /\ \A c \in CORES : coreCloses[c] <= 1
ListenersAtMostOnce == \A l \in DOMAIN notified : notified[l] <= 1

\* at quiescence (both apps released everything they own, no task in flight)
NoLeak == AllDone => (/\ \A x \in RDR : rc[x] = 0
                      /\ \A c \in CORES : ~coreOpen[c])
ListenersExactlyOnce ==
  AllDone => \A l \in lis : notified[l] = 1

\* reachability probes (each must be VIOLATED in the matching config: vacuity check)
ProbeNoReopenOwned == ~(holds["app1"]["r2"] = 1 \/ holds["app1"]["w2"] = 1)
ProbeNoListenerFired == \A l \in lis : notified[l] = 0
ProbeNoAllDone == ~AllDone
=============================================================================

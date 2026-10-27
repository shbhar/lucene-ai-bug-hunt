-------------------------------- MODULE CRT --------------------------------
(***************************************************************************)
(* ControlledRealTimeReopenThread: reopen thread, waitForGeneration,       *)
(* close(), its RefreshListener, and an application thread that refreshes  *)
(* the same ReferenceManager.  Lucene main ea57360912ed.                   *)
(* Paths: lucene/core/src/java/org/apache/lucene/search/                   *)
(*                                                                         *)
(* model element   Java (ControlledRealTimeReopenThread.java unless noted) *)
(*  finish         :44 volatile boolean                                    *)
(*  waitingGen     :45 AtomicLong                                          *)
(*  sg             :46 volatile searchingGen (MAX = Long.MAX_VALUE)        *)
(*  permits        :49 Semaphore reloadRequests                            *)
(*  wr / wset      :51 waitingRoom monitor and its wait set                *)
(*  lstart         :89 HandleRefresh.refreshStartGen                       *)
(*  Before         :92-97  refreshStartGen = writer.getMaxCompletedSeqNo   *)
(*  After          :100-105 synchronized: searchingGen = refreshStartGen;  *)
(*                 notifyAll.  Called from ReferenceManager.java:182 in a  *)
(*                 finally block, i.e. also when the refresh threw         *)
(*  refresh steps  ReferenceManager.maybeRefreshBlocking :234-244 ->       *)
(*                 doMaybeRefresh :154-188 (lock, before, open, after)     *)
(*  cv             generation contained in the manager's current searcher *)
(*  BG*            run() :190-219 (minStale = 0, maxStale = infinite, so   *)
(*                 the thread sleeps in tryAcquire until a permit arrives) *)
(*                 an exception from maybeRefreshBlocking ends the thread  *)
(*                 (:214-216 rethrows as RuntimeException)                 *)
(*  W*             waitForGeneration(targetGen, -1) :159-187               *)
(*  C*             close() :110-129 (join, then searchingGen = MAX, notify)*)
(*  A*             an application thread calling mgr.maybeRefreshBlocking()*)
(*                 (ReferenceManager is a separate public object; the      *)
(*                 listener registered at :85 is never removed)            *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS NumWaiters,     \* threads that index one doc and wait for its seqNo
          AppRefreshes,   \* refreshes done by an application thread
          AppAfterClose,  \* TRUE: the application thread refreshes only after close() returned
          AppBeforeCloseDone, \* TRUE: the application refresh must START before close() returned
          RefreshFail,    \* a refresh attempt may throw (any refresher)
          JoinInterrupt,  \* close() may be interrupted in join() (open PR #16210)
          Fix,            \* "none" | "notify-on-success" | "monotone-sg" | "both"
          Mut             \* vacuity mutant: "none" | "lossy-signal" (no permit memory)

MAX == 100
Ws == 1..NumWaiters
BG == NumWaiters + 1
CL == NumWaiters + 2
AP == NumWaiters + 3
Procs == Ws \cup {BG, CL, AP}

VARIABLES idx, cv, lock, finish, waitingGen, sg, permits, wr, wset, lstart, listening,
          pc, tgt, hasW, exc, appLeft, closeDone, bgDead, atMax

vars == <<idx, cv, lock, finish, waitingGen, sg, permits, wr, wset, lstart, listening,
          pc, tgt, hasW, exc, appLeft, closeDone, bgDead, atMax>>

Init == /\ idx = 0 /\ cv = 0 /\ lock = 0 /\ finish = FALSE /\ waitingGen = 0 /\ sg = 0
        /\ permits = 0 /\ wr = 0 /\ wset = {} /\ lstart = 0 /\ listening = TRUE
        /\ pc = [p \in Procs |-> IF p = AP THEN "astart" ELSE IF p = BG THEN "loop"
                                 ELSE IF p = CL THEN "c0" ELSE "write"]
        /\ tgt = [w \in Ws |-> 0] /\ hasW = FALSE /\ exc = [p \in Procs |-> FALSE]
        /\ appLeft = AppRefreshes /\ closeDone = FALSE /\ bgDead = FALSE
        /\ atMax = {}   \* ghost: waiters blocked in waitForGeneration when close() set MAX

Goto(p, l) == pc' = [pc EXCEPT ![p] = l]
Wake(S) == S          \* notifyAll: every waiter in the wait set must re-acquire the monitor

\* ---------- shared refresh (doMaybeRefresh) for p \in {BG, AP} ----------
\* labels: <x>lock, <x>before, <x>open, <x>after, <x>unlock ; <x> = "b" or "a"
RLock(p, x, next) == /\ pc[p] = x \o "lock" /\ lock = 0 /\ lock' = p /\ Goto(p, x \o "before")
                     /\ UNCHANGED <<idx, cv, finish, waitingGen, sg, permits, wr, wset, lstart,
                                    listening, tgt, hasW, exc, appLeft, closeDone, bgDead>>
RBefore(p, x) == /\ pc[p] = x \o "before"
                 /\ lstart' = IF listening THEN idx ELSE lstart
                 /\ Goto(p, x \o "open")
                 /\ UNCHANGED <<idx, cv, lock, finish, waitingGen, sg, permits, wr, wset, listening,
                                tgt, hasW, exc, appLeft, closeDone, bgDead>>
ROpen(p, x) == /\ pc[p] = x \o "open"
               /\ \/ /\ cv' = idx /\ UNCHANGED exc                    \* opened (or no change)
                  \/ /\ RefreshFail /\ cv < idx                       \* openIfChanged/factory threw
                     /\ exc' = [exc EXCEPT ![p] = TRUE] /\ UNCHANGED cv
               /\ Goto(p, x \o "after")
               /\ UNCHANGED <<idx, lock, finish, waitingGen, sg, permits, wr, wset, lstart, listening,
                              tgt, hasW, appLeft, closeDone, bgDead>>
RAfter(p, x) == /\ pc[p] = x \o "after"
                /\ IF listening /\ ~(Fix \in {"notify-on-success", "both"} /\ exc[p])
                   THEN /\ wr = 0                                  \* synchronized (waitingRoom)
                        /\ sg' = IF Fix \in {"monotone-sg", "both"} /\ sg > lstart THEN sg ELSE lstart
                        /\ wset' = {}                             \* notifyAll
                        /\ pc' = [q \in Procs |-> IF q = p THEN x \o "unlock"
                                                  ELSE IF q \in wset THEN "reacq" ELSE pc[q]]
                   ELSE /\ Goto(p, x \o "unlock") /\ UNCHANGED <<sg, wset>>
                /\ UNCHANGED <<idx, cv, lock, finish, waitingGen, permits, wr, lstart, listening,
                               tgt, hasW, exc, appLeft, closeDone, bgDead>>
RUnlock(p, x, okLabel) ==
  /\ pc[p] = x \o "unlock" /\ lock' = 0
  /\ IF exc[p]
     THEN /\ Goto(p, IF p = BG THEN "dead" ELSE okLabel)   \* BG: RuntimeException ends run()
          /\ bgDead' = (bgDead \/ p = BG)
     ELSE /\ Goto(p, okLabel) /\ UNCHANGED bgDead
  /\ exc' = [exc EXCEPT ![p] = FALSE]
  /\ UNCHANGED <<idx, cv, finish, waitingGen, sg, permits, wr, wset, lstart, listening, tgt, hasW,
                 appLeft, closeDone>>
Refresher(p, x, okLabel) == RLock(p, x, okLabel) \/ RBefore(p, x) \/ ROpen(p, x) \/ RAfter(p, x)
                            \/ RUnlock(p, x, okLabel)

\* ---------- reopen thread run() ----------
UB == UNCHANGED <<idx, cv, lock, finish, waitingGen, sg, wr, wset, lstart, listening, tgt, exc,
                  appLeft, closeDone, bgDead>>
BGLoop == /\ pc[BG] = "loop"
          /\ IF finish THEN Goto(BG, "term") /\ UNCHANGED <<hasW, permits>>
             ELSE /\ hasW' = (waitingGen > sg)
                  /\ Goto(BG, IF waitingGen > sg THEN "block" ELSE "sleep") \* minStale 0: no sleep
                  /\ UNCHANGED permits
          /\ UNCHANGED <<idx, cv, lock, finish, waitingGen, sg, wr, wset, lstart, listening, tgt, exc,
                         appLeft, closeDone, bgDead>>
\* :199-202 compute nextReopenStartNS / sleepNS (the window between the waitingGen read and tryAcquire)
BGSleep == /\ pc[BG] = "sleep" /\ Goto(BG, "acq") /\ UNCHANGED <<hasW, permits>> /\ UB
BGAcq == /\ pc[BG] = "acq" /\ permits > 0 /\ permits' = 0   \* tryAcquire + drainPermits
         /\ Goto(BG, "loop") /\ UNCHANGED hasW /\ UB
\* the reopen (lastReopenStartNS = now; manager.maybeRefreshBlocking()) starts at label "block"
\* (= "b" \o "lock") of Refresher(BG, "b", "loop")

\* ---------- waiters ----------
UW == UNCHANGED <<cv, lock, finish, sg, wset, lstart, listening, hasW, exc, appLeft, closeDone, bgDead>>
WWrite(w) == /\ pc[w] = "write" /\ idx' = idx + 1 /\ tgt' = [tgt EXCEPT ![w] = idx + 1]
             /\ Goto(w, "enter") /\ UNCHANGED <<waitingGen, permits, wr>> /\ UW
WEnter(w) == /\ pc[w] = "enter" /\ wr = 0 /\ wr' = w
             /\ IF tgt[w] > sg
                THEN /\ waitingGen' = IF tgt[w] > waitingGen THEN tgt[w] ELSE waitingGen
                     /\ permits' = IF Mut = "lossy-signal" /\ pc[BG] # "acq" THEN permits
                                   ELSE permits + 1
                     /\ Goto(w, "check")
                ELSE /\ Goto(w, "rettrue") /\ UNCHANGED <<waitingGen, permits>>
             /\ UNCHANGED <<idx, tgt>> /\ UW
WCheck(w) == /\ pc[w] = "check"
             /\ IF tgt[w] > sg
                THEN /\ wr' = 0 /\ wset' = wset \cup {w} /\ Goto(w, "waiting")
                ELSE /\ Goto(w, "rettrue") /\ UNCHANGED <<wr, wset>>
             /\ UNCHANGED <<idx, cv, lock, finish, waitingGen, sg, permits, lstart, listening, tgt,
                            hasW, exc, appLeft, closeDone, bgDead>>
WReacq(w) == /\ pc[w] = "reacq" /\ wr = 0 /\ wr' = w /\ Goto(w, "check")
             /\ UNCHANGED <<idx, waitingGen, permits, tgt>> /\ UW
WRet(w) == /\ pc[w] = "rettrue" /\ wr' = 0 /\ Goto(w, "done")
           /\ UNCHANGED <<idx, waitingGen, permits, tgt>> /\ UW

\* ---------- close() ----------
UC == UNCHANGED <<idx, cv, lock, waitingGen, lstart, tgt, hasW, exc, appLeft, bgDead>>
C0 == /\ pc[CL] = "c0" /\ finish' = TRUE
      /\ permits' = IF Mut = "lossy-signal" /\ pc[BG] # "acq" THEN permits ELSE permits + 1
      /\ Goto(CL, "join") /\ UNCHANGED <<sg, wr, wset, listening, closeDone>> /\ UC
CJoin == /\ pc[CL] = "join"
         /\ \/ /\ pc[BG] \in {"term", "dead"} /\ Goto(CL, "max")
            \/ /\ JoinInterrupt /\ Goto(CL, "done")     \* ThreadInterruptedException before :123
         /\ UNCHANGED <<finish, permits, sg, wr, wset, listening, closeDone>> /\ UC
CMax == /\ pc[CL] = "max" /\ sg' = MAX /\ Goto(CL, "notify")
        /\ atMax' = {w \in Ws : pc[w] \in {"waiting", "reacq", "check"}}
        /\ UNCHANGED <<finish, permits, wr, wset, listening, closeDone>> /\ UC
CNotify == /\ pc[CL] = "notify" /\ wr = 0 /\ wset' = {}
           /\ pc' = [q \in Procs |-> IF q = CL THEN "done" ELSE IF q \in wset THEN "reacq" ELSE pc[q]]
           /\ UNCHANGED listening
           /\ closeDone' = TRUE
           /\ UNCHANGED <<finish, permits, sg, wr>> /\ UC

\* ---------- application thread ----------
AStart == /\ pc[AP] = "astart"
          /\ IF appLeft = 0 \/ (AppBeforeCloseDone /\ closeDone)
             THEN /\ Goto(AP, "done") /\ UNCHANGED appLeft
             ELSE /\ AppAfterClose => closeDone
                  /\ Goto(AP, "alock") /\ appLeft' = appLeft - 1
          /\ UNCHANGED <<idx, cv, lock, finish, waitingGen, sg, permits, wr, wset, lstart, listening,
                         tgt, hasW, exc, closeDone, bgDead>>

Final == {"done", "term", "dead"}
AllDone == \A p \in Procs : pc[p] \in Final
Next == \/ BGLoop \/ BGSleep \/ BGAcq \/ Refresher(BG, "b", "loop")
        \/ \E w \in Ws : WWrite(w) \/ WEnter(w) \/ WCheck(w) \/ WReacq(w) \/ WRet(w)
        \/ C0 \/ CJoin \/ CMax \/ CNotify
        \/ AStart \/ Refresher(AP, "a", "astart")
        \/ (AllDone /\ UNCHANGED vars)
\* CMax is the only action that writes the ghost atMax
NextG == CMax \/ (Next /\ UNCHANGED atMax)
Spec == Init /\ [][NextG]_vars

\* ---------- properties ----------
\* waitForGeneration returned true for a generation the searcher does not contain, while the
\* thread was still open (CRT javadoc :155 "true if the targetGen is now available", :221)
Visible == \A w \in Ws : (pc[w] = "rettrue" /\ ~finish) => tgt[w] <= cv
SearchingGenSound == (~finish) => sg <= cv
\* hang = TLC deadlock (no step enabled while a waiter has not returned)
\* a waiter that was blocked when close() published MAX must not block again
NoReblockAfterClose == closeDone => \A w \in atMax : pc[w] # "waiting"
ProbeWaited == \A w \in Ws : pc[w] # "waiting"
ProbeAppAfterClose == ~(closeDone /\ pc[AP] = "aafter")
=============================================================================

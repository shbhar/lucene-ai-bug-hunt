------------------------------- MODULE RefMgr -------------------------------
(***************************************************************************)
(* ReferenceManager / SearcherManager: reader ref counts, refresh, close   *)
(* and a refresh listener, with the exception paths.  Lucene main          *)
(* ea57360912ed.  Paths under lucene/core/src/java/org/apache/lucene/.     *)
(*                                                                         *)
(* model element      Java                                                 *)
(*  current           search/ReferenceManager.java:41 (volatile)           *)
(*  lock              :43 refreshLock (ReentrantLock)                      *)
(*  mon               the RM object monitor: swapReference :53, close :134 *)
(*  Acq1/Acq2/Acq3    :91-116 acquire() loop; Acq3 = the ISE check :101    *)
(*  Close*            :134-142 close() -> swapReference(null)              *)
(*  RStart            :206-208 / :234-235 ensureOpen() in maybeRefresh*    *)
(*  RLock             :210 tryLock / :238 lock                             *)
(*  RAcq*             :164 acquire()                                       *)
(*  RBefore           :166 notifyRefreshListenersBefore()                  *)
(*  RRefresh          :167 refreshIfNeeded(): SearcherManager.java:194-206 *)
(*                    returns null if the index did not change; may throw  *)
(*                    (openIfChanged I/O error, SearcherFactory failure:   *)
(*                    SearcherManager.getSearcher :248-267 decRefs the new *)
(*                    reader itself, so a failed refresh creates nothing)  *)
(*  RSwapMon/RSwap1/2 :171-173 swapReference: ensureOpen; current = new;   *)
(*                    release(old)                                         *)
(*  RRelNew           :174-178 finally { if (!refreshed) release(new) }    *)
(*  RRelRef           :181 finally { release(reference); ...               *)
(*  RAfter            :182          notifyRefreshListenersRefreshed(..) }  *)
(*  RUnlock           :186 refreshLock.unlock()                            *)
(*  DecRef            index/IndexReader.java decRef(): rc-1; at 0 the      *)
(*                    reader is marked closed, then doClose()/closed       *)
(*                    listeners run and may throw IOException              *)
(*  lstart/lgen       ControlledRealTimeReopenThread.java:88-106           *)
(*                    HandleRefresh: before: refreshStartGen = writer max  *)
(*                    completed seqNo; after(any): searchingGen = start.   *)
(*                    LiveFieldValues.java:55-73 has the same shape (drops *)
(*                    'old' in afterRefresh regardless of the outcome)     *)
(*  idx / cv          writer generation / content generation of a reader   *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS MaxIdx,        \* writer changes (bounds readers to MaxIdx + 1)
          RefreshFail,   \* refreshIfNeeded may throw when there is a change
          CloseFail,     \* closing a reader (rc 1 -> 0) may throw afterwards
          NumRefreshers, Rounds,
          NumSearchers,
          WithClose,
          Fix,           \* "none" | "notify-on-success"
          Mut            \* mutants for vacuity: "none" | "refresh-no-incref" | "swap-release-new"

Refs  == 1..NumRefreshers
Srchs == (NumRefreshers + 1)..(NumRefreshers + NumSearchers)
CloserId == NumRefreshers + NumSearchers + 1
Procs == Refs \cup Srchs \cup (IF WithClose THEN {CloserId} ELSE {})
MaxR == MaxIdx + 1
Rd == 1..MaxR

VARIABLES current, rc, closed, cv, nreaders, idx, lock, mon,
          pc, ref, newr, old, refreshed, exc, rounds, bcalled, acalled,
          lstart, lgen, badDecRef, ise

vars == <<current, rc, closed, cv, nreaders, idx, lock, mon, pc, ref, newr, old,
          refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>

Init ==
  /\ current = 1 /\ nreaders = 1 /\ idx = 0
  /\ rc = [r \in Rd |-> IF r = 1 THEN 1 ELSE 0]
  /\ closed = [r \in Rd |-> FALSE]
  /\ cv = [r \in Rd |-> 0]
  /\ lock = 0 /\ mon = 0
  /\ pc = [p \in Procs |-> "start"]
  /\ ref = [p \in Procs |-> 0] /\ newr = [p \in Procs |-> 0] /\ old = [p \in Procs |-> 0]
  /\ refreshed = [p \in Procs |-> FALSE] /\ exc = [p \in Procs |-> FALSE]
  /\ rounds = [p \in Procs |-> Rounds]
  /\ bcalled = [p \in Procs |-> FALSE] /\ acalled = [p \in Procs |-> FALSE]
  /\ lstart = 0 /\ lgen = 0 /\ badDecRef = FALSE /\ ise = FALSE

\* IndexReader.decRef on r; thr = the close threw (only possible when rc reaches 0)
DecRefOutcomes(r) == IF CloseFail /\ rc[r] = 1 THEN {FALSE, TRUE} ELSE {FALSE}
DoDecRef(r) ==
  IF rc[r] = 0
  THEN /\ badDecRef' = TRUE /\ UNCHANGED <<rc, closed>>
  ELSE /\ rc' = [rc EXCEPT ![r] = @ - 1]
       /\ closed' = IF rc[r] = 1 THEN [closed EXCEPT ![r] = TRUE] ELSE closed
       /\ UNCHANGED badDecRef

Goto(p, l) == pc' = [pc EXCEPT ![p] = l]

Writer == /\ idx < MaxIdx /\ idx' = idx + 1
          /\ UNCHANGED <<current, rc, closed, cv, nreaders, lock, mon, pc, ref, newr, old,
                         refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>

\* ---- acquire() as used by searchers ("a") and by doMaybeRefresh ("r") ----
Acq1(p, pre, onNull) ==
  /\ pc[p] = pre \o "acq1"
  /\ IF current = 0
     THEN /\ Goto(p, onNull) /\ exc' = [exc EXCEPT ![p] = TRUE] /\ UNCHANGED ref   \* ACE
     ELSE /\ ref' = [ref EXCEPT ![p] = current] /\ Goto(p, pre \o "acq2") /\ UNCHANGED exc
  /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, newr, old, refreshed,
                 rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
Acq2(p, pre, onOk) ==
  /\ pc[p] = pre \o "acq2"
  /\ IF rc[ref[p]] > 0
     THEN /\ rc' = IF Mut = "refresh-no-incref" /\ pre = "r" THEN rc ELSE [rc EXCEPT ![ref[p]] = @ + 1]
          /\ Goto(p, onOk)
     ELSE /\ Goto(p, pre \o "acq3") /\ UNCHANGED rc
  /\ UNCHANGED <<current, closed, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed, exc,
                 rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
Acq3(p, pre, onIse) ==
  /\ pc[p] = pre \o "acq3"
  /\ IF current = ref[p]   \* rc[ref] = 0 here (rc never rises from 0)
     THEN /\ ise' = TRUE /\ exc' = [exc EXCEPT ![p] = TRUE] /\ Goto(p, onIse)
     ELSE /\ Goto(p, pre \o "acq1") /\ UNCHANGED <<ise, exc>>
  /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed,
                 rounds, bcalled, acalled, lstart, lgen, badDecRef>>

\* ---- searchers: acquire / use / release ----
SUse(p) == /\ pc[p] = "use" /\ Goto(p, "rel")
           /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old,
                          refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
SRel(p) == /\ pc[p] = "rel" /\ DoDecRef(ref[p]) /\ Goto(p, "done")
           /\ UNCHANGED <<current, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed, exc,
                          rounds, bcalled, acalled, lstart, lgen, ise>>
SStart(p) == /\ pc[p] = "start" /\ Goto(p, "aacq1")
             /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old,
                            refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>

\* ---- close() ----
Close0 == /\ pc[CloserId] = "start" /\ mon = 0 /\ mon' = CloserId /\ Goto(CloserId, "c1")
          /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, ref, newr, old, refreshed,
                         exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
Close1 == /\ pc[CloserId] = "c1"
          /\ IF current = 0
             THEN /\ mon' = 0 /\ Goto(CloserId, "done") /\ UNCHANGED <<current, old>>
             ELSE /\ old' = [old EXCEPT ![CloserId] = current] /\ current' = 0
                  /\ Goto(CloserId, "c2") /\ UNCHANGED mon
          /\ UNCHANGED <<rc, closed, cv, nreaders, idx, lock, ref, newr, refreshed, exc, rounds,
                         bcalled, acalled, lstart, lgen, badDecRef, ise>>
Close2 == /\ pc[CloserId] = "c2" /\ DoDecRef(old[CloserId]) /\ mon' = 0 /\ Goto(CloserId, "done")
          /\ UNCHANGED <<current, cv, nreaders, idx, lock, ref, newr, old, refreshed, exc, rounds,
                         bcalled, acalled, lstart, lgen, ise>>

\* ---- maybeRefreshBlocking() / doMaybeRefresh() ----
U(p) == UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed,
                    exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
RStart(p) == /\ pc[p] = "start"
             /\ IF rounds[p] = 0 \/ current = 0 THEN Goto(p, "done") ELSE Goto(p, "lock")
             /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old,
                            refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
RLock(p) == /\ pc[p] = "lock" /\ lock = 0 /\ lock' = p /\ Goto(p, "racq1")
            /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, mon, ref, newr, old, refreshed,
                           exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
RBefore(p) == /\ pc[p] = "before" /\ lstart' = idx /\ bcalled' = [bcalled EXCEPT ![p] = TRUE]
              /\ Goto(p, "refresh")
              /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old,
                             refreshed, exc, rounds, acalled, lgen, badDecRef, ise>>
RRefresh(p) ==
  /\ pc[p] = "refresh"
  /\ IF idx = cv[ref[p]]
     THEN /\ Goto(p, "relref") /\ UNCHANGED <<rc, cv, nreaders, newr, exc>>     \* null: no change
     ELSE \/ /\ LET n == nreaders + 1 IN                                       \* new reference
                  /\ nreaders' = n /\ rc' = [rc EXCEPT ![n] = 1] /\ cv' = [cv EXCEPT ![n] = idx]
                  /\ newr' = [newr EXCEPT ![p] = n]
             /\ Goto(p, "swapmon") /\ UNCHANGED exc
          \/ /\ RefreshFail                                                    \* throws
             /\ exc' = [exc EXCEPT ![p] = TRUE] /\ Goto(p, "relref")
             /\ UNCHANGED <<rc, cv, nreaders, newr>>
  /\ UNCHANGED <<current, closed, idx, lock, mon, ref, old, refreshed, rounds, bcalled, acalled,
                 lstart, lgen, badDecRef, ise>>
RSwapMon(p) == /\ pc[p] = "swapmon" /\ mon = 0 /\ mon' = p /\ Goto(p, "swap1")
               /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, ref, newr, old,
                              refreshed, exc, rounds, bcalled, acalled, lstart, lgen, badDecRef, ise>>
RSwap1(p) == /\ pc[p] = "swap1"
             /\ IF current = 0
                THEN /\ exc' = [exc EXCEPT ![p] = TRUE] /\ mon' = 0 /\ Goto(p, "relnew")
                     /\ UNCHANGED <<current, old>>                              \* ensureOpen: ACE
                ELSE /\ old' = [old EXCEPT ![p] = current] /\ current' = newr[p]
                     /\ Goto(p, "swap2") /\ UNCHANGED <<exc, mon>>
             /\ UNCHANGED <<rc, closed, cv, nreaders, idx, lock, ref, newr, refreshed, rounds,
                            bcalled, acalled, lstart, lgen, badDecRef, ise>>
RSwap2(p) == /\ pc[p] = "swap2" /\ mon' = 0 /\ DoDecRef(IF Mut = "swap-release-new" THEN newr[p] ELSE old[p])
             /\ \E thr \in DecRefOutcomes(old[p]) :
                  IF thr THEN /\ exc' = [exc EXCEPT ![p] = TRUE] /\ Goto(p, "relnew") /\ UNCHANGED refreshed
                         ELSE /\ refreshed' = [refreshed EXCEPT ![p] = TRUE] /\ Goto(p, "relref") /\ UNCHANGED exc
             /\ UNCHANGED <<current, cv, nreaders, idx, lock, ref, newr, old, rounds, bcalled,
                            acalled, lstart, lgen, ise>>
RRelNew(p) == /\ pc[p] = "relnew" /\ DoDecRef(newr[p]) /\ Goto(p, "relref")
              /\ UNCHANGED <<current, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed, exc,
                             rounds, bcalled, acalled, lstart, lgen, ise>>
RRelRef(p) == /\ pc[p] = "relref" /\ DoDecRef(ref[p])
              /\ \E thr \in DecRefOutcomes(ref[p]) :
                   IF thr THEN /\ exc' = [exc EXCEPT ![p] = TRUE] /\ Goto(p, "unlock")  \* skips :182
                          ELSE /\ Goto(p, "after") /\ UNCHANGED exc
              /\ UNCHANGED <<current, cv, nreaders, idx, lock, mon, ref, newr, old, refreshed,
                             rounds, bcalled, acalled, lstart, lgen, ise>>
RAfter(p) == /\ pc[p] = "after"
             /\ lgen' = IF Fix = "notify-on-success" /\ exc[p] THEN lgen ELSE lstart
             /\ acalled' = [acalled EXCEPT ![p] = TRUE] /\ Goto(p, "unlock")
             /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, lock, mon, ref, newr, old,
                            refreshed, exc, rounds, bcalled, lstart, badDecRef, ise>>
RUnlock(p) == /\ pc[p] = "unlock" /\ lock' = IF lock = p THEN 0 ELSE lock
              /\ rounds' = [rounds EXCEPT ![p] = @ - 1] /\ exc' = [exc EXCEPT ![p] = FALSE]
              /\ refreshed' = [refreshed EXCEPT ![p] = FALSE] /\ newr' = [newr EXCEPT ![p] = 0]
              /\ bcalled' = [bcalled EXCEPT ![p] = FALSE] /\ acalled' = [acalled EXCEPT ![p] = FALSE]
              /\ Goto(p, "start")
              /\ UNCHANGED <<current, rc, closed, cv, nreaders, idx, mon, ref, old, lstart, lgen,
                             badDecRef, ise>>

AllDone == \A p \in Procs : pc[p] = "done"
Next ==
  \/ Writer
  \/ \E p \in Srchs : SStart(p) \/ Acq1(p, "a", "done") \/ Acq2(p, "a", "use") \/ Acq3(p, "a", "done")
                      \/ SUse(p) \/ SRel(p)
  \/ \E p \in Refs : RStart(p) \/ RLock(p) \/ Acq1(p, "r", "unlock") \/ Acq2(p, "r", "before")
                     \/ Acq3(p, "r", "unlock") \/ RBefore(p) \/ RRefresh(p) \/ RSwapMon(p)
                     \/ RSwap1(p) \/ RSwap2(p) \/ RRelNew(p) \/ RRelRef(p) \/ RAfter(p) \/ RUnlock(p)
  \/ (WithClose /\ (Close0 \/ Close1 \/ Close2))
  \/ (AllDone /\ UNCHANGED vars)
Spec == Init /\ [][Next]_vars

\* ---- properties ----
CurrentOpen     == current # 0 => ~closed[current]          \* else acquire() throws ISE forever
NoUseAfterClose == \A p \in Srchs : pc[p] = "use" => ~closed[ref[p]]
NoBadDecRef     == ~badDecRef
NoISE           == ~ise
RcBounded       == \A r \in Rd : rc[r] <= NumSearchers + NumRefreshers + 1
\* the listener's claim "current includes generation lgen" (CRT.getSearchingGen javadoc :221)
ListenerSound   == current # 0 => lgen <= cv[current]
\* every beforeRefresh is matched by an afterRefresh (RefreshListener javadoc :298-305)
ListenerPaired  == \A p \in Refs : pc[p] = "unlock" /\ bcalled[p] => acalled[p]
NoLeak == AllDone =>
  \A r \in 1..nreaders : IF r = current THEN rc[r] = 1 ELSE rc[r] = 0
\* vacuity probes (must be violated in a run that is meant to reach them)
ProbeSwapHappened == nreaders < 2
ProbeRefreshFailed == ~(\E p \in Refs : exc[p] /\ pc[p] = "relref")
=============================================================================

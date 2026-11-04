----------------------- MODULE AddIndexesMergeSource -----------------------
(***************************************************************************)
(* TLA+ model of the addIndexes(CodecReader...) merge-source protocol of   *)
(* IndexWriter (Lucene main a9779ef3c193), with deleteAll, rollback, a     *)
(* running natural merge, merge failures and addIndexes(Directory...).     *)
(* It re-uses the abortMerges / rollback / merge-thread structure of       *)
(* iw-forcemerge-wait-protocol-tla's ForceMergeWait.tla, re-scoped to the addIndexes merge source     *)
(* (the forceMerge wait loop itself is iw-forcemerge-wait-protocol-tla's and is not repeated here).   *)
(*                                                                         *)
(* Granularity: one action = one synchronized(IndexWriter) block, or one   *)
(* unsynchronized stretch between such blocks.  wait(1000) loops are       *)
(* modelled as "the waiter proceeds once its condition holds" (the timeout *)
(* only re-checks), so a waiter whose condition never becomes true shows   *)
(* up as a TLC deadlock.  Terminal states are an explicit Done (action     *)
(* Finished), so deadlock checking stays ON.                               *)
(*                                                                         *)
(* File:line map (lucene/core/src/java/org/apache/lucene/index/...)        *)
(*  addIndexes(CodecReader...) caller c                                    *)
(*   CFlush      IndexWriter.java:3276-3286 ensureOpen, flush(false,true)   *)
(*               (doFlush takes fullFlushLock, held by deleteAll 2764)     *)
(*   CCheck      3298-3304 sync: ensureOpen(); merges.areEnabled()==false  *)
(*               -> AlreadyClosedException("this IndexWriter is closed")   *)
(*   CFind+CReg  3306-3311 findMerges(readers); registerMerge 3408-3417:   *)
(*               sync only to enqueue, no enabled/closed re-check          *)
(*   CSchedCMS   3312 -> ConcurrentMergeScheduler.java:542-590: loop      *)
(*               getNextMerge (IndexWriter.java:3420-3429: pop, add to     *)
(*               runningMerges), start a MergeThread (no closed check)     *)
(*   CSchedSMS   3312 -> SerialMergeScheduler.java merge(): synchronized,  *)
(*               getNextMerge + mergeSource.merge(merge) on the caller     *)
(*   CAwait      3313 spec.await() (MergePolicy.java:533-544)              *)
(*   CDecide     3314-3315 allMatch hasCompletedSuccessfully; failure      *)
(*               branch 3371-3388 (isAborted -> MergeAbortedException,     *)
(*               getException -> rethrow, else RuntimeException)           *)
(*   CRegSegs    3350-3370 sync: ensureOpen(); reserveDocs; segmentInfos.  *)
(*               addAll(infos); checkpoint()  (no deleteAll/abort check)   *)
(*  addIndexes merge m (AddIndexesMergeSource.merge 3459-3480 ->           *)
(*  addIndexesReaderMerge 3491-3645)                                       *)
(*   MStart      3493-3494 mergeInit, checkAborted; 3583-3586 checkAborted,*)
(*               sync runningAddIndexesMerges.add                          *)
(*   MMerge      3589 merger.merge() (SegmentMerger.java:117-121           *)
(*               mergeFieldInfos registers the fields in                   *)
(*               globalFieldNumberMap), finally 3591-3594 sync remove,     *)
(*               notifyAll; may fail (I/O) or abort (rate limiter)         *)
(*   MCfs        3609-3618 sync: merge.checkAborted() (last abort check)   *)
(*   MFinal      3621-3641 CFS + .si write (may fail), 3465-3470 InfoStream *)
(*               "merged new segment"                                      *)
(*   MClose      3472-3479 catch: handleMergeException 5030-5058 (MAE      *)
(*               swallowed, others rethrown); finally sync: merge.close    *)
(*               (success) (MergePolicy.java:285-299 completes the future),*)
(*               onMergeFinished (3432-3436 runningMerges.remove)          *)
(*  deleteAll d (2750-2815)                                                *)
(*   DAStart     2764 fullFlushLock; 2767-2771 sync abortMerges 2821-2870: *)
(*               merges.disable; abort pending natural merges;             *)
(*               abortPendingMerges 3443-3457 (setAborted, close(false),   *)
(*               onMergeFinished, clear); setAborted on runningMerges      *)
(*   DAWait      2849-2861 wait while runningMerges+runningAddIndexesMerges*)
(*   DAFinish    2781 merges.enable() (ensureOpen!), 2785 segmentInfos.    *)
(*               clear(), 2800 globalFieldNumberMap.clear()                *)
(*  rollback r (2576-2720)                                                 *)
(*   RBStart     2581 shouldClose (1374-1395): closing := true             *)
(*   RBAbort/RBWait 2621-2627 sync abortMerges (merges stay disabled)      *)
(*   RBCms       2634 mergeScheduler.close() (CMS: waits for its threads)  *)
(*   RBFinal     2640-2690 sync: rollbackSegmentInfos, closed := true      *)
(*  natural merge n: a merge already running when the scenario starts      *)
(*   NatExit     IndexWriter.merge finally (5088-5107): runningMerges.remove*)
(*  addIndexes(Directory...) caller x (3103-3226)                          *)
(*   XFlush 3104-3119; XFields 3177-3182 globalFieldNumberMap.addOrGet     *)
(*   (outside the IW monitor); XCopy 3183 copySegmentAsIs; XReg 3194-3211  *)
(*   sync: ensureOpen; reserveDocs; segmentInfos.addAll; checkpoint        *)
(*                                                                         *)
(* Abstraction of the field schema: globalFieldNumberMap is an epoch       *)
(* counter; deleteAll's clear() starts a new epoch.  A segment records the *)
(* epoch in which its fields were registered.  "The writer's field map     *)
(* covers every registered segment" is: every segment's epoch = epoch.     *)
(*                                                                         *)
(* Fix switches (for mutation / fix validation):                           *)
(*   FIX_SCHEMA  the registration blocks (CRegSegs, XReg) re-register the  *)
(*               new segments' FieldInfos in the current map               *)
(*   FIX_ACE     CCheck reports a deleteAll in progress (merges disabled   *)
(*               while not closing) as MergeAbortedException, not ACE      *)
(*   FIX_184     registerMerge, under the IW monitor, aborts the merge if  *)
(*               merges are disabled (the known LFV-184 window)            *)
(*   MUT_NOFIN   mutation: abortPendingMerges does not close the merge     *)
(*               (drops close(false)); must produce a hang                 *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS Callers, NM, XCallers, SCHED, ALLOW_DA, ALLOW_RB, NAT, MaxFail,
          FIX_SCHEMA, FIX_ACE, FIX_184, MUT_NOFIN

Own(c) == {<<c, i>> : i \in 1..NM[c]}
MIds == UNION {Own(c) : c \in Callers}
NoM == <<"none", 0>>
NatId == <<"nat", 0>>

VARIABLES
  ffl,        \* fullFlushLock holder: "none" | "da"
  pend,       \* AddIndexesMergeSource.pendingAddIndexesMerges (Seq of MIds)
  run,        \* IndexWriter.runningMerges (addIndexes merges + "nat")
  raim,       \* IndexWriter.runningAddIndexesMerges (merges inside merger.merge())
  mpc,        \* merge phase
  mres,       \* completion future: "none" | "ok" | "fail"
  mab,        \* OneMerge aborted flag
  mexc,       \* exception: "none" | "io" | "abort" | "ace"
  mep,        \* field-map epoch in which the merge registered its fields (0 = not yet)
  mthr,       \* executing thread: "none" | "cms" | a caller (SerialMergeScheduler)
  natpc,      \* natural merge: "none" | "run" | "done"
  segs,       \* IndexWriter.segmentInfos: set of [id, ep]
  epoch,      \* globalFieldNumberMap epoch
  enabled, closing, closed, cmsClosed,
  smsOwner,   \* SerialMergeScheduler monitor: "none" | "nat" | a caller
  cpc, cnext, cres, cur, aceOpen, foreign,
  dpc, dres, rpc,
  xpc, xep, xres,
  fails

vars == <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
          epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
          cres, cur, aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

SeqToSet(s) == {s[k] : k \in DOMAIN s}

Init ==
  /\ ffl = "none"
  /\ pend = <<>>
  /\ run = IF NAT THEN {NatId} ELSE {}
  /\ raim = {}
  /\ mpc = [m \in MIds |-> "new"]
  /\ mres = [m \in MIds |-> "none"]
  /\ mab = [m \in MIds |-> FALSE]
  /\ mexc = [m \in MIds |-> "none"]
  /\ mep = [m \in MIds |-> 0]
  /\ mthr = [m \in MIds |-> "none"]
  /\ natpc = IF NAT THEN "run" ELSE "none"
  /\ segs = {}
  /\ epoch = 1
  /\ enabled = TRUE /\ closing = FALSE /\ closed = FALSE /\ cmsClosed = FALSE
  /\ smsOwner = IF NAT /\ SCHED = "SMS" THEN "nat" ELSE "none"
  /\ cpc = [c \in Callers |-> "start"]
  /\ cnext = [c \in Callers |-> 1]
  /\ cres = [c \in Callers |-> "none"]
  /\ cur = [c \in Callers |-> NoM]
  /\ aceOpen = [c \in Callers |-> FALSE]
  /\ foreign = [c \in Callers |-> FALSE]
  /\ dpc = IF ALLOW_DA THEN "start" ELSE "done"
  /\ dres = "none"
  /\ rpc = IF ALLOW_RB THEN "start" ELSE "done"
  /\ xpc = [x \in XCallers |-> "start"]
  /\ xep = [x \in XCallers |-> 0]
  /\ xres = [x \in XCallers |-> "none"]
  /\ fails = 0

(* abortMerges' effect on the addIndexes queue and on runningMerges       *)
(* (IndexWriter.java:2836-2844, 3443-3457).                               *)
AbortQueuedAndRunning ==
  LET q == SeqToSet(pend)
      ai == {m \in run : m # NatId} IN
  /\ mab' = [m \in MIds |-> IF m \in q \cup ai THEN TRUE ELSE mab[m]]
  /\ mres' = [m \in MIds |-> IF m \in q /\ ~MUT_NOFIN THEN "fail" ELSE mres[m]]
  /\ mpc' = [m \in MIds |-> IF m \in q THEN "done" ELSE mpc[m]]
  /\ pend' = <<>>

----------------------------------------------------------------------------
(* addIndexes(CodecReader...) caller *)

CFlush(c) ==
  /\ cpc[c] = "start" /\ ffl = "none"
  /\ IF closed
       THEN /\ cpc' = [cpc EXCEPT ![c] = "done"] /\ cres' = [cres EXCEPT ![c] = "ace"]
       ELSE /\ cpc' = [cpc EXCEPT ![c] = "check"] /\ UNCHANGED cres
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cnext, cur,
                 aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

CCheck(c) ==
  /\ cpc[c] = "check"
  /\ IF closed \/ closing
       THEN /\ cpc' = [cpc EXCEPT ![c] = "done"] /\ cres' = [cres EXCEPT ![c] = "ace"]
            /\ UNCHANGED aceOpen
       ELSE IF ~enabled
         THEN /\ cpc' = [cpc EXCEPT ![c] = "done"]
              /\ IF FIX_ACE
                   THEN /\ cres' = [cres EXCEPT ![c] = "mae"] /\ UNCHANGED aceOpen
                   ELSE /\ cres' = [cres EXCEPT ![c] = "ace"]
                        /\ aceOpen' = [aceOpen EXCEPT ![c] = TRUE]
         ELSE /\ cpc' = [cpc EXCEPT ![c] = "reg"] /\ UNCHANGED <<cres, aceOpen>>
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cnext, cur,
                 foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

CReg(c) ==
  /\ cpc[c] = "reg"
  /\ LET m == <<c, cnext[c]>> IN
     /\ IF FIX_184 /\ ~enabled
          THEN /\ mab' = [mab EXCEPT ![m] = TRUE] /\ mres' = [mres EXCEPT ![m] = "fail"]
               /\ mpc' = [mpc EXCEPT ![m] = "done"] /\ UNCHANGED pend
          ELSE /\ pend' = Append(pend, m) /\ mpc' = [mpc EXCEPT ![m] = "queued"]
               /\ UNCHANGED <<mab, mres>>
     /\ IF cnext[c] < NM[c]
          THEN /\ cnext' = [cnext EXCEPT ![c] = @ + 1] /\ UNCHANGED cpc
          ELSE /\ cpc' = [cpc EXCEPT ![c] = "sched"] /\ UNCHANGED cnext
  /\ UNCHANGED <<ffl, run, raim, mexc, mep, mthr, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, smsOwner, cres, cur, aceOpen, foreign,
                 dpc, dres, rpc, xpc, xep, xres, fails>>

(* ConcurrentMergeScheduler.merge: one getNextMerge + thread start per step *)
CSchedCMS(c) ==
  /\ SCHED = "CMS" /\ cpc[c] = "sched"
  /\ IF Len(pend) > 0
       THEN LET m == Head(pend) IN
            /\ pend' = Tail(pend) /\ run' = run \cup {m}
            /\ mpc' = [mpc EXCEPT ![m] = "taken"] /\ mthr' = [mthr EXCEPT ![m] = "cms"]
            /\ UNCHANGED cpc
       ELSE /\ cpc' = [cpc EXCEPT ![c] = "await"] /\ UNCHANGED <<pend, run, mpc, mthr>>
  /\ UNCHANGED <<ffl, raim, mres, mab, mexc, mep, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, smsOwner, cnext, cres, cur, aceOpen,
                 foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

(* SerialMergeScheduler.merge: synchronized; runs every queued merge inline *)
CSchedSMS(c) ==
  /\ SCHED = "SMS" /\ cpc[c] = "sched" /\ cur[c] = NoM
  /\ smsOwner \in {"none", c}
  /\ IF Len(pend) > 0
       THEN LET m == Head(pend) IN
            /\ pend' = Tail(pend) /\ run' = run \cup {m}
            /\ mpc' = [mpc EXCEPT ![m] = "taken"] /\ mthr' = [mthr EXCEPT ![m] = c]
            /\ cur' = [cur EXCEPT ![c] = m] /\ smsOwner' = c
            /\ UNCHANGED cpc
       ELSE /\ cpc' = [cpc EXCEPT ![c] = "await"] /\ smsOwner' = "none"
            /\ UNCHANGED <<pend, run, mpc, mthr, cur>>
  /\ UNCHANGED <<ffl, raim, mres, mab, mexc, mep, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, cnext, cres, aceOpen, foreign, dpc,
                 dres, rpc, xpc, xep, xres, fails>>

CAwait(c) ==
  /\ cpc[c] = "await" /\ \A m \in Own(c) : mres[m] # "none"
  /\ cpc' = [cpc EXCEPT ![c] = "decide"]
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cnext, cres,
                 cur, aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

CDecide(c) ==
  /\ cpc[c] = "decide"
  /\ IF \A m \in Own(c) : mres[m] = "ok"
       THEN /\ cpc' = [cpc EXCEPT ![c] = "regsegs"] /\ UNCHANGED cres
       ELSE /\ cpc' = [cpc EXCEPT ![c] = "done"]
            /\ cres' = [cres EXCEPT ![c] =
                 IF \E m \in Own(c) : mab[m] THEN "mae"
                 ELSE IF \E m \in Own(c) : mexc[m] \in {"io", "ace"} THEN "io"
                 ELSE "rte"]
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cnext, cur,
                 aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

CRegSegs(c) ==
  /\ cpc[c] = "regsegs"
  /\ cpc' = [cpc EXCEPT ![c] = "done"]
  /\ IF closed \/ closing
       THEN /\ cres' = [cres EXCEPT ![c] = "ace"] /\ UNCHANGED segs
       ELSE /\ cres' = [cres EXCEPT ![c] = "ok"]
            /\ segs' = segs \cup {[id |-> m, ep |-> IF FIX_SCHEMA THEN epoch ELSE mep[m]]
                                  : m \in Own(c)}
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cnext, cur,
                 aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

----------------------------------------------------------------------------
(* addIndexes merge thread (CMS thread, or the SMS caller that took it) *)

CanRun(m) == \/ mthr[m] = "cms"
             \/ (mthr[m] \in Callers /\ cur[mthr[m]] = m)

MStart(m) ==
  /\ mpc[m] = "taken" /\ CanRun(m)
  /\ IF closed
       THEN /\ mexc' = [mexc EXCEPT ![m] = "ace"] /\ mpc' = [mpc EXCEPT ![m] = "closing"]
            /\ UNCHANGED raim
       ELSE IF mab[m]
         THEN /\ mexc' = [mexc EXCEPT ![m] = "abort"] /\ mpc' = [mpc EXCEPT ![m] = "closing"]
              /\ UNCHANGED raim
         ELSE /\ raim' = raim \cup {m} /\ mpc' = [mpc EXCEPT ![m] = "merging"]
              /\ UNCHANGED mexc
  /\ UNCHANGED <<ffl, pend, run, mres, mab, mep, mthr, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur, aceOpen,
                 foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

(* merger.merge(): registers fields in the current epoch; ok, io failure, *)
(* or abort through the rate limiter (only if aborted)                    *)
MMerge(m) ==
  /\ mpc[m] = "merging" /\ CanRun(m)
  /\ mep' = [mep EXCEPT ![m] = epoch]
  /\ raim' = raim \ {m}
  /\ \/ /\ mpc' = [mpc EXCEPT ![m] = "merged"] /\ UNCHANGED <<mexc, fails>>
     \/ /\ fails < MaxFail /\ fails' = fails + 1
        /\ mexc' = [mexc EXCEPT ![m] = "io"] /\ mpc' = [mpc EXCEPT ![m] = "closing"]
     \/ /\ mab[m]
        /\ mexc' = [mexc EXCEPT ![m] = "abort"] /\ mpc' = [mpc EXCEPT ![m] = "closing"]
        /\ UNCHANGED fails
  /\ UNCHANGED <<ffl, pend, run, mres, mab, mthr, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur, aceOpen,
                 foreign, dpc, dres, rpc, xpc, xep, xres>>

MCfs(m) ==
  /\ mpc[m] = "merged" /\ CanRun(m)
  /\ IF mab[m]
       THEN /\ mexc' = [mexc EXCEPT ![m] = "abort"] /\ mpc' = [mpc EXCEPT ![m] = "closing"]
       ELSE /\ mpc' = [mpc EXCEPT ![m] = "final"] /\ UNCHANGED mexc
  /\ UNCHANGED <<ffl, pend, run, raim, mres, mab, mep, mthr, natpc, segs, epoch,
                 enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur,
                 aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

MFinal(m) ==
  /\ mpc[m] = "final" /\ CanRun(m)
  /\ mpc' = [mpc EXCEPT ![m] = "closing"]
  /\ \/ UNCHANGED <<mexc, fails>>
     \/ /\ fails < MaxFail /\ fails' = fails + 1 /\ mexc' = [mexc EXCEPT ![m] = "io"]
     \/ /\ mab[m] /\ mexc' = [mexc EXCEPT ![m] = "abort"] /\ UNCHANGED fails
  /\ UNCHANGED <<ffl, pend, run, raim, mres, mab, mep, mthr, natpc, segs, epoch,
                 enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur,
                 aceOpen, foreign, dpc, dres, rpc, xpc, xep, xres>>

MClose(m) ==
  /\ mpc[m] = "closing" /\ CanRun(m)
  /\ mpc' = [mpc EXCEPT ![m] = "done"]
  /\ mres' = [mres EXCEPT ![m] = IF mexc[m] = "none" THEN "ok" ELSE "fail"]
  /\ run' = run \ {m}
  /\ IF mthr[m] \in Callers
       THEN LET c == mthr[m] IN
            /\ cur' = [cur EXCEPT ![c] = NoM]
            /\ IF mexc[m] \in {"io", "ace"}
                 THEN \* rethrown out of SerialMergeScheduler.merge to the caller
                      /\ cpc' = [cpc EXCEPT ![c] = "done"]
                      /\ cres' = [cres EXCEPT ![c] = "io"]
                      /\ foreign' = [foreign EXCEPT ![c] = (m[1] # c)]
                      /\ smsOwner' = "none"
                 ELSE UNCHANGED <<cpc, cres, foreign, smsOwner>>
       ELSE UNCHANGED <<cur, cpc, cres, foreign, smsOwner>>
  /\ UNCHANGED <<ffl, pend, raim, mab, mexc, mep, mthr, natpc, segs, epoch, enabled,
                 closing, closed, cmsClosed, cnext, aceOpen, dpc, dres, rpc, xpc, xep,
                 xres, fails>>

----------------------------------------------------------------------------
(* natural merge already running at the start *)
NatExit ==
  /\ natpc = "run"
  /\ natpc' = "done" /\ run' = run \ {NatId}
  /\ smsOwner' = IF smsOwner = "nat" THEN "none" ELSE smsOwner
  /\ UNCHANGED <<ffl, pend, raim, mpc, mres, mab, mexc, mep, mthr, segs, epoch,
                 enabled, closing, closed, cmsClosed, cpc, cnext, cres, cur, aceOpen,
                 foreign, dpc, dres, rpc, xpc, xep, xres, fails>>

----------------------------------------------------------------------------
(* deleteAll *)
DAFinishVals ==
  IF closing \/ closed
    THEN \* merges.enable() calls ensureOpen() and throws; nothing is cleared
         /\ dres' = "ace" /\ UNCHANGED <<segs, epoch, enabled>>
    ELSE /\ enabled' = TRUE /\ segs' = {} /\ epoch' = epoch + 1 /\ dres' = "ok"

DAStart ==
  /\ dpc = "start" /\ ffl = "none"
  /\ IF closed \/ closing
       THEN /\ dpc' = "done" /\ dres' = "ace"
            /\ UNCHANGED <<ffl, pend, mab, mres, mpc, enabled, segs, epoch>>
       ELSE /\ AbortQueuedAndRunning
            /\ IF run \cup raim = {}
                 THEN /\ dpc' = "done" /\ UNCHANGED ffl
                      /\ enabled' = TRUE /\ segs' = {} /\ epoch' = epoch + 1
                      /\ dres' = "ok"
                 ELSE /\ dpc' = "wait" /\ ffl' = "da" /\ enabled' = FALSE
                      /\ UNCHANGED <<segs, epoch, dres>>
  /\ UNCHANGED <<run, raim, mexc, mep, mthr, natpc, closing, closed, cmsClosed,
                 smsOwner, cpc, cnext, cres, cur, aceOpen, foreign, rpc, xpc, xep,
                 xres, fails>>

DAWait ==
  /\ dpc = "wait" /\ run \cup raim = {}
  /\ dpc' = "done" /\ ffl' = "none"
  /\ DAFinishVals
  /\ UNCHANGED <<pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc,
                 closing, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur,
                 aceOpen, foreign, rpc, xpc, xep, xres, fails>>

----------------------------------------------------------------------------
(* rollback *)
RBStart ==
  /\ rpc = "start" /\ ~closing /\ ~closed
  /\ closing' = TRUE /\ rpc' = "abort"
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closed, cmsClosed, smsOwner, cpc, cnext, cres, cur,
                 aceOpen, foreign, dpc, dres, xpc, xep, xres, fails>>

RBAbort ==
  /\ rpc = "abort"
  /\ AbortQueuedAndRunning /\ enabled' = FALSE
  /\ rpc' = "wait"
  /\ UNCHANGED <<ffl, run, raim, mexc, mep, mthr, natpc, segs, epoch, closing,
                 closed, cmsClosed, smsOwner, cpc, cnext, cres, cur, aceOpen, foreign,
                 dpc, dres, xpc, xep, xres, fails>>

RBWait ==
  /\ rpc = "wait" /\ run \cup raim = {}
  /\ rpc' = "cms"
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
                 cres, cur, aceOpen, foreign, dpc, dres, xpc, xep, xres, fails>>

(* CMS.close() -> sync(): waits until every merge thread it started is done *)
RBCms ==
  /\ rpc = "cms"
  /\ \A m \in MIds : mthr[m] = "cms" => mpc[m] = "done"
  /\ rpc' = "final" /\ cmsClosed' = TRUE
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, smsOwner, cpc, cnext, cres, cur,
                 aceOpen, foreign, dpc, dres, xpc, xep, xres, fails>>

RBFinal ==
  /\ rpc = "final"
  /\ rpc' = "done" /\ segs' = {} /\ closed' = TRUE /\ closing' = FALSE
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc,
                 epoch, enabled, cmsClosed, smsOwner, cpc, cnext, cres, cur, aceOpen,
                 foreign, dpc, dres, xpc, xep, xres, fails>>

----------------------------------------------------------------------------
(* addIndexes(Directory...) caller *)
XFlush(x) ==
  /\ xpc[x] = "start" /\ ffl = "none"
  /\ IF closed
       THEN /\ xpc' = [xpc EXCEPT ![x] = "done"] /\ xres' = [xres EXCEPT ![x] = "ace"]
       ELSE /\ xpc' = [xpc EXCEPT ![x] = "fields"] /\ UNCHANGED xres
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
                 cres, cur, aceOpen, foreign, dpc, dres, rpc, xep, fails>>

XFields(x) ==
  /\ xpc[x] = "fields"
  /\ xep' = [xep EXCEPT ![x] = epoch] /\ xpc' = [xpc EXCEPT ![x] = "copy"]
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
                 cres, cur, aceOpen, foreign, dpc, dres, rpc, xres, fails>>

XCopy(x) ==
  /\ xpc[x] = "copy"
  /\ xpc' = [xpc EXCEPT ![x] = "reg"]
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc, segs,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
                 cres, cur, aceOpen, foreign, dpc, dres, rpc, xep, xres, fails>>

XReg(x) ==
  /\ xpc[x] = "reg"
  /\ xpc' = [xpc EXCEPT ![x] = "done"]
  /\ IF closed \/ closing
       THEN /\ xres' = [xres EXCEPT ![x] = "ace"] /\ UNCHANGED segs
       ELSE /\ xres' = [xres EXCEPT ![x] = "ok"]
            /\ segs' = segs \cup {[id |-> <<x, 0>>, ep |-> IF FIX_SCHEMA THEN epoch ELSE xep[x]]}
  /\ UNCHANGED <<ffl, pend, run, raim, mpc, mres, mab, mexc, mep, mthr, natpc,
                 epoch, enabled, closing, closed, cmsClosed, smsOwner, cpc, cnext,
                 cres, cur, aceOpen, foreign, dpc, dres, rpc, xep, fails>>

----------------------------------------------------------------------------
AllDone ==
  /\ \A c \in Callers : cpc[c] = "done"
  /\ \A x \in XCallers : xpc[x] = "done"
  /\ dpc = "done" /\ rpc = "done" /\ natpc # "run"
  /\ \A m \in MIds : mthr[m] = "cms" => mpc[m] = "done"

Finished == AllDone /\ UNCHANGED vars

CallerNext(c) == CFlush(c) \/ CCheck(c) \/ CReg(c) \/ CSchedCMS(c) \/ CSchedSMS(c)
                 \/ CAwait(c) \/ CDecide(c) \/ CRegSegs(c)
MergeNext(m) == MStart(m) \/ MMerge(m) \/ MCfs(m) \/ MFinal(m) \/ MClose(m)
OtherNext == NatExit \/ DAStart \/ DAWait \/ RBStart \/ RBAbort \/ RBWait \/ RBCms
             \/ RBFinal
XNext(x) == XFlush(x) \/ XFields(x) \/ XCopy(x) \/ XReg(x)

Next == \/ \E c \in Callers : CallerNext(c)
        \/ \E m \in MIds : MergeNext(m)
        \/ \E x \in XCallers : XNext(x)
        \/ OtherNext
        \/ Finished

Spec == Init /\ [][Next]_vars

(* A junction list, so that no \A below swallows the conjuncts after it (the *)
(* first version wrote "Spec /\ \A c ... /\ ...", which nests every later    *)
(* conjunct inside the quantifiers, and with XCallers = {} the deleteAll and *)
FairSpec ==
  /\ Spec
  /\ \A c \in Callers : WF_vars(CallerNext(c))
  /\ \A m \in MIds : WF_vars(MergeNext(m))
  /\ \A x \in XCallers : WF_vars(XNext(x))
  /\ WF_vars(NatExit)
  /\ WF_vars(DAStart)
  /\ WF_vars(DAWait)
  /\ WF_vars(RBAbort)
  /\ WF_vars(RBWait)
  /\ WF_vars(RBCms)
  /\ WF_vars(RBFinal)

----------------------------------------------------------------------------
(* Properties *)

(* S1: the writer's field map covers every registered segment *)
SchemaCovered == \A s \in segs : s.ep = epoch

(* A1: AlreadyClosedException only when the writer is closing or closed *)
NoSpuriousACE == \A c \in Callers : ~aceOpen[c]

(* T1a: a normal return implies all own merges completed successfully *)
OkImpliesAllOk == \A c \in Callers : cres[c] = "ok" => \A m \in Own(c) : mres[m] = "ok"

(* T1b: a caller never fails with another caller's merge exception *)
NoForeignFailure == \A c \in Callers : ~foreign[c]

(* T2 (plan expectation): no aborted addIndexes merge is registered *)
AbortedNotRegistered == \A s \in segs : s.id \in MIds => ~mab[s.id]

(* LFV-184 sanity: no addIndexes merge thread is running after CMS was closed *)
NoMergeAfterClose == cmsClosed => \A m \in MIds : mthr[m] = "cms" => mpc[m] \in {"done"}

(* T4: bookkeeping is empty at the end *)
CleanAtEnd == AllDone => (pend = <<>> /\ run = {} /\ raim = {})

(* T3 (liveness): every call returns.  The first form required rollback to *)
(* finish too, and rollback is optional (RBStart is not fair), so TLC found *)
(* a stuttering "counterexample" with rollback never started: a modelling  *)
(* error, replaced by the two properties below.                           *)
EveryCallReturns == <>AllDone
CallsReturn == <>(/\ \A c \in Callers : cpc[c] = "done"
                  /\ \A x \in XCallers : xpc[x] = "done"
                  /\ dpc = "done")
RollbackReturns == [](rpc \notin {"start", "done"} => <>(rpc = "done"))
MergesFinish == \A m \in MIds : [](mpc[m] \in {"taken", "merging", "merged", "final", "closing"}
                                    => <>(mpc[m] = "done"))

(* vacuity probes: must be violated *)
ProbeNoOk == \A c \in Callers : cres[c] # "ok"
ProbeNoDA == dres # "ok"
ProbeNoRBDone == rpc # "done"

TypeOK ==
  /\ ffl \in {"none", "da"}
  /\ run \subseteq MIds \cup {NatId} /\ raim \subseteq MIds
  /\ \A m \in MIds : mres[m] \in {"none", "ok", "fail"}
=============================================================================

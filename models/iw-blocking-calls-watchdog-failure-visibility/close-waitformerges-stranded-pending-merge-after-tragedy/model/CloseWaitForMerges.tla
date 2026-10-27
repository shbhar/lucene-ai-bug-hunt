------------------------- MODULE CloseWaitForMerges -------------------------
(***************************************************************************)
(* IndexWriter.close() (commitOnClose=true) waiting for background merges  *)
(* under ConcurrentMergeScheduler, with merge failures (tragic events).   *)
(*                                                                         *)
(* Code under test: Lucene main a9779ef3c193, lucene/core/src/java/org/    *)
(* apache/lucene/index/.  Model element -> Java (file:line):               *)
(*                                                                         *)
(*  closerPC "cms"     IndexWriter.waitForMerges:2883 mergeScheduler.merge  *)
(*                     (CLOSING) -> ConcurrentMergeScheduler.merge:542-597  *)
(*                     (maybeStall:609-644 polls hasPendingMerges, which    *)
(*                     throws once tragedy is set, IndexWriter:2558-2563)   *)
(*  closerPC "wait"    IndexWriter.waitForMerges:2891-2893 (loop on         *)
(*                     pendingMerges/runningMerges, no tragedy check)       *)
(*  FIX                proposed: tragedy check inside that loop             *)
(*  closerPC "commit"  IndexWriter.shutdown:1327 commitInternal ->          *)
(*                     prepareCommitInternal:3782 tragedy check             *)
(*  closerPC "rollback"/"sync"  shutdown:1331/1337 rollbackInternal ->      *)
(*                     abortMerges:2821-2870 (abort pending, wait running), *)
(*                     mergeScheduler.close:2631 -> CMS.sync:481 (join all  *)
(*                     merge threads)                                       *)
(*  closing            shutdown:1319 shouldClose(true) sets closing=true;   *)
(*                     so maybeCloseOnTragicEvent:6103 shouldClose(false)   *)
(*                     returns false: no tragic rollback while closing      *)
(*  MergeSucceed       IndexWriter.merge:5072-5107: commitMerge (tragedy    *)
(*                     check :4899, abort check :4917) then, in one IW-     *)
(*                     synchronized block, mergeFinish:5375 and the forced- *)
(*                     merge cascade updatePendingMerges(MERGE_FINISHED):   *)
(*                     5105 (no registration once tragedy is set)           *)
(*  PostCMS            ConcurrentMergeScheduler.MergeThread.run:720-757 ->  *)
(*                     runOnMergeFinished:665-687 -> merge(MERGE_FINISHED): *)
(*                     maybeStall returns false for a merge thread when     *)
(*                     mergeThreadCount() >= maxMergeCount; hasPending/     *)
(*                     getNextMerge throw after the tragedy (:2538-2563)    *)
(*  MergeFailIO        IndexWriter.merge:5084-5094 (handleMergeException,   *)
(*                     then mergeFinish under the IW lock)                  *)
(*  TragicEvent        IndexWriter.merge:5109-5113 tragicEvent outside the  *)
(*                     lock, after mergeFinish; the failing CMS thread then *)
(*                     exits WITHOUT runOnMergeFinished (MergeThread.run    *)
(*                     catch block :749-756)                                *)
(*  MergeAbortStop     abortMerges: running merges see isAborted and stop   *)
(*                                                                         *)
(* Merge identities are abstract; Cascade merges are the ones a successful *)
(* forced merge may register (forceMerge(n, false) before close()).        *)
(* Registration is nondeterministic (an over-approximation of the policy); *)
(* the JUnit repro shows the trace is reachable with LogDocMergePolicy.    *)
(* Run with deadlock checking ON: a hang of close() is a TLC deadlock.     *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS InitialRunning,   \* merges running when close() starts
          InitialPending,   \* merges registered but not started when close() starts
          Cascade,          \* merges a successful merge may register (forced-merge cascade)
          MaxMergeCount,    \* CMS maxMergeCount
          MaxFailures,      \* bound on injected merge I/O failures
          FIX               \* TRUE = tragedy check in the waitForMerges loop

VARIABLES pending,      \* IW.pendingMerges
          running,      \* IW.runningMerges
          post,         \* merge threads between their commit and runOnMergeFinished
          failing,      \* merge threads between mergeFinish and tragicEvent
          alive,        \* live CMS merge threads (named by their merge)
          tragedy,      \* IW.tragedy != null
          aborted,      \* merges disabled and aborted by rollback
          failures,
          cascadeLeft,
          closerPC

vars == <<pending, running, post, failing, alive, tragedy, aborted, failures, cascadeLeft, closerPC>>

Merges == InitialRunning \cup InitialPending \cup Cascade

TypeOK ==
  /\ pending \subseteq Merges /\ running \subseteq Merges /\ post \subseteq Merges
  /\ failing \subseteq Merges /\ alive \subseteq Merges
  /\ tragedy \in BOOLEAN /\ aborted \in BOOLEAN
  /\ failures \in 0..MaxFailures
  /\ closerPC \in {"cms", "wait", "commit", "rollback", "sync", "done"}

Init ==
  /\ pending = InitialPending
  /\ running = InitialRunning
  /\ post = {}
  /\ failing = {}
  /\ alive = InitialRunning
  /\ tragedy = FALSE
  /\ aborted = FALSE
  /\ failures = 0
  /\ cascadeLeft = Cascade
  /\ closerPC = "cms"

Min(a, b) == IF a < b THEN a ELSE b

(* ---------------- merge threads ---------------- *)

\* commitMerge succeeds; mergeFinish + cascade registration in one IW-locked block
MergeSucceed(m) ==
  /\ m \in running
  /\ ~tragedy /\ ~aborted
  /\ running' = running \ {m}
  /\ post' = post \cup {m}
  /\ \/ /\ cascadeLeft # {}
        /\ \E c \in cascadeLeft :
             /\ pending' = pending \cup {c}
             /\ cascadeLeft' = cascadeLeft \ {c}
     \/ UNCHANGED <<pending, cascadeLeft>>
  /\ UNCHANGED <<failing, alive, tragedy, aborted, failures, closerPC>>

\* the merge hits an I/O error (e.g. disk full): mergeFinish under the lock first
MergeFailIO(m) ==
  /\ m \in running
  /\ failures < MaxFailures
  /\ running' = running \ {m}
  /\ failing' = failing \cup {m}
  /\ failures' = failures + 1
  /\ UNCHANGED <<pending, post, alive, tragedy, aborted, cascadeLeft, closerPC>>

\* a running merge that reaches commitMerge (or mergeInit) after the tragedy fails too
MergeFailByTragedy(m) ==
  /\ m \in running
  /\ tragedy /\ ~aborted
  /\ running' = running \ {m}
  /\ alive' = alive \ {m}          \* tragicEvent: already set; no rollback (closing); thread exits
  /\ UNCHANGED <<pending, post, failing, tragedy, aborted, failures, cascadeLeft, closerPC>>

\* tragicEvent after mergeFinish, outside the lock; closing => no rollback; thread exits
\* without runOnMergeFinished, so it never starts pending merges
TragicEvent(m) ==
  /\ m \in failing
  /\ tragedy' = TRUE
  /\ failing' = failing \ {m}
  /\ alive' = alive \ {m}
  /\ UNCHANGED <<pending, running, post, aborted, failures, cascadeLeft, closerPC>>

\* an aborted running merge stops (rollback/abortMerges)
MergeAbortStop(m) ==
  /\ m \in running
  /\ aborted
  /\ running' = running \ {m}
  /\ alive' = alive \ {m}
  /\ UNCHANGED <<pending, post, failing, tragedy, aborted, failures, cascadeLeft, closerPC>>

\* runOnMergeFinished -> CMS.merge(MERGE_FINISHED) on the merge thread, CMS-synchronized
PostCMS(m) ==
  /\ m \in post
  /\ post' = post \ {m}
  /\ IF tragedy
       THEN \* hasPendingMerges()/getNextMerge() throw ISE: nothing is started, thread exits
            /\ alive' = alive \ {m}
            /\ UNCHANGED <<pending, running>>
       ELSE LET others == Cardinality(alive \ {m})
                k == IF aborted THEN 0
                     ELSE IF others >= MaxMergeCount THEN 0      \* maybeStall: false for a merge thread
                     ELSE Min(Cardinality(pending), MaxMergeCount - others)
            IN \E L \in SUBSET pending :
                 /\ Cardinality(L) = k
                 /\ pending' = pending \ L
                 /\ running' = running \cup L
                 /\ alive' = (alive \ {m}) \cup L
  /\ UNCHANGED <<failing, tragedy, aborted, failures, cascadeLeft, closerPC>>

(* ---------------- the closing thread ---------------- *)

\* waitForMerges: mergeScheduler.merge(CLOSING) on the closer thread (not a merge thread:
\* it stalls while count >= maxMergeCount, polling hasPendingMerges, which throws on tragedy)
CloserCms ==
  /\ closerPC = "cms"
  /\ \/ /\ tragedy
        /\ closerPC' = "rollback"                          \* ISE -> shutdown's catch -> rollback
        /\ UNCHANGED <<pending, running, alive>>
     \/ /\ ~tragedy /\ pending = {}
        /\ closerPC' = "wait"
        /\ UNCHANGED <<pending, running, alive>>
     \/ /\ ~tragedy /\ pending # {}
        /\ Cardinality(alive) < MaxMergeCount
        /\ \E m \in pending :
             /\ pending' = pending \ {m}
             /\ running' = running \cup {m}
             /\ alive' = alive \cup {m}
        /\ closerPC' = "cms"
  /\ UNCHANGED <<post, failing, tragedy, aborted, failures, cascadeLeft>>

\* waitForMerges loop: while (pendingMerges.size() > 0 || runningMerges.size() > 0) doWait();
CloserWait ==
  /\ closerPC = "wait"
  /\ \/ /\ pending = {} /\ running = {}
        /\ closerPC' = "commit"
     \/ /\ FIX /\ tragedy
        /\ closerPC' = "rollback"
  /\ UNCHANGED <<pending, running, post, failing, alive, tragedy, aborted, failures, cascadeLeft>>

\* commitInternal: throws if the tragedy is visible, else commits; both end in rollbackInternal
CloserCommit ==
  /\ closerPC = "commit"
  /\ closerPC' = "rollback"
  /\ UNCHANGED <<pending, running, post, failing, alive, tragedy, aborted, failures, cascadeLeft>>

\* rollbackInternal -> abortMerges: disable merges, abort pending, mark running aborted,
\* wait for running to stop
CloserRollback ==
  /\ closerPC = "rollback"
  /\ ~aborted
  /\ aborted' = TRUE
  /\ pending' = {}
  /\ closerPC' = "sync"
  /\ UNCHANGED <<running, post, failing, alive, tragedy, failures, cascadeLeft>>

\* abortMerges waits for runningMerges; mergeScheduler.close() -> sync() joins all merge threads
CloserSync ==
  /\ closerPC = "sync"
  /\ running = {} /\ alive = {}
  /\ closerPC' = "done"
  /\ UNCHANGED <<pending, running, post, failing, alive, tragedy, aborted, failures, cascadeLeft>>

Terminated == closerPC = "done" /\ UNCHANGED vars

Next ==
  \/ \E m \in Merges : MergeSucceed(m) \/ MergeFailIO(m) \/ MergeFailByTragedy(m)
                       \/ TragicEvent(m) \/ MergeAbortStop(m) \/ PostCMS(m)
  \/ CloserCms \/ CloserWait \/ CloserCommit \/ CloserRollback \/ CloserSync
  \/ Terminated

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

CloseReturns == <>(closerPC = "done")

\* vacuity probes (must be violated)
ProbeNeverDone == closerPC # "done"
ProbeNoTragedy == ~tragedy
ProbeNoCascade == cascadeLeft = Cascade
=============================================================================

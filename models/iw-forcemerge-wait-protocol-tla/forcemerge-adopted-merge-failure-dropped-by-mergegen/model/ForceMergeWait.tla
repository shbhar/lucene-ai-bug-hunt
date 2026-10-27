--------------------------- MODULE ForceMergeWait ---------------------------
(***************************************************************************)
(* TLA+ model of IndexWriter's forced-merge waiting protocol (Lucene main  *)
(* a9779ef3c193).  Granularity: one TLA+ action = one synchronized(IW)     *)
(* block, or one unsynchronized stretch between such blocks.  wait(1000)   *)
(* is modelled as "the waiter may re-check at any time" (timeout), which   *)
(* is what makes the loop safe against missed notifies.                    *)
(*                                                                         *)
(* File:line map (lucene/core/src/java/org/apache/lucene/index/...)        *)
(*   CStart       IndexWriter.java:2238-2248  ensureOpen, flush(true,true) *)
(*                4367-4381 flush -> maybeMerge(FULL_FLUSH) natural regs   *)
(*   CReset       2249-2279  resetMergeExceptions (3039-3042: clear list,  *)
(*                mergeGen++), segmentsToMerge reset, mergeMaxNumSegments,  *)
(*                adopt pending+running merges (maxNumSegments := n,       *)
(*                info -> segmentsToMerge TRUE); mergeGen NOT re-assigned  *)
(*   CMaybeMerge  2281, 2442-2518 updatePendingMerges(EXPLICIT, n):        *)
(*                findForcedMerges, merge.maxNumSegments := n, register    *)
(*   CLoop        2284-2311 tragedy? -> ISE; mergeExceptions with          *)
(*                maxNumSegments set -> IOException; forced merge pending  *)
(*                or running (2326-2336) -> doWait (5860-5872) else break   *)
(*   CEnsureOpen  2318 ensureOpen()                                        *)
(*   Register     5158-5250 registerMerge: reject if a segment is merging  *)
(*                or not live; maxNumSegments := mergeMaxNumSegments if a  *)
(*                segment is in segmentsToMerge (5199-5200); pending add;  *)
(*                merge.mergeGen := mergeGen (5218); mergingSegments add    *)
(*   TTake        2538-2548 getNextMerge (ISE if tragedy)                  *)
(*   TInit        5256-5349 mergeInit/_mergeInit (ISE if tragedy; info)    *)
(*   TMiddle      5467ff mergeMiddle (unsynchronized work; may throw)      *)
(*   TCommit      4890-5027 commitMerge (sync): apply, cascade             *)
(*                segmentsToMerge.put(info, FALSE) if forced (5020-5025)   *)
(*   TFinOK       5088-5107 finally: mergeFinish (5375-5391, notifyAll),   *)
(*                updatePendingMerges(MERGE_FINISHED, merge.maxNumSegments)*)
(*   TFailExc     5030-5058 handleMergeException: setException (347),      *)
(*                addMergeException (5819-5824: only if mergeGen equal)    *)
(*   TFinFail     5088-5099 finally: closeMergeReaders, mergeFinish        *)
(*   TTragic      5109-5113 tragicEvent -> onTragicEvent 6068-6084         *)
(*                (infoStream message, then tragedy.compareAndSet)         *)
(*   TRb1/TRb2    6098-6106 maybeCloseOnTragicEvent -> rollbackInternal,   *)
(*                abortMerges 2821-2870, closed := true                    *)
(*   IFlush       another indexing thread: flush + maybeMerge(SEGMENT_FLUSH)*)
(*                                                                         *)
(* Abstractions: the merge policy.  Natural merges: any subset (size >= 2)*)
(* of live, non-merging segments, or none.  Forced merges ("ideal"        *)
(* findForcedMerges): merge all eligible (in segmentsToMerge, not merging) *)
(* segments if there are >= 2 of them and eligible + in-flight merges over *)
(* segmentsToMerge exceed n.  The merge scheduler: idle merge threads take *)
(* any pending merge (CMS-like; CMS caps are cms-merge-lifecycle's model).  Flush publish  *)
(* and its maybeMerge are one action.  UNBOUNDED_MAX_MERGE_SEGMENTS (-1)   *)
(* is 0 here.                                                              *)
(*                                                                         *)
(* Fix switches: FIX_GEN (adoption also sets merge.mergeGen := mergeGen),  *)
(* FIX_TRAGIC_FIRST (tragedy published inside handleMergeException, before *)
(* mergeFinish wakes the waiters).                                         *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS NInit, MaxSeg, MaxMerges, Callers, CallerN, MThreads, MaxFail,
          MaxFlush, FIX_GEN, FIX_TRAGIC_FIRST, CALLER_FLUSH, ALLOW_DELETEALL

None == 0
UNB  == 0
Seg  == 1..MaxSeg
MId  == 1..MaxMerges

VARIABLES segs, anc, nextSeg,
          pending, running, merging, tmT, tmF, mmns, gen, mexc,
          tragedy, closing, closed, enabled,
          mSegs, mN, mGen, mInfo, mExc, mAbort, mSt, nextM,
          cpc, cOrig, cRet, cFail,
          tpc, tmg, tkind, fails, flushes, delAll, cDA

vars == <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
          mexc, tragedy, closing, closed, enabled, mSegs, mN, mGen, mInfo,
          mExc, mAbort, mSt, nextM, cpc, cOrig, cRet, cFail, tpc, tmg, tkind,
          fails, flushes, delAll, cDA>>

TM == tmT \cup tmF
InFlight == pending \cup running

OrigAnc(c) == UNION {anc[s] : s \in cOrig[c]}
Desc(c) == {s \in segs : anc[s] \cap OrigAnc(c) /= {}}

AfterReset(c) == cpc[c] \in {"mm", "loop", "eo"}

\* ideal findForcedMerges(n) given the current merging set
FindForced(n, mergingIn, inflightIn) ==
  LET elig == (segs \cap TM) \ mergingIn
      infl == {m \in inflightIn : mSegs[m] \cap TM /= {}}
  IN IF Cardinality(elig) >= 2 /\ Cardinality(elig) + Cardinality(infl) > n
     THEN elig ELSE {}

NaturalChoices(mergingIn) ==
  {S \in SUBSET (segs \ mergingIn) : Cardinality(S) >= 2} \cup {{}}

\* registerMerge of a fresh merge id with segments S and preset maxNumSegments n.
\* Writes nextM, mSegs, mN, mGen, mSt, pending, merging given the base sets.
RegisterNew(S, n, pendingIn, mergingIn) ==
  LET m == nextM
      ok == S \cap mergingIn = {} /\ S \subseteq segs
      nn == IF S \cap TM /= {} THEN mmns ELSE n
  IN /\ nextM' = nextM + 1
     /\ mSegs' = [mSegs EXCEPT ![m] = S]
     /\ mN' = [mN EXCEPT ![m] = nn]
     /\ mGen' = [mGen EXCEPT ![m] = IF ok THEN gen ELSE mGen[m]]
     /\ mSt' = [mSt EXCEPT ![m] = IF ok THEN "pend" ELSE "rej"]
     /\ pending' = IF ok THEN pendingIn \cup {m} ELSE pendingIn
     /\ merging' = IF ok THEN mergingIn \cup S ELSE mergingIn

NoRegister(pendingIn, mergingIn) ==
  /\ pending' = pendingIn /\ merging' = mergingIn
  /\ UNCHANGED <<nextM, mSegs, mN, mGen, mSt>>

CanRegister == nextM <= MaxMerges

Init ==
  /\ segs = 1..NInit
  /\ anc = [s \in Seg |-> IF s <= NInit THEN {s} ELSE {}]
  /\ nextSeg = NInit + 1
  /\ pending = {} /\ running = {} /\ merging = {}
  /\ tmT = {} /\ tmF = {} /\ mmns = UNB /\ gen = 0 /\ mexc = {}
  /\ tragedy = FALSE /\ closing = FALSE /\ closed = FALSE /\ enabled = TRUE
  /\ mSegs = [m \in MId |-> {}] /\ mN = [m \in MId |-> UNB]
  /\ mGen = [m \in MId |-> 0] /\ mInfo = [m \in MId |-> None]
  /\ mExc = [m \in MId |-> FALSE] /\ mAbort = [m \in MId |-> FALSE]
  /\ mSt = [m \in MId |-> "free"] /\ nextM = 1
  /\ cpc = [c \in Callers |-> "init"]
  /\ cOrig = [c \in Callers |-> {}] /\ cRet = [c \in Callers |-> 0]
  /\ cFail = [c \in Callers |-> FALSE]
  /\ tpc = [t \in MThreads |-> "idle"] /\ tmg = [t \in MThreads |-> 0]
  /\ tkind = [t \in MThreads |-> "none"]
  /\ fails = 0 /\ flushes = 0 /\ delAll = FALSE
  /\ cDA = [c \in Callers |-> FALSE]

-----------------------------------------------------------------------------
(* caller: forceMerge(n) *)
CStart(c) ==
  /\ cpc[c] = "init"
  /\ IF closing \/ closed \/ tragedy
       THEN /\ cpc' = [cpc EXCEPT ![c] = "threw"]
            /\ UNCHANGED <<segs, anc, nextSeg, flushes, pending, merging, nextM, mSegs, mN, mGen, mSt>>
       ELSE \/ /\ cpc' = [cpc EXCEPT ![c] = "reset"]
               /\ UNCHANGED <<segs, anc, nextSeg, flushes, pending, merging, nextM, mSegs, mN, mGen, mSt>>
            \/ /\ CALLER_FLUSH /\ flushes < MaxFlush /\ nextSeg <= MaxSeg
               /\ cpc' = [cpc EXCEPT ![c] = "reset"]
               /\ LET s == nextSeg IN
                  /\ nextSeg' = nextSeg + 1
                  /\ segs' = segs \cup {s}
                  /\ anc' = [anc EXCEPT ![s] = {s}]
                  /\ flushes' = flushes + 1
                  /\ UNCHANGED <<pending, merging, nextM, mSegs, mN, mGen, mSt>>
            \/ /\ CALLER_FLUSH /\ flushes < MaxFlush /\ nextSeg <= MaxSeg /\ CanRegister /\ enabled
               /\ cpc' = [cpc EXCEPT ![c] = "reset"]
               /\ LET s == nextSeg IN
                  /\ nextSeg' = nextSeg + 1
                  /\ segs' = segs \cup {s}
                  /\ anc' = [anc EXCEPT ![s] = {s}]
                  /\ flushes' = flushes + 1
                  /\ \E S \in {X \in SUBSET ((segs \cup {s}) \ merging) : Cardinality(X) >= 2} :
                        LET m == nextM
                            nn == IF S \cap TM /= {} THEN mmns ELSE UNB
                        IN /\ nextM' = nextM + 1
                           /\ mSegs' = [mSegs EXCEPT ![m] = S]
                           /\ mN' = [mN EXCEPT ![m] = nn]
                           /\ mGen' = [mGen EXCEPT ![m] = gen]
                           /\ mSt' = [mSt EXCEPT ![m] = "pend"]
                           /\ pending' = pending \cup {m}
                           /\ merging' = merging \cup S
  /\ UNCHANGED <<running, tmT, tmF, mmns, gen, mexc, tragedy, closing, closed, enabled,
                 mInfo, mExc, mAbort, cOrig, cRet, cFail, tpc, tmg, tkind, fails, delAll, cDA>>

CReset(c) ==
  /\ cpc[c] = "reset"
  /\ cOrig' = [cOrig EXCEPT ![c] = segs]
  /\ gen' = gen + 1
  /\ mexc' = {}
  /\ tmT' = segs \cup {mInfo[m] : m \in {x \in InFlight : mInfo[x] /= None}}
  /\ tmF' = {}
  /\ mmns' = CallerN[c]
  /\ mN' = [m \in MId |-> IF m \in InFlight THEN CallerN[c] ELSE mN[m]]
  /\ mGen' = IF FIX_GEN THEN [m \in MId |-> IF m \in InFlight THEN gen + 1 ELSE mGen[m]]
                       ELSE mGen
  /\ cpc' = [cpc EXCEPT ![c] = "mm"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tragedy, closing,
                 closed, enabled, mSegs, mInfo, mExc, mAbort, mSt, nextM, cRet,
                 cFail, tpc, tmg, tkind, fails, flushes, delAll, cDA>>

CMaybeMerge(c) ==
  /\ cpc[c] = "mm"
  /\ IF closed
       THEN /\ cpc' = [cpc EXCEPT ![c] = "threw"]
            /\ NoRegister(pending, merging)
       ELSE /\ cpc' = [cpc EXCEPT ![c] = "loop"]
            /\ LET S == FindForced(CallerN[c], merging, InFlight) IN
               IF enabled /\ ~tragedy /\ S /= {} /\ CanRegister
                 THEN RegisterNew(S, CallerN[c], pending, merging)
                 ELSE NoRegister(pending, merging)
  /\ UNCHANGED <<segs, anc, nextSeg, running, tmT, tmF, mmns, gen, mexc, tragedy,
                 closing, closed, enabled, mInfo, mExc, mAbort, cOrig, cRet, cFail,
                 tpc, tmg, tkind, fails, flushes, delAll, cDA>>

ForcedPending == \E m \in InFlight : mN[m] /= UNB

CLoop(c) ==
  /\ cpc[c] = "loop"
  /\ \/ tragedy
     \/ \E m \in mexc : mN[m] /= UNB
     \/ ~ForcedPending
  /\ cpc' = [cpc EXCEPT ![c] =
               IF tragedy \/ (\E m \in mexc : mN[m] /= UNB) THEN "threw" ELSE "eo"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 mexc, tragedy, closing, closed, enabled, mSegs, mN, mGen, mInfo,
                 mExc, mAbort, mSt, nextM, cOrig, cRet, cFail, tpc, tmg, tkind,
                 fails, flushes, delAll, cDA>>

CEnsureOpen(c) ==
  /\ cpc[c] = "eo"
  /\ cpc' = [cpc EXCEPT ![c] = IF closing \/ closed THEN "threw" ELSE "ok"]
  /\ cRet' = [cRet EXCEPT ![c] = Cardinality(Desc(c))]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 mexc, tragedy, closing, closed, enabled, mSegs, mN, mGen, mInfo,
                 mExc, mAbort, mSt, nextM, cOrig, cFail, tpc, tmg, tkind,
                 fails, flushes, delAll, cDA>>

-----------------------------------------------------------------------------
(* another indexing thread flushes a segment; maybeMerge(SEGMENT_FLUSH) *)
IFlush ==
  /\ flushes < MaxFlush /\ nextSeg <= MaxSeg
  /\ ~tragedy /\ ~closing /\ ~closed
  /\ LET s == nextSeg IN
     /\ nextSeg' = nextSeg + 1
     /\ segs' = segs \cup {s}
     /\ anc' = [anc EXCEPT ![s] = {s}]
     /\ flushes' = flushes + 1
     /\ \/ NoRegister(pending, merging)
        \/ /\ CanRegister /\ enabled
           /\ \E S \in {X \in SUBSET ((segs \cup {s}) \ merging) : Cardinality(X) >= 2} :
                LET m == nextM
                    nn == IF S \cap TM /= {} THEN mmns ELSE UNB
                IN /\ nextM' = nextM + 1
                   /\ mSegs' = [mSegs EXCEPT ![m] = S]
                   /\ mN' = [mN EXCEPT ![m] = nn]
                   /\ mGen' = [mGen EXCEPT ![m] = gen]
                   /\ mSt' = [mSt EXCEPT ![m] = "pend"]
                   /\ pending' = pending \cup {m}
                   /\ merging' = merging \cup S
  /\ UNCHANGED <<running, tmT, tmF, mmns, gen, mexc, tragedy, closing, closed, enabled,
                 mInfo, mExc, mAbort, cpc, cOrig, cRet, cFail, tpc, tmg, tkind,
                 fails, delAll, cDA>>

(* deleteAll (2750-2815): under IW lock abortMerges (2821-2870) aborts pending
   merges (abortOneMerge + mergeFinish, no exception recorded), flags running
   merges aborted and doWait()s (releasing the lock) until no merge runs; then,
   in the same synchronized block, drops all segments and re-enables merges.
   With no running merge the whole thing is one atomic step (DAAbort with
   running = {} goes straight to the drop).  deleteAll's javadoc (2743-2745)
   allows concurrent forceMerge callers to see MergeAbortedException, so F1/F2
   exempt calls that overlap a deleteAll (ghost cDA). *)
DAAbort ==
  /\ ALLOW_DELETEALL /\ ~delAll /\ ~closing /\ ~closed /\ ~tragedy
  /\ delAll' = TRUE
  /\ mAbort' = [m \in MId |-> IF m \in InFlight THEN TRUE ELSE mAbort[m]]
  /\ mSt' = [m \in MId |-> IF m \in pending THEN "abortedPend" ELSE mSt[m]]
  /\ merging' = merging \ UNION {mSegs[m] : m \in pending}
  /\ pending' = {}
  /\ IF running = {}
       THEN /\ segs' = {} /\ enabled' = TRUE
       ELSE /\ enabled' = FALSE /\ UNCHANGED segs
  /\ cDA' = [c \in Callers |-> cDA[c] \/ cpc[c] \in {"init", "reset", "mm", "loop", "eo"}]
  /\ UNCHANGED <<anc, nextSeg, running, tmT, tmF, mmns, gen, mexc, tragedy,
                 closing, closed, mSegs, mN, mGen, mInfo, mExc, nextM, cpc, cOrig,
                 cRet, cFail, tpc, tmg, tkind, fails, flushes>>

DADrop ==
  /\ delAll /\ ~enabled /\ ~closing /\ ~closed /\ running = {}
  /\ segs' = {}
  /\ enabled' = TRUE
  /\ cDA' = [c \in Callers |-> cDA[c] \/ cpc[c] \in {"init", "reset", "mm", "loop", "eo"}]
  /\ UNCHANGED <<anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen, mexc,
                 tragedy, closing, closed, mSegs, mN, mGen, mInfo, mExc, mAbort,
                 mSt, nextM, cpc, cOrig, cRet, cFail, tpc, tmg, tkind, fails,
                 flushes, delAll>>

-----------------------------------------------------------------------------
(* merge threads *)
TTake(t) ==
  /\ tpc[t] = "idle" /\ pending /= {} /\ ~tragedy
  /\ \E m \in pending :
       /\ pending' = pending \ {m}
       /\ running' = running \cup {m}
       /\ mSt' = [mSt EXCEPT ![m] = "run"]
       /\ tmg' = [tmg EXCEPT ![t] = m]
       /\ tpc' = [tpc EXCEPT ![t] = "init"]
  /\ UNCHANGED <<segs, anc, nextSeg, merging, tmT, tmF, mmns, gen, mexc, tragedy,
                 closing, closed, enabled, mSegs, mN, mGen, mInfo, mExc, mAbort,
                 nextM, cpc, cOrig, cRet, cFail, tkind, fails, flushes, delAll, cDA>>

TInit(t) ==
  /\ tpc[t] = "init"
  /\ LET m == tmg[t] IN
     IF tragedy
       THEN /\ tpc' = [tpc EXCEPT ![t] = "failExc"]
            /\ tkind' = [tkind EXCEPT ![t] = "ise"]
            /\ UNCHANGED <<mInfo, nextSeg>>
       ELSE IF mAbort[m] \/ nextSeg > MaxSeg
            THEN /\ tpc' = [tpc EXCEPT ![t] = "failExc"]
                 /\ tkind' = [tkind EXCEPT ![t] = "abort"]
                 /\ UNCHANGED <<mInfo, nextSeg>>
            ELSE /\ mInfo' = [mInfo EXCEPT ![m] = nextSeg]
                 /\ nextSeg' = nextSeg + 1
                 /\ tpc' = [tpc EXCEPT ![t] = "middle"]
                 /\ UNCHANGED tkind
  /\ UNCHANGED <<segs, anc, pending, running, merging, tmT, tmF, mmns, gen, mexc,
                 tragedy, closing, closed, enabled, mSegs, mN, mGen, mExc, mAbort,
                 mSt, nextM, cpc, cOrig, cRet, cFail, tmg, fails, flushes, delAll, cDA>>

TMiddle(t) ==
  /\ tpc[t] = "middle"
  /\ LET m == tmg[t] IN
     \/ /\ mAbort[m]
        /\ tpc' = [tpc EXCEPT ![t] = "failExc"]
        /\ tkind' = [tkind EXCEPT ![t] = "abort"]
        /\ UNCHANGED fails
     \/ /\ ~mAbort[m]
        /\ tpc' = [tpc EXCEPT ![t] = "commit"]
        /\ UNCHANGED <<tkind, fails>>
     \/ /\ ~mAbort[m] /\ fails < MaxFail
        /\ tpc' = [tpc EXCEPT ![t] = "failExc"]
        /\ tkind' = [tkind EXCEPT ![t] = "io"]
        /\ fails' = fails + 1
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 mexc, tragedy, closing, closed, enabled, mSegs, mN, mGen, mInfo,
                 mExc, mAbort, mSt, nextM, cpc, cOrig, cRet, cFail, tmg, flushes, delAll, cDA>>

TCommit(t) ==
  /\ tpc[t] = "commit"
  /\ LET m == tmg[t]
         r == mInfo[m] IN
     IF tragedy
       THEN /\ tpc' = [tpc EXCEPT ![t] = "failExc"]
            /\ tkind' = [tkind EXCEPT ![t] = "ise"]
            /\ UNCHANGED <<segs, anc, tmF>>
       ELSE IF mAbort[m]
            THEN /\ tpc' = [tpc EXCEPT ![t] = "finOK"]   \* commitMerge returns false
                 /\ UNCHANGED <<segs, anc, tmF, tkind>>
            ELSE /\ segs' = (segs \ mSegs[m]) \cup {r}
                 /\ anc' = [anc EXCEPT ![r] = UNION {anc[s] : s \in mSegs[m]}]
                 /\ tmF' = IF mN[m] /= UNB /\ r \notin TM THEN tmF \cup {r} ELSE tmF
                 /\ tpc' = [tpc EXCEPT ![t] = "finOK"]
                 /\ UNCHANGED tkind
  /\ UNCHANGED <<nextSeg, pending, running, merging, tmT, mmns, gen, mexc, tragedy,
                 closing, closed, enabled, mSegs, mN, mGen, mInfo, mExc, mAbort, mSt,
                 nextM, cpc, cOrig, cRet, cFail, tmg, fails, flushes, delAll, cDA>>

(* finally block of merge(), success: mergeFinish then updatePendingMerges *)
TFinOK(t) ==
  /\ tpc[t] = "finOK"
  /\ LET m == tmg[t]
         merging1 == merging \ mSegs[m]
         running1 == running \ {m}
         cascade == ~mAbort[m] /\ (mN[m] /= UNB \/ (~closed /\ ~closing))
                    /\ enabled /\ ~tragedy /\ CanRegister
         choices == IF ~cascade THEN {{}}
                    ELSE IF mN[m] /= UNB
                         THEN {FindForced(mN[m], merging1, pending \cup running1)}
                         ELSE NaturalChoices(merging1)
     IN /\ running' = running1
        /\ tpc' = [tpc EXCEPT ![t] = "idle"]
        /\ \E S \in choices :
             IF S = {}
               THEN /\ merging' = merging1 /\ pending' = pending
                    /\ mSt' = [mSt EXCEPT ![m] = "done"]
                    /\ UNCHANGED <<nextM, mSegs, mN, mGen>>
               ELSE LET mm == nextM
                        nn == IF S \cap TM /= {} THEN mmns ELSE mN[m]
                    IN /\ nextM' = nextM + 1
                       /\ mSegs' = [mSegs EXCEPT ![mm] = S]
                       /\ mN' = [mN EXCEPT ![mm] = nn]
                       /\ mGen' = [mGen EXCEPT ![mm] = gen]
                       /\ mSt' = [mSt EXCEPT ![m] = "done", ![mm] = "pend"]
                       /\ pending' = pending \cup {mm}
                       /\ merging' = merging1 \cup S
  /\ UNCHANGED <<segs, anc, nextSeg, tmT, tmF, mmns, gen, mexc, tragedy, closing,
                 closed, enabled, mInfo, mExc, mAbort, cpc, cOrig, cRet, cFail, tmg,
                 tkind, fails, flushes, delAll, cDA>>

(* handleMergeException: setException + addMergeException (gen filter) *)
TFailExc(t) ==
  /\ tpc[t] = "failExc"
  /\ LET m == tmg[t]
         \* a merge the caller is waiting for: failure while c is between reset and loop exit
         dep(c) == cpc[c] \in {"mm", "loop"} /\ (UNION {anc[s] : s \in mSegs[m]}) \cap OrigAnc(c) /= {}
     IN /\ mExc' = [mExc EXCEPT ![m] = TRUE]
        /\ mexc' = IF mGen[m] = gen THEN mexc \cup {m} ELSE mexc
        /\ tragedy' = IF FIX_TRAGIC_FIRST /\ tkind[t] /= "abort" THEN TRUE ELSE tragedy
        /\ cFail' = [c \in Callers |-> cFail[c] \/ (tkind[t] = "io" /\ dep(c))]
        /\ tpc' = [tpc EXCEPT ![t] = "finFail"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 closing, closed, enabled, mSegs, mN, mGen, mInfo, mAbort, mSt, nextM,
                 cpc, cOrig, cRet, tmg, tkind, fails, flushes, delAll, cDA>>

TFinFail(t) ==
  /\ tpc[t] = "finFail"
  /\ LET m == tmg[t] IN
     /\ merging' = merging \ mSegs[m]
     /\ running' = running \ {m}
     /\ mSt' = [mSt EXCEPT ![m] = "fail"]
     /\ tpc' = [tpc EXCEPT ![t] = IF tkind[t] = "abort" THEN "idle" ELSE "tragic"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, tmT, tmF, mmns, gen, mexc, tragedy,
                 closing, closed, enabled, mSegs, mN, mGen, mInfo, mExc, mAbort, nextM,
                 cpc, cOrig, cRet, cFail, tmg, tkind, fails, flushes, delAll, cDA>>

TTragic(t) ==
  /\ tpc[t] = "tragic"
  /\ tragedy' = TRUE
  /\ tpc' = [tpc EXCEPT ![t] = "rb1"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 mexc, closing, closed, enabled, mSegs, mN, mGen, mInfo, mExc, mAbort,
                 mSt, nextM, cpc, cOrig, cRet, cFail, tmg, tkind, fails, flushes, delAll, cDA>>

(* maybeCloseOnTragicEvent: shouldClose(false) then rollbackInternal *)
TRb1(t) ==
  /\ tpc[t] = "rb1"
  /\ IF ~closing /\ ~closed
       THEN /\ closing' = TRUE
            /\ enabled' = FALSE
            /\ mAbort' = [m \in MId |-> IF m \in InFlight THEN TRUE ELSE mAbort[m]]
            /\ mSt' = [m \in MId |-> IF m \in pending THEN "abortedPend" ELSE mSt[m]]
            /\ merging' = merging \ UNION {mSegs[m] : m \in pending}
            /\ pending' = {}
            /\ tpc' = [tpc EXCEPT ![t] = "rb2"]
       ELSE /\ tpc' = [tpc EXCEPT ![t] = "dead"]
            /\ UNCHANGED <<closing, enabled, mAbort, mSt, merging, pending>>
  /\ UNCHANGED <<segs, anc, nextSeg, running, tmT, tmF, mmns, gen, mexc, tragedy,
                 closed, mSegs, mN, mGen, mInfo, mExc, nextM, cpc, cOrig, cRet, cFail,
                 tmg, tkind, fails, flushes, delAll, cDA>>

TRb2(t) ==
  /\ tpc[t] = "rb2" /\ running = {}
  /\ closed' = TRUE /\ closing' = FALSE
  /\ tpc' = [tpc EXCEPT ![t] = "dead"]
  /\ UNCHANGED <<segs, anc, nextSeg, pending, running, merging, tmT, tmF, mmns, gen,
                 mexc, tragedy, enabled, mSegs, mN, mGen, mInfo, mExc, mAbort, mSt,
                 nextM, cpc, cOrig, cRet, cFail, tmg, tkind, fails, flushes, delAll, cDA>>

-----------------------------------------------------------------------------
CallerStep(c) == CStart(c) \/ CReset(c) \/ CMaybeMerge(c) \/ CLoop(c) \/ CEnsureOpen(c)
ThreadStep(t) == TTake(t) \/ TInit(t) \/ TMiddle(t) \/ TCommit(t) \/ TFinOK(t)
                 \/ TFailExc(t) \/ TFinFail(t) \/ TTragic(t) \/ TRb1(t) \/ TRb2(t)

Next == \/ \E c \in Callers : CallerStep(c)
        \/ \E t \in MThreads : ThreadStep(t)
        \/ IFlush \/ DAAbort \/ DADrop

Spec == Init /\ [][Next]_vars
FairSpec == Spec /\ \A c \in Callers : SF_vars(CallerStep(c))
                 /\ \A t \in MThreads : WF_vars(ThreadStep(t))
                 /\ WF_vars(DADrop)

-----------------------------------------------------------------------------
(* Properties *)
TypeOK ==
  /\ segs \subseteq Seg /\ pending \subseteq MId /\ running \subseteq MId
  /\ pending \cap running = {}

\* mergingSegments is exactly the sources of registered (pending/running) merges
MergingConsistent == merging = UNION {mSegs[m] : m \in InFlight}

\* F1: a normal return leaves the segments present at the call merged into <= n
F1 == \A c \in Callers : cpc[c] = "ok" /\ ~cDA[c] => cRet[c] <= CallerN[c]

\* F2: a failed (non-abort) merge over the call's segments is reported to the caller
F2 == \A c \in Callers : cpc[c] = "ok" /\ ~cDA[c] => ~cFail[c]

\* F3: no caller waits forever
F3 == \A c \in Callers : <>(cpc[c] \in {"ok", "threw"})

\* vacuity probes (expected to be violated)
ProbeNoOk == \A c \in Callers : cpc[c] /= "ok"
ProbeNoFail == fails = 0
=============================================================================

------------------------------ MODULE FnbFlush ------------------------------
(***************************************************************************)
(* Focused model of IndexWriter#flushNextBuffer racing indexing threads and *)
(* a full flush (commit / getReader), Lucene main a9779ef3c193.             *)
(* Paths under lucene/core/src/java/org/apache/lucene/index/.               *)
(*                                                                         *)
(* file:line map                                                            *)
(*  Obtain      DWFlushControl.java:526-555 obtainAndLock (stale DWPT of an *)
(*              advanced queue is dropped from the free list),              *)
(*              DWPTPool.java:115-127 getAndLock, 85-106 newWriter          *)
(*  Index       DocumentsWriter.java:427-428 dwpt.updateDocuments           *)
(*  After       DWFlushControl.java:207-251 doAfterDocument: FlushPolicy    *)
(*              (FlushByRamOrCountsPolicy.java:34-90: own DWPT by count, or *)
(*              markLargestWriterPending on ANY largest non-pending DWPT,    *)
(*              without its lock), then checkout() 253-272                  *)
(*  Release     DocumentsWriter.java:440-446 (free unless pending/advanced) *)
(*  NP1         DWFlushControl.java:441-452 nextPendingFlush, monitor part: *)
(*              poll flushQueue, else READ fullFlush and numPending         *)
(*  NP2/NP3     DWFlushControl.java:453-466: OUTSIDE the monitor, if the    *)
(*              values read were (numPending>0, !fullFlush): snapshot the   *)
(*              pool (DWPTPool.java:156-159) and checkOutForFlush (409-424) *)
(*              the first pending, unlocked, registered DWPT.  NOTE:        *)
(*              checkOutForFlush does NOT re-check fullFlush.               *)
(*  CL1-CL3     DWFlushControl.java:749-798 checkoutLargestNonPendingWriter *)
(*              (find under monitor; blocking lock(); isRegistered; then    *)
(*              checkout(dwpt, !pending) under the monitor = FRESH fullFlush*)
(*  FNB         IndexWriter.java:3758-3763 flushNextBuffer ->               *)
(*              DocumentsWriter.java:246-260 flushOneDWPT (maybeFlush, else *)
(*              checkoutLargestNonPendingWriter)                            *)
(*  Ticket/Seg  DocumentsWriter.java:502-581 doFlush (assert 508-515 that   *)
(*              the DWPT belongs to the full-flush queue; addTicket 534-536 *)
(*              -> ticket order; flush; doAfterFlush; loop nextPendingFlush)*)
(*  FF1         DocumentsWriter.java:674-682 + DWFlushControl.java:557-579  *)
(*              markForFullFlush: fullFlush=true, swap delete queue         *)
(*  FF2         DWFlushControl.java:580-606 filterAndLock old-queue DWPTs,  *)
(*              set pending + checkOutForFlush (or drop a 0-doc DWPT)       *)
(*  FF3         DWFlushControl.java:607-619 pruneBlockedQueue, flushQueue   *)
(*  FF4         DocumentsWriter.java:688 maybeFlush (only flushQueue: the   *)
(*              flusher's own fullFlush read is fresh = true)               *)
(*  FF5         DocumentsWriter.java:690 waitForFlush (flushingWriters={})  *)
(*  FF6         IndexWriter getReader/commit: forcePurge publishes tickets  *)
(*  FF7         DWFlushControl.java:649-664 finishFullFlush                 *)
(*                                                                         *)
(* Abstractions: RAM sizes are not modelled; "largest" in                   *)
(* markLargestWriterPending / findLargestNonPendingWriter is any eligible   *)
(* DWPT (sizes are data dependent).  The pool scan of NP3 is one step (one  *)
(* of the schedules the real loop can take).  Deletes are not modelled:     *)
(* the ticket-order invariant is what delete correctness rests on           *)
(* (IndexWriter.publishFlushedSegment applies a global packet only to       *)
(* segments published before it), checked end to end in the Java repro.    *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS Idx, Ctl, FF, NDocs, NCalls, MaxD, MODE, IdxHelp
\* MODE \in {"stock", "atomic", "fix"}:
\*   stock  = real code (NP1 and NP2/NP3 are separate steps)
\*   atomic = mutant: NP1..NP3 in one monitor step (dw-flush-control-deletequeue-tla DWFlush.tla semantics)
\*   fix    = checkOutForFlush in NP3 refuses when fullFlush is set
\* IdxHelp: indexing threads also run postUpdate's maybeFlush (checkPendingFlushOnUpdate)

Thr == Idx \cup Ctl \cup {FF}
D == 1..MaxD
NONE == 0
NOBODY == "nobody"

VARIABLES pc, k, own, fl, npff, npnp, snap, ret, cand, fnbRet,
          dst, holder, dq, ddocs, dpend,
          gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf

vars == <<pc, k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs,
          dpend, gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

Registered(d) == dst[d] \in {"free", "held", "parked"}
Unlocked(d) == holder[d] = NOBODY
NumPending == Cardinality({d \in D : Registered(d) /\ dpend[d]})
AllDocs == UNION {ddocs[d] : d \in D}

TypeOK == /\ \A d \in D : dst[d] \in {"none", "free", "held", "parked", "flushing",
                                        "blocked", "flushed", "dropped"}
          /\ fullFlush \in BOOLEAN

Init ==
  /\ pc = [t \in Thr |-> "start"] /\ k = [t \in Thr |-> 0]
  /\ own = [t \in Thr |-> NONE] /\ fl = [t \in Thr |-> NONE]
  /\ npff = [t \in Thr |-> FALSE] /\ npnp = [t \in Thr |-> FALSE]
  /\ snap = [t \in Thr |-> {}] /\ ret = [t \in Thr |-> "x"] /\ cand = [t \in Thr |-> NONE]
  /\ fnbRet = [t \in Thr |-> <<>>]
  /\ dst = [d \in D |-> "none"] /\ holder = [d \in D |-> NOBODY]
  /\ dq = [d \in D |-> 0] /\ ddocs = [d \in D |-> {}] /\ dpend = [d \in D |-> FALSE]
  /\ gen = 0 /\ fullFlush = FALSE /\ ffgen = 0 /\ fq = <<>> /\ flw = {}
  /\ tickets = <<>> /\ commit = <<>> /\ bad = {} /\ markDone = FALSE /\ ffbuf = <<>>

Goto(t, l) == pc' = [pc EXCEPT ![t] = l]

(* ---------------- nextPendingFlush sub-machine (NP1 -> NP2/NP3) ---------- *)
\* entry: pc[t] = "np1", ret[t] = label to go to when nothing was found
TakeFq(t) ==
  /\ fq /= <<>>
  /\ fl' = [fl EXCEPT ![t] = Head(fq)] /\ fq' = Tail(fq)
  /\ Goto(t, "ticket")

ScanCands == {d \in D : Registered(d) /\ dpend[d] /\ Unlocked(d)}

CheckOut(t, d) ==   \* checkOutForFlush(d) (lock taken and released around it)
  /\ dst' = [dst EXCEPT ![d] = "flushing"] /\ flw' = flw \cup {d}
  /\ fl' = [fl EXCEPT ![t] = d]

Aft(t) == IF t \in Ctl THEN "fnbdone" ELSE IF t = FF THEN "ff5" ELSE "next"

UNCH_T == UNCHANGED <<k, own, cand, fnbRet>>

NP1(t) ==
  /\ pc[t] = "np1"
  /\ IF fq /= <<>>
     THEN /\ TakeFq(t)
          /\ UNCHANGED <<npff, npnp, snap, ret, dst, holder, dq, ddocs, dpend, gen, fullFlush,
                         ffgen, flw, tickets, commit, bad, markDone, ffbuf>>
     ELSE IF MODE = "atomic"
          THEN IF NumPending > 0 /\ ~fullFlush /\ ScanCands /= {}
               THEN \E d \in ScanCands :
                      /\ CheckOut(t, d) /\ Goto(t, "ticket")
                      /\ UNCHANGED <<npff, npnp, snap, ret, holder, dq, ddocs, dpend, gen,
                                     fullFlush, ffgen, fq, tickets, commit, bad, markDone, ffbuf>>
               ELSE /\ Goto(t, ret[t])
                    /\ UNCHANGED <<fl, npff, npnp, snap, ret, dst, holder, dq, ddocs, dpend, gen,
                                   fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>
          ELSE /\ npff' = [npff EXCEPT ![t] = fullFlush]
               /\ npnp' = [npnp EXCEPT ![t] = (NumPending > 0)]
               /\ Goto(t, "np2")
               /\ UNCHANGED <<fl, snap, ret, dst, holder, dq, ddocs, dpend, gen, fullFlush,
                              ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>
  /\ UNCH_T

NP2(t) ==   \* outside the monitor: `if (numPending > 0 && fullFlush == false)` + pool snapshot
  /\ pc[t] = "np2"
  /\ IF npnp[t] /\ ~npff[t]
     THEN /\ snap' = [snap EXCEPT ![t] = {d \in D : Registered(d)}] /\ Goto(t, "np3")
     ELSE /\ Goto(t, ret[t]) /\ UNCHANGED snap
  /\ UNCHANGED <<fl, npff, npnp, ret, dst, holder, dq, ddocs, dpend, gen, fullFlush, ffgen, fq,
                 flw, tickets, commit, bad, markDone, ffbuf>>
  /\ UNCH_T

NP3(t) ==   \* scan: isFlushPending, tryLock, isRegistered, checkOutForFlush (no fullFlush check)
  /\ pc[t] = "np3"
  /\ LET C == {d \in snap[t] : Registered(d) /\ dpend[d] /\ Unlocked(d)}
         ok == MODE /= "fix" \/ ~fullFlush
     IN IF C /= {} /\ ok
        THEN \E d \in C : CheckOut(t, d) /\ Goto(t, "ticket")
        ELSE Goto(t, ret[t]) /\ UNCHANGED <<fl, dst, flw>>
  /\ UNCHANGED <<npff, npnp, snap, ret, holder, dq, ddocs, dpend, gen, fullFlush, ffgen, fq,
                 tickets, commit, bad, markDone, ffbuf>>
  /\ UNCH_T

(* ---------------- doFlush loop ---------------------------------------- *)
Ticket(t) ==
  /\ pc[t] = "ticket"
  /\ LET d == fl[t] IN
       /\ tickets' = Append(tickets, [g |-> dq[d], docs |-> ddocs[d], done |-> FALSE, by |-> t])
       \* DocumentsWriter.java:508-515: assert currentFullFlushDelQueue == null ||
       \* flushingDWPT.deleteQueue == currentFullFlushDelQueue (set between FF3 and FF7)
       /\ bad' = IF markDone /\ dq[d] /= ffgen THEN bad \cup {"doflush-assert"} ELSE bad
  /\ Goto(t, "seg")
  /\ UNCHANGED <<fl, npff, npnp, snap, ret, dst, holder, dq, ddocs, dpend, gen, fullFlush, ffgen,
                 fq, flw, commit, markDone, ffbuf>>
  /\ UNCH_T

Seg(t) ==   \* flush + addSegment, doAfterFlush, then loop on nextPendingFlush
  /\ pc[t] = "seg"
  /\ LET d == fl[t]
         i == CHOOSE j \in 1..Len(tickets) : tickets[j].by = t /\ tickets[j].done = FALSE
     IN /\ tickets' = [tickets EXCEPT ![i].done = TRUE]
        /\ dst' = [dst EXCEPT ![d] = "flushed"] /\ flw' = flw \ {d}
  /\ fl' = [fl EXCEPT ![t] = NONE]
  /\ ret' = [ret EXCEPT ![t] = Aft(t)]
  /\ fnbRet' = IF t \in Ctl THEN [fnbRet EXCEPT ![t] = Append(fnbRet[t], k[t])] ELSE fnbRet
  /\ Goto(t, "np1")
  /\ UNCHANGED <<npff, npnp, snap, holder, dq, ddocs, dpend, gen, fullFlush, ffgen, fq, commit,
                 bad, markDone, ffbuf, k, own, cand>>

(* ---------------- indexing threads ------------------------------------- *)
IStart(t) == /\ t \in Idx /\ pc[t] = "start" /\ Goto(t, "obtain")
             /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs,
                            dpend, gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

Obtain(t) ==
  /\ t \in Idx /\ pc[t] = "obtain"
  /\ LET Free == {d \in D : dst[d] = "free" /\ Unlocked(d)} IN
     IF Free /= {}
     THEN \E d \in Free :
            IF dq[d] = gen
            THEN /\ dst' = [dst EXCEPT ![d] = "held"] /\ holder' = [holder EXCEPT ![d] = t]
                 /\ own' = [own EXCEPT ![t] = d] /\ Goto(t, "index")
                 /\ UNCHANGED <<dq, ddocs, dpend>>
            ELSE /\ dst' = [dst EXCEPT ![d] = "parked"]   \* stale: polled, unlocked, dropped
                 /\ UNCHANGED <<holder, own, pc, dq, ddocs, dpend>>
     ELSE \E d \in D :   \* newWriter (DWPTPool.java:85-106): current delete queue
            /\ dst[d] = "none"
            /\ dst' = [dst EXCEPT ![d] = "held"] /\ holder' = [holder EXCEPT ![d] = t]
            /\ dq' = [dq EXCEPT ![d] = gen] /\ ddocs' = [ddocs EXCEPT ![d] = {}]
            /\ dpend' = [dpend EXCEPT ![d] = FALSE]
            /\ own' = [own EXCEPT ![t] = d] /\ Goto(t, "index")
  /\ UNCHANGED <<k, fl, npff, npnp, snap, ret, cand, fnbRet, gen, fullFlush, ffgen, fq, flw,
                 tickets, commit, bad, markDone, ffbuf>>

Index(t) ==
  /\ t \in Idx /\ pc[t] = "index"
  /\ ddocs' = [ddocs EXCEPT ![own[t]] = @ \cup {[id |-> <<t, k[t]>>, g |-> dq[own[t]]]}]
  /\ Goto(t, "after")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

\* doAfterDocument (monitor): policy marks own or any largest non-pending DWPT, then checkout
After(t) ==
  /\ t \in Idx /\ pc[t] = "after"
  /\ LET d == own[t] IN
     \E pol \in {"none", "self", "other"} :
     \E o \in (IF pol = "other"
               THEN {x \in D : Registered(x) /\ ~dpend[x] /\ ddocs[x] /= {}}
               ELSE {d}) :
       LET p1 == IF pol = "none" THEN dpend
                 ELSE IF pol = "self" THEN [dpend EXCEPT ![d] = TRUE]
                 ELSE [dpend EXCEPT ![o] = TRUE]
       IN /\ (pol = "self" => ~dpend[d])
          /\ dpend' = p1
          /\ IF fullFlush
             THEN IF p1[d]
                  THEN /\ dst' = [dst EXCEPT ![d] = "blocked"]   \* checkoutAndBlock
                       \* return nextPendingFlush() under the monitor: fresh fullFlush = true
                       /\ IF fq /= <<>>
                          THEN /\ fl' = [fl EXCEPT ![t] = Head(fq)] /\ fq' = Tail(fq)
                          ELSE UNCHANGED <<fl, fq>>
                       /\ UNCHANGED flw
                  ELSE UNCHANGED <<dst, fl, fq, flw>>
             ELSE IF p1[d]
                  THEN /\ dst' = [dst EXCEPT ![d] = "flushing"] /\ flw' = flw \cup {d}
                       /\ fl' = [fl EXCEPT ![t] = d] /\ UNCHANGED fq
                  ELSE UNCHANGED <<dst, fl, fq, flw>>
  /\ Goto(t, "release")
  /\ UNCHANGED <<k, own, npff, npnp, snap, ret, cand, fnbRet, holder, dq, ddocs, gen, fullFlush,
                 ffgen, tickets, commit, bad, markDone, ffbuf>>

Release(t) ==   \* DocumentsWriter.java:440-446 (monitor)
  /\ t \in Idx /\ pc[t] = "release"
  /\ LET d == own[t] IN
       /\ dst' = IF dst[d] = "held"
                 THEN [dst EXCEPT ![d] = IF dpend[d] \/ dq[d] /= gen THEN "parked" ELSE "free"]
                 ELSE dst
       /\ holder' = [holder EXCEPT ![d] = NOBODY]
  /\ own' = [own EXCEPT ![t] = NONE]
  /\ IF fl[t] /= NONE THEN Goto(t, "ticket") ELSE Goto(t, "post")
  /\ UNCHANGED <<k, fl, npff, npnp, snap, ret, cand, fnbRet, dq, ddocs, dpend, gen, fullFlush,
                 ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

Post(t) ==   \* postUpdate: maybeFlush() when checkPendingFlushOnUpdate (default true)
  /\ t \in Idx /\ pc[t] = "post"
  /\ IF IdxHelp THEN /\ ret' = [ret EXCEPT ![t] = "next"] /\ Goto(t, "np1")
                ELSE /\ Goto(t, "next") /\ UNCHANGED ret
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, cand, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

INext(t) ==
  /\ t \in Idx /\ pc[t] = "next"
  /\ k' = [k EXCEPT ![t] = @ + 1]
  /\ IF k[t] + 1 < NDocs[t] THEN Goto(t, "obtain") ELSE Goto(t, "end")
  /\ UNCHANGED <<own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

(* ---------------- controller: flushNextBuffer -------------------------- *)
CStart(t) == /\ t \in Ctl /\ pc[t] = "start" /\ Goto(t, "fnb")
             /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs,
                            dpend, gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

Fnb(t) ==   \* flushOneDWPT: maybeFlush(), else checkoutLargestNonPendingWriter
  /\ t \in Ctl /\ pc[t] = "fnb"
  /\ IF k[t] < NCalls THEN /\ ret' = [ret EXCEPT ![t] = "cl1"] /\ Goto(t, "np1")
                       ELSE /\ Goto(t, "end") /\ UNCHANGED ret
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, cand, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

CL1(t) ==   \* findLargestNonPendingWriter (monitor)
  /\ t \in Ctl /\ pc[t] = "cl1"
  /\ LET C == {d \in D : Registered(d) /\ ~dpend[d] /\ ddocs[d] /= {}} IN
     IF C = {} THEN Goto(t, "fnbdone") /\ UNCHANGED cand
     ELSE \E d \in C : cand' = [cand EXCEPT ![t] = d] /\ Goto(t, "cl2")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

CL2(t) ==   \* largestNonPendingWriter.lock() (blocking)
  /\ t \in Ctl /\ pc[t] = "cl2" /\ Unlocked(cand[t])
  /\ holder' = [holder EXCEPT ![cand[t]] = t] /\ Goto(t, "cl3")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

CL3(t) ==   \* isRegistered; checkout(dwpt, !pending) under the monitor; unlock
  /\ t \in Ctl /\ pc[t] = "cl3"
  /\ LET d == cand[t] IN
     /\ holder' = [holder EXCEPT ![d] = NOBODY]
     /\ IF ~Registered(d)
        THEN /\ Goto(t, "fnbdone") /\ UNCHANGED <<fl, dst, dpend, fq, flw>>
        ELSE IF fullFlush
             THEN IF dpend[d]
                  THEN /\ dst' = [dst EXCEPT ![d] = "blocked"] /\ UNCHANGED <<dpend, flw>>
                       /\ IF fq /= <<>>
                          THEN /\ fl' = [fl EXCEPT ![t] = Head(fq)] /\ fq' = Tail(fq)
                               /\ Goto(t, "ticket")
                          ELSE /\ UNCHANGED <<fl, fq>> /\ Goto(t, "fnbdone")
                  ELSE /\ Goto(t, "fnbdone") /\ UNCHANGED <<fl, dst, dpend, fq, flw>>
             ELSE /\ dpend' = [dpend EXCEPT ![d] = TRUE]
                  /\ dst' = [dst EXCEPT ![d] = "flushing"] /\ flw' = flw \cup {d}
                  /\ fl' = [fl EXCEPT ![t] = d] /\ Goto(t, "ticket") /\ UNCHANGED fq
  /\ UNCHANGED <<k, own, npff, npnp, snap, ret, cand, fnbRet, dq, ddocs, gen, fullFlush, ffgen,
                 tickets, commit, bad, markDone, ffbuf>>

FnbDone(t) ==
  /\ t \in Ctl /\ pc[t] = "fnbdone"
  /\ k' = [k EXCEPT ![t] = @ + 1] /\ Goto(t, "fnb")
  /\ UNCHANGED <<own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

(* ---------------- full flush (commit / getReader) ---------------------- *)
FStart == /\ pc[FF] = "start" /\ Goto(FF, "ff1")
          /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs,
                         dpend, gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

FF1 ==   \* markForFullFlush first block: fullFlush = true, swap the delete queue
  /\ pc[FF] = "ff1"
  /\ fullFlush' = TRUE /\ ffgen' = gen /\ gen' = gen + 1 /\ Goto(FF, "ff2")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs, dpend,
                 fq, flw, tickets, commit, bad, markDone, ffbuf>>

FF2 ==   \* filterAndLock(old queue): lock, set pending + checkOutForFlush, or drop a 0-doc DWPT
  /\ pc[FF] = "ff2"
  /\ LET Old == {d \in D : Registered(d) /\ dq[d] = ffgen} IN
     IF Old = {}
     THEN Goto(FF, "ff3") /\ UNCHANGED <<dst, dpend, flw, ffbuf>>
     ELSE \E d \in Old :
            /\ Unlocked(d)
            /\ IF ddocs[d] /= {}
               THEN /\ dpend' = [dpend EXCEPT ![d] = TRUE]
                    /\ dst' = [dst EXCEPT ![d] = "flushing"] /\ flw' = flw \cup {d}
                    /\ ffbuf' = Append(ffbuf, d)
               ELSE /\ dst' = [dst EXCEPT ![d] = "dropped"] /\ UNCHANGED <<dpend, flw, ffbuf>>
            /\ UNCHANGED pc
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, holder, dq, ddocs, gen,
                 fullFlush, ffgen, fq, tickets, commit, bad, markDone>>

RECURSIVE SeqOf(_)
SeqOf(S) == IF S = {} THEN <<>> ELSE LET x == CHOOSE y \in S : TRUE IN <<x>> \o SeqOf(S \ {x})

FF3 ==   \* pruneBlockedQueue(old queue) + flushQueue.addAll + markDone (monitor)
  /\ pc[FF] = "ff3"
  /\ LET B == {d \in D : dst[d] = "blocked" /\ dq[d] = ffgen} IN
       /\ fq' = fq \o SeqOf(B) \o ffbuf
       /\ dst' = [d \in D |-> IF d \in B THEN "flushing" ELSE dst[d]]
       /\ flw' = flw \cup B
  /\ markDone' = TRUE /\ Goto(FF, "ff4")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, holder, dq, ddocs, dpend, gen,
                 fullFlush, ffgen, tickets, commit, bad, ffbuf>>

FF4 == /\ pc[FF] = "ff4" /\ ret' = [ret EXCEPT ![FF] = "ff5"] /\ Goto(FF, "np1")
       /\ UNCHANGED <<k, own, fl, npff, npnp, snap, cand, fnbRet, dst, holder, dq, ddocs, dpend, gen,
                      fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

FF5 == /\ pc[FF] = "ff5" /\ flw = {} /\ Goto(FF, "ff6")   \* waitForFlush
       /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs, dpend,
                      gen, fullFlush, ffgen, fq, flw, tickets, commit, bad, markDone, ffbuf>>

RECURSIVE DonePrefix(_)
DonePrefix(T) == IF T = <<>> \/ ~Head(T).done THEN <<>> ELSE <<Head(T)>> \o DonePrefix(Tail(T))

FF6 ==   \* publish (forcePurge) and take the commit / reader: must be exactly the old generation
  /\ pc[FF] = "ff6"
  /\ LET P == DonePrefix(tickets)
         PD == UNION {P[i].docs : i \in 1..Len(P)}
         Want == {x \in AllDocs : x.g <= ffgen}
     IN /\ commit' = P
        /\ bad' = bad \cup (IF \E x \in PD : x.g > ffgen THEN {"commit-has-later-op"} ELSE {})
                      \cup (IF ~(Want \subseteq PD) THEN {"commit-misses-op"} ELSE {})
  /\ Goto(FF, "ff7")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, dst, holder, dq, ddocs, dpend,
                 gen, fullFlush, ffgen, fq, flw, tickets, markDone, ffbuf>>

FF7 ==   \* finishFullFlush: blocked new-queue DWPTs -> flushQueue
  /\ pc[FF] = "ff7"
  /\ LET B == {d \in D : dst[d] = "blocked" /\ dq[d] = gen} IN
       /\ fq' = fq \o SeqOf(B)
       /\ dst' = [d \in D |-> IF d \in B THEN "flushing" ELSE dst[d]]
       /\ flw' = flw \cup B
  /\ fullFlush' = FALSE /\ markDone' = FALSE /\ Goto(FF, "end")
  /\ UNCHANGED <<k, own, fl, npff, npnp, snap, ret, cand, fnbRet, holder, dq, ddocs, dpend, gen,
                 ffgen, tickets, commit, bad, ffbuf>>

Done == /\ \A t \in Thr : pc[t] = "end"
        /\ UNCHANGED vars

Next ==
  \/ \E t \in Thr : NP1(t) \/ NP2(t) \/ NP3(t) \/ Ticket(t) \/ Seg(t)
  \/ \E t \in Idx : IStart(t) \/ Obtain(t) \/ Index(t) \/ After(t) \/ Release(t) \/ Post(t) \/ INext(t)
  \/ \E t \in Ctl : CStart(t) \/ Fnb(t) \/ CL1(t) \/ CL2(t) \/ CL3(t) \/ FnbDone(t)
  \/ FStart \/ FF1 \/ FF2 \/ FF3 \/ FF4 \/ FF5 \/ FF6 \/ FF7
  \/ Done

Spec == Init /\ [][Next]_vars

(* Invariants *)
TicketOrder == \A i, j \in 1..Len(tickets) : i < j => tickets[i].g <= tickets[j].g
NoBad == bad = {}
\* -da semantics: ignore the doFlush assertion, check only the commit content
NoCommitBad == bad \cap {"commit-has-later-op", "commit-misses-op"} = {}
\* vacuity probe: a ticket of the new generation exists at all (must be violated)
ProbeNoNewGenTicket == \A i \in 1..Len(tickets) : tickets[i].g = 0
=============================================================================

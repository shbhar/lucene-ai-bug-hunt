------------------------------- MODULE DWFlush -------------------------------
(***************************************************************************)
(* Model of the IndexWriter / DocumentsWriter flush protocol on Lucene main *)
(* ea57360912ed: DWPT pool checkout, flush-pending / blocked / flushQueue,  *)
(* delete-queue slices and sequence numbers with queue advance, flush       *)
(* tickets published in order, full flush (NRT reader / commit, optionally  *)
(* failing after the flush), deleteAll (lockAndAbortAll) and                *)
(* tryDeleteDocument.  One delete key: every delete/update deletes all      *)
(* earlier documents, so a delete is fully described by its seqNo.          *)
(*                                                                         *)
(* file:line map (paths under lucene/core/src/java/org/apache/lucene/index) *)
(*  Obtain       DocumentsWriterFlushControl.java:526-555 obtainAndLock,    *)
(*               DocumentsWriterPerThreadPool.java:115-127,84-103          *)
(*  Index        DocumentsWriterPerThread.java:245-312,361-390 (seqNo at    *)
(*               finishDocuments), DocumentsWriterDeleteQueue.java:154-179, *)
(*               284-293 (add / updateSlice), 567-571 (assert <= maxSeqNo) *)
(*  After        DocumentsWriterFlushControl.java:207-272 doAfterDocument + *)
(*               checkout; DocumentsWriter.java:435-447 (free / park)       *)
(*  MarkOther    FlushByRamOrCountsPolicy.java:72-80 markLargestWriterPending*)
(*               (setFlushPending without the DWPT lock, DWFC:365-376)      *)
(*  Post         DocumentsWriter.java:400-410 postUpdate, 168-186 applyAllDel*)
(*  FTicket      DocumentsWriter.java:534-536, DWPT.java:427-439 prepareFlush*)
(*               DocumentsWriterDeleteQueue.java:217-277 freezeGlobalBuffer *)
(*  FSeg         DocumentsWriter.java:541-542 flush + addSegment            *)
(*  FAfter       DocumentsWriter.java:577-579 doAfterFlush + nextPendingFlush*)
(*               DWFC.java:290-303, 441-469                                 *)
(*  Purge        DocumentsWriterFlushQueue.java:79-133 innerPurge, IW 6266, *)
(*               IW 2953-2990 publishFlushedSegment (global packet first)   *)
(*  Del          DocumentsWriter.java:153-165 applyDeleteOrUpdate (DW sync) *)
(*  FFMark       DocumentsWriter.java:667-682, DWFC.java:557-579 (advance,  *)
(*               DocumentsWriterDeleteQueue.java:616-630)                   *)
(*  FFCollect    DWFC.java:580-606 (filterAndLock, 0-doc DWPT dropped)      *)
(*  FFMarkDone   DWFC.java:607-619 pruneBlockedQueue + flushQueue.addAll    *)
(*  FFWait/Close DocumentsWriter.java:686-709, DWDQ.java:317-331 close      *)
(*  FFPub        IW.java:592 (getReader) / 3817 (commit) forcePurge         *)
(*  FFEnd        IW.java:595-628 snapshot, 669-675 / 3864-3870             *)
(*               finishFullFlush(true|false): DWFC.java:649-664, 673-710    *)
(*  DA*          IW.java:2750-2812 deleteAll; DocumentsWriter.java:266-325  *)
(*               lockAndAbortAll (forcePurge discards tickets FIRST, then   *)
(*               aborts pool DWPTs, abortPendingFlushes, waitForFlush)      *)
(*  Try          IW.java:1790-1826 tryModifyDocument (seqNo from current    *)
(*               queue under IW monitor)                                    *)
(* Not modelled: RAM byte accounting and stall control (see DWStall.tla),   *)
(* doc-values updates, merges, aborting exceptions (tragic).                *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS Idx, Ctl, IScript, CScript, MaxDWPT, FIX, MUT
\* FIX \subseteq {"A","B"}: A = a getReader failure after flushAllThreads keeps blocked
\*   DWPTs (finishFullFlush(true) semantics, as prepareCommitInternal's `flushed` flag);
\*   B = deleteAll discards tickets again after waitForFlush.
\* MUT \in {"none","upto","nofreeze","nogap"}: vacuity mutants (must be caught):
\*   upto = DWPT applies a new delete to its own new doc; nofreeze = prepareFlush does not
\*   freeze the global buffer; nogap = queue advance without the per-DWPT seqNo gap.
\* IScript[t] : Seq of "add" | "upd" | "del" | "try"
\* CScript[c] : Seq of "nrt" | "nrtfail" | "commit" | "da" | "final"
\* "final" = commit that waits until every indexer finished (the end check)

Thr == Idx \cup Ctl
D == 1..MaxDWPT
NONE == 0
NOBODY == "nobody"   \* no thread holds the monitor / lock
Gens == 0..3

VARIABLES pc, k, cur, fl, ret, anyFl,          \* per-thread locals
          dst, dq, ddocs, dpend, dslice, dupto, \* DWPTs
          gen, qnext, qmax, qclosed, nodes, gtail, \* delete queues
          tickets, pub, hist, fq, blocked, flw,  \* flush queue / published
          fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad, evq  \* evq: queued forced-purge events

vars == <<pc, k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto,
          gen, qnext, qmax, qclosed, nodes, gtail, tickets, pub, hist, fq,
          blocked, flw, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad, evq>>

Kind(t) == IF t \in Idx THEN IScript[t][k[t]] ELSE CScript[t][k[t]]
Len2(t) == IF t \in Idx THEN Len(IScript[t]) ELSE Len(CScript[t])
InPool(d) == dst[d] \in {"free", "held", "parked"}
Pool == {d \in D : InPool(d)}
Max(S) == IF S = {} THEN 0 ELSE CHOOSE x \in S : \A y \in S : y <= x
\* max delete seqNo among queue-g nodes with index in (lo, hi]
MaxDel(g, lo, hi) == Max({nodes[g][i] : i \in (lo+1)..hi})
ApplyDel(P, f) == {x \in P : x.s > f}
Docs == {[id |-> o.id, s |-> o.s] : o \in {h \in hist : h.k \in {"add", "upd"}}}
Live(S) == {x \in Docs : x.s <= S /\
              ~\E o \in hist : o.s <= S /\ o.s > x.s /\
                 (o.k \in {"del", "upd", "da"} \/ (o.k = "try" /\ o.tid = x.id))}
IdxDone == \A t \in Idx : pc[t] = "end"
MaxCompleted == IF qnext[gen] > (IF gen = 0 THEN 1 ELSE qmax[gen-1] + 1)
                THEN qnext[gen] - 1
                ELSE IF gen = 0 THEN 0 ELSE qnext[gen-1] - 1
Flag(r) == bad' = bad \cup {r}

\* Publish every head ticket that can publish, in order (innerPurge loop).
RECURSIVE PubAll(_, _)
PubAll(T, P) == IF T = <<>> \/ Head(T).st = "pend" THEN [t |-> T, p |-> P]
                ELSE PubAll(Tail(T), ApplyDel(P, Head(T).frz) \cup
                                     (IF Head(T).st = "ok" THEN Head(T).seg ELSE {}))
RECURSIVE DropAll(_)
DropAll(T) == IF T = <<>> \/ Head(T).st = "pend" THEN T ELSE DropAll(Tail(T))

\* nextPendingFlush (DWFC:441-469): head of flushQueue, else (not full flush) any
\* pending DWPT in the pool not locked by another thread.
NextCands == IF fq /= <<>> THEN {Head(fq)}
             ELSE IF fullFlush THEN {}
             ELSE {d \in D : dst[d] \in {"free", "parked"} /\ dpend[d]}
\* checkOutForFlush (pool DWPT) or flushQueue.poll; dbase/fbase = state to update
Take(t, d, dbase, fbase) ==
  /\ fl' = [fl EXCEPT ![t] = d]
  /\ IF fq /= <<>> THEN fq' = Tail(fq) ELSE fq' = fq
  /\ dst' = [dbase EXCEPT ![d] = "flushing"]
  /\ flw' = fbase \cup {d}

Init ==
  /\ pc = [t \in Thr |-> "next"] /\ k = [t \in Thr |-> 0]
  /\ cur = [t \in Thr |-> NONE] /\ fl = [t \in Thr |-> NONE]
  /\ ret = [t \in Thr |-> "ret"] /\ anyFl = [t \in Thr |-> FALSE]
  /\ dst = [d \in D |-> "none"] /\ dq = [d \in D |-> 0]
  /\ ddocs = [d \in D |-> <<>>] /\ dpend = [d \in D |-> FALSE]
  /\ dslice = [d \in D |-> 0] /\ dupto = [d \in D |-> 0]
  /\ gen = 0 /\ qnext = [g \in Gens |-> 1] /\ qmax = [g \in Gens |-> 1000]
  /\ qclosed = [g \in Gens |-> FALSE] /\ nodes = [g \in Gens |-> <<>>]
  /\ gtail = [g \in Gens |-> 0]
  /\ tickets = <<>> /\ pub = {} /\ hist = {} /\ fq = <<>> /\ blocked = {}
  /\ flw = {} /\ fullFlush = FALSE /\ ffq = 0 /\ ffSeq = 0 /\ ffl = NOBODY
  /\ dwMon = NOBODY /\ nwLock = 0 /\ bad = {} /\ evq = 0

Next1(t) ==   \* pick the next scripted op
  /\ pc[t] = "next"
  /\ IF k[t] < Len2(t)
     THEN /\ k' = [k EXCEPT ![t] = k[t] + 1]
          /\ pc' = [pc EXCEPT ![t] =
               LET o == IF t \in Idx THEN IScript[t][k[t]+1] ELSE CScript[t][k[t]+1]
               IN CASE o \in {"add", "upd"} -> "obtain"
                    [] o = "del" -> "del"
                    [] o = "try" -> "try"
                    [] OTHER -> "c_lock"]
     ELSE /\ pc' = [pc EXCEPT ![t] = "end"] /\ k' = k
  /\ UNCHANGED <<cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen,
                 qnext, qmax, qclosed, nodes, gtail, tickets, pub, hist, fq,
                 blocked, flw, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

\* a DWPT slot can model a new DWPT once its old instance is gone (bounded model, ids recycled)
Reusable(d) ==
  /\ dst[d] \in {"none", "flushed", "aborted", "dropped"}
  /\ d \notin flw
  /\ d \notin blocked
  /\ \A i \in DOMAIN fq : fq[i] /= d
  /\ \A u \in Thr : fl[u] /= d /\ cur[u] /= d
  /\ \A i \in DOMAIN tickets : ~(tickets[i].d = d /\ tickets[i].st = "pend")
PreHelp(t) ==   \* preUpdate: checkPendingFlushOnUpdate && numQueuedFlushes() > 0 -> maybeFlush()
  /\ pc[t] = "obtain" /\ fq /= <<>>
  /\ Take(t, Head(fq), dst, flw) /\ pc' = [pc EXCEPT ![t] = "f_ticket"]
  /\ ret' = [ret EXCEPT ![t] = "obtain"]
  /\ UNCHANGED <<k, cur, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax, qclosed, nodes,
                 gtail, tickets, pub, hist, blocked, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad, evq>>

Obtain(t) ==   \* reuse a free DWPT of the current queue, or create one
  /\ pc[t] = "obtain" /\ fq = <<>>
  /\ \/ \E d \in D : /\ dst[d] = "free" /\ dq[d] = gen
                     /\ dst' = [dst EXCEPT ![d] = "held"] /\ cur' = [cur EXCEPT ![t] = d]
                     /\ UNCHANGED <<dq, dslice, ddocs, dpend, dupto>>
     \/ /\ nwLock = 0 /\ ~\E d \in D : dst[d] = "free" /\ dq[d] = gen
        /\ \E d \in D : /\ Reusable(d) /\ \A e \in D : e < d => ~Reusable(e)
                        /\ dst' = [dst EXCEPT ![d] = "held"] /\ cur' = [cur EXCEPT ![t] = d]
                        /\ dq' = [dq EXCEPT ![d] = gen]
                        /\ dslice' = [dslice EXCEPT ![d] = Len(nodes[gen])]
                        /\ ddocs' = [ddocs EXCEPT ![d] = <<>>]
                        /\ dpend' = [dpend EXCEPT ![d] = FALSE]
                        /\ dupto' = [dupto EXCEPT ![d] = 0]
  /\ pc' = [pc EXCEPT ![t] = "index"]
  /\ UNCHANGED <<k, fl, ret, anyFl, gen, qnext, qmax, qclosed,
                 nodes, gtail, tickets, pub, hist, fq, blocked, flw, fullFlush, ffq,
                 ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

Index(t) ==   \* DWPT.updateDocuments + finishDocuments (seqNo assignment)
  LET d == cur[t]  g == dq[d]  s == qnext[g]  n == Len(ddocs[d])
      nd == IF Kind(t) = "upd" THEN Append(nodes[g], s) ELSE nodes[g]
  IN /\ pc[t] = "index"
     /\ bad' = bad \cup (IF qclosed[g] THEN {"index-on-closed-queue"} ELSE {})
                    \cup (IF s > qmax[g] THEN {"seqno-above-maxSeqNo"} ELSE {})
     /\ qnext' = [qnext EXCEPT ![g] = s + 1]
     /\ nodes' = [nodes EXCEPT ![g] = nd]
     /\ dupto' = [dupto EXCEPT ![d] = IF Len(nd) > dslice[d] THEN (IF MUT = "upto" THEN n + 1 ELSE n)
                                         ELSE dupto[d]]
     /\ dslice' = [dslice EXCEPT ![d] = Len(nd)]
     /\ ddocs' = [ddocs EXCEPT ![d] = Append(ddocs[d], [id |-> <<t, k[t]>>, s |-> s])]
     /\ hist' = hist \cup {[k |-> Kind(t), s |-> s, id |-> <<t, k[t]>>, tid |-> <<>>]}
     /\ pc' = [pc EXCEPT ![t] = "after"]
     /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, dpend, gen, qmax, qclosed, gtail,
                    tickets, pub, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock>>
  /\ UNCHANGED evq

After(t) ==   \* doAfterDocument + checkout + free/park (all under the DWFC monitor)
  LET d == cur[t]
      park == [dst EXCEPT ![d] = IF dq[d] /= gen THEN "parked" ELSE "free"]
  IN
  /\ pc[t] = "after"
  /\ \E mk \in BOOLEAN, other \in {NONE} \cup {e \in Pool : e /= d /\ ~dpend[e] /\ ddocs[e] /= <<>>}:
       LET p1 == dpend[d] \/ mk
           dp == [dpend EXCEPT ![d] = p1]
       IN /\ dpend' = IF other /= NONE THEN [dp EXCEPT ![other] = TRUE] ELSE dp
          /\ IF fullFlush /\ p1      \* checkoutAndBlock, then nextPendingFlush()
             THEN /\ blocked' = blocked \cup {d}
                  /\ IF fq /= <<>> THEN Take(t, Head(fq), [dst EXCEPT ![d] = "blocked"], flw)
                     ELSE /\ dst' = [dst EXCEPT ![d] = "blocked"] /\ UNCHANGED <<fl, fq, flw>>
             ELSE IF p1               \* checkOutForFlush(perThread)
             THEN /\ dst' = [dst EXCEPT ![d] = "flushing"] /\ flw' = flw \cup {d}
                  /\ fl' = [fl EXCEPT ![t] = d] /\ UNCHANGED <<blocked, fq>>
             ELSE /\ dst' = park /\ UNCHANGED <<blocked, fl, fq, flw>>
  /\ cur' = [cur EXCEPT ![t] = NONE] /\ pc' = [pc EXCEPT ![t] = "post"]
  /\ UNCHANGED <<k, ret, anyFl, dq, ddocs, dslice, dupto, gen, qnext, qmax, qclosed, nodes,
                 gtail, tickets, pub, hist, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

\* postUpdate: applyAllDeletes (optional, never during a full flush), then doFlush of
\* the returned DWPT, else maybeFlush (checkPendingFlushOnUpdate). Deletes only
\* applyAllDeletes (DocumentsWriter.java:153-165).
Post(t) ==
  /\ pc[t] = "post"
  /\ \E ad \in BOOLEAN :
       LET doAD == ad /\ ~fullFlush /\ ~qclosed[gen] /\ gtail[gen] < Len(nodes[gen])
           tk == [st |-> "ok", frz |-> MaxDel(gen, gtail[gen], Len(nodes[gen])), seg |-> {}, d |-> NONE]
       IN /\ tickets' = IF doAD THEN Append(tickets, tk) ELSE tickets
          /\ gtail' = IF doAD THEN [gtail EXCEPT ![gen] = Len(nodes[gen])] ELSE gtail
  /\ ret' = [ret EXCEPT ![t] = "ret"]
  /\ IF fl[t] /= NONE
     THEN /\ pc' = [pc EXCEPT ![t] = "f_ticket"] /\ UNCHANGED <<fl, fq, dst, flw>>
     ELSE IF Kind(t) /= "del" /\ NextCands /= {}
     THEN /\ \E d \in NextCands : Take(t, d, dst, flw)
          /\ pc' = [pc EXCEPT ![t] = "f_ticket"]
     ELSE /\ pc' = [pc EXCEPT ![t] = "ret"] /\ UNCHANGED <<fl, fq, dst, flw>>
  /\ UNCHANGED <<k, cur, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax, qclosed,
                 nodes, pub, hist, blocked, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

FTicket(t) ==   \* addTicket(prepareFlush): freeze global buffer, sync + apply DWPT slice
  LET d == fl[t]  g == dq[d]  tl == Len(nodes[g]) IN
  /\ pc[t] = "f_ticket"
  /\ IF qclosed[g] THEN Flag("flush-on-closed-queue") ELSE UNCHANGED bad
  /\ tickets' = Append(tickets, [st |-> "pend", seg |-> {}, d |-> d,
                                 frz |-> IF MUT = "nofreeze" THEN 0 ELSE MaxDel(g, gtail[g], tl)])
  /\ gtail' = [gtail EXCEPT ![g] = IF MUT = "nofreeze" THEN gtail[g] ELSE tl]
  /\ dupto' = [dupto EXCEPT ![d] = IF tl > dslice[d] THEN Len(ddocs[d]) ELSE dupto[d]]
  /\ dslice' = [dslice EXCEPT ![d] = tl]
  /\ pc' = [pc EXCEPT ![t] = "f_seg"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, gen, qnext, qmax, qclosed,
                 nodes, pub, hist, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock>>
  /\ UNCHANGED evq

FSeg(t) ==   \* DWPT.flush done: segment = docs not covered by the private delete upto
  LET d == fl[t]
      seg == {ddocs[d][i] : i \in {j \in 1..Len(ddocs[d]) : j > dupto[d]}}
      i0 == CHOOSE i \in 1..Len(tickets) : tickets[i].d = d /\ tickets[i].st = "pend"
  IN /\ pc[t] = "f_seg"
     /\ tickets' = [tickets EXCEPT ![i0] = [@ EXCEPT !.st = "ok", !.seg = seg]]
     /\ dst' = [dst EXCEPT ![d] = "flushed"]
     \* DocumentsWriter.java:569-575: ticketCount >= perThreadPool.size() -> onTicketBacklog
     \* queues a forced purge (IW.java:470-472), run by the next processEvents of any thread
     /\ evq' = IF Len(tickets) >= Cardinality(Pool) THEN evq + 1 ELSE evq
     /\ pc' = [pc EXCEPT ![t] = "f_after"]
     /\ UNCHANGED <<k, cur, fl, ret, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                    qmax, qclosed, nodes, gtail, pub, hist, fq, blocked, flw, fullFlush,
                    ffq, ffSeq, ffl, dwMon, nwLock, bad>>

FAfter(t) ==   \* doAfterFlush (wakes waitForFlush), then nextPendingFlush
  /\ pc[t] = "f_after"
  /\ anyFl' = [anyFl EXCEPT ![t] = TRUE]
  /\ LET flw1 == flw \ {fl[t]} IN
     IF NextCands /= {}
     THEN /\ \E d \in NextCands : Take(t, d, dst, flw1)
          /\ pc' = [pc EXCEPT ![t] = "f_ticket"]
     ELSE /\ flw' = flw1 /\ pc' = [pc EXCEPT ![t] = "f_purge"]
          /\ UNCHANGED <<fl, fq, dst>>
  /\ UNCHANGED <<k, cur, ret, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax, qclosed, nodes,
                 gtail, tickets, pub, hist, blocked, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

Purging(t) == \E u \in Thr \ {t} : pc[u] \in {"f_purge", "ret", "ff_pub"}
FPurge(t) ==   \* afterSegmentsFlushed -> publishFlushedSegments(false): tryPurge
  /\ pc[t] = "f_purge"
  /\ \/ LET r == PubAll(tickets, pub) IN tickets' = r.t /\ pub' = r.p
     \/ Purging(t) /\ UNCHANGED <<tickets, pub>>   \* purge lock held by another thread
  /\ pc' = [pc EXCEPT ![t] = ret[t]] /\ fl' = [fl EXCEPT ![t] = NONE]
  /\ UNCHANGED <<k, cur, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax,
                 qclosed, nodes, gtail, hist, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl,
                 dwMon, nwLock, bad, evq>>

Ret(t) ==   \* the indexing call returns; forced purge (processEvents) is optional
  /\ pc[t] = "ret"
  /\ \/ LET r == PubAll(tickets, pub) IN tickets' = r.t /\ pub' = r.p /\ evq' = 0
     \/ evq = 0 /\ UNCHANGED <<tickets, pub, evq>>   \* no event to process (maybeProcessEvents)
  /\ pc' = [pc EXCEPT ![t] = IF t \in Idx THEN "next" ELSE ret[t]]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                 qmax, qclosed, nodes, gtail, hist, fq, blocked, flw, fullFlush, ffq, ffSeq,
                 ffl, dwMon, nwLock, bad>>

Del(t) ==   \* DocumentsWriter.applyDeleteOrUpdate (synchronized on DW)
  LET s == qnext[gen] IN
  /\ pc[t] = "del" /\ dwMon = NOBODY
  /\ IF qclosed[gen] THEN Flag("delete-on-closed-queue") ELSE UNCHANGED bad
  /\ qnext' = [qnext EXCEPT ![gen] = s + 1]
  /\ nodes' = [nodes EXCEPT ![gen] = Append(@, s)]
  /\ hist' = hist \cup {[k |-> "del", s |-> s, id |-> <<t, k[t]>>, tid |-> <<>>]}
  /\ pc' = [pc EXCEPT ![t] = "post"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qmax,
                 qclosed, gtail, tickets, pub, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl,
                 dwMon, nwLock>>
  /\ UNCHANGED evq

Try(t) ==   \* tryDeleteDocument on a published doc (IW monitor; DW-synced seqNo)
  /\ pc[t] = "try" /\ dwMon = NOBODY
  /\ \/ /\ pub = {} /\ UNCHANGED <<qnext, pub, hist>>
     \/ \E x \in pub :
          /\ qnext' = [qnext EXCEPT ![gen] = @ + 1] /\ pub' = pub \ {x}
          /\ hist' = hist \cup {[k |-> "try", s |-> qnext[gen], id |-> <<t, k[t]>>, tid |-> x.id]}
  /\ pc' = [pc EXCEPT ![t] = "next"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qmax,
                 qclosed, nodes, gtail, tickets, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl,
                 dwMon, nwLock, bad>>
  /\ UNCHANGED evq

U_ctl == <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax,
           qclosed, nodes, gtail, tickets, pub, hist, fq, blocked, flw, fullFlush, ffq, ffSeq,
           ffl, dwMon, nwLock, bad>>

CLock(t) ==
  /\ pc[t] = "c_lock" /\ ffl = NOBODY
  /\ Kind(t) = "final" => IdxDone
  /\ ffl' = t
  /\ pc' = [pc EXCEPT ![t] = IF Kind(t) = "da" THEN "da_lock" ELSE "ff_mark"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                 qmax, qclosed, nodes, gtail, tickets, pub, hist, fq, blocked, flw, fullFlush,
                 ffq, ffSeq, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

FFMark(t) ==   \* markForFullFlush first block: advance the delete queue
  LET g == gen  mx == qnext[g] - 1 + (IF MUT = "nogap" THEN 0 ELSE Cardinality(Pool) + 1) IN
  /\ pc[t] = "ff_mark" /\ dwMon = NOBODY /\ gen < 3
  /\ fullFlush' = TRUE /\ ffq' = g /\ ffSeq' = mx /\ gen' = g + 1
  /\ qmax' = [qmax EXCEPT ![g] = mx]
  /\ qnext' = [qnext EXCEPT ![g+1] = mx + 1]
  /\ anyFl' = [anyFl EXCEPT ![t] = FALSE]
  /\ pc' = [pc EXCEPT ![t] = "ff_collect"]
  /\ UNCHANGED <<k, cur, fl, ret, dst, dq, ddocs, dpend, dslice, dupto, qclosed, nodes, gtail,
                 tickets, pub, hist, fq, blocked, flw, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

FFCollect(t) ==   \* filterAndLock(old queue): one DWPT per step; waits for held ones
  /\ pc[t] = "ff_collect"
  /\ \/ \E d \in D :
          /\ dq[d] = ffq /\ dst[d] \in {"free", "parked"}
          /\ IF ddocs[d] /= <<>>
             THEN /\ dst' = [dst EXCEPT ![d] = "ffbuf"] /\ dpend' = [dpend EXCEPT ![d] = TRUE]
                  /\ flw' = flw \cup {d}
             ELSE /\ dst' = [dst EXCEPT ![d] = "dropped"] /\ UNCHANGED <<dpend, flw>>
          /\ UNCHANGED <<pc, fq, blocked>>
     \/ /\ ~\E d \in D : dq[d] = ffq /\ InPool(d)
        /\ LET pr == {d \in blocked : dq[d] = ffq}
               fb == {d \in D : dst[d] = "ffbuf"}
               S2 == pr \cup fb
           IN /\ blocked' = blocked \ pr
              /\ \E sq \in {f \in [1..Cardinality(S2) -> S2] : \A i, j \in DOMAIN f : i /= j => f[i] /= f[j]} :
                    fq' = fq \o sq
              /\ dst' = [d \in D |-> IF d \in S2 THEN "fq" ELSE dst[d]]
              /\ flw' = flw \cup S2
        /\ pc' = [pc EXCEPT ![t] = "ff_flush"] /\ UNCHANGED dpend
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dq, ddocs, dslice, dupto, gen, qnext, qmax, qclosed,
                 nodes, gtail, tickets, pub, hist, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

FFFlush(t) ==   \* maybeFlush(): help flushing the flushQueue
  /\ pc[t] = "ff_flush"
  /\ ret' = [ret EXCEPT ![t] = "ff_wait"]
  /\ IF fq /= <<>> THEN Take(t, Head(fq), dst, flw) /\ pc' = [pc EXCEPT ![t] = "f_ticket"]
     ELSE /\ pc' = [pc EXCEPT ![t] = "ff_wait"] /\ UNCHANGED <<fl, fq, dst, flw>>
  /\ UNCHANGED <<k, cur, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax, qclosed,
                 nodes, gtail, tickets, pub, hist, blocked, fullFlush, ffq, ffSeq, ffl, dwMon,
                 nwLock, bad>>
  /\ UNCHANGED evq

FFWait(t) ==   \* waitForFlush, naked global deletes, close the old queue
  LET g == ffq  ch == gtail[g] < Len(nodes[g])
      tk == [st |-> "ok", frz |-> MaxDel(g, gtail[g], Len(nodes[g])), seg |-> {}, d |-> NONE]
  IN /\ pc[t] = "ff_wait" /\ flw = {}
     /\ tickets' = IF ~anyFl[t] /\ ch THEN Append(tickets, tk) ELSE tickets
     /\ gtail' = IF ~anyFl[t] /\ ch THEN [gtail EXCEPT ![g] = Len(nodes[g])] ELSE gtail
     /\ bad' = bad \cup (IF anyFl[t] /\ ch THEN {"close-queue-with-changes"} ELSE {})
                    \cup (IF qnext[g] - 1 > qmax[g] THEN {"close-seq-gt-max"} ELSE {})
     /\ qclosed' = [qclosed EXCEPT ![g] = TRUE] /\ qnext' = [qnext EXCEPT ![g] = qmax[g] + 1]
     /\ pc' = [pc EXCEPT ![t] = "ff_pub"]
     /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qmax,
                    nodes, pub, hist, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock>>
  /\ UNCHANGED evq

FFPub(t) ==   \* publishFlushedSegments(true) + the reader/commit snapshot (or a failure)
  LET r == PubAll(tickets, pub)  o == Kind(t) IN
  /\ pc[t] = "ff_pub"
  /\ tickets' = r.t /\ pub' = r.p /\ evq' = 0
  /\ IF o \in {"nrt", "commit", "final"} /\ r.p /= Live(ffSeq)
     THEN bad' = bad \cup {<<o, "content /= ops with seqNo <= its seqNo">>}
     ELSE UNCHANGED bad
  /\ pc' = [pc EXCEPT ![t] = IF o = "nrtfail" /\ "A" \notin FIX THEN "ff_abort" ELSE "ff_end"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                 qmax, qclosed, nodes, gtail, hist, fq, blocked, flw, fullFlush, ffq, ffSeq,
                 ffl, dwMon, nwLock>>

FFEnd(t) ==   \* finishFullFlush(true): pruneBlockedQueue(new queue) -> flushQueue
  LET S2 == blocked IN
  /\ pc[t] = "ff_end"
  /\ blocked' = {}
  /\ \E sq \in {f \in [1..Cardinality(S2) -> S2] : \A i, j \in DOMAIN f : i /= j => f[i] /= f[j]} :
        fq' = fq \o sq
  /\ dst' = [d \in D |-> IF d \in S2 THEN "fq" ELSE dst[d]] /\ flw' = flw \cup S2
  /\ fullFlush' = FALSE /\ ffl' = NOBODY /\ pc' = [pc EXCEPT ![t] = "next"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax,
                 qclosed, nodes, gtail, tickets, pub, hist, ffq, ffSeq, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

FFAbort(t) ==   \* getReader failed after the flush: finishFullFlush(false) -> abortFullFlushes
  LET S2 == blocked \cup {fq[i] : i \in DOMAIN fq} IN
  /\ pc[t] = "ff_abort"
  /\ blocked' = {} /\ fq' = <<>>
  /\ dst' = [d \in D |-> IF d \in S2 THEN "aborted" ELSE dst[d]] /\ flw' = flw \ S2
  /\ fullFlush' = FALSE /\ ffl' = NOBODY /\ pc' = [pc EXCEPT ![t] = "next"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qnext, qmax,
                 qclosed, nodes, gtail, tickets, pub, hist, ffq, ffSeq, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

DALock(t) ==   \* lockAndAbortAll start (DW monitor held until DAWait):
               \* forcePurge DISCARDS publishable tickets, clear queue, lockNewWriters
  /\ pc[t] = "da_lock" /\ dwMon = NOBODY
  /\ dwMon' = t /\ nwLock' = nwLock + 1
  /\ tickets' = DropAll(tickets)
  /\ gtail' = [gtail EXCEPT ![gen] = Len(nodes[gen])]
  /\ pc' = [pc EXCEPT ![t] = "da_abort"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                 qmax, qclosed, nodes, pub, hist, fq, blocked, flw, fullFlush, ffq, ffSeq, ffl, bad>>
  /\ UNCHANGED evq

DAAbort(t) ==   \* abort pool DWPTs one at a time (waits for held ones); then
                \* abortPendingFlushes (flushQueue + blocked)
  /\ pc[t] = "da_abort"
  /\ \/ \E d \in D : dst[d] \in {"free", "parked"} /\ dst' = [dst EXCEPT ![d] = "aborted"]
                     /\ UNCHANGED <<pc, fq, blocked, flw, gtail, qnext>>
     \/ /\ Pool = {}
        /\ LET S2 == blocked \cup {fq[i] : i \in DOMAIN fq} IN
           /\ dst' = [d \in D |-> IF d \in S2 THEN "aborted" ELSE dst[d]]
           /\ flw' = flw \ S2 /\ fq' = <<>> /\ blocked' = {}
        /\ gtail' = [gtail EXCEPT ![gen] = Len(nodes[gen])]
        /\ qnext' = [qnext EXCEPT ![gen] = @ + 1]   \* skipSequenceNumbers(size()+1)
        /\ pc' = [pc EXCEPT ![t] = "da_wait"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dq, ddocs, dpend, dslice, dupto, gen, qmax, qclosed,
                 nodes, tickets, pub, hist, fullFlush, ffq, ffSeq, ffl, dwMon, nwLock, bad>>
  /\ UNCHANGED evq

DAWait(t) ==   \* waitForFlush; lockAndAbortAll returns (DW monitor released)
  /\ pc[t] = "da_wait" /\ flw = {}
  /\ dwMon' = NOBODY /\ pc' = [pc EXCEPT ![t] = "da_iw"]
  /\ tickets' = IF "B" \in FIX THEN DropAll(tickets) ELSE tickets
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qnext,
                 qmax, qclosed, nodes, gtail, pub, hist, fq, blocked, flw, fullFlush,
                 ffq, ffSeq, ffl, nwLock, bad>>
  /\ UNCHANGED evq

DAIW(t) ==   \* synchronized(IW): segmentInfos.clear(); seqNo; release (unlockNewWriters)
  /\ pc[t] = "da_iw"
  \* processEvents(false) first (IW.java:2766): a queued backlog event publishes ready tickets,
  \* which segmentInfos.clear() then removes
  /\ tickets' = (IF evq > 0 THEN PubAll(tickets, pub).t ELSE tickets)
  /\ evq' = 0
  /\ pub' = {} /\ qnext' = [qnext EXCEPT ![gen] = @ + 1]
  /\ hist' = hist \cup {[k |-> "da", s |-> qnext[gen], id |-> <<t, k[t]>>, tid |-> <<>>]}
  /\ nwLock' = nwLock - 1 /\ ffl' = NOBODY /\ pc' = [pc EXCEPT ![t] = "next"]
  /\ UNCHANGED <<k, cur, fl, ret, anyFl, dst, dq, ddocs, dpend, dslice, dupto, gen, qmax,
                 qclosed, nodes, gtail, fq, blocked, flw, fullFlush, ffq, ffSeq,
                 dwMon, bad>>

Step(t) == \/ Next1(t) \/ PreHelp(t) \/ Obtain(t) \/ Index(t) \/ After(t) \/ Post(t) \/ FTicket(t)
           \/ FSeg(t) \/ FAfter(t) \/ FPurge(t) \/ Ret(t) \/ Del(t) \/ Try(t)
           \/ CLock(t) \/ FFMark(t) \/ FFCollect(t) \/ FFFlush(t) \/ FFWait(t) \/ FFPub(t)
           \/ FFEnd(t) \/ FFAbort(t) \/ DALock(t) \/ DAAbort(t) \/ DAWait(t) \/ DAIW(t)

Done == \A t \in Thr : pc[t] = "end"
Next == (\E t \in Thr : Step(t)) \/ (Done /\ UNCHANGED vars)
Spec == Init /\ [][Next]_vars

\* ---------------------------------------------------------------- properties
NoBad == bad = {}
SeqNoUnique == \A a, b \in hist : a /= b => a.s /= b.s
\* every scripted op eventually runs (no deadlock): checked by TLC deadlock check
\* (the Done stutter step is the only allowed terminal state).
=============================================================================

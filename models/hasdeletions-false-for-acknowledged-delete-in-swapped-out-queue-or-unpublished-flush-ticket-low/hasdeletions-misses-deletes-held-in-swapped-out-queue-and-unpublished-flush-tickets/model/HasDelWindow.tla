---------------------------- MODULE HasDelWindow ----------------------------
(***************************************************************************)
(* Where an acknowledged buffered delete is while ONE full flush (commit,   *)
(* NRT open or flush()) carries it to the index, versus what one concurrent  *)
(* IndexWriter.hasDeletions() call reads.  Lucene main 5adff78bb168,         *)
(* lucene/core/src/java/org/apache/lucene/index/.                            *)
(*                                                                           *)
(* Mapping, model element -> Java:                                           *)
(*  loc "cur"      deleteQueue global buffer (DocumentsWriter.java:95, the   *)
(*                 delete appended by deleteDocuments)                       *)
(*  Swap           flushAllThreads :674-682, markForFullFlush swaps          *)
(*                 DocumentsWriter.deleteQueue (no IndexWriter monitor)      *)
(*  loc "swapped"  the swapped-out queue (local flushingDeleteQueue)         *)
(*  Freeze1/2      DocumentsWriterFlushQueue.addTicket :38-52 (synchronized  *)
(*                 on the ticket queue): incTickets, supplier =              *)
(*                 dwpt.prepareFlush() (DocumentsWriter.java:535-536) or     *)
(*                 maybeFreezeGlobalBuffer (:698, :717-724) empties the      *)
(*                 global buffer ("limbo" = only the supplier's local), then *)
(*                 queue.add(ticket)                                         *)
(*  loc "ticket"   frozenUpdates of a queued FlushTicket; for a DWPT ticket  *)
(*                 it stays there while the segment is written (:541) - the  *)
(*                 window the JUnit holds                                    *)
(*  Publish        innerPurge consumer -> IndexWriter.publishFlushedSegment  *)
(*                 :2953 (synchronized on IW) -> publishFrozenUpdates ->     *)
(*                 BufferedUpdatesStream.push; ticket still queued           *)
(*  Poll           innerPurge finally: queue.poll() (FlushQueue :100-106)    *)
(*  Apply          IndexWriter.forceApply :6591 updates.apply(segStates),    *)
(*                 outside the IW monitor: rld delCount > 0, packet still in *)
(*                 the stream                                                *)
(*  Finished       :6629-6640 bufferedUpdatesStream.finished under IW        *)
(*  FinishApply    :6705-6733 finishApply under IW: closeSegmentStates ->    *)
(*                 ReaderPool.release -> writeLiveDocs (SegmentCommitInfo    *)
(*                 hasDeletions)                                             *)
(*  Observer       IndexWriter.hasDeletions :1449-1461 (synchronized on IW): *)
(*                 bufferedUpdatesStream.any() (BufferedUpdatesStream:108),  *)
(*                 docWriter.anyDeletions() (DocumentsWriter:376-378, reads  *)
(*                 the volatile deleteQueue then takes its globalBufferLock),*)
(*                 readerPool.anyDeletions() (ReaderPool:156), then          *)
(*                 SegmentCommitInfo.hasDeletions per segment                *)
(*  Variant "fix"  fix/fix-DocumentsWriter.diff + fix-DocumentsWriterFlush   *)
(*                 Queue.diff: anyDeletions() also reads the swapped-out     *)
(*                 queue (volatile field set BEFORE the swap) and then the   *)
(*                 ticket queue under its monitor                            *)
(*  "fixRev"       mutant: tickets read before the swapped-out queue         *)
(*  "fixLate"      mutant: the field is set AFTER the swap                   *)
(*  "ticketOnly"   only the ticket-queue read is added (fix/ticket-only)     *)
(* The delete is acknowledged before the flush starts (Init loc = "cur") and *)
(* matches a document of a committed segment that survives (doc c), so      *)
(* every correct answer is TRUE (javadoc IndexWriter.java:1444-1448).        *)
(***************************************************************************)
EXTENDS Naturals, Sequences

CONSTANT Variant

Reads ==
  CASE Variant = "stock"      -> <<"stream", "cur", "pool", "seg">>
    [] Variant = "fix"        -> <<"stream", "cur", "swapped", "tickets", "pool", "seg">>
    [] Variant = "fixRev"     -> <<"stream", "cur", "tickets", "swapped", "pool", "seg">>
    [] Variant = "fixLate"    -> <<"stream", "cur", "swapped", "tickets", "pool", "seg">>
    [] Variant = "ticketOnly" -> <<"stream", "cur", "tickets", "pool", "seg">>

VARIABLES loc,      \* where the delete is
          swapped,  \* has markForFullFlush swapped the queue
          fld,      \* fix: the volatile field names the swapped-out queue
          iw,       \* holder of the IndexWriter monitor: "none" | "obs"
          tq,       \* holder of the ticket-queue monitor: "none" | "obs" | "fl"
          opc,      \* observer: index into Reads, 0 = not started, Len+1 = done
          oldRef,   \* observer captured deleteQueue before the swap
          saw,      \* observer's OR so far
          startLoc  \* auxiliary: where the delete was when the observer took the IW monitor

vars == <<loc, swapped, fld, iw, tq, opc, oldRef, saw, startLoc>>

Init == /\ loc = "cur" /\ swapped = FALSE /\ fld = FALSE /\ iw = "none" /\ tq = "none"
        /\ opc = 0 /\ oldRef = FALSE /\ saw = FALSE /\ startLoc = "none"

\* ---------------- flusher ----------------
Swap == /\ loc = "cur" /\ ~swapped
        /\ swapped' = TRUE /\ loc' = "swapped"
        /\ fld' = (Variant \in {"fix", "fixRev"})          \* set before the swap, same step
        /\ UNCHANGED <<startLoc, iw, tq, opc, oldRef, saw>>
SetLate == /\ Variant = "fixLate" /\ swapped /\ ~fld /\ fld' = TRUE
           /\ UNCHANGED <<startLoc, loc, swapped, iw, tq, opc, oldRef, saw>>
Freeze1 == /\ loc = "swapped" /\ tq = "none" /\ (Variant = "fixLate" => fld)
           /\ tq' = "fl" /\ loc' = "limbo"
           /\ UNCHANGED <<startLoc, swapped, fld, iw, opc, oldRef, saw>>
Freeze2 == /\ loc = "limbo" /\ tq = "fl" /\ tq' = "none" /\ loc' = "ticket"
           /\ UNCHANGED <<startLoc, swapped, fld, iw, opc, oldRef, saw>>
Publish == /\ loc = "ticket" /\ iw = "none" /\ loc' = "ticketStream"
           /\ UNCHANGED <<startLoc, swapped, fld, iw, tq, opc, oldRef, saw>>
Poll == /\ loc = "ticketStream" /\ tq = "none" /\ loc' = "stream"
        /\ UNCHANGED <<startLoc, swapped, fld, iw, tq, opc, oldRef, saw>>
Apply == /\ loc = "stream" /\ loc' = "streamRld"
         /\ UNCHANGED <<startLoc, swapped, fld, iw, tq, opc, oldRef, saw>>
Finished == /\ loc = "streamRld" /\ iw = "none" /\ loc' = "rld"
            /\ UNCHANGED <<startLoc, swapped, fld, iw, tq, opc, oldRef, saw>>
FinishApply == /\ loc = "rld" /\ iw = "none" /\ loc' = "seg"
               /\ UNCHANGED <<startLoc, swapped, fld, iw, tq, opc, oldRef, saw>>

\* ---------------- observer: hasDeletions() ----------------
Start == /\ opc = 0 /\ iw = "none" /\ iw' = "obs" /\ opc' = 1 /\ startLoc' = loc
         /\ UNCHANGED <<loc, swapped, fld, tq, oldRef, saw>>

Holds(r) ==
  CASE r = "stream"  -> loc \in {"ticketStream", "stream", "streamRld"}
    [] r = "cur"     -> loc = "cur" \/ (oldRef /\ loc = "swapped")
    [] r = "swapped" -> fld /\ loc = "swapped"
    [] r = "tickets" -> loc \in {"ticket", "ticketStream"}
    [] r = "pool"    -> loc \in {"streamRld", "rld"}
    [] r = "seg"     -> loc = "seg"

\* "cur" is two steps: read the volatile deleteQueue, then evaluate anyChanges() on that queue.
CaptureRef == /\ opc >= 1 /\ opc <= Len(Reads) /\ Reads[opc] = "cur" /\ oldRef = FALSE
              /\ ~swapped /\ oldRef' = TRUE
              /\ UNCHANGED <<startLoc, loc, swapped, fld, iw, tq, opc, saw>>
ReadStep == /\ opc >= 1 /\ opc <= Len(Reads)
            /\ (Reads[opc] = "tickets" => tq # "fl")         \* blocks on the ticket-queue monitor
            /\ saw' = (saw \/ Holds(Reads[opc]))
            /\ opc' = opc + 1
            /\ UNCHANGED <<startLoc, loc, swapped, fld, iw, tq, oldRef>>
Finish == /\ opc = Len(Reads) + 1 /\ iw = "obs" /\ iw' = "none" /\ opc' = Len(Reads) + 2
          /\ UNCHANGED <<startLoc, loc, swapped, fld, tq, oldRef, saw>>

Next == \/ Swap \/ SetLate \/ Freeze1 \/ Freeze2 \/ Publish \/ Poll \/ Apply \/ Finished \/ FinishApply
        \/ Start \/ CaptureRef \/ ReadStep \/ Finish

Spec == Init /\ [][Next]_vars

\* Oracle: the acknowledged delete is never reported as absent.
HasDelTrue == (opc = Len(Reads) + 2) => saw

\* Vacuity: the observer does finish in some behaviours, at every location of the delete.
TypeOK == loc \in {"cur", "swapped", "limbo", "ticket", "ticketStream", "stream", "streamRld", "rld", "seg"}
\* Vacuity witnesses (each is EXPECTED to be violated: the observer can finish while the delete is
\* in the swapped-out queue, and while it is in a queued ticket).
VacSwapped == ~(opc = Len(Reads) + 2 /\ loc = "swapped")
VacTicket == ~(opc = Len(Reads) + 2 /\ loc = "ticket")
\* Window witnesses on stock (each EXPECTED to be violated): a hasDeletions() call that starts while the
\* delete is in the swapped-out queue (window a) / in a queued, unpublished ticket (window b) returns false.
WinA == ~(opc = Len(Reads) + 2 /\ ~saw /\ startLoc = "swapped")
WinB == ~(opc = Len(Reads) + 2 /\ ~saw /\ startLoc = "ticket")
=============================================================================

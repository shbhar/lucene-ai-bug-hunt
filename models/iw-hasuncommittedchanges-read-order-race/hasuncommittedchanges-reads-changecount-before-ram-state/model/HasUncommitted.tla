---------------------------- MODULE HasUncommitted ----------------------------
(***************************************************************************)
(* IndexWriter.hasUncommittedChanges() racing one full flush that applies  *)
(* one acknowledged, uncommitted delete-by-term (no buffered documents).   *)
(* Code: Lucene main 645d4fe14a20, lucene/core/src/java/org/apache/lucene/ *)
(* index/.  Model element -> Java line:                                    *)
(*   cc, last  IndexWriter.changeCount / lastCommitChangeCount             *)
(*   R_cc      IndexWriter.java:4226  changeCount.get() != last...         *)
(*   R_qref    DocumentsWriter.java:377 deleteQueue (volatile field) read  *)
(*   R_q       DocumentsWriterDeleteQueue.java:181-194 anyChanges() (lock) *)
(*   R_t       DocumentsWriter.java:355 / FlushQueue.java:77 hasTickets()  *)
(*   R_p       DocumentsWriter.java:356 pendingChangesInCurrentFullFlush   *)
(*   R_s       IndexWriter.java:4231 / BufferedUpdatesStream.java:108 any()*)
(*   (numDocsInRAM, DocumentsWriter.java:353, is 0 throughout: no docs)    *)
(*   W1  DocumentsWriter.java:675  pending := anyChanges()                 *)
(*   W2  DocumentsWriter.java:680  markForFullFlush swaps deleteQueue      *)
(*   W3  DocumentsWriterFlushQueue.java:40  incTickets (before freeze)     *)
(*   W4  DocumentsWriterDeleteQueue.java:244-258 maybeFreezeGlobalBuffer   *)
(*   W5  BufferedUpdatesStream.java:80  push (bytesUsed += packet)         *)
(*   W6  DocumentsWriterFlushQueue.java:105  decTickets after publish      *)
(*   W7  DocumentsWriter.java:741  finishFullFlush: pending := false       *)
(*   W8  IndexWriter.java:6723/2918  finishApply -> checkpoint: cc++       *)
(*   W9  IndexWriter.java:6640/BufferedUpdatesStream.java:164 finished     *)
(* Order = "stock": changeCount read first (as on main);                   *)
(* Order = "fixed": hasChangesInRam() first, changeCount last.             *)
(* Order = "recheck": stock order, then changeCount read again.           *)
(* No commit runs, so the delete stays uncommitted in every state: the     *)
(* only correct answer of the reader is TRUE.                              *)
(***************************************************************************)
EXTENDS Naturals, Sequences

CONSTANT Order               \* "stock", "fixed" or "recheck"

VARIABLES wpc, curQ, oldQ, tickets, pending, stream, cc,   \* writer side
          rpc, qref, result, rdone                           \* reader side

vars == <<wpc, curQ, oldQ, tickets, pending, stream, cc, rpc, qref, result, rdone>>

last == 0

StockSeq == <<"cc", "qref", "q", "t", "p", "s">>
FixedSeq == <<"qref", "q", "t", "p", "s", "cc">>
RecheckSeq == <<"cc", "qref", "q", "t", "p", "s", "cc">>   \* stock order plus a second changeCount read
Seq_ == IF Order = "stock" THEN StockSeq ELSE IF Order = "fixed" THEN FixedSeq ELSE RecheckSeq

Init ==
  /\ wpc = 0 /\ curQ = "old" /\ oldQ = TRUE      \* deleteDocuments returned: delete in the queue
  /\ tickets = 0 /\ pending = FALSE /\ stream = FALSE /\ cc = last
  /\ rpc = 1 /\ qref = "none" /\ result = FALSE /\ rdone = FALSE

QHas(r) == IF r = "old" THEN oldQ ELSE FALSE     \* the new queue never gets a change

Writer ==
  \/ wpc = 0 /\ wpc' = 1 /\ pending' = (QHas(curQ) \/ tickets > 0 \/ pending)
     /\ UNCHANGED <<curQ, oldQ, tickets, stream, cc>>
  \/ wpc = 1 /\ wpc' = 2 /\ curQ' = "new" /\ UNCHANGED <<oldQ, tickets, pending, stream, cc>>
  \/ wpc = 2 /\ wpc' = 3 /\ tickets' = tickets + 1 /\ UNCHANGED <<curQ, oldQ, pending, stream, cc>>
  \/ wpc = 3 /\ wpc' = 4 /\ oldQ' = FALSE /\ UNCHANGED <<curQ, tickets, pending, stream, cc>>
  \/ wpc = 4 /\ wpc' = 5 /\ stream' = TRUE /\ UNCHANGED <<curQ, oldQ, tickets, pending, cc>>
  \/ wpc = 5 /\ wpc' = 6 /\ tickets' = tickets - 1 /\ UNCHANGED <<curQ, oldQ, pending, stream, cc>>
  \/ wpc = 6 /\ wpc' = 7 /\ pending' = FALSE /\ UNCHANGED <<curQ, oldQ, tickets, stream, cc>>
  \/ wpc = 7 /\ wpc' = 8 /\ cc' = cc + 1 /\ UNCHANGED <<curQ, oldQ, tickets, pending, stream>>
  \/ wpc = 8 /\ wpc' = 9 /\ stream' = FALSE /\ UNCHANGED <<curQ, oldQ, tickets, pending, cc>>

WriterVars == <<wpc, curQ, oldQ, tickets, pending, stream, cc>>

\* One read of the reader; a TRUE operand short-circuits (Java ||).
ReadVal(op) ==
  CASE op = "cc" -> cc # last
    [] op = "q"  -> QHas(qref)
    [] op = "t"  -> tickets > 0
    [] op = "p"  -> pending
    [] op = "s"  -> stream
    [] OTHER     -> FALSE

Reader ==
  /\ rdone = FALSE
  /\ LET op == Seq_[rpc] IN
       IF op = "qref"
       THEN /\ qref' = curQ /\ rpc' = rpc + 1 /\ UNCHANGED <<result, rdone>>
       ELSE /\ UNCHANGED qref
            /\ IF ReadVal(op)
               THEN result' = TRUE /\ rdone' = TRUE /\ UNCHANGED rpc
               ELSE IF rpc = Len(Seq_)
                    THEN result' = FALSE /\ rdone' = TRUE /\ UNCHANGED rpc
                    ELSE rpc' = rpc + 1 /\ UNCHANGED <<result, rdone>>
  /\ UNCHANGED WriterVars

Done == wpc = 9 /\ rdone /\ UNCHANGED vars

Next == (Writer /\ UNCHANGED <<rpc, qref, result, rdone>>) \/ Reader \/ Done

Spec == Init /\ [][Next]_vars

\* The delete is acknowledged and never committed: a finished call must say TRUE.
NoFalseNegative == rdone => result = TRUE

\* Vacuity: the reader does finish (this invariant must be VIOLATED).
ReaderNeverFinishes == ~rdone
\* Vacuity: the reader can finish after the writer applied the delete (must be VIOLATED).
NeverFinishAfterApply == ~(rdone /\ wpc = 9)
=============================================================================

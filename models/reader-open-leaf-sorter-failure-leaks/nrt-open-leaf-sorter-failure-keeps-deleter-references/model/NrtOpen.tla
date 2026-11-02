------------------------------- MODULE NrtOpen -------------------------------
(***************************************************************************)
(* Acquire/release protocol of the NRT reader open of Lucene main          *)
(* 645d4fe14a20, one call of IndexWriter.getReader -> StandardDirectory-   *)
(* Reader.open(IndexWriter, readerFunction, infos, ...), with a fault at   *)
(* every step that can throw.  Sequential: the whole open runs under the   *)
(* IndexWriter monitor (IndexWriter.java:598), so no interleaving exists.  *)
(*                                                                         *)
(* Model element -> Java (lucene/core/src/java/org/apache/lucene/index/)   *)
(*   SEGS, live        infos of the writer; seg 2 is fully deleted         *)
(*                     (numDocs()==0) when FULLY_DELETED_SEG2              *)
(*   Apply(i)          StandardDirectoryReader.java:136                    *)
(*                     reader = readerFunction.apply(info): a read-only    *)
(*                     clone with refCount 1, owned by this call           *)
(*                     (IndexWriter.java:549-563, getReadOnlyClone)        *)
(*   Keep(i)           StandardDirectoryReader.java:137-145                *)
(*                     numDocs()>0 || mergePolicy.keepFullyDeleted-        *)
(*                     Segment(..) (may throw: LFV-626's trigger);         *)
(*                     true -> readers.add (steal the ref) :140,           *)
(*                     false -> reader.decRef() :143                       *)
(*   IncDel            StandardDirectoryReader.java:148                    *)
(*                     writer.incRefDeleter(segmentInfos)                  *)
(*                     (IndexWriter.java:6306-6317: ensureOpen() first,    *)
(*                     then deleter.incRef(segmentInfos,false))            *)
(*   Ctor              StandardDirectoryReader.java:150-157 -> :60 ->      *)
(*                     BaseCompositeReader.java:75-77 Arrays.sort with     *)
(*                     writer.getConfig().getLeafSorter() (may throw)      *)
(*   Catch             StandardDirectoryReader.java:158-164                *)
(*                     IOUtils.applyToAll(readers, SegmentReader::decRef)  *)
(*                     -- only the readers in the list; no decRefDeleter   *)
(*   IwCatch           IndexWriter.java:669-676 (finishFullFlush(false))   *)
(*                     and :711-721 (onTragicEvent only for Error;         *)
(*                     closeWhileSuppressingExceptions(t, r=null, ..))     *)
(*                     -> releases nothing the SDR open acquired           *)
(*   Close             StandardDirectoryReader.java:548-568 doClose:       *)
(*                     writer.decRefDeleter(segmentInfos) + decRef of      *)
(*                     every sub-reader                                    *)
(* FIXES: "keep" = release the current reader when the keep check throws   *)
(*        (LFV-626's fix);  "del" = decRefDeleter in the catch when        *)
(*        incRefDeleter succeeded (this unit's root (b));  "delNaive" =    *)
(*        a mutant fix that calls decRefDeleter unconditionally in the     *)
(*        catch (must violate NoOverRelease: over-release of files).       *)
(***************************************************************************)
EXTENDS Integers, FiniteSets

CONSTANTS
  FAULT,              \* "none" | "apply1" | "apply2" | "keep2" | "incDel" | "ctor"
  FULLY_DELETED_SEG2, \* TRUE: seg 2 has numDocs()==0, so the keep check runs for it
  KEEP2,              \* result of keepFullyDeletedSegment for seg 2 when it does not throw
  FIXES               \* subset of {"keep", "del", "delNaive"}

SEGS == {1, 2}
N == 2

VARIABLES
  pc,       \* "loop" | "keep" | "incDel" | "ctor" | "catch" | "iwCatch" | "failed" | "open" | "closed"
  i,        \* loop index (1..N+1)
  cur,      \* segment whose reader was just obtained at :136 (0 = none)
  held,     \* [SEGS -> 0..1]: reference this call holds on the read-only clone of each segment
  list,     \* segments in the 'readers' list (:123, :140)
  delRef,   \* IndexFileDeleter references this call holds on the point-in-time (:148); < 0 = over-release
  thrown    \* TRUE once an exception left the open

vars == <<pc, i, cur, held, list, delRef, thrown>>

Init ==
  /\ pc = "loop" /\ i = 1 /\ cur = 0
  /\ held = [s \in SEGS |-> 0] /\ list = {} /\ delRef = 0 /\ thrown = FALSE

\* :136 readerFunction.apply(info)
Apply ==
  /\ pc = "loop" /\ i <= N
  /\ IF (FAULT = "apply1" /\ i = 1) \/ (FAULT = "apply2" /\ i = 2)
       THEN \* getPooledInstance / getReadOnlyClone throws: no clone was handed out
            /\ pc' = "catch" /\ thrown' = TRUE
            /\ UNCHANGED <<i, cur, held, list, delRef>>
       ELSE /\ held' = [held EXCEPT ![i] = 1] /\ cur' = i /\ pc' = "keep"
            /\ UNCHANGED <<i, list, delRef, thrown>>

\* :137-145 numDocs() > 0 || keepFullyDeletedSegment(...)
Keep ==
  /\ pc = "keep"
  /\ LET fullyDeleted == FULLY_DELETED_SEG2 /\ cur = 2 IN
     IF fullyDeleted /\ FAULT = "keep2"
       THEN /\ pc' = "catch" /\ thrown' = TRUE
            /\ IF "keep" \in FIXES   \* LFV-626 fix: release the reader the list does not own
                 THEN held' = [held EXCEPT ![cur] = 0]
                 ELSE held' = held
            /\ UNCHANGED <<i, cur, list, delRef>>
     ELSE IF ~fullyDeleted \/ KEEP2
       THEN /\ list' = list \cup {cur}     \* :140 steal the ref
            /\ i' = i + 1 /\ cur' = 0 /\ pc' = "loop"
            /\ UNCHANGED <<held, delRef, thrown>>
       ELSE /\ held' = [held EXCEPT ![cur] = 0]   \* :143 reader.decRef()
            /\ i' = i + 1 /\ cur' = 0 /\ pc' = "loop"
            /\ UNCHANGED <<list, delRef, thrown>>

EndLoop ==
  /\ pc = "loop" /\ i = N + 1 /\ pc' = "incDel"
  /\ UNCHANGED <<i, cur, held, list, delRef, thrown>>

\* :148 writer.incRefDeleter(segmentInfos): ensureOpen() may throw before the incRef
IncDel ==
  /\ pc = "incDel"
  /\ IF FAULT = "incDel"
       THEN /\ pc' = "catch" /\ thrown' = TRUE /\ UNCHANGED <<i, cur, held, list, delRef>>
       ELSE /\ delRef' = 1 /\ pc' = "ctor" /\ UNCHANGED <<i, cur, held, list, thrown>>

\* :150-157 new StandardDirectoryReader(...) -> Arrays.sort(leaf sorter)
Ctor ==
  /\ pc = "ctor"
  /\ IF FAULT = "ctor"
       THEN /\ pc' = "catch" /\ thrown' = TRUE /\ UNCHANGED <<i, cur, held, list, delRef>>
       ELSE /\ pc' = "open" /\ UNCHANGED <<i, cur, held, list, delRef, thrown>>

\* :158-164 catch (Throwable t): decRef every reader in the list
Catch ==
  /\ pc = "catch"
  /\ held' = [s \in SEGS |-> IF s \in list THEN 0 ELSE held[s]]
  /\ delRef' = IF "del" \in FIXES THEN 0
               ELSE IF "delNaive" \in FIXES THEN delRef - 1
               ELSE delRef
  /\ pc' = "iwCatch"
  /\ UNCHANGED <<i, cur, list, thrown>>

\* IndexWriter.java:669-676, :711-721: r == null, not an Error -> nothing released
IwCatch ==
  /\ pc = "iwCatch" /\ pc' = "failed"
  /\ UNCHANGED <<i, cur, held, list, delRef, thrown>>

\* :548-568 the caller closes the returned reader
Close ==
  /\ pc = "open"
  /\ held' = [s \in SEGS |-> IF s \in list THEN 0 ELSE held[s]]
  /\ delRef' = 0
  /\ pc' = "closed"
  /\ UNCHANGED <<i, cur, list, thrown>>

Done == pc \in {"failed", "closed"} /\ UNCHANGED vars

Next == Apply \/ Keep \/ EndLoop \/ IncDel \/ Ctor \/ Catch \/ IwCatch \/ Close \/ Done

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

TypeOK ==
  /\ held \in [SEGS -> 0..1] /\ list \subseteq SEGS /\ delRef \in -1..1

\* After the exception reached the application (which holds nothing from this call),
\* and after the returned reader was closed: no segment-reader reference and no
\* IndexFileDeleter reference may remain.
NoSegmentReaderLeak == pc \in {"failed", "closed"} => \A s \in SEGS : held[s] = 0
NoDeleterLeak       == pc \in {"failed", "closed"} => delRef = 0
NoLeak == NoSegmentReaderLeak /\ NoDeleterLeak
\* A fix must never release a deleter reference this call did not take
NoOverRelease == delRef >= 0

\* Non-vacuity probes (each must be VIOLATED in its probe config):
ProbeNeverFails  == pc # "failed"
ProbeNeverCloses == pc # "closed"
Terminates == <>(pc \in {"failed", "closed"})
=============================================================================

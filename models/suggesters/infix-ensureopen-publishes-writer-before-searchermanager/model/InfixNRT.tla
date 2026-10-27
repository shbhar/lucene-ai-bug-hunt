------------------------------ MODULE InfixNRT ------------------------------
(***************************************************************************)
(* AnalyzingInfixSuggester NRT protocol: build / add / update / refresh /  *)
(* lookup / close from concurrent threads.                                 *)
(*                                                                         *)
(* Model element -> Java (lucene/suggest/.../analyzing/                    *)
(*                         AnalyzingInfixSuggester.java @ ea57360912ed)    *)
(*   writer, sm                 fields `writer` (136), `searcherMgr` (145) *)
(*   wlock                      synchronized(writerLock) (139, 329, 419)   *)
(*   rwlock (readers/writer)    searcherMgrLock (148-151)                  *)
(*   Build                      build() 327-375; closeIndexWriterOnBuild   *)
(*                              (default true, DEFAULT_CLOSE_INDEXWRITER_  *)
(*                              ON_BUILD 163) closes the writer at 370-373 *)
(*   EnsureOpen a1..a3          ensureOpen() 418-434: writer = new IW (423)  *)
(*                              then setAndCloseOldSearcherManager(new     *)
(*                              SearcherManager(writer)) (431, 293-305)    *)
(*   AddDoc                     add() 441-445: writer.addDocument outside  *)
(*                              the lock                                   *)
(*   Refresh r1..r3             refresh() 489-498: unsynchronized reads of *)
(*                              searcherMgr (490, 494) and writer (493)    *)
(*   Lookup l1..l3              lookup() 731-748 (acquire under read lock) *)
(*   maybeRefreshBlocking on an SM whose writer is closed throws           *)
(*   AlreadyClosedException (upstream TestAnalyzingInfixSuggester          *)
(*   testCloseIndexWriterOnBuild 1561-1563); on a closed SM it throws too  *)
(*   (ReferenceManager.ensureOpen).                                        *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, Sequences

CONSTANTS CloseOnBuild,   \* closeIndexWriterOnBuild
          Docs,           \* docs added by the adder after build
          RefreshFirst,   \* TRUE: the refresher starts only after the adder finished (sequential check)
          FixLock         \* TRUE: the fix sketch, refresh() runs under synchronized(writerLock)

VARIABLES writer,      \* 0 = null, else writer id
          wclosed,     \* set of closed writer ids
          widx,        \* docs in each writer: function id -> set of docs (index content)
          sm,          \* 0 = null, else SM id
          smw,         \* SM id -> writer id it was opened on
          smview,      \* SM id -> set of docs visible to its current searcher
          smclosed,    \* set of closed SM ids
          nextW, nextS,
          wlock,       \* holder of writerLock or "none"
          readers,     \* number of read-lock holders of searcherMgrLock
          wlockSM,     \* write lock of searcherMgrLock held? (BOOLEAN)
          pcA, pcR,    \* program counters
          aDocs,       \* docs still to add
          aLocal,      \* adder: local writer reference read before addDocument
          aNewSM,      \* adder: SM under construction
          rLocal,      \* refresher: local copy of searcherMgr read at line 494
          lMgr,        \* lookup local mgr
          lResult,     \* docs seen by the refresher's lookup after refresh
          err,         \* error string (first failure) or ""
          addDoneAtRefreshStart

vars == <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM,
          pcA, pcR, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

Base == {"built"}   \* the build() dictionary

(* State right after build() returned. Writer 1 built the index; SM 1 was opened on it. *)
Init ==
  /\ writer = IF CloseOnBuild THEN 0 ELSE 1
  /\ wclosed = IF CloseOnBuild THEN {1} ELSE {}
  /\ widx = [i \in 1..3 |-> IF i = 1 THEN Base ELSE {}]
  /\ sm = 1
  /\ smw = [i \in 1..3 |-> IF i = 1 THEN 1 ELSE 0]
  /\ smview = [i \in 1..3 |-> IF i = 1 THEN Base ELSE {}]
  /\ smclosed = {}
  /\ nextW = 2 /\ nextS = 2
  /\ wlock = "none" /\ readers = 0 /\ wlockSM = FALSE
  /\ pcA = "add"
  /\ pcR = IF RefreshFirst THEN "waitA" ELSE "r1"
  /\ aDocs = Docs
  /\ aLocal = 0 /\ aNewSM = 0 /\ rLocal = 0 /\ lMgr = 0 /\ lResult = {}
  /\ err = ""
  /\ addDoneAtRefreshStart = FALSE

Fail(msg) == err' = IF err = "" THEN msg ELSE err

(* ---------------- adder: add(x) for every x in Docs ---------------- *)
A_add ==   \* add(): ensureOpen() enters synchronized(writerLock)
  /\ pcA = "add" /\ aDocs # {} /\ wlock = "none"
  /\ wlock' = "A"
  /\ pcA' = IF writer = 0 THEN "a1" ELSE "a4"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, readers, wlockSM, pcR, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

A_a1 ==    \* writer = new IndexWriter(dir, APPEND)  (line 423-425): the field is published here
  /\ pcA = "a1"
  /\ writer' = nextW
  /\ widx' = [widx EXCEPT ![nextW] = widx[1]]   \* opens the committed index
  /\ nextW' = nextW + 1
  /\ pcA' = "a2"
  /\ UNCHANGED <<wclosed, sm, smw, smview, smclosed, nextS, wlock, readers, wlockSM, pcR, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

A_a2 ==    \* new SearcherManager(writer, null) (line 431): opens an NRT reader of the new writer
  /\ pcA = "a2"
  /\ aNewSM' = nextS
  /\ smw' = [smw EXCEPT ![nextS] = writer]
  /\ smview' = [smview EXCEPT ![nextS] = widx[writer]]
  /\ nextS' = nextS + 1
  /\ pcA' = "a3"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smclosed, nextW, wlock, readers, wlockSM, pcR, aDocs, aLocal, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

A_a3 ==    \* setAndCloseOldSearcherManager: write lock, swap, close old, unlock (293-305)
  /\ pcA = "a3" /\ readers = 0 /\ ~wlockSM
  /\ sm' = aNewSM
  /\ smclosed' = smclosed \cup {sm}
  /\ pcA' = "a4"
  /\ UNCHANGED <<writer, wclosed, widx, smw, smview, nextW, nextS, wlock, readers, wlockSM, pcR, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

A_a4 ==    \* leave synchronized; read the field `writer` for writer.addDocument (444)
  /\ pcA = "a4"
  /\ wlock' = "none"
  /\ aLocal' = writer
  /\ pcA' = "a5"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, readers, wlockSM, pcR, aDocs, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

A_a5 ==    \* addDocument
  /\ pcA = "a5"
  /\ \E x \in aDocs:
       /\ IF aLocal = 0 THEN Fail("add: NullPointerException (writer null)")
          ELSE IF aLocal \in wclosed THEN Fail("add: AlreadyClosedException (writer closed)")
          ELSE err' = err
       /\ widx' = IF aLocal # 0 /\ aLocal \notin wclosed THEN [widx EXCEPT ![aLocal] = @ \cup {x}] ELSE widx
       /\ aDocs' = aDocs \ {x}
  /\ pcA' = "add"
  /\ UNCHANGED <<writer, wclosed, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM, pcR, aLocal, aNewSM, rLocal, lMgr, lResult, addDoneAtRefreshStart>>

A_done ==
  /\ pcA = "add" /\ aDocs = {}
  /\ pcA' = "done"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM, pcR, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

(* ---------------- refresher: refresh(); then lookup() ---------------- *)
R_wait ==
  /\ pcR = "waitA" /\ pcA = "done"
  /\ pcR' = "r1"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

R_r1 ==    \* if (searcherMgr == null) throw ISE   (490); with FixLock: enter synchronized(writerLock) first
  /\ pcR = "r1"
  /\ (FixLock => wlock = "none")
  /\ wlock' = IF FixLock THEN "R" ELSE wlock
  /\ addDoneAtRefreshStart' = (pcA = "done")
  /\ IF sm = 0 THEN /\ Fail("refresh: IllegalStateException") /\ pcR' = "end"
     ELSE /\ err' = err /\ pcR' = "r2"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult>>

R_r2 ==    \* if (writer != null)   (493)
  /\ pcR = "r2"
  /\ pcR' = IF writer # 0 THEN "r3" ELSE "l1"
  /\ wlock' = IF writer = 0 /\ wlock = "R" THEN "none" ELSE wlock
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, err, addDoneAtRefreshStart>>

R_r3 ==    \* searcherMgr.maybeRefreshBlocking()   (494): read the field again, then refresh it
  /\ pcR = "r3"
  /\ rLocal' = sm
  /\ pcR' = "r4"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, lMgr, lResult, err, addDoneAtRefreshStart>>

R_r4 ==
  /\ pcR = "r4"
  /\ IF rLocal \in smclosed THEN /\ Fail("refresh: AlreadyClosedException (SearcherManager closed)") /\ pcR' = "end" /\ UNCHANGED smview
     ELSE IF smw[rLocal] \in wclosed THEN /\ Fail("refresh: AlreadyClosedException (SearcherManager's writer closed)") /\ pcR' = "end" /\ UNCHANGED smview
     ELSE /\ smview' = [smview EXCEPT ![rLocal] = widx[smw[rLocal]]] /\ err' = err /\ pcR' = "l1"
  /\ wlock' = IF wlock = "R" THEN "none" ELSE wlock
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smclosed, nextW, nextS, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, rLocal, lMgr, lResult, addDoneAtRefreshStart>>

R_l1 ==    \* lookup: read lock; mgr = searcherMgr; acquire; unlock  (731-737)
  /\ pcR = "l1" /\ ~wlockSM
  /\ IF sm = 0 THEN /\ Fail("lookup: NullPointerException") /\ pcR' = "end" /\ UNCHANGED <<lMgr, lResult>>
     ELSE IF sm \in smclosed THEN /\ Fail("lookup: AlreadyClosedException") /\ pcR' = "end" /\ UNCHANGED <<lMgr, lResult>>
     ELSE /\ lMgr' = sm /\ lResult' = smview[sm] /\ err' = err /\ pcR' = "end"
  /\ UNCHANGED <<writer, wclosed, widx, sm, smw, smview, smclosed, nextW, nextS, wlock, readers, wlockSM, pcA, aDocs, aLocal, aNewSM, rLocal, addDoneAtRefreshStart>>

Next == A_add \/ A_a1 \/ A_a2 \/ A_a3 \/ A_a4 \/ A_a5 \/ A_done
        \/ R_wait \/ R_r1 \/ R_r2 \/ R_r3 \/ R_r4 \/ R_l1
        \/ (pcA = "done" /\ pcR = "end" /\ UNCHANGED vars)

Spec == Init /\ [][Next]_vars

(* No thread fails with an exception the API does not document for this use. *)
NoError == err = ""

(* After refresh() returns, a lookup sees every add() that completed before the refresh started
   (AnalyzingInfixSuggester.add/update javadoc 437-439 and 450-452: "call refresh in the end in order
   to see the suggestions in lookup"). *)
Visibility == (pcR = "end" /\ err = "" /\ addDoneAtRefreshStart) => (Docs \subseteq lResult)

(* vacuity probes: these must be violated *)
ProbeRefreshEnds == ~(pcR = "end" /\ err = "" /\ addDoneAtRefreshStart /\ Docs \subseteq lResult)
=============================================================================

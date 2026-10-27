---------------------------- MODULE MonitorWrite ----------------------------
(***************************************************************************)
(* monitor-lifecycle-persistence-mbt: the write protocol of the Lucene Monitor's WritableQueryIndex     *)
(* WITH query decomposition, non-aborting indexing failures, concurrent    *)
(* deleteById/clear, and close+reopen (main @ ea57360912ed).               *)
(*                                                                         *)
(* Model element -> Java line (lucene/monitor/src/java/org/apache/lucene/  *)
(* monitor/):                                                              *)
(*   iw         IndexWriter contents incl. buffered ops (deletes by term   *)
(*              apply to docs added before them; addDocument is atomic)    *)
(*   committed  last commit point (what a reopen / read-only monitor loads)*)
(*   vis        searcher of SearcherManager (WritableQueryIndex.java:79)   *)
(*   cache      WritableQueryIndex.queries, keyed by cacheId <<id, part>>  *)
(*              = QueryCacheEntry.cacheId "id_part" (QueryCacheEntry.java:56)*)
(*   lock       commitLock (WritableQueryIndex.java:64)                    *)
(*   Register   commitWithoutNotify (WritableQueryIndex.java:107-133):     *)
(*     R1 lock; R2 deleteDocuments(id) (:117-119); R3 per part:            *)
(*     queries.put (:121) then addDocument (:122) which may throw a        *)
(*     non-aborting exception; R4 writer.commit (:127); R5 maybeRefresh    *)
(*     (:128); R6 unlock.  RFAIL: the exception propagates (no rollback).  *)
(*   Delete     deleteQueries (:338-344): D0 deleteDocuments OUTSIDE the   *)
(*              commitLock, then commitWithoutNotify(empty) D1..D4          *)
(*   Clear      clear (:347-351): X0 deleteAll OUTSIDE the commitLock, then *)
(*              commitWithoutNotify(empty) X1..X4                           *)
(*   Restart    Monitor.close (IndexWriter.close commits pending changes:  *)
(*              commitOnClose default) + new Monitor: populateQueryCache   *)
(*              (:137-181): first doc per id (docID order, modelled as any)*)
(*              is deserialized and decomposed into all its parts           *)
(* FIX = TRUE models the candidate fix: deletes/deleteAll under commitLock,*)
(*   one atomic updateDocuments(Term(id), parts) per query, cache puts     *)
(*   after the successful add.                                             *)
(* Properties (checked only in idle states, i.e. no operation in flight):  *)
(*   AtomicQuery     every id's docs in the searcher are none or exactly   *)
(*                   all parts of one version, and the cache entry of each *)
(*                   visible doc is that doc's own part (else Monitor.match *)
(*                   runs another version's disjunct = wrong results)      *)
(* SEQ = TRUE lets an operation start only when no other one is in flight *)
(*   (single-threaded use), to separate sequential from concurrent errors. *)
(*   FailureNoOp     after a register that threw, until a later operation  *)
(*                   on that id succeeds, the id's visible docs are the    *)
(*                   ones it had before the failed call                    *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS Ids, K, NReg, NDel, NClear, NRestart, MayFail, FIX, SEQ

None == <<"none">>
Vers == 1..NReg
Parts(n) == 0..(n - 1)
Docs == Ids \X Vers \X Parts(K)
Idle(p) == p = "idle"

VARIABLES iw, committed, vis, cache, lock,
          rpc, rId, rVer, rPart, nR, verParts,
          dpc, dId, nD, xpc, nX, nRs,
          failedLast, preFail

vars == <<iw, committed, vis, cache, lock, rpc, rId, rVer, rPart, nR, verParts,
          dpc, dId, nD, xpc, nX, nRs, failedLast, preFail>>
regv == <<rpc, rId, rVer, rPart, nR, verParts>>
delv == <<dpc, dId, nD>>
clrv == <<xpc, nX>>

Init ==
  /\ iw = {} /\ committed = {} /\ vis = {}
  /\ cache = [c \in Ids \X Parts(K) |-> None]
  /\ lock = "free"
  /\ rpc = "idle" /\ rId \in Ids /\ rVer = 0 /\ rPart = 0 /\ nR = 0
  /\ verParts = [v \in Vers |-> 0]
  /\ dpc = "idle" /\ dId \in Ids /\ nD = 0
  /\ xpc = "idle" /\ nX = 0 /\ nRs = 0
  /\ failedLast = [i \in Ids |-> FALSE] /\ preFail = [i \in Ids |-> {}]

DocsOf(S, i) == {d \in S : d[1] = i}
Without(S, i) == {d \in S : d[1] /= i}
AllIdle == Idle(rpc) /\ Idle(dpc) /\ Idle(xpc)
FullVersion(i, v) == {<<i, v, p>> : p \in Parts(verParts[v])}

(* ---------------- Register(MonitorQuery(id, version with n parts)) ---------------- *)
R0 == /\ Idle(rpc) /\ nR < NReg /\ (SEQ => AllIdle)
      /\ \E i \in Ids, n \in {1, K} :
           /\ rId' = i /\ rVer' = nR + 1 /\ rPart' = 0
           /\ verParts' = [verParts EXCEPT ![nR + 1] = n]
      /\ nR' = nR + 1 /\ rpc' = "R1"
      /\ UNCHANGED <<iw, committed, vis, cache, lock, delv, clrv, nRs, failedLast, preFail>>
R1 == /\ rpc = "R1" /\ lock = "free" /\ lock' = "R"
      /\ rpc' = IF FIX THEN "R2F" ELSE "R2"
      /\ UNCHANGED <<iw, committed, vis, cache, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R2 == /\ rpc = "R2" /\ iw' = Without(iw, rId) /\ rpc' = "R3"
      /\ UNCHANGED <<committed, vis, cache, lock, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R3 == /\ rpc = "R3"
      /\ cache' = [cache EXCEPT ![<<rId, rPart>>] = <<rId, rVer, rPart>>]       \* queries.put first
      /\ \/ /\ iw' = iw \cup {<<rId, rVer, rPart>>}                              \* addDocument ok
            /\ rPart' = rPart + 1
            /\ rpc' = IF rPart + 1 = verParts[rVer] THEN "R4" ELSE "R3"
         \/ /\ MayFail /\ UNCHANGED <<iw, rPart>> /\ rpc' = "RFAIL"              \* non-aborting failure
      /\ UNCHANGED <<committed, vis, lock, rId, rVer, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R2F == /\ rpc = "R2F"                                                            \* FIX: atomic per-query update
       /\ \/ /\ iw' = Without(iw, rId) \cup FullVersion(rId, rVer)
             /\ cache' = [c \in DOMAIN cache |-> IF c[1] = rId /\ c[2] < verParts[rVer]
                                                   THEN <<rId, rVer, c[2]>> ELSE cache[c]]
             /\ rpc' = "R4"
          \/ /\ MayFail /\ UNCHANGED <<iw, cache>> /\ rpc' = "RFAIL"
       /\ UNCHANGED <<committed, vis, lock, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R4 == /\ rpc = "R4" /\ committed' = iw /\ rpc' = "R5"
      /\ UNCHANGED <<iw, vis, cache, lock, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R5 == /\ rpc = "R5" /\ vis' = iw /\ rpc' = "R6"
      /\ UNCHANGED <<iw, committed, cache, lock, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, failedLast, preFail>>
R6 == /\ rpc = "R6" /\ lock' = "free" /\ rpc' = "idle"
      /\ failedLast' = [failedLast EXCEPT ![rId] = FALSE]
      /\ UNCHANGED <<iw, committed, vis, cache, rId, rVer, rPart, nR, verParts, delv, clrv, nRs, preFail>>
RFAIL == /\ rpc = "RFAIL" /\ lock' = "free" /\ rpc' = "idle"                    \* exception propagates
         /\ failedLast' = [failedLast EXCEPT ![rId] = TRUE]
         /\ preFail' = [preFail EXCEPT ![rId] = DocsOf(vis, rId)]
         /\ UNCHANGED <<iw, committed, vis, cache, rId, rVer, rPart, nR, verParts, delv, clrv, nRs>>

(* ---------------- deleteById(id) ---------------- *)
D0 == /\ Idle(dpc) /\ nD < NDel /\ ~FIX /\ (SEQ => AllIdle)                   \* deleteDocuments OUTSIDE the commitLock
      /\ \E i \in Ids : dId' = i /\ iw' = Without(iw, i)
      /\ nD' = nD + 1 /\ dpc' = "D1"
      /\ UNCHANGED <<committed, vis, cache, lock, regv, clrv, nRs, failedLast, preFail>>
D0F == /\ Idle(dpc) /\ nD < NDel /\ FIX /\ lock = "free" /\ (SEQ => AllIdle)   \* FIX: lock, then delete
       /\ \E i \in Ids : dId' = i /\ iw' = Without(iw, i)
       /\ lock' = "D" /\ nD' = nD + 1 /\ dpc' = "D2"
       /\ UNCHANGED <<committed, vis, cache, regv, clrv, nRs, failedLast, preFail>>
D1 == /\ dpc = "D1" /\ lock = "free" /\ lock' = "D" /\ dpc' = "D2"
      /\ UNCHANGED <<iw, committed, vis, cache, regv, dId, nD, clrv, nRs, failedLast, preFail>>
D2 == /\ dpc = "D2" /\ committed' = iw /\ dpc' = "D3"
      /\ UNCHANGED <<iw, vis, cache, lock, regv, dId, nD, clrv, nRs, failedLast, preFail>>
D3 == /\ dpc = "D3" /\ vis' = iw /\ dpc' = "D4"
      /\ UNCHANGED <<iw, committed, cache, lock, regv, dId, nD, clrv, nRs, failedLast, preFail>>
D4 == /\ dpc = "D4" /\ lock' = "free" /\ dpc' = "idle"
      /\ failedLast' = [failedLast EXCEPT ![dId] = FALSE]
      /\ UNCHANGED <<iw, committed, vis, cache, regv, dId, nD, clrv, nRs, preFail>>

(* ---------------- clear() ---------------- *)
X0 == /\ Idle(xpc) /\ nX < NClear /\ ~FIX /\ (SEQ => AllIdle)/\ iw' = {} /\ nX' = nX + 1 /\ xpc' = "X1"
      /\ UNCHANGED <<committed, vis, cache, lock, regv, delv, nRs, failedLast, preFail>>
X0F == /\ Idle(xpc) /\ nX < NClear /\ FIX /\ lock = "free" /\ (SEQ => AllIdle) /\ lock' = "X"
       /\ iw' = {} /\ nX' = nX + 1 /\ xpc' = "X2"
       /\ UNCHANGED <<committed, vis, cache, regv, delv, nRs, failedLast, preFail>>
X1 == /\ xpc = "X1" /\ lock = "free" /\ lock' = "X" /\ xpc' = "X2"
      /\ UNCHANGED <<iw, committed, vis, cache, regv, delv, nX, nRs, failedLast, preFail>>
X2 == /\ xpc = "X2" /\ committed' = iw /\ xpc' = "X3"
      /\ UNCHANGED <<iw, vis, cache, lock, regv, delv, nX, nRs, failedLast, preFail>>
X3 == /\ xpc = "X3" /\ vis' = iw /\ xpc' = "X4"
      /\ UNCHANGED <<iw, committed, cache, lock, regv, delv, nX, nRs, failedLast, preFail>>
X4 == /\ xpc = "X4" /\ lock' = "free" /\ xpc' = "idle"
      /\ failedLast' = [i \in Ids |-> FALSE]
      /\ UNCHANGED <<iw, committed, vis, cache, regv, delv, nX, nRs, preFail>>

(* ---------------- close + reopen (only when nothing is in flight) ---------------- *)
Restart == /\ AllIdle /\ nRs < NRestart
           /\ committed' = iw /\ vis' = iw           \* IndexWriter.close() commits; reopen loads it
           /\ \E pick \in [Ids -> Docs \cup {None}] :
                /\ \A i \in Ids : IF DocsOf(iw, i) = {} THEN pick[i] = None ELSE pick[i] \in DocsOf(iw, i)
                /\ cache' = [c \in DOMAIN cache |->
                               IF pick[c[1]] /= None /\ c[2] < verParts[pick[c[1]][2]]
                                 THEN <<c[1], pick[c[1]][2], c[2]>> ELSE None]
           /\ nRs' = nRs + 1
           /\ UNCHANGED <<iw, lock, regv, delv, clrv, failedLast, preFail>>

Next == R0 \/ R1 \/ R2 \/ R3 \/ R2F \/ R4 \/ R5 \/ R6 \/ RFAIL
        \/ D0 \/ D0F \/ D1 \/ D2 \/ D3 \/ D4
        \/ X0 \/ X0F \/ X1 \/ X2 \/ X3 \/ X4 \/ Restart
        \/ (AllIdle /\ UNCHANGED vars)

Spec == Init /\ [][Next]_vars

AtomicQuery ==
  AllIdle =>
    /\ \A i \in Ids : DocsOf(vis, i) = {} \/ \E v \in Vers : DocsOf(vis, i) = FullVersion(i, v)
    /\ \A d \in vis : cache[<<d[1], d[3]>>] = d

FailureNoOp == AllIdle => \A i \in Ids : failedLast[i] => DocsOf(vis, i) = preFail[i]

TypeOK == /\ iw \subseteq Docs /\ vis \subseteq Docs /\ committed \subseteq Docs
          /\ lock \in {"free", "R", "D", "X"}
=============================================================================

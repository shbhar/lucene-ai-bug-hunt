-------------------------- MODULE QueryIndexConc --------------------------
(***************************************************************************)
(* monitor-presearcher method_secondary: the WritableQueryIndex update/purge/search        *)
(* protocol of the Lucene Monitor (Lucene main @ ea57360912ed).           *)
(*                                                                         *)
(* Model element -> Java line (lucene/monitor/src/java/.../monitor/):      *)
(*   iw            IndexWriter state incl. buffered ops                     *)
(*   vis           searcher published by SearcherManager (refresh copies iw)*)
(*   maps[cur]     WritableQueryIndex.queries (volatile ConcurrentMap,      *)
(*                 WritableQueryIndex.java:63; swapped only in purge)       *)
(*   pcache        WritableQueryIndex.purgeCache (WritableQueryIndex.java:50)*)
(*   clock         commitLock (WritableQueryIndex.java:54)                  *)
(*   readers/wr    purgeLock ReentrantReadWriteLock (WritableQueryIndex.java:53)*)
(*   Committer C0-C5  commitWithoutNotify (WritableQueryIndex.java:97-123):  *)
(*                 deleteDocuments(id); queries.put; addDocument;          *)
(*                 purgeCache.put if non-null; commit; maybeRefresh         *)
(*   Deleter D0-D1 deleteQueries (WritableQueryIndex.java:330-336):         *)
(*                 deleteDocuments OUTSIDE the locks, then an empty commit  *)
(*   Purger P0-P3  purgeCache(CachePopulator) (WritableQueryIndex.java:275-320)*)
(*                 + purgeCache() scan collector (:258-266)                 *)
(*   Searcher S0-S1 search (WritableQueryIndex.java:206-246) + the          *)
(*                 StandardQueryCollector null check (Monitor.java:327:     *)
(*                 "if (query == null) return;" = a silently skipped query) *)
(* Cache ids are modelled = query ids (no decomposition); versions model    *)
(* re-registration of an id.                                               *)
(*                                                                         *)
(* Properties:                                                             *)
(*   NoNullEntry  every doc in a search's searcher snapshot has an entry in *)
(*                the cache map that search captured (else the query is     *)
(*                silently skipped by Monitor.match).                       *)
(*   VisibleAfterReturn  a search that starts after commit(id,v) returned   *)
(*                (and no delete of id is pending) sees a doc for id whose  *)
(*                map entry is present.                                     *)
(* MUTANT selects a deliberately broken variant (vacuity evidence):        *)
(*   noPurgeCache    commits during a purge are not logged in purgeCache    *)
(*   putAfterRefresh queries.put happens after the searcher refresh         *)
(*   lateLog         purgeCache is created only after the index scan        *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS Ids, NCommits, NPurges, NSearches, NDeletes, MUTANT

Procs == {"C", "P", "S", "D"}
NoMap == [i \in Ids |-> 0]

VARIABLES iw, vis, maps, cur, pcache, pOn, clock, readers, wr,
          pcC, pcP, pcS, pcD,
          cId, cVer, nC, nP, nS, nD, dId,
          pSnap, pMapIdx, pNew,
          sSnap, sMap, sStartReturned, returned, deletePending, violation

vars == <<iw, vis, maps, cur, pcache, pOn, clock, readers, wr, pcC, pcP, pcS, pcD,
          cId, cVer, nC, nP, nS, nD, dId, pSnap, pMapIdx, pNew,
          sSnap, sMap, sStartReturned, returned, deletePending, violation>>

Init ==
  /\ iw = NoMap /\ vis = NoMap
  /\ maps = <<NoMap>> /\ cur = 1
  /\ pcache = NoMap /\ pOn = FALSE
  /\ clock = "free" /\ readers = {} /\ wr = "none"
  /\ pcC = "C0" /\ pcP = "P0" /\ pcS = "S0" /\ pcD = "D0"
  /\ cId \in Ids /\ cVer = 0 /\ nC = 0 /\ nP = 0 /\ nS = 0 /\ nD = 0 /\ dId \in Ids
  /\ pSnap = NoMap /\ pMapIdx = 1 /\ pNew = NoMap
  /\ sSnap = NoMap /\ sMap = 1 /\ sStartReturned = NoMap
  /\ returned = NoMap /\ deletePending = {} /\ violation = "none"

PutCur(i, v) == maps' = [maps EXCEPT ![cur][i] = v]

(* ---------------- Committer: register(MonitorQuery(id, version)) ---------------- *)
C0 == /\ pcC = "C0" /\ nC < NCommits
      /\ clock = "free" /\ wr = "none"
      /\ \E i \in Ids : cId' = i
      /\ cVer' = nC + 1
      /\ clock' = "C" /\ readers' = readers \cup {"C"}
      /\ pcC' = "C1"
      /\ UNCHANGED <<pOn, iw, vis, maps, cur, pcache, wr, pcP, pcS, pcD, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
C1 == /\ pcC = "C1"                                  \* writer.deleteDocuments(id); queries.put(...)
      /\ iw' = [iw EXCEPT ![cId] = 0]
      /\ IF MUTANT = "putAfterRefresh" THEN UNCHANGED maps ELSE PutCur(cId, cVer)
      /\ pcC' = "C2"
      /\ UNCHANGED <<pOn, vis, cur, pcache, clock, readers, wr, pcP, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
C2 == /\ pcC = "C2"                                  \* writer.addDocument; purgeCache.put if non-null
      /\ iw' = [iw EXCEPT ![cId] = cVer]
      /\ pcache' = IF ~pOn \/ MUTANT = "noPurgeCache" THEN pcache ELSE [pcache EXCEPT ![cId] = cVer]
      /\ pcC' = "C3"
      /\ UNCHANGED <<pOn, vis, maps, cur, clock, readers, wr, pcP, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
C3 == /\ pcC = "C3"                                  \* writer.commit(); manager.maybeRefresh()
      /\ vis' = iw
      /\ pcC' = "C4"
      /\ UNCHANGED <<pOn, iw, maps, cur, pcache, clock, readers, wr, pcP, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
C4 == /\ pcC = "C4"                                  \* unlock; register() returns
      /\ clock' = "free" /\ readers' = readers \ {"C"}
      /\ returned' = [returned EXCEPT ![cId] = cVer]
      /\ IF MUTANT = "putAfterRefresh" THEN PutCur(cId, cVer) ELSE UNCHANGED maps
      /\ nC' = nC + 1 /\ pcC' = "C0"
      /\ UNCHANGED <<pOn, iw, vis, cur, pcache, wr, pcP, pcS, pcD, cId, cVer, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, deletePending, violation>>

(* ---------------- Deleter: deleteById(id) ---------------- *)
D0 == /\ pcD = "D0" /\ nD < NDeletes                 \* writer.deleteDocuments OUTSIDE the locks
      /\ \E i \in Ids : /\ dId' = i
                        /\ iw' = [iw EXCEPT ![i] = 0]
                        /\ deletePending' = deletePending \cup {i}
      /\ pcD' = "D1"
      /\ UNCHANGED <<pOn, vis, maps, cur, pcache, clock, readers, wr, pcC, pcP, pcS, cId, cVer, nC, nP, nS, nD,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, violation>>
D1 == /\ pcD = "D1" /\ clock = "free" /\ wr = "none" \* commitWithoutNotify(empty): lock, commit, refresh, unlock
      /\ vis' = iw
      /\ deletePending' = deletePending \ {dId}
      /\ returned' = [returned EXCEPT ![dId] = 0]
      /\ nD' = nD + 1 /\ pcD' = "D0"
      /\ UNCHANGED <<pOn, iw, maps, cur, pcache, clock, readers, wr, pcC, pcP, pcS, cId, cVer, nC, nP, nS, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, violation>>

(* ---------------- Purger: purgeCache() ---------------- *)
P0 == /\ pcP = "P0" /\ nP < NPurges                  \* synchronized(commitLock){ writeLock; purgeCache = new; unlock }
      /\ clock = "free" /\ readers = {} /\ wr = "none"
      /\ IF MUTANT = "lateLog" THEN pcache' = pcache /\ pOn' = pOn ELSE pcache' = NoMap /\ pOn' = TRUE
      /\ pcP' = "P1"
      /\ UNCHANGED <<iw, vis, maps, cur, clock, readers, wr, pcC, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
P1 == /\ pcP = "P1" /\ wr = "none"                   \* scan = search(MatchAll): readLock; acquire; queries=this.queries
      /\ pSnap' = vis /\ pMapIdx' = cur
      /\ pcP' = "P2"
      /\ UNCHANGED <<pOn, iw, vis, maps, cur, pcache, clock, readers, wr, pcC, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
P2 == /\ pcP = "P2"                                  \* collect: query = queries.get(id); if != null newCache.put
      /\ pNew' = [i \in Ids |-> IF pSnap[i] /= 0 THEN maps[pMapIdx][i] ELSE 0]
      /\ IF MUTANT = "lateLog" THEN pcache' = NoMap /\ pOn' = TRUE ELSE pcache' = pcache /\ pOn' = pOn
      /\ pcP' = "P3"
      /\ UNCHANGED <<iw, vis, maps, cur, clock, readers, wr, pcC, pcS, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, sSnap, sMap, sStartReturned, returned, deletePending, violation>>
P3 == /\ pcP = "P3"                                  \* synchronized(commitLock){ writeLock; newCache.putAll(purgeCache); swap }
      /\ clock = "free" /\ readers = {} /\ wr = "none"
      /\ LET merged == [i \in Ids |-> IF pcache[i] /= 0 THEN pcache[i] ELSE pNew[i]]
         IN /\ maps' = Append(maps, merged)
            /\ cur' = Len(maps) + 1
      /\ pcache' = NoMap /\ pOn' = FALSE
      /\ nP' = nP + 1 /\ pcP' = "P0"
      /\ UNCHANGED <<iw, vis, clock, readers, wr, pcC, pcS, pcD, cId, cVer, nC, nS, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending, violation>>

(* ---------------- Searcher: Monitor.match -> QueryIndex.search ---------------- *)
S0 == /\ pcS = "S0" /\ nS < NSearches /\ wr = "none" \* readLock; searcher = acquire(); queries = this.queries
      /\ sSnap' = vis /\ sMap' = cur
      /\ sStartReturned' = [i \in Ids |-> IF i \in deletePending THEN 0 ELSE returned[i]]
      /\ pcS' = "S1"
      /\ UNCHANGED <<pOn, iw, vis, maps, cur, pcache, clock, readers, wr, pcC, pcP, pcD, cId, cVer, nC, nP, nS, nD, dId,
                     pSnap, pMapIdx, pNew, returned, deletePending, violation>>
S1 == /\ pcS = "S1"                                  \* collect each hit: queries.get(cacheId); null => skipped
      /\ violation' =
           IF \E i \in Ids : sSnap[i] /= 0 /\ maps[sMap][i] = 0 THEN "NullEntry"
           ELSE IF \E i \in Ids : sStartReturned[i] /= 0 /\ (sSnap[i] = 0 \/ maps[sMap][i] = 0) THEN "NotVisibleAfterReturn"
           ELSE violation
      /\ nS' = nS + 1 /\ pcS' = "S0"
      /\ UNCHANGED <<pOn, iw, vis, maps, cur, pcache, clock, readers, wr, pcC, pcP, pcD, cId, cVer, nC, nP, nD, dId,
                     pSnap, pMapIdx, pNew, sSnap, sMap, sStartReturned, returned, deletePending>>

Next == C0 \/ C1 \/ C2 \/ C3 \/ C4 \/ D0 \/ D1 \/ P0 \/ P1 \/ P2 \/ P3 \/ S0 \/ S1
          \/ (/\ nC = NCommits /\ nP = NPurges /\ nS = NSearches /\ nD = NDeletes
              /\ pcC = "C0" /\ pcP = "P0" /\ pcS = "S0" /\ pcD = "D0" /\ UNCHANGED vars)

Spec == Init /\ [][Next]_vars

NoViolation == violation = "none"
TypeOK == /\ cur \in 1..(NPurges + 1) /\ readers \subseteq Procs
=============================================================================

------------------------------ MODULE LRUCacheM1 ------------------------------
(***************************************************************************)
(* Design-level model of one LRUQueryCache partition plus the cache-wide  *)
(* deferred clean-up state, Lucene main 7cecf79523                         *)
(* (lucene/core/src/java/org/apache/lucene/search/LRUQueryCache.java).    *)
(* All queries of one value route to one partition (:1273-1279), and every *)
(* mutation of a partition runs under its write lock, so each partition    *)
(* operation is one atomic step; cleanUp() is split into its snapshot step *)
(* and one step per removed key, which other operations can interleave.    *)
(*                                                                         *)
(* Mapping:                                                                *)
(*  cache / lru / uniq   :947 cache, :940-941 uniqueCacheKeys (LRU order), *)
(*                       :954 uniqueQueries (canonical instance + refCount)*)
(*  Get                  :1060-1083 (read lock; LRU access-order update)   *)
(*  Put                  :1085-1131 (containsKey, acquireQuery, accounting,*)
(*                       evictIfNecessary :1223-1248)                       *)
(*  RegisterListener     :1133-1136 (outside the lock)                      *)
(*  Remove               :1155-1189 (remove(key, -1) and eviction variant)  *)
(*  Clear                :1191-1203, :457-463                               *)
(*  ClearQuery           :450-455, :1250-1257 (deferred)                    *)
(*  CloseCore            :443-448 clearCoreCacheKey via close listener      *)
(*  CleanUpStart / Step  :1392-1413                                         *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS
  Segs,      \* core cache keys
  Insts,     \* query instances
  QVal,      \* QVal[i] = value class of instance i (equals()/hashCode())
  QBytes,    \* QBytes[i] = RamUsageEstimator.sizeOf(instance i)
  VBytes,    \* set of possible CacheAndCount sizes
  PerEntry,  \* QueryCacheKey.ramBytesUsed()
  Hash,      \* HASHTABLE_RAM_BYTES_PER_ENTRY
  MaxSize, MaxRam,
  MaxOps

Vals == { QVal[i] : i \in Insts }
Keys == Segs \X Vals

VARIABLES
  cache,     \* key -> [inst |-> canonical instance, vb |-> value bytes, ver |-> version]
  lru,       \* sequence of keys, least recently used first
  uniq,      \* value -> [inst, qb, rc]
  ram, csize, ccount,
  cbIns, cbEv, cbCleared,     \* sums reported to onCacheEntryInserted / onCacheEntryEvicted / reset by clear
  keysToClean, queriesToClean, registered, closed,
  cuPhase, cuSnap, cuKeys, cuQueries, cuTargets,
  stale,     \* set of <<key, ver>> entries that a clear-query / core close made obsolete
  pendingReg,\* cores whose putIfAbsent finished the locked part but did not register yet
  ver, ops

vars == <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
          queriesToClean, registered, closed, cuPhase, cuSnap, cuKeys, cuQueries, cuTargets,
          stale, pendingReg, ver, ops>>

Init ==
  /\ cache = [k \in {} |-> 0]
  /\ lru = <<>>
  /\ uniq = [v \in {} |-> 0]
  /\ ram = 0 /\ csize = 0 /\ ccount = 0
  /\ cbIns = 0 /\ cbEv = 0 /\ cbCleared = 0
  /\ keysToClean = {} /\ queriesToClean = {} /\ registered = {} /\ closed = {}
  /\ cuPhase = "idle" /\ cuSnap = {} /\ cuKeys = {} /\ cuQueries = {} /\ cuTargets = {}
  /\ stale = {}
  /\ pendingReg = {}
  /\ ver = 0 /\ ops = 0

Range(s) == { s[i] : i \in DOMAIN s }
RemoveFromSeq(s, x) == SelectSeq(s, LAMBDA y : y # x)
EntryBytes(k) == PerEntry + Hash + cache[k].vb
RECURSIVE SumE(_)
SumE(S) == IF S = {} THEN 0 ELSE LET k == CHOOSE k \in S : TRUE IN EntryBytes(k) + SumE(S \ {k})
RECURSIVE SumQ(_)
SumQ(S) == IF S = {} THEN 0 ELSE LET v == CHOOSE v \in S : TRUE IN uniq[v].qb + SumQ(S \ {v})

\* functional update helpers
MapRemove(f, x) == [y \in DOMAIN f \ {x} |-> f[y]]
MapPut(f, x, e) == [y \in DOMAIN f \cup {x} |-> IF y = x THEN e ELSE f[y]]

\* the result of remove(key) on a state record (used for eviction and clean-up)
\* state record fields: cache, lru, uniq, ram, csize, ev
RemoveRec(st, k) ==
  IF k \notin DOMAIN st.cache THEN st
  ELSE LET v == k[2]
           vb == PerEntry + Hash + st.cache[k].vb
           rc == st.uniq[v].rc
           qb == 0
       IN [cache |-> MapRemove(st.cache, k),
           lru |-> RemoveFromSeq(st.lru, k),
           uniq |-> IF rc = 1 THEN MapRemove(st.uniq, v)
                    ELSE [st.uniq EXCEPT ![v].rc = rc - 1],
           ram |-> st.ram - vb - qb,
           csize |-> st.csize - 1,
           ev |-> st.ev + vb + qb]

RequiresEviction(st) == Len(st.lru) > 0 /\ (Len(st.lru) > MaxSize \/ st.ram > MaxRam)
RECURSIVE Evict(_)
Evict(st) == IF RequiresEviction(st) THEN Evict(RemoveRec(st, Head(st.lru))) ELSE st

Get(s, i) ==
  /\ s \notin closed
  /\ LET k == <<s, QVal[i]>> IN
     /\ k \in DOMAIN cache
     /\ lru' = Append(RemoveFromSeq(lru, k), k)
  /\ UNCHANGED <<cache, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
                 queriesToClean, registered, closed, cuPhase, cuSnap, cuKeys, cuQueries,
                 cuTargets, stale, pendingReg, ver, ops>>

Put(s, i, vb) ==
  /\ ops < MaxOps /\ s \notin closed
  /\ ops' = ops + 1
  /\ LET v == QVal[i]
         k == <<s, v>> IN
     IF k \in DOMAIN cache
     THEN /\ pendingReg' = pendingReg \cup {s}
          /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, ver, stale>>
     ELSE LET first == v \notin DOMAIN uniq
              u1 == IF first THEN MapPut(uniq, v, [inst |-> i, qb |-> QBytes[i], rc |-> 1])
                    ELSE [uniq EXCEPT ![v].rc = @ + 1]
              qbytes == PerEntry + (IF first THEN QBytes[i] ELSE 0)
              st0 == [cache |-> MapPut(cache, k, [inst |-> u1[v].inst, vb |-> vb, ver |-> ver + 1]),
                      lru |-> Append(lru, k), uniq |-> u1,
                      ram |-> ram + qbytes + Hash + vb, csize |-> csize + 1, ev |-> cbEv]
              st1 == Evict(st0)
          IN /\ cache' = st1.cache /\ lru' = st1.lru /\ uniq' = st1.uniq
             /\ ram' = st1.ram /\ csize' = st1.csize /\ cbEv' = st1.ev
             /\ ccount' = ccount + 1
             /\ cbIns' = cbIns + qbytes + Hash + vb
             /\ ver' = ver + 1
             /\ stale' = { e \in stale : e[1] \in DOMAIN st1.cache /\ st1.cache[e[1]].ver = e[2] }
             /\ pendingReg' = pendingReg \cup {s}
  /\ UNCHANGED <<cbCleared, keysToClean, queriesToClean, registered, closed,
                 cuPhase, cuSnap, cuKeys, cuQueries, cuTargets>>

\* registeredClosedListeners.putIfAbsent + addClosedListener, after the write lock is released
RegisterListener(s) ==
  /\ s \in pendingReg
  /\ pendingReg' = pendingReg \ {s}
  /\ registered' = registered \cup {s}
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
                 queriesToClean, closed, cuPhase, cuSnap, cuKeys, cuQueries, cuTargets, stale, ver, ops>>

\* the segment core closes: its close listener (if registered) calls clearCoreCacheKey
CloseCore(s) ==
  /\ s \notin closed /\ s \notin pendingReg   \* a search holding the core cannot be mid-put
  /\ closed' = closed \cup {s}
  /\ IF s \in registered
     THEN /\ registered' = registered \ {s}
          /\ keysToClean' = keysToClean \cup {s}
     ELSE UNCHANGED <<registered, keysToClean>>
  /\ stale' = stale \cup { <<k, cache[k].ver>> : k \in { k \in DOMAIN cache : k[1] = s } }
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, queriesToClean,
                 cuPhase, cuSnap, cuKeys, cuQueries, cuTargets, pendingReg, ver, ops>>

ClearQuery(v) ==
  /\ ops < MaxOps
  /\ ops' = ops + 1
  /\ queriesToClean' = queriesToClean \cup {v}
  /\ stale' = stale \cup { <<k, cache[k].ver>> : k \in { k \in DOMAIN cache : k[2] = v } }
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
                 registered, closed, cuPhase, cuSnap, cuKeys, cuQueries, cuTargets, pendingReg, ver>>

Clear ==
  /\ ops < MaxOps
  /\ ops' = ops + 1
  /\ cache' = [k \in {} |-> 0] /\ lru' = <<>> /\ uniq' = [v \in {} |-> 0]
  /\ cbCleared' = cbCleared + ram
  /\ ram' = 0 /\ csize' = 0
  /\ stale' = {}
  /\ UNCHANGED <<ccount, cbIns, cbEv, keysToClean, queriesToClean, registered, closed,
                 cuPhase, cuSnap, cuKeys, cuQueries, cuTargets, pendingReg, ver>>

\* cleanUp(): copy + removeAll of the two sets, then snapshot of keys() (:1393-1404)
CleanUpStart ==
  /\ cuPhase = "idle" /\ ops < MaxOps
  /\ ops' = ops + 1
  /\ (keysToClean # {} \/ queriesToClean # {})
  /\ cuPhase' = "run"
  /\ cuKeys' = keysToClean /\ cuQueries' = queriesToClean
  /\ keysToClean' = {} /\ queriesToClean' = {}
  /\ cuSnap' = DOMAIN cache
  /\ cuTargets' = stale
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, registered,
                 closed, stale, pendingReg, ver>>

CleanUpStep ==
  /\ cuPhase = "run" /\ cuSnap # {}
  /\ \E k \in cuSnap :
       /\ cuSnap' = cuSnap \ {k}
       /\ IF k[1] \in cuKeys \/ k[2] \in cuQueries
          THEN LET st == RemoveRec([cache |-> cache, lru |-> lru, uniq |-> uniq, ram |-> ram,
                                    csize |-> csize, ev |-> cbEv], k)
               IN /\ cache' = st.cache /\ lru' = st.lru /\ uniq' = st.uniq
                  /\ ram' = st.ram /\ csize' = st.csize /\ cbEv' = st.ev
                  /\ stale' = { e \in stale : e[1] \in DOMAIN st.cache /\ st.cache[e[1]].ver = e[2] }
          ELSE UNCHANGED <<cache, lru, uniq, ram, csize, cbEv, stale>>
  /\ UNCHANGED <<ccount, cbIns, cbCleared, keysToClean, queriesToClean, registered, closed,
                 cuPhase, cuKeys, cuQueries, cuTargets, pendingReg, ver, ops>>

CleanUpEnd ==
  /\ cuPhase = "run" /\ cuSnap = {}
  /\ cuPhase' = "done"
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
                 queriesToClean, registered, closed, cuSnap, cuKeys, cuQueries, cuTargets,
                 stale, pendingReg, ver, ops>>

CleanUpReset ==
  /\ cuPhase = "done"
  /\ cuPhase' = "idle" /\ cuTargets' = {}
  /\ UNCHANGED <<cache, lru, uniq, ram, csize, ccount, cbIns, cbEv, cbCleared, keysToClean,
                 queriesToClean, registered, closed, cuSnap, cuKeys, cuQueries, stale, pendingReg, ver, ops>>

Next ==
  \/ \E s \in Segs, i \in Insts : Get(s, i)
  \/ \E s \in Segs, i \in Insts, vb \in VBytes : Put(s, i, vb)
  \/ \E s \in Segs : RegisterListener(s) \/ CloseCore(s)
  \/ \E v \in Vals : ClearQuery(v)
  \/ Clear
  \/ CleanUpStart \/ CleanUpStep \/ CleanUpEnd \/ CleanUpReset

Spec == Init /\ [][Next]_vars

(* ---------------- invariants ---------------- *)
\* assertConsistent (:474-535): ramBytesUsed is the exact sum
RamExact == ram = SumE(DOMAIN cache) + SumQ(DOMAIN uniq)
KeysInSync == Range(lru) = DOMAIN cache /\ Len(lru) = Cardinality(DOMAIN cache)
RefCountsExact ==
  /\ DOMAIN uniq = { k[2] : k \in DOMAIN cache }
  /\ \A v \in DOMAIN uniq : uniq[v].rc = Cardinality({ k \in DOMAIN cache : k[2] = v })
\* every entry of a value references the one canonical instance
Canonical == \A k \in DOMAIN cache : cache[k].inst = uniq[k[2]].inst
SizeCounter == csize = Cardinality(DOMAIN cache)
Bounded == ~RequiresEviction([cache |-> cache, lru |-> lru, uniq |-> uniq, ram |-> ram,
                               csize |-> csize, ev |-> cbEv])
\* onCacheEntryInserted - onCacheEntryEvicted sums agree with ramBytesUsed (modulo clear())
CallbackBalance == cbIns - cbEv - cbCleared = ram
EvictionCountNonNeg == ccount >= csize
\* a cleanUp() that started after clearQuery(q)/core close leaves none of those entries behind
CleanUpComplete == cuPhase = "done" => \A e \in cuTargets :
                      ~(e[1] \in DOMAIN cache /\ cache[e[1]].ver = e[2])
=============================================================================

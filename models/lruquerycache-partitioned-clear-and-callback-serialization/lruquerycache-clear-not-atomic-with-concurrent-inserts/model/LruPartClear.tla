----------------------------- MODULE LruPartClear -----------------------------
(***************************************************************************)
(* Lock-level model of LRUQueryCache's subclass-visible callback protocol: *)
(* N partitions with one write lock each (main a9779ef3c193) versus one    *)
(* cache-wide write lock (branch_10_6 4ae946e5a9b), plus one candidate fix *)
(* (clear() takes every partition lock in ascending order).                *)
(*                                                                         *)
(* A subclass keeps an entry count `led` through the insertion / eviction  *)
(* callbacks and resets it in onClear(), the class javadoc's "per-index    *)
(* statistics" pattern. Ledger = "sync": the subclass updates `led`        *)
(* atomically (synchronized); "plain": read-modify-write of a plain field  *)
(* split into a read step (callback entry) and a write step (exit).        *)
(* Every callback has an entry and an exit step, so `inCb` shows overlap.  *)
(* Only entry counts are modelled (bytes follow the same code paths).      *)
(*                                                                         *)
(* file:line map (main = lucene/core/src/java/org/apache/lucene/search/    *)
(* LRUQueryCache.java @ a9779ef3c193; rc = same file @ 4ae946e5a9b):       *)
(*  LockOf / owner     main :937-959 per-partition ReentrantReadWriteLock; *)
(*                     rc :140-142 one lock for the whole cache            *)
(*  PutAcq             main :430-440 routing by query hash, :1100 lock;    *)
(*                     rc :321 writeLock.lock()                            *)
(*  PutIns/PutInsX     main :1118-1127 cache.put + onCacheEntryInserted;   *)
(*                     rc :732-737 LeafCache.putIfAbsent -> onDocIdSetCache*)
(*  PutEvc/PutEvcX     main :1128, :1223-1248 evictIfNecessary ->          *)
(*                     remove :1155-1189 -> onCacheEntryEvicted;           *)
(*                     rc :346-371 evictIfNecessary -> onEviction          *)
(*  PutRel             main :1130 unlock; rc :342                          *)
(*  Clear (main)       :457-463 loop over partitions, each               *)
(*                     LRUQueryCachePartition.clear() :1191-1203 under its *)
(*                     own write lock, THEN onClear() :462 with no lock    *)
(*  Clear (rc)         :415-426 writeLock; cache.clear; onClear() :422     *)
(*                     under the lock                                      *)
(*  Clear (fix)        proposed: lock all partitions ascending, clear all, *)
(*                     onClear(), unlock all                               *)
(*  CuSnap             main :1404 keys() :214-222 -> partition keys()      *)
(*                     :1024-1031 (read lock per partition, one at a time) *)
(*  CuRem/CuRemX/CuRel main :1408-1411 remove(key) :1155-1189: callback    *)
(*                     only if the key is still cached                     *)
(*  The rc variant has no cleanUp(); clearQuery/clearCoreCacheKey remove   *)
(*  synchronously under the write lock (rc :374-403), which CuRem* also    *)
(*  models when WithCleanup is TRUE (one key per locked step).             *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS
  Parts,        \* partition ids, e.g. {0, 1}
  PartOrder,    \* the same ids as a sequence in clear()'s loop order, e.g. <<0, 1>>
  Putters,      \* search threads that cache entries
  NPuts,        \* insertions per putter
  MaxSize,      \* per-partition entry cap (evictIfNecessary)
  Variant,      \* "main" | "rc" | "fix"
  Ledger,       \* "sync" | "plain"
  WithClear,    \* a thread calls clear() once
  WithCleanup   \* a thread runs cleanUp() once, removing every key of its snapshot

None == "none"
Clr == "clr"
Cln == "cln"
Threads == Putters \cup (IF WithClear THEN {Clr} ELSE {}) \cup (IF WithCleanup THEN {Cln} ELSE {})
LockIds == IF Variant = "rc" THEN {0} ELSE Parts
LockOf(p) == IF Variant = "rc" THEN 0 ELSE p
MaxIds == Cardinality(Putters) * NPuts

VARIABLES
  part,      \* partition -> sequence of entry ids, least recently used first
  idPart,    \* entry id -> partition (routing is static per key)
  nid,       \* next entry id
  owner,     \* lock id -> thread holding its write lock, or None
  led,       \* the subclass's entry count
  tmp,       \* thread -> value read at callback entry (plain ledger)
  inCb,      \* threads currently inside a callback
  pc, cnt, tp, ci, snap, rem

vars == <<part, idPart, nid, owner, led, tmp, inCb, pc, cnt, tp, ci, snap, rem>>

Init ==
  /\ part = [p \in Parts |-> <<>>]
  /\ idPart = [i \in {} |-> 0]
  /\ nid = 0
  /\ owner = [l \in LockIds |-> None]
  /\ led = 0
  /\ tmp = [t \in Threads |-> 0]
  /\ inCb = {}
  /\ pc = [t \in Threads |-> IF t \in Putters THEN "idle"
                             ELSE IF t = Clr THEN "c_acq" ELSE "s_snap"]
  /\ cnt = [t \in Threads |-> 0]
  /\ tp = [t \in Threads |-> 0]
  /\ ci = [t \in Threads |-> 1]
  /\ snap = {}
  /\ rem = 0

Range(s) == { s[i] : i \in DOMAIN s }
RECURSIVE SumLen(_)
SumLen(S) == IF S = {} THEN 0 ELSE LET p == CHOOSE p \in S : TRUE IN Len(part[p]) + SumLen(S \ {p})
Total == SumLen(Parts)
Free(l) == owner[l] = None
CbEnter(t) == /\ inCb' = inCb \cup {t} /\ tmp' = [tmp EXCEPT ![t] = led]
CbExit(t, delta) ==
  /\ inCb' = inCb \ {t}
  /\ led' = IF Ledger = "sync" THEN led + delta ELSE tmp[t] + delta

(* ---------------- putters: putIfAbsent of a new key ---------------- *)
PutAcq(t) ==
  /\ pc[t] = "idle" /\ cnt[t] < NPuts
  /\ \E p \in Parts :
       /\ Free(LockOf(p))
       /\ owner' = [owner EXCEPT ![LockOf(p)] = t]
       /\ tp' = [tp EXCEPT ![t] = p]
  /\ pc' = [pc EXCEPT ![t] = "ins"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, ci, snap, rem>>

PutIns(t) ==
  /\ pc[t] = "ins"
  /\ part' = [part EXCEPT ![tp[t]] = Append(@, nid)]
  /\ idPart' = [i \in DOMAIN idPart \cup {nid} |-> IF i = nid THEN tp[t] ELSE idPart[i]]
  /\ nid' = nid + 1
  /\ CbEnter(t)
  /\ pc' = [pc EXCEPT ![t] = "insX"]
  /\ UNCHANGED <<owner, led, cnt, tp, ci, snap, rem>>

PutInsX(t) ==
  /\ pc[t] = "insX"
  /\ CbExit(t, 1)
  /\ pc' = [pc EXCEPT ![t] = "evc"]
  /\ UNCHANGED <<part, idPart, nid, owner, tmp, cnt, tp, ci, snap, rem>>

PutEvc(t) ==
  /\ pc[t] = "evc"
  /\ IF Len(part[tp[t]]) > MaxSize
     THEN /\ part' = [part EXCEPT ![tp[t]] = Tail(@)]
          /\ CbEnter(t)
          /\ pc' = [pc EXCEPT ![t] = "evcX"]
     ELSE /\ pc' = [pc EXCEPT ![t] = "rel"]
          /\ UNCHANGED <<part, tmp, inCb>>
  /\ UNCHANGED <<idPart, nid, owner, led, cnt, tp, ci, snap, rem>>

PutEvcX(t) ==
  /\ pc[t] = "evcX"
  /\ CbExit(t, -1)
  /\ pc' = [pc EXCEPT ![t] = "evc"]
  /\ UNCHANGED <<part, idPart, nid, owner, tmp, cnt, tp, ci, snap, rem>>

PutRel(t) ==
  /\ pc[t] = "rel"
  /\ owner' = [owner EXCEPT ![LockOf(tp[t])] = None]
  /\ cnt' = [cnt EXCEPT ![t] = @ + 1]
  /\ pc' = [pc EXCEPT ![t] = IF cnt[t] + 1 < NPuts THEN "idle" ELSE "done"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, tp, ci, snap, rem>>

(* ---------------- clear() ---------------- *)
\* main: per partition lock, clear, unlock; afterwards onClear() with no lock
ClrAcqMain ==
  /\ Variant = "main" /\ pc[Clr] = "c_acq"
  /\ LET p == PartOrder[ci[Clr]] IN
       /\ Free(p)
       /\ owner' = [owner EXCEPT ![p] = Clr]
  /\ pc' = [pc EXCEPT ![Clr] = "c_clr"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, tp, ci, snap, rem>>

ClrPartMain ==
  /\ Variant = "main" /\ pc[Clr] = "c_clr"
  /\ LET p == PartOrder[ci[Clr]] IN
       /\ part' = [part EXCEPT ![p] = <<>>]
       /\ owner' = [owner EXCEPT ![p] = None]
  /\ ci' = [ci EXCEPT ![Clr] = @ + 1]
  /\ pc' = [pc EXCEPT ![Clr] = IF ci[Clr] + 1 > Len(PartOrder) THEN "onclr" ELSE "c_acq"]
  /\ UNCHANGED <<idPart, nid, led, tmp, inCb, cnt, tp, snap, rem>>

\* rc: one lock; fix: every partition lock in ascending order (same order for all)
ClrAcqAll ==
  /\ Variant \in {"rc", "fix"} /\ pc[Clr] = "c_acq"
  /\ LET l == IF Variant = "rc" THEN 0 ELSE PartOrder[ci[Clr]] IN
       /\ Free(l)
       /\ owner' = [owner EXCEPT ![l] = Clr]
  /\ ci' = [ci EXCEPT ![Clr] = @ + 1]
  /\ pc' = [pc EXCEPT ![Clr] =
              IF Variant = "rc" \/ ci[Clr] + 1 > Len(PartOrder) THEN "c_clr" ELSE "c_acq"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, tp, snap, rem>>

ClrAllParts ==
  /\ Variant \in {"rc", "fix"} /\ pc[Clr] = "c_clr"
  /\ part' = [p \in Parts |-> <<>>]
  /\ pc' = [pc EXCEPT ![Clr] = "onclr"]
  /\ UNCHANGED <<idPart, nid, owner, led, tmp, inCb, cnt, tp, ci, snap, rem>>

OnClear ==
  /\ pc[Clr] = "onclr"
  /\ inCb' = inCb \cup {Clr}
  /\ pc' = [pc EXCEPT ![Clr] = "onclrX"]
  /\ UNCHANGED <<part, idPart, nid, owner, led, tmp, cnt, tp, ci, snap, rem>>

OnClearX ==
  /\ pc[Clr] = "onclrX"
  /\ inCb' = inCb \ {Clr}
  /\ led' = 0
  /\ pc' = [pc EXCEPT ![Clr] = IF Variant = "main" THEN "done" ELSE "c_rel"]
  /\ UNCHANGED <<part, idPart, nid, owner, tmp, cnt, tp, ci, snap, rem>>

ClrRelAll ==
  /\ pc[Clr] = "c_rel"
  /\ owner' = [l \in LockIds |-> IF owner[l] = Clr THEN None ELSE owner[l]]
  /\ pc' = [pc EXCEPT ![Clr] = "done"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, tp, ci, snap, rem>>

(* ---------------- cleanUp(): snapshot, then remove(key) per key ---------------- *)
CuSnap ==
  /\ pc[Cln] = "s_snap"
  /\ LET p == PartOrder[ci[Cln]] IN
       /\ Free(LockOf(p))          \* partition keys() under the read lock
       /\ snap' = snap \cup Range(part[p])
  /\ ci' = [ci EXCEPT ![Cln] = @ + 1]
  /\ pc' = [pc EXCEPT ![Cln] = IF ci[Cln] + 1 > Len(PartOrder) THEN "s_pick" ELSE "s_snap"]
  /\ UNCHANGED <<part, idPart, nid, owner, led, tmp, inCb, cnt, tp, rem>>

CuPick ==
  /\ pc[Cln] = "s_pick"
  /\ IF snap = {}
     THEN /\ pc' = [pc EXCEPT ![Cln] = "done"] /\ UNCHANGED <<snap, rem>>
     ELSE \E i \in snap :
            /\ snap' = snap \ {i}
            /\ rem' = i
            /\ pc' = [pc EXCEPT ![Cln] = "s_acq"]
  /\ UNCHANGED <<part, idPart, nid, owner, led, tmp, inCb, cnt, tp, ci>>

CuAcq ==
  /\ pc[Cln] = "s_acq"
  /\ Free(LockOf(idPart[rem]))
  /\ owner' = [owner EXCEPT ![LockOf(idPart[rem])] = Cln]
  /\ pc' = [pc EXCEPT ![Cln] = "s_rem"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, tp, ci, snap, rem>>

CuRem ==
  /\ pc[Cln] = "s_rem"
  /\ LET p == idPart[rem] IN
     IF rem \in Range(part[p])
     THEN /\ part' = [part EXCEPT ![p] = SelectSeq(@, LAMBDA x : x # rem)]
          /\ CbEnter(Cln)
          /\ pc' = [pc EXCEPT ![Cln] = "s_remX"]
     ELSE /\ pc' = [pc EXCEPT ![Cln] = "s_rel"]
          /\ UNCHANGED <<part, tmp, inCb>>
  /\ UNCHANGED <<idPart, nid, owner, led, cnt, tp, ci, snap, rem>>

CuRemX ==
  /\ pc[Cln] = "s_remX"
  /\ CbExit(Cln, -1)
  /\ pc' = [pc EXCEPT ![Cln] = "s_rel"]
  /\ UNCHANGED <<part, idPart, nid, owner, tmp, cnt, tp, ci, snap, rem>>

CuRel ==
  /\ pc[Cln] = "s_rel"
  /\ owner' = [owner EXCEPT ![LockOf(idPart[rem])] = None]
  /\ pc' = [pc EXCEPT ![Cln] = "s_pick"]
  /\ UNCHANGED <<part, idPart, nid, led, tmp, inCb, cnt, tp, ci, snap, rem>>

AllDone == \A t \in Threads : pc[t] = "done"

Next ==
  \/ \E t \in Putters : PutAcq(t) \/ PutIns(t) \/ PutInsX(t) \/ PutEvc(t) \/ PutEvcX(t) \/ PutRel(t)
  \/ (WithClear /\ (ClrAcqMain \/ ClrPartMain \/ ClrAcqAll \/ ClrAllParts \/ OnClear \/ OnClearX \/ ClrRelAll))
  \/ (WithCleanup /\ (CuSnap \/ CuPick \/ CuAcq \/ CuRem \/ CuRemX \/ CuRel))
  \/ (AllDone /\ UNCHANGED vars)   \* termination is not a deadlock

Spec == Init /\ [][Next]_vars

(* ---------------- properties ---------------- *)
\* At quiescence, the subclass's count equals getCacheSize() (sum of partition sizes).
StatsConsistentAtQuiescence == AllDone => led = Total
\* The subclass-visible callbacks never overlap (10.x behaviour; javadoc "under a lock").
CallbacksSerialized == Cardinality(inCb) <= 1
\* The cache's own bound holds whenever no writer is inside the partition.
PartitionBound == \A p \in Parts : Free(LockOf(p)) => Len(part[p]) <= MaxSize
\* Vacuity probe: must be VIOLATED (quiescent states are reachable).
NeverQuiescent == ~AllDone
TypeOK == /\ led \in -MaxIds..MaxIds+1 /\ nid \in 0..MaxIds
=============================================================================

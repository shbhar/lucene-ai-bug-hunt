---------------------------- MODULE TaxoWriterCache ----------------------------
(***************************************************************************)
(* Model of DirectoryTaxonomyWriter.addCategory, its TaxonomyWriterCache    *)
(* (LruTaxonomyWriterCache / NameIntCacheLRU), the lazily opened reader     *)
(* manager, cacheIsComplete, perhapsFillCache and the lazily initialised    *)
(* parent array, with concurrent lock-free cache reads and getParent.       *)
(*                                                                         *)
(* Lucene main @ ea57360912ed. Map from model element to Java line         *)
(* (D = lucene/facet/src/java/org/apache/lucene/facet/taxonomy/directory/  *)
(*  DirectoryTaxonomyWriter.java, W = .../taxonomy/writercache/):          *)
(*   FastGet   D:381-386  ensureOpen; cache.get outside the monitor        *)
(*   SlowStart D:388      synchronized (this)                              *)
(*   FC0       D:323-329  findCategory: cache.get; return if hit/complete  *)
(*   FCFill    D:331,339  cacheMisses++; perhapsFillCache D:604-663        *)
(*   FC1       D:340-345  second cache.get                                 *)
(*   FCDisk    D:351-373  initReaderManager + term lookup in NRT reader    *)
(*   FCPut     D:374-376  addToCache(disk hit) D:484-494                   *)
(*   stk/mode  D:411-429  internalAddCategory recursion over ancestors     *)
(*   ACD1      D:452-472  indexWriter.addDocument; nextID.getAndIncrement; *)
(*                        shouldRefreshReaderManager = true                *)
(*   ACD2      D:475      getTaxoArrays() D:665-684 + TaxonomyIndexArrays  *)
(*                        .add (TaxonomyIndexArrays.java:230-241)          *)
(*   ACD3      D:479      addToCache D:484-494: put; on eviction           *)
(*                        refreshReaderManager D:496-508; then             *)
(*                        cacheIsComplete = false                          *)
(*   InitRM    D:246-258  initReaderManager: opens an NRT reader (I/O)     *)
(*   Refresh   D:504-507  readerManager.maybeRefresh (I/O)                 *)
(*   GPL1/GP2  D:687-698  getParent: checkIndex(ord, nextID) then read     *)
(*                        taxoArrays (no monitor unless taxoArrays==null)  *)
(*   LRU       W/LruTaxonomyWriterCache.java:95-118 (synchronized get/put),*)
(*             W/NameIntCacheLRU.java:53-126 (access-ordered LinkedHashMap,*)
(*             put returns size>max, makeRoomLRU removes                   *)
(*             size - floor(2*max/3) eldest entries)                       *)
(*   Reject    core/.../index/DocumentsWriterPerThread.java:281-307: a     *)
(*             non-aborting failure inside addDocument still consumes a    *)
(*             docID and marks the document deleted                        *)
(*   I/O failure: core/.../index/IndexWriter.java getReader: an exception  *)
(*             other than an Error while opening the NRT reader is         *)
(*             rethrown without closing the IndexWriter (not tragic)       *)
(*                                                                         *)
(* Abstractions: the IndexWriter is a sequence of documents (docID order); *)
(* an NRT reader is a prefix length (all docs present when it was opened); *)
(* deleted (rejected) documents are invisible to lookups and fill; a disk  *)
(* lookup returns the first live doc with the label. Labels are flat       *)
(* strings with a parent map. One thread holds the monitor at a time.      *)
(***************************************************************************)
EXTENDS Naturals, Integers, Sequences, FiniteSets, TLC

CONSTANTS
  Threads,          \* e.g. {"t1","t2"}
  CacheMax,         \* LruTaxonomyWriterCache(cacheSize); 1000 = effectively unbounded
  MissesUntilFill,  \* setCacheMissesUntilFill (default 11)
  ExistingKind,     \* "new" = new taxonomy (OpenMode.CREATE); "ac" = existing taxonomy {a, c}; "aab" = {a, a/b}
  MaxOps,           \* operations per thread
  MaxFail,          \* injected I/O failures (initReaderManager / maybeRefresh)
  MaxReject,        \* rejected ordinal documents (non-aborting addDocument failure)
  AddLabels,        \* labels the threads add, subset of DOMAIN Par
  WithGPL,          \* also run getParent(getSize()-1)
  Mutant            \* "none" | "noCompleteReset" | "noRefresh" | "cacheBeforeArrays" | "fixC" | "fixFull" | "fixFlag"

Root == "r"
Existing == CASE ExistingKind = "new" -> <<>> [] ExistingKind = "ac" -> <<"a", "c">> [] ExistingKind = "aab" -> <<"a", "ab">>
Par == [a |-> "r", ab |-> "a", c |-> "r", cd |-> "c"]
TermOrder == <<"r", "a", "ab", "c", "cd">>   \* sorted order of the encoded paths
MaxOrd == 9
UNSET == -9
NONE == "none"

VARIABLES
  docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill,
  arrNull, arr, fails, rejects, stale,
  lock, pc, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results

shared == <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill,
            arrNull, arr, fails, rejects, stale>>
tvars == <<lock, pc, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
vars == <<shared, tvars>>

-----------------------------------------------------------------------------
(* helpers *)
Ord(i) == i - 1                    \* docs index (1-based) -> ordinal
LiveIdx(l, s) == {i \in 1..s : docs[i].lab = l /\ ~docs[i].del}
\* first ordinal of label l among the first s docs, or -1
FirstBelow(l, s) == IF LiveIdx(l, s) = {} THEN -1
                    ELSE Ord(CHOOSE i \in LiveIdx(l, s) : \A j \in LiveIdx(l, s) : i <= j)
FirstDoc(l) == FirstBelow(l, Len(docs))

CIdx(c, l) == IF \E i \in 1..Len(c) : c[i][1] = l
              THEN CHOOSE i \in 1..Len(c) : c[i][1] = l ELSE 0
RemoveAt(s, i) == [j \in 1..(Len(s) - 1) |-> IF j < i THEN s[j] ELSE s[j + 1]]
Touch(c, i) == Append(RemoveAt(c, i), c[i])
PutRaw(c, l, o) == IF CIdx(c, l) > 0 THEN Append(RemoveAt(c, CIdx(c, l)), <<l, o>>)
                   ELSE Append(c, <<l, o>>)
\* NameIntCacheLRU.makeRoomLRU: remove size - floor(2*max/3) eldest entries
EvictLRU(c) == LET n == Len(c) - ((2 * CacheMax) \div 3)
               IN SubSeq(c, n + 1, Len(c))
InCache(l) == CIdx(cache, l) > 0

ArraysFrom(s) == [o \in 0..MaxOrd |-> IF o < s /\ o < Len(docs) /\ ~docs[o + 1].del
                                      THEN docs[o + 1].par ELSE UNSET]

\* perhapsFillCache: put terms of the reader snapshot in term order while !isFull
RECURSIVE FillRec(_, _)
FillRec(c, ts) == IF ts = <<>> THEN <<c, FALSE>>
                  ELSE IF Len(c) = CacheMax THEN <<c, TRUE>>
                  ELSE FillRec(PutRaw(c, Head(ts)[1], Head(ts)[2]), Tail(ts))
FillTerms(s) == LET ls == SelectSeq(TermOrder, LAMBDA l : FirstBelow(l, s) >= 0)
                IN [k \in 1..Len(ls) |-> <<ls[k], FirstBelow(ls[k], s)>>]

-----------------------------------------------------------------------------
RECURSIVE ExistingDocs(_, _)
ExistingDocs(ds, ls) ==
  IF ls = <<>> THEN ds
  ELSE LET l == Head(ls)
           p == CHOOSE i \in 1..Len(ds) : ds[i].lab = Par[l]
       IN ExistingDocs(Append(ds, [lab |-> l, par |-> p - 1, del |-> FALSE]), Tail(ls))

RootDoc == [lab |-> Root, par |-> -1, del |-> FALSE]

Init ==
  /\ IF Existing = <<>>
     THEN \* constructor with OpenMode.CREATE: addCategory(root) D:181-185
          /\ docs = <<RootDoc>>
          /\ nextID = 1
          /\ cache = <<<<Root, 0>>>>
          /\ complete = TRUE
          /\ rmInit = TRUE /\ rmSnap = 1 /\ shouldRefresh = FALSE
          /\ arrNull = FALSE /\ arr = [o \in 0..MaxOrd |-> IF o = 0 THEN -1 ELSE UNSET]
     ELSE \* existing taxonomy: D:186-194
          /\ docs = ExistingDocs(<<RootDoc>>, Existing)
          /\ nextID = Len(ExistingDocs(<<RootDoc>>, Existing))
          /\ cache = <<>>
          /\ complete = FALSE
          /\ rmInit = FALSE /\ rmSnap = 0 /\ shouldRefresh = FALSE
          /\ arrNull = TRUE /\ arr = [o \in 0..MaxOrd |-> UNSET]
  /\ misses = 0 /\ shouldFill = TRUE /\ fails = 0 /\ rejects = 0 /\ stale = FALSE
  /\ lock = NONE
  /\ pc = [t \in Threads |-> "idle"]
  /\ lab = [t \in Threads |-> Root]
  /\ stk = [t \in Threads |-> <<>>]
  /\ tgt = [t \in Threads |-> Root]
  /\ mode = [t \in Threads |-> "top"]
  /\ pord = [t \in Threads |-> 0]
  /\ cid = [t \in Threads |-> 0]
  /\ fres = [t \in Threads |-> 0]
  /\ gord = [t \in Threads |-> 0]
  /\ ops = [t \in Threads |-> 0]
  /\ results = {}

-----------------------------------------------------------------------------
(* An operation fails with an exception: unwind, release the monitor. *)
FailOp(t) ==
  /\ lock' = NONE
  /\ pc' = [pc EXCEPT ![t] = "idle"]
  /\ stk' = [stk EXCEPT ![t] = <<>>]
  /\ results' = results \cup {[k |-> "exc", t |-> t, lab |-> lab[t]]}
  /\ UNCHANGED <<lab, tgt, mode, pord, cid, fres, gord, ops>>

(* addCategory returns r from the slow path *)
ReturnAdd(t, r) ==
  /\ lock' = NONE
  /\ pc' = [pc EXCEPT ![t] = "gp2"]
  /\ gord' = [gord EXCEPT ![t] = r]
  /\ stk' = [stk EXCEPT ![t] = <<>>]
  /\ results' = results \cup {[k |-> "add", t |-> t, lab |-> lab[t], ord |-> r]}
  /\ UNCHANGED <<lab, tgt, mode, pord, cid, fres, ops>>

(* findCategory(tgt[t]) returned r: continue addCategory / internalAddCategory *)
FoundRes(t, r) ==
  IF mode[t] = "top" THEN
    IF r >= 0 THEN ReturnAdd(t, r)
    ELSE \* internalAddCategory(lab) D:397
      /\ stk' = [stk EXCEPT ![t] = <<lab[t]>>]
      /\ lock' = lock /\ UNCHANGED <<lab, cid, fres, gord, ops, results>>
      /\ IF Par[lab[t]] = Root
         THEN /\ pord' = [pord EXCEPT ![t] = 0] /\ pc' = [pc EXCEPT ![t] = "acd1"]
              /\ UNCHANGED <<tgt, mode>>
         ELSE /\ tgt' = [tgt EXCEPT ![t] = Par[lab[t]]] /\ mode' = [mode EXCEPT ![t] = "anc"]
              /\ pc' = [pc EXCEPT ![t] = "fc0"] /\ UNCHANGED pord
  ELSE \* findCategory(parentPath) inside internalAddCategory D:419-421
    IF r >= 0 THEN
      /\ pord' = [pord EXCEPT ![t] = r] /\ pc' = [pc EXCEPT ![t] = "acd1"]
      /\ lock' = lock /\ UNCHANGED <<lab, stk, tgt, mode, cid, fres, gord, ops, results>>
    ELSE
      /\ stk' = [stk EXCEPT ![t] = Append(stk[t], tgt[t])]
      /\ lock' = lock /\ UNCHANGED <<lab, mode, cid, fres, gord, ops, results>>
      /\ IF Par[tgt[t]] = Root
         THEN /\ pord' = [pord EXCEPT ![t] = 0] /\ pc' = [pc EXCEPT ![t] = "acd1"]
              /\ UNCHANGED tgt
         ELSE /\ tgt' = [tgt EXCEPT ![t] = Par[tgt[t]]] /\ pc' = [pc EXCEPT ![t] = "fc0"]
              /\ UNCHANGED pord

-----------------------------------------------------------------------------
(* addCategory: lock-free cache read D:385 *)
FastGet(t) ==
  /\ pc[t] = "idle" /\ ops[t] < MaxOps
  /\ \E l \in AddLabels :
       /\ ops' = [ops EXCEPT ![t] = ops[t] + 1]
       /\ lab' = [lab EXCEPT ![t] = l]
       /\ IF InCache(l)
          THEN /\ cache' = Touch(cache, CIdx(cache, l))
               /\ results' = results \cup {[k |-> "add", t |-> t, lab |-> l, ord |-> cache[CIdx(cache, l)][2]]}
               /\ pc' = [pc EXCEPT ![t] = "gp2"]
               /\ gord' = [gord EXCEPT ![t] = cache[CIdx(cache, l)][2]]
          ELSE /\ pc' = [pc EXCEPT ![t] = "wait"] /\ UNCHANGED <<cache, results, gord>>
  /\ UNCHANGED <<docs, nextID, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill,
                 arrNull, arr, fails, rejects, lock, stk, tgt, mode, pord, cid, fres, stale>>

SlowStart(t) ==
  /\ pc[t] = "wait" /\ lock = NONE
  /\ lock' = t
  /\ tgt' = [tgt EXCEPT ![t] = lab[t]] /\ mode' = [mode EXCEPT ![t] = "top"]
  /\ stk' = [stk EXCEPT ![t] = <<>>] /\ pc' = [pc EXCEPT ![t] = "fc0"]
  /\ UNCHANGED <<shared, lab, pord, cid, fres, gord, ops, results>>

FC0(t) ==
  /\ pc[t] = "fc0"
  /\ IF InCache(tgt[t])
     THEN /\ cache' = Touch(cache, CIdx(cache, tgt[t]))
          /\ FoundRes(t, cache[CIdx(cache, tgt[t])][2])
          /\ UNCHANGED <<docs, nextID, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
     ELSE IF complete
     THEN /\ FoundRes(t, -1) /\ UNCHANGED shared
     ELSE /\ misses' = misses + 1 /\ pc' = [pc EXCEPT ![t] = IF Mutant = "fixFlag" THEN "fcref" ELSE "fcfill"]
          /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, shouldFill, arrNull, arr, fails, rejects, stale>>
          /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>

\* fixFlag variant only: findCategory refreshes the reader before the fill and the disk lookup when
\* the reader may miss categories that are not in the cache (a refresh or an add failed part-way)
FCRef(t) ==
  /\ pc[t] = "fcref"
  /\ IF stale /\ rmInit /\ shouldRefresh
     THEN \/ /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE /\ stale' = FALSE
             /\ pc' = [pc EXCEPT ![t] = "fcfill"]
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, misses, shouldFill, arrNull, arr, fails, rejects>>
             /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
          \/ /\ fails < MaxFail /\ fails' = fails + 1 /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects, stale>>
     ELSE /\ stale' = FALSE /\ pc' = [pc EXCEPT ![t] = "fcfill"]
          /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects>>
          /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>

\* perhapsFillCache D:604-663
FCFill(t) ==
  /\ pc[t] = "fcfill"
  /\ IF misses < MissesUntilFill \/ ~shouldFill
     THEN /\ pc' = [pc EXCEPT ![t] = "fc1"] /\ UNCHANGED shared
          /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
     ELSE IF Mutant = "fixFull" /\ rmInit /\ shouldRefresh
     THEN \* fix variant: refresh a stale reader before filling the cache from it
          \/ /\ fails < MaxFail /\ fails' = fails + 1 /\ shouldFill' = FALSE /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, arrNull, arr, rejects, stale>>
          \/ /\ LET s == Len(docs)
                    fr == FillRec(cache, FillTerms(s))
                IN /\ cache' = fr[1]
                   /\ complete' = ~fr[2]
                   /\ rmInit' = fr[2]
                   /\ rmSnap' = s
             /\ shouldRefresh' = FALSE /\ shouldFill' = FALSE
             /\ pc' = [pc EXCEPT ![t] = "fc1"]
             /\ UNCHANGED <<docs, nextID, misses, arrNull, arr, fails, rejects, stale>>
             /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
     ELSE IF ~rmInit /\ fails < MaxFail
     THEN \/ \* initReaderManager throws (shouldFillCache already false)
             /\ shouldFill' = FALSE /\ fails' = fails + 1 /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, arrNull, arr, rejects, stale>>
          \/ /\ LET s == Len(docs)
                    fr == FillRec(cache, FillTerms(s))
                IN /\ cache' = fr[1]
                   /\ complete' = ~fr[2]
                   /\ rmInit' = fr[2]          \* closed when complete D:653-661
                   /\ rmSnap' = s
             /\ shouldRefresh' = FALSE /\ shouldFill' = FALSE
             /\ pc' = [pc EXCEPT ![t] = "fc1"]
             /\ UNCHANGED <<docs, nextID, misses, arrNull, arr, fails, rejects, stale>>
             /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
     ELSE /\ LET s == IF rmInit THEN rmSnap ELSE Len(docs)
                 fr == FillRec(cache, FillTerms(s))
             IN /\ cache' = fr[1]
                /\ complete' = ~fr[2]
                /\ rmInit' = fr[2]
                /\ rmSnap' = s
                /\ shouldRefresh' = IF rmInit THEN shouldRefresh ELSE FALSE
          /\ shouldFill' = FALSE
          /\ pc' = [pc EXCEPT ![t] = "fc1"]
          /\ UNCHANGED <<docs, nextID, misses, arrNull, arr, fails, rejects, stale>>
          /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>

FC1(t) ==
  /\ pc[t] = "fc1"
  /\ IF InCache(tgt[t])
     THEN /\ cache' = Touch(cache, CIdx(cache, tgt[t]))
          /\ FoundRes(t, cache[CIdx(cache, tgt[t])][2])
          /\ UNCHANGED <<docs, nextID, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
     ELSE IF complete
     THEN /\ FoundRes(t, -1) /\ UNCHANGED shared
     ELSE /\ pc' = [pc EXCEPT ![t] = "fcdisk"] /\ UNCHANGED shared
          /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>

\* term lookup on the (possibly just opened) NRT reader D:351-377
DiskResult(t, s) ==
  LET d == FirstBelow(tgt[t], s) IN
  IF d > 0 THEN /\ fres' = [fres EXCEPT ![t] = d] /\ pc' = [pc EXCEPT ![t] = "fcput"]
                /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, gord, ops, results>>
  ELSE /\ FoundRes(t, d) /\ UNCHANGED fres

FCDisk(t) ==
  /\ pc[t] = "fcdisk"
  /\ IF rmInit /\ shouldRefresh /\ Mutant = "fixFull"
     THEN \* fix variant: refresh a stale reader before the disk lookup
          \/ /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE
             /\ DiskResult(t, Len(docs))
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
          \/ /\ fails < MaxFail /\ fails' = fails + 1 /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects, stale>>
     ELSE IF rmInit
     THEN /\ DiskResult(t, rmSnap) /\ UNCHANGED shared
     ELSE \/ /\ rmInit' = TRUE /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE
             /\ DiskResult(t, Len(docs))
             /\ UNCHANGED <<docs, nextID, cache, complete, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
          \/ /\ fails < MaxFail /\ fails' = fails + 1 /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects, stale>>

\* addToCache(categoryPath, doc) of a disk hit D:374-376, 484-494
FCPut(t) ==
  /\ pc[t] = "fcput"
  /\ LET c1 == PutRaw(cache, tgt[t], fres[t])
         ev == Len(c1) > CacheMax
     IN IF ~ev
        THEN /\ cache' = c1 /\ FoundRes(t, fres[t])
             /\ UNCHANGED <<docs, nextID, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
        ELSE IF shouldRefresh /\ rmInit /\ Mutant # "noRefresh"
        THEN \/ /\ cache' = EvictLRU(c1) /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE
                /\ complete' = IF Mutant = "noCompleteReset" THEN complete ELSE FALSE
                /\ FoundRes(t, fres[t])
                /\ stale' = IF Mutant = "fixFlag" THEN FALSE ELSE stale
                /\ UNCHANGED <<docs, nextID, rmInit, misses, shouldFill, arrNull, arr, fails, rejects>>
             \/ /\ fails < MaxFail /\ fails' = fails + 1
                /\ cache' = EvictLRU(c1)
                /\ complete' = IF Mutant \in {"fixC", "fixFull", "fixFlag"} THEN FALSE ELSE complete
                /\ FailOp(t)
                /\ stale' = IF Mutant = "fixFlag" THEN TRUE ELSE stale
                /\ UNCHANGED <<docs, nextID, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects>>
        ELSE /\ cache' = EvictLRU(c1)
             /\ complete' = IF Mutant = "noCompleteReset" THEN complete ELSE FALSE
             /\ FoundRes(t, fres[t])
             /\ stale' = IF Mutant = "fixFlag" THEN FALSE ELSE stale
             /\ UNCHANGED <<docs, nextID, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects>>

-----------------------------------------------------------------------------
(* addCategoryDocument D:452-482 for the label on top of stk *)
Cur(t) == stk[t][Len(stk[t])]

ACD1(t) ==
  /\ pc[t] = "acd1"
  /\ \/ /\ docs' = Append(docs, [lab |-> Cur(t), par |-> pord[t], del |-> FALSE])
        /\ cid' = [cid EXCEPT ![t] = nextID]
        /\ nextID' = nextID + 1
        /\ shouldRefresh' = TRUE
        /\ pc' = [pc EXCEPT ![t] = IF Mutant = "cacheBeforeArrays" THEN "acd3" ELSE "acd2"]
        /\ UNCHANGED <<cache, complete, rmInit, rmSnap, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
        /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, fres, gord, ops, results>>
     \/ \* non-aborting rejection: doc gets a docID, is marked deleted, exception
        /\ rejects < MaxReject /\ rejects' = rejects + 1
        /\ docs' = Append(docs, [lab |-> Cur(t), par |-> pord[t], del |-> TRUE])
        /\ FailOp(t)
        /\ UNCHANGED <<nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, stale>>

\* after ACD2/ACD3 (whichever is last), continue with the child or return
AfterDoc(t) ==
  LET rest == SubSeq(stk[t], 1, Len(stk[t]) - 1) IN
  IF rest = <<>> THEN ReturnAdd(t, cid[t])
  ELSE /\ stk' = [stk EXCEPT ![t] = rest] /\ pord' = [pord EXCEPT ![t] = cid[t]]
       /\ pc' = [pc EXCEPT ![t] = "acd1"]
       /\ lock' = lock /\ UNCHANGED <<lab, tgt, mode, cid, fres, gord, ops, results>>

\* getTaxoArrays().add(id, parent) D:475
ACD2(t) ==
  /\ pc[t] = "acd2"
  /\ IF ~arrNull
     THEN /\ arr' = [arr EXCEPT ![cid[t]] = pord[t]]
          /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, fails, rejects, stale>>
          /\ IF Mutant = "cacheBeforeArrays" THEN AfterDoc(t)
             ELSE /\ pc' = [pc EXCEPT ![t] = "acd3"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
     ELSE IF rmInit
     THEN /\ arr' = [ArraysFrom(rmSnap) EXCEPT ![cid[t]] = pord[t]] /\ arrNull' = FALSE
          /\ UNCHANGED <<docs, nextID, cache, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, fails, rejects, stale>>
          /\ IF Mutant = "cacheBeforeArrays" THEN AfterDoc(t)
             ELSE /\ pc' = [pc EXCEPT ![t] = "acd3"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
     ELSE \/ \* initReaderManager opens a fresh NRT reader (sees the new doc)
             /\ rmInit' = TRUE /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE
             /\ arr' = [ArraysFrom(Len(docs)) EXCEPT ![cid[t]] = pord[t]] /\ arrNull' = FALSE
             /\ UNCHANGED <<docs, nextID, cache, complete, misses, shouldFill, fails, rejects, stale>>
             /\ IF Mutant = "cacheBeforeArrays" THEN AfterDoc(t)
                ELSE /\ pc' = [pc EXCEPT ![t] = "acd3"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
          \/ /\ fails < MaxFail /\ fails' = fails + 1
             /\ IF Mutant \in {"fixC", "fixFull", "fixFlag"} THEN complete' = FALSE ELSE UNCHANGED complete
             /\ stale' = IF Mutant = "fixFlag" THEN TRUE ELSE stale
             /\ FailOp(t)
             /\ UNCHANGED <<docs, nextID, cache, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects>>

\* addToCache(categoryPath, id) D:479, 484-494
ACD3(t) ==
  /\ pc[t] = "acd3"
  /\ LET c1 == PutRaw(cache, Cur(t), cid[t])
         ev == Len(c1) > CacheMax
         next == IF Mutant = "cacheBeforeArrays" THEN "acd2" ELSE "x"
     IN IF ~ev
        THEN /\ cache' = c1
             /\ UNCHANGED <<docs, nextID, complete, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects, stale>>
             /\ IF next = "acd2" THEN /\ pc' = [pc EXCEPT ![t] = "acd2"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
                ELSE AfterDoc(t)
        ELSE IF shouldRefresh /\ rmInit /\ Mutant # "noRefresh"
        THEN \/ /\ cache' = EvictLRU(c1) /\ rmSnap' = Len(docs) /\ shouldRefresh' = FALSE
                /\ complete' = IF Mutant = "noCompleteReset" THEN complete ELSE FALSE
                /\ stale' = IF Mutant = "fixFlag" THEN FALSE ELSE stale
                /\ UNCHANGED <<docs, nextID, rmInit, misses, shouldFill, arrNull, arr, fails, rejects>>
                /\ IF next = "acd2" THEN /\ pc' = [pc EXCEPT ![t] = "acd2"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
                   ELSE AfterDoc(t)
             \/ /\ fails < MaxFail /\ fails' = fails + 1
                /\ cache' = EvictLRU(c1)
                /\ complete' = IF Mutant \in {"fixC", "fixFull", "fixFlag"} THEN FALSE ELSE complete
                /\ FailOp(t)
                /\ stale' = IF Mutant = "fixFlag" THEN TRUE ELSE stale
                /\ UNCHANGED <<docs, nextID, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, rejects>>
        ELSE /\ cache' = EvictLRU(c1)
             /\ complete' = IF Mutant = "noCompleteReset" THEN complete ELSE FALSE
             /\ stale' = IF Mutant = "fixFlag" THEN FALSE ELSE stale
             /\ UNCHANGED <<docs, nextID, rmInit, rmSnap, shouldRefresh, misses, shouldFill, arrNull, arr, fails, rejects>>
             /\ IF next = "acd2" THEN /\ pc' = [pc EXCEPT ![t] = "acd2"] /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops, results>>
                ELSE AfterDoc(t)

-----------------------------------------------------------------------------
(* getParent D:687-698. GP2 reads the parent array for gord[t]. When the  *)
(* array is null getTaxoArrays synchronizes and initialises it.            *)
GP2(t) ==
  /\ pc[t] = "gp2"
  /\ IF ~arrNull
     THEN /\ results' = results \cup {[k |-> "gp", t |-> t, ord |-> gord[t], val |-> arr[gord[t]]]}
          /\ UNCHANGED shared
     ELSE /\ lock = NONE
          /\ LET s == IF rmInit THEN rmSnap ELSE Len(docs)
                 a == ArraysFrom(s)
             IN /\ arr' = a /\ arrNull' = FALSE
                /\ results' = results \cup {[k |-> "gp", t |-> t, ord |-> gord[t], val |-> a[gord[t]]]}
                /\ rmSnap' = s
          /\ rmInit' = TRUE
          /\ shouldRefresh' = IF rmInit THEN shouldRefresh ELSE FALSE
          /\ UNCHANGED <<docs, nextID, cache, complete, misses, shouldFill, fails, rejects, stale>>
  /\ pc' = [pc EXCEPT ![t] = "idle"]
  /\ UNCHANGED <<lock, lab, stk, tgt, mode, pord, cid, fres, gord, ops>>

\* n = getSize(); getParent(n - 1): GPL1 reads nextID, GP2 reads the array
GPL1(t) ==
  /\ WithGPL /\ pc[t] = "idle" /\ ops[t] < MaxOps
  /\ ops' = [ops EXCEPT ![t] = ops[t] + 1]
  /\ gord' = [gord EXCEPT ![t] = nextID - 1]
  /\ pc' = [pc EXCEPT ![t] = "gp2"]
  /\ UNCHANGED <<shared, lock, lab, stk, tgt, mode, pord, cid, fres, results>>

Done == /\ \A t \in Threads : pc[t] = "idle" /\ ops[t] = MaxOps
        /\ UNCHANGED vars

Next ==
  \/ \E t \in Threads :
       \/ FastGet(t) \/ SlowStart(t) \/ FC0(t) \/ FCRef(t) \/ FCFill(t) \/ FC1(t) \/ FCDisk(t) \/ FCPut(t)
       \/ ACD1(t) \/ ACD2(t) \/ ACD3(t) \/ GP2(t) \/ GPL1(t)
  \/ Done

Spec == Init /\ [][Next]_vars

StateBound == Len(docs) <= MaxOrd + 1

-----------------------------------------------------------------------------
(* Properties *)
LiveLabels == {docs[i].lab : i \in {j \in 1..Len(docs) : ~docs[j].del}}

\* one ordinal per label (TaxonomyWriter.java:52-53)
NoDuplicateLabel ==
  \A i, j \in 1..Len(docs) : (i < j /\ ~docs[i].del /\ ~docs[j].del) => docs[i].lab # docs[j].lab

\* ordinal == docID: getSize() counts the categories (TaxonomyWriter.java:83-93)
SizeMatchesDocs == nextID = Len(docs)

\* every ordinal returned by addCategory is the one a reopened reader reports
AddResultsOK ==
  \A r \in results : r.k = "add" =>
     /\ r.ord >= 0 /\ r.ord < Len(docs)
     /\ docs[r.ord + 1].lab = r.lab /\ ~docs[r.ord + 1].del
     /\ r.ord = FirstDoc(r.lab)

\* getParent agrees with the parent stored for the ordinal
GetParentOK ==
  \A r \in results : r.k = "gp" =>
     r.ord < Len(docs) /\ r.val = docs[r.ord + 1].par

\* parents before children, parent field is the parent's (first) ordinal
ParentsOK ==
  \A i \in 2..Len(docs) : ~docs[i].del =>
     /\ docs[i].par >= 0 /\ docs[i].par < Ord(i)
     /\ docs[docs[i].par + 1].lab = Par[docs[i].lab]

CacheSound == \A i \in 1..Len(cache) :
  cache[i][2] < Len(docs) /\ docs[cache[i][2] + 1].lab = cache[i][1]

\* the protocol invariant: with nobody inside the monitor, every category in the
\* index is found by findCategory (cache, or reader when the cache is incomplete)
LookupComplete ==
  lock = NONE =>
    \A l \in LiveLabels :
       InCache(l) \/ (~complete /\ (~rmInit \/ FirstDoc(l) < rmSnap))

\* probes (expected to be violated: shows the scenario is reached)
ProbeEvicted == ~(complete = FALSE /\ Existing = <<>>)
ProbeFilledComplete == ~(Existing # <<>> /\ complete)
ProbeDiskHit == ~(\E t \in Threads : pc[t] = "fcput")
ProbeDup2Threads == ~(\E r1, r2 \in results : r1.k = "add" /\ r2.k = "add" /\ r1.t # r2.t /\ r1.lab = r2.lab)
=============================================================================

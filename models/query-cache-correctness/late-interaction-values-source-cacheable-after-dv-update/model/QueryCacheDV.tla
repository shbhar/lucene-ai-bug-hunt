--------------------------- MODULE QueryCacheDV ---------------------------
(***************************************************************************)
(* Unit query-cache-correctness. Model of the LRUQueryCache caching path across in-place      *)
(* doc-values updates, NRT reopen, deletes, merges, eviction and racy     *)
(* (two-phase) cache population, with each query's Weight#isCacheable     *)
(* modelled as one of the three shapes found by the ClassFile scan.       *)
(*                                                                         *)
(* Mapping (model element -> Lucene main ea57360912ed):                    *)
(*   CacheKeyOf(r) = r.core      LRUQueryCache.java:827 getCoreCacheHelper *)
(*   cache[q][c]                 LRUQueryCache.java:420-440 get/putIfAbsent*)
(*                               keyed by (core key, query)                *)
(*   IsCacheable guard           LRUQueryCache.java:816 (scorerSupplier),  *)
(*                               :903 (count)                              *)
(*   cached set ignores live docs LRUQueryCache.java:617-652 (acceptDocs  *)
(*                               = null); live docs applied by the caller  *)
(*   count shortcut              LRUQueryCache.java:892-925 (only when the *)
(*                               reader has no deletions)                  *)
(*   DvIsCacheable               DocValues.java:340-346 (fi != null &&     *)
(*                               fi.getDocValuesGen() > -1 -> false)       *)
(*   Update                      IndexWriter.updateNumeric/Binary/         *)
(*                               SortedNumericDocValue; ReadersAndUpdates  *)
(*                               .java:408 setDocValuesGen(nextGen >= 1),  *)
(*                               all paths incl. overlays (:597-615)       *)
(*   Reopen                      DirectoryReader.openIfChanged(r, w): new  *)
(*                               reader, same SegmentCoreReaders           *)
(*   Merge                       new segment = new core key, gens -1       *)
(*   Check[q], ConstTrue[q]      shape of q's Weight#isCacheable:          *)
(*     ConstTrue                 SpatialQuery.java:146 (BaseShapeDocValues *)
(*                               Query), LateInteractionFloatValuesSource  *)
(*                               .java isCacheable                         *)
(*     Check = Read              SortedNumericDocValuesRangeQuery.java:120 *)
(*     Check subset of Read      facet DoubleRange.java:208-211 /          *)
(*                               LongRange.java:195-198 (fastMatch fields  *)
(*                               not checked)                              *)
(***************************************************************************)
EXTENDS Naturals, Integers, FiniteSets, Sequences, TLC

CONSTANTS Docs, Fields, Queries, Read, Check, ConstTrue,
          MaxUpdates, MaxReopens, MaxMerges, MaxDeletes, MaxPending,
          TrackVacuity   \* TRUE only for the vacuity-probe configs (keeps the flags constant otherwise)

NONE == "none"

VARIABLES core, wcontent, wexists, wgen, wlive,
          readers, cache, present, pending,
          nUpd, nReopen, nMerge, nDel, nextGen,
          bad, badInfo, sawHit, sawUncacheable

vars == <<core, wcontent, wexists, wgen, wlive, readers, cache, present, pending,
          nUpd, nReopen, nMerge, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

Cores == 0..MaxMerges

Snapshot == [core |-> core, content |-> wcontent, exists |-> wexists,
             gen |-> wgen, live |-> wlive]

Match(q, content) == {d \in Docs : \A f \in Read[q] : content[f][d] = 1}

LiveSet(r) == {d \in Docs : r.live[d]}

DvIsCacheable(r, flds) == \A f \in flds : ~(r.exists[f] /\ r.gen[f] > -1)

IsCacheable(q, r) == IF ConstTrue[q] THEN TRUE ELSE DvIsCacheable(r, Check[q])

Truth(q, r) == Match(q, r.content) \cap LiveSet(r)

HasDeletions(r) == \E d \in Docs : ~r.live[d]

\* record one observed answer; only a wrong answer changes 'bad'/'badInfo'
Observe(kind, i, q, result, truth) ==
  /\ bad' = (bad \/ result # truth)
  /\ badInfo' = IF ~bad /\ result # truth
                  THEN [kind |-> kind, reader |-> i, q |-> q, result |-> result, truth |-> truth]
                  ELSE badInfo
  /\ sawHit' = (sawHit \/ (TrackVacuity /\ kind = "search-hit"))
  /\ sawUncacheable' = (sawUncacheable \/ (TrackVacuity /\ kind = "search-uncacheable"))

Init ==
  /\ core = 0
  \* initial content: any assignment; absent fields have no values
  \* a field may be absent from the segment's FieldInfos (segment written before the field existed)
  /\ wexists \in [Fields -> BOOLEAN]
  /\ wcontent \in [Fields -> [Docs -> {0, 1}]]
  /\ \A f \in Fields : ~wexists[f] => \A d \in Docs : wcontent[f][d] = 0
  /\ wgen = [f \in Fields |-> -1]
  /\ wlive = [d \in Docs |-> TRUE]
  /\ readers = << [core |-> 0, content |-> wcontent, exists |-> wexists,
                   gen |-> [f \in Fields |-> -1], live |-> [d \in Docs |-> TRUE]] >>
  /\ cache = [q \in Queries |-> [c \in Cores |-> {}]]
  /\ present = {}
  /\ pending = {}
  /\ nUpd = 0 /\ nReopen = 0 /\ nMerge = 0 /\ nDel = 0 /\ nextGen = 1
  /\ bad = FALSE /\ sawHit = FALSE /\ sawUncacheable = FALSE
  /\ badInfo = [kind |-> "init", reader |-> 0, q |-> NONE, result |-> {}, truth |-> {}]

\* In-place doc-values update of one doc (IndexWriter.update*DocValue). The field becomes present in
\* the segment's FieldInfos and gets docValuesGen = next generation (>= 1).
Update(f, d, v) ==
  /\ nUpd < MaxUpdates
  /\ wcontent[f][d] # v
  /\ wcontent' = [wcontent EXCEPT ![f][d] = v]
  /\ wexists' = [wexists EXCEPT ![f] = TRUE]
  /\ wgen' = [wgen EXCEPT ![f] = nextGen]
  /\ nextGen' = nextGen + 1
  /\ nUpd' = nUpd + 1
  /\ UNCHANGED <<core, wlive, readers, cache, present, pending, nReopen, nMerge, nDel, bad, badInfo, sawHit, sawUncacheable>>

Delete(d) ==
  /\ nDel < MaxDeletes /\ wlive[d]
  /\ wlive' = [wlive EXCEPT ![d] = FALSE]
  /\ nDel' = nDel + 1
  /\ UNCHANGED <<core, wcontent, wexists, wgen, readers, cache, present, pending, nUpd, nReopen, nMerge, nextGen, bad, badInfo, sawHit, sawUncacheable>>

Reopen ==
  /\ nReopen < MaxReopens
  /\ readers' = Append(readers, Snapshot)
  /\ nReopen' = nReopen + 1
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, cache, present, pending, nUpd, nMerge, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

\* Merge: a new segment (new core key) with the current values written as the base column.
\* Deleted docs are dropped: they have no values and stay non-live.
Merge ==
  /\ nMerge < MaxMerges
  /\ core' = core + 1
  /\ wcontent' = [f \in Fields |-> [d \in Docs |-> IF wlive[d] THEN wcontent[f][d] ELSE 0]]
  /\ wgen' = [f \in Fields |-> -1]
  /\ nMerge' = nMerge + 1
  /\ UNCHANGED <<wexists, wlive, readers, cache, present, pending, nUpd, nReopen, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

\* Atomic search through CachingWrapperWeight.scorerSupplier (policy = always cache).
Search(i, q) ==
  LET r == readers[i] IN
  /\ IF IsCacheable(q, r)
       THEN IF <<q, r.core>> \in present
              THEN /\ Observe("search-hit", i, q, cache[q][r.core] \cap LiveSet(r), Truth(q, r))
                   /\ UNCHANGED <<cache, present>>
              ELSE /\ cache' = [cache EXCEPT ![q][r.core] = Match(q, r.content)]
                   /\ present' = present \cup {<<q, r.core>>}
                   /\ Observe("search-miss", i, q, Match(q, r.content) \cap LiveSet(r), Truth(q, r))
       ELSE /\ Observe("search-uncacheable", i, q, Truth(q, r), Truth(q, r))
            /\ UNCHANGED <<cache, present>>
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, readers, pending, nUpd, nReopen, nMerge, nDel, nextGen>>

\* Two-phase population (another thread's putIfAbsent lands later): compute on reader i ...
Compute(i, q) ==
  LET r == readers[i] IN
  /\ Cardinality(pending) < MaxPending
  /\ IsCacheable(q, r) /\ <<q, r.core>> \notin present
  /\ pending' = pending \cup {<<q, r.core, Match(q, r.content)>>}
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, readers, cache, present, nUpd, nReopen, nMerge, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

\* ... and put it later (LRUQueryCache.java:1103 skips if already present).
Put(p) ==
  /\ p \in pending
  /\ pending' = pending \ {p}
  /\ cache' = IF <<p[1], p[2]>> \notin present THEN [cache EXCEPT ![p[1]][p[2]] = p[3]] ELSE cache
  /\ present' = present \cup {<<p[1], p[2]>>}
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, readers, nUpd, nReopen, nMerge, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

\* CachingWrapperWeight.count (LRUQueryCache.java:892-928).
Count(i, q) ==
  LET r == readers[i] IN
  /\ Observe("count", i, q,
             IF ~HasDeletions(r) /\ IsCacheable(q, r) /\ <<q, r.core>> \in present
               THEN Cardinality(cache[q][r.core]) ELSE Cardinality(Truth(q, r)),
             Cardinality(Truth(q, r)))
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, readers, cache, present, pending, nUpd, nReopen, nMerge, nDel, nextGen>>

Evict(q, c) ==
  /\ <<q, c>> \in present
  /\ present' = present \ {<<q, c>>}
  /\ cache' = [cache EXCEPT ![q][c] = {}]
  /\ UNCHANGED <<core, wcontent, wexists, wgen, wlive, readers, pending, nUpd, nReopen, nMerge, nDel, nextGen, bad, badInfo, sawHit, sawUncacheable>>

Next ==
  \/ \E f \in Fields, d \in Docs, v \in {0, 1} : Update(f, d, v)
  \/ \E d \in Docs : Delete(d)
  \/ Reopen
  \/ Merge
  \/ \E i \in 1..Len(readers), q \in Queries : Search(i, q) \/ Count(i, q) \/ Compute(i, q)
  \/ \E p \in pending : Put(p)
  \/ \E q \in Queries, c \in Cores : Evict(q, c)

Spec == Init /\ [][Next]_vars

\* ---- properties ----
TypeOK ==
  /\ core \in Cores
  /\ wgen \in [Fields -> {-1} \cup (1..(MaxUpdates + 1))]

\* every search/count answers what the uncached weight answers on that reader
SearchCorrect == ~bad

\* stronger, cache-wide: an entry is valid for every open reader on its core that is allowed to use it
CacheEntriesValid ==
  \A q \in Queries, c \in Cores :
    <<q, c>> \in present =>
      \A i \in 1..Len(readers) :
        (readers[i].core = c /\ IsCacheable(q, readers[i])) => cache[q][c] = Match(q, readers[i].content)

\* the soundness lemma behind the fix: two readers of one core that both pass isCacheable see the
\* same matches (does not depend on the cache at all)
CacheableImpliesSameMatches ==
  \A i, j \in 1..Len(readers), q \in Queries :
    (readers[i].core = readers[j].core /\ IsCacheable(q, readers[i]) /\ IsCacheable(q, readers[j]))
      => Match(q, readers[i].content) = Match(q, readers[j].content)

\* vacuity probes (each MUST be violated)
NoCacheHit == ~sawHit
NoUncacheable == ~sawUncacheable
=============================================================================

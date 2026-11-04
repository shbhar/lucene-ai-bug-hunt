/*
 * index-sorting index sorting: Alloy 6 model of the doc maps that index sorting builds when documents come in
 * parent/child blocks (IndexWriterConfig.setParentField), at flush (Sorter via IndexingChain) and at
 * merge (MultiSorter), with deletions, failed (non-aborting) block adds and doc-values updates.
 *
 * Model element  ->  Java (lucene main ea57360912ed)
 *  Doc.mark           parent-field doc value present. IndexingChain writes it only for the last doc
 *                     of a block, in the 2nd pass after schema validation: IndexingChain.java:602-650.
 *                     A doc that fails stays in the DWPT as a deleted doc: DocumentsWriterPerThread.java:
 *                     283-306 (reserveOneDoc / numDocsInRAM++ / deleteLastDocs).
 *  hasBlocks          set only after every doc of a block of >1 doc was indexed: DWPT.java:296-299;
 *                     a merged segment has blocks if any input has: IndexWriter.java:5313-5318.
 *  effKey             value of parents.nextSetBit(docID): MultiSorter.java:166; IndexingChain.java:266-271;
 *                     Sorter.java:193-198.
 *  orphanCrash        nextSetBit == NO_MORE_DOCS used as a docID: IndexSorter.java:297-298 (merge),
 *                     IndexSorter.java:322 (flush, AIOOBE index 2147483647).
 *  Doc.t (emission)   MultiSorter priority-queue merge, comparator (effective key, readerIndex, docID):
 *                     MultiSorter.java:88-102 and 118-136; every doc is visited, live or deleted.
 *  Doc.nid            new docID; only live docs consume one: MultiSorter.java:125-128, 141-153.
 *  flush order        TimSorter over (effective key, docID tiebreak): Sorter.java:132-172, 225-239.
 *  block contract     IndexWriterConfig.java:579-589: "the API guarantee that the document order of a
 *                     block is not altered by the IndexWriter".
 * Simplifications: one ascending sort field with small integer keys (reverse sort is the same with the
 * comparison flipped); values compared exactly; missing values are just another key.
 */
module SortBlocks

abstract sig Bool {}
one sig T, F extends Bool {}

sig Blk {}
sig Seg { rank: one Int }
sig Doc {
  seg: one Seg,
  pos: one Int,     // docID in its input segment (or DWPT buffer for the flush model)
  key: one Int,     // the doc's own sort value
  mark: one Bool,   // parent-field value present
  del: one Bool,    // deleted (live-docs bit clear)
  blk: one Blk,     // user block (one addDocuments call)
  ok: one Bool,     // the addDocuments call that added this doc succeeded
  t: one Int,       // emission step of the MultiSorter merge
  nid: lone Int     // new docID in the merged segment
}

fun segDocs[s: Seg]: set Doc { seg.s }

/* ---------- shape of the inputs ---------- */
pred shape {
  // ranks 0..#Seg-1, distinct
  all s: Seg | s.rank >= 0 and s.rank < #Seg
  all disj s1, s2: Seg | s1.rank != s2.rank
  // docIDs 0..n-1 per segment
  all s: Seg | all d: segDocs[s] | d.pos >= 0 and d.pos < #segDocs[s]
  all disj d1, d2: Doc | d1.seg = d2.seg implies d1.pos != d2.pos
  all d: Doc | d.key >= 0 and d.key <= 2
  // a user block lives in one segment, on contiguous docIDs, and all its docs share the call outcome
  all b: Blk | some blk.b
  all b: Blk | one (blk.b).seg
  all b: Blk | one (blk.b).ok
  all b: Blk | all d1, d2: blk.b, x: Doc |
      (x.seg = d1.seg and d1.pos < x.pos and x.pos < d2.pos) implies x.blk = b
}

fun lastOf[b: Blk]: one Doc { { d: blk.b | no d2: blk.b | d2.pos > d.pos } }
fun nextMark[d: Doc]: lone Doc {
  { m: Doc | m.seg = d.seg and m.mark = T and m.pos >= d.pos and
             no m2: Doc | m2.seg = d.seg and m2.mark = T and m2.pos >= d.pos and m2.pos < m.pos }
}
fun effKey[d: Doc]: Int { nextMark[d].key }
pred hasBlocks[s: Seg] { some b: Blk | (blk.b).seg = s and #(blk.b) > 1 and (blk.b).ok = T }
pred orphanCrash[s: Seg] { hasBlocks[s] and some d: segDocs[s] | no nextMark[d] }

/* A successful block: last doc marked, others not (IndexingChain.java:602-650). */
pred blocksMarkedAsWritten {
  all b: Blk | (blk.b).ok = T implies
     (all d: blk.b | d.mark = T iff d = lastOf[b])
}
/* Failed block (non-aborting exception): every doc of it that reached the DWPT is deleted; a mark can
 * exist only on the doc that failed in the 2nd pass while being the last of its block. */
pred failedBlocksAsWritten {
  all b: Blk | (blk.b).ok = F implies
     ((all d: blk.b | d.del = T) and (all d: blk.b - lastOf[b] | d.mark = F))
}
/* Segment sorted by effective key: what a successful flush or merge produces. */
pred sortedSeg[s: Seg] {
  all d1, d2: segDocs[s] | d1.pos < d2.pos implies effKey[d1] <= effKey[d2]
}

/* ---------- merge (MultiSorter) ---------- */
pred lexLess[a, b: Doc] {
  effKey[a] < effKey[b] or
  (effKey[a] = effKey[b] and (a.seg.rank < b.seg.rank or (a.seg = b.seg and a.pos < b.pos)))
}
pred mergeOrder {
  all d: Doc | d.t >= 0 and d.t < #Doc
  all disj d1, d2: Doc | d1.t != d2.t
  all d1, d2: Doc | (d1.seg = d2.seg and d1.pos < d2.pos) implies d1.t < d2.t
  // greedy priority-queue choice: the emitted doc is <= the head of every other segment
  all d, h: Doc | (h.seg != d.seg and h.t > d.t and
                   (no h2: Doc | h2.seg = h.seg and h2.t > d.t and h2.pos < h.pos)) implies lexLess[d, h]
  all d: Doc | d.del = F implies d.nid = #{ e: Doc | e.del = F and e.t < d.t }
  all d: Doc | d.del = T implies no d.nid
}
fun live: set Doc { del.F }
fun oNextMark[d: Doc]: lone Doc {
  { m: live | m.mark = T and m.nid >= d.nid and
              no m2: live | m2.mark = T and m2.nid >= d.nid and m2.nid < m.nid }
}
fun oEffKey[d: Doc]: Int { oNextMark[d].key }

/* merge-input preconditions: segments as a successful earlier flush/merge leaves them */
pred goodInputs {
  shape
  blocksMarkedAsWritten
  all b: Blk | (blk.b).ok = T          // only successful adds in flushed segments here
  all s: Seg | sortedSeg[s] and not orphanCrash[s]
  all s: Seg | all d: segDocs[s] | some nextMark[d]
}
pred wholeBlockDeletes { all b: Blk | one (blk.b).del }

/* Output properties */
pred outNoTrailingOrphan { all d: live | some oNextMark[d] }
/* sortedness is only defined for docs that have an output parent; orphans are MergeAnyDeletesNoOrphan
 * (model fix 1: an orphan's empty effective key read as 0 gave a spurious counterexample) */
pred outSorted { all d1, d2: live | (d1.nid < d2.nid and some oNextMark[d1] and some oNextMark[d2])
                                   implies oEffKey[d1] <= oEffKey[d2] }
/* Every fully live block keeps its docs, its order and its parent, and no foreign doc joins it. */
pred outBlocksPreserved {
  all b: Blk | (all d: blk.b | d.del = F) implies {
    all d: blk.b | oNextMark[d] = lastOf[b]
    all x: live | oNextMark[x] = lastOf[b] implies x.blk = b
    all d1, d2: blk.b | d1.pos < d2.pos implies d1.nid < d2.nid
  }
}

assert MergeWholeBlockDeletes {
  (goodInputs and wholeBlockDeletes and mergeOrder) implies
    (outNoTrailingOrphan and outSorted and outBlocksPreserved)
}
assert MergeAnyDeletesNoOrphan { (goodInputs and mergeOrder) implies outNoTrailingOrphan }
assert MergeAnyDeletesSorted { (goodInputs and mergeOrder) implies outSorted }
assert MergeAnyDeletesBlocks { (goodInputs and mergeOrder) implies outBlocksPreserved }

/* doc-values update of the parent field before the merge (IndexWriter.updateNumericDocValue /
 * updateDocValues do not reject the parent field; IndexWriter.java:1962-2110, FieldInfos.java:562-640):
 * marks may then differ from the block structure. No deletions. */
pred goodInputsAfterParentDVUpdate {
  shape
  all b: Blk | (blk.b).ok = T
  all d: Doc | d.del = F
  all s: Seg | all d: segDocs[s] | some nextMark[d]
  // the segment was sorted by the ORIGINAL marks; model the original marks as block lasts
  all s: Seg | all d1, d2: segDocs[s] | d1.pos < d2.pos implies
      lastOf[d1.blk].key <= lastOf[d2.blk].key
}
assert MergeAfterParentDVUpdate {
  (goodInputsAfterParentDVUpdate and mergeOrder) implies outBlocksPreserved
}

/* ---------- flush (Sorter over the DWPT buffer) ---------- */
pred flushInputs {
  shape
  one Seg
  blocksMarkedAsWritten
  failedBlocksAsWritten
  all b: Blk | (blk.b).ok = T implies (all d: blk.b | d.del = F)
}
assert FlushNoCrashNoFailures {
  (flushInputs and (all b: Blk | (blk.b).ok = T)) implies (all s: Seg | not orphanCrash[s])
}
assert FlushNoCrash { flushInputs implies (all s: Seg | not orphanCrash[s]) }

/* Mutation check (vacuity): a merge comparator WITHOUT the readerIndex tie-break
 * (MultiSorter.java:101 removed) must break MergeWholeBlockDeletes. */
pred lexLessNoRank[a, b: Doc] {
  effKey[a] < effKey[b] or (effKey[a] = effKey[b] and a.pos < b.pos) or
  (effKey[a] = effKey[b] and a.pos = b.pos and a.seg.rank < b.seg.rank)
}
pred mergeOrderNoRank {
  all d: Doc | d.t >= 0 and d.t < #Doc
  all disj d1, d2: Doc | d1.t != d2.t
  all d1, d2: Doc | (d1.seg = d2.seg and d1.pos < d2.pos) implies d1.t < d2.t
  all d, h: Doc | (h.seg != d.seg and h.t > d.t and
                   (no h2: Doc | h2.seg = h.seg and h2.t > d.t and h2.pos < h.pos)) implies lexLessNoRank[d, h]
  all d: Doc | d.del = F implies d.nid = #{ e: Doc | e.del = F and e.t < d.t }
  all d: Doc | d.del = T implies no d.nid
}
assert MutantNoRankWholeBlockDeletes {
  (goodInputs and wholeBlockDeletes and mergeOrderNoRank) implies
    (outNoTrailingOrphan and outSorted and outBlocksPreserved)
}
check MutantNoRankWholeBlockDeletes for 6 Doc, 2 Seg, 3 Blk, 5 Int

/* vacuity probes: each must find an instance */
run probeMerge { goodInputs and wholeBlockDeletes and mergeOrder and #Seg = 2 and
                 some b: Blk | #(blk.b) > 1 and some d: Doc | d.del = T } for 6 Doc, 2 Seg, 3 Blk, 5 Int
run probeMergeInterleave { goodInputs and mergeOrder and #Seg = 2 and
                 some d1, d2, d3: Doc | d1.seg = d3.seg and d2.seg != d1.seg and d1.t < d2.t and d2.t < d3.t }
                 for 6 Doc, 2 Seg, 3 Blk, 5 Int
run probeFlushFailure { flushInputs and some b: Blk | (blk.b).ok = F and #(blk.b) > 1 } for 6 Doc, 1 Seg, 3 Blk, 5 Int

check MergeWholeBlockDeletes for 6 Doc, 2 Seg, 3 Blk, 5 Int
check MergeAnyDeletesNoOrphan for 6 Doc, 2 Seg, 3 Blk, 5 Int
check MergeAnyDeletesSorted for 6 Doc, 2 Seg, 3 Blk, 5 Int
check MergeAnyDeletesBlocks for 6 Doc, 2 Seg, 3 Blk, 5 Int
check MergeAfterParentDVUpdate for 6 Doc, 2 Seg, 3 Blk, 5 Int
check FlushNoCrashNoFailures for 6 Doc, 1 Seg, 3 Blk, 5 Int
check FlushNoCrash for 6 Doc, 1 Seg, 3 Blk, 5 Int

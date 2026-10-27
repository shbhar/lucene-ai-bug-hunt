# Formal models and scripts: priorityqueue-topdocs-merge

Tools: Dafny.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- TopDocs.merge(start, topN,...) returns a page of null ScoreDocs, with no error, when start + topN overflows int (for example start = 1 and topN = Integer.MAX_VALUE meaning 'no limit') (bug LFV-262; [repro](../../repro/LFV-262/))
- TopDocs.merge with a custom tie breaker fails an assertion (only with -ea) when the tie breaker returns 0 for tied hits from two different shards at the same rank (bug LFV-267; [repro](../../repro/LFV-267/))
- PriorityQueue.downHeap computes the child index i &lt;&lt; 1 in int, so a queue holding 2^30 or more elements throws ArrayIndexOutOfBoundsException from updateTop, pop or insertWithOverflow and is left with one element lost and another duplicated (bug LFV-294; [repro](../../repro/LFV-294/))

## Files

- [priorityqueue-downheap-child-index-overflow/model/PQ.dfy](priorityqueue-downheap-child-index-overflow/model/PQ.dfy) (used for LFV-294)
- [priorityqueue-downheap-child-index-overflow/model/PQInt32.dfy](priorityqueue-downheap-child-index-overflow/model/PQInt32.dfy) (used for LFV-294)
- [topdocs-merge-tiebreaker-zero-across-shards-assert/model/Merge.dfy](topdocs-merge-tiebreaker-zero-across-shards-assert/model/Merge.dfy) (used for LFV-267)
- [topdocs-merge-window-int-overflow/model/Merge.dfy](topdocs-merge-window-int-overflow/model/Merge.dfy) (used for LFV-262)

## How to run

- Dafny: `dafny verify <file>.dfy` (a failed obligation marks where the code's contract breaks).

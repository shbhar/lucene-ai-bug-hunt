# Formal models and scripts: facets-topn-and-topndims-queues-presized-uncapped

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SortedSetDocValues facets size the child priority queue from topN instead of the number of labels, so getTopChildren, getAllDims and getTopDims(.., topNChildren) with topN = Integer.MAX_VALUE throw IllegalArgumentException where taxonomy facets return every label, and a large topN allocates topN + 1 queue slots even for a 3-label dim (bug LFV-994; [repro](../../repro/LFV-994/))
- SortedSetDocValues and taxonomy Facets#getTopDims size their dimension priority queue by the caller's topNDims without a cap, so getTopDims(Integer.MAX_VALUE, n) throws IllegalArgumentException where getAllDims(n) and the default Facets#getTopDims return every dim, and a large topNDims allocates topNDims + 1 queue slots even for 2 dims (bug LFV-983; [repro](../../repro/LFV-983/))

## Files

- [gettopdims-dim-queue-presized-by-topndims/model/queue_size_smt.py](gettopdims-dim-queue-presized-by-topndims/model/queue_size_smt.py) (used for LFV-983)
- [ssdv-facet-child-queue-presized-by-topn/model/queue_size_smt.py](ssdv-facet-child-queue-presized-by-topn/model/queue_size_smt.py) (used for LFV-994)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

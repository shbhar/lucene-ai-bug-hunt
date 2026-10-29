# Formal models and scripts: rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SortRescorer and Expression#getRescorer size their sort collector by topN instead of by the number of first-pass hits, so a topN far above the hit count (such as Integer.MAX_VALUE for 're-sort them all') throws (OutOfMemoryError on main, IllegalArgumentException in current releases) or allocates memory in proportion to topN, while QueryRescorer and DoubleValuesSourceRescorer return the hits (bug LFV-997; [repro](../../repro/LFV-997/), [bug page](../../bugs/LFV-997.html))
- RescoreTopNQuery and LatLonPoint.nearest size their priority queue from the caller's n, not from what the index can return, so n = Integer.MAX_VALUE ('all') throws IllegalArgumentException and a large n throws OutOfMemoryError, even on an index of a few documents (bug LFV-995; [repro](../../repro/LFV-995/))

## Files

- [rescoretopn-and-latlon-nearest-queues-presized-by-n/model/topn_cap.py](rescoretopn-and-latlon-nearest-queues-presized-by-n/model/topn_cap.py) (used for LFV-995)
- [sortrescorer-collector-presized-by-topn/model/topn_cap.py](sortrescorer-collector-presized-by-topn/model/topn_cap.py) (used for LFV-997)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

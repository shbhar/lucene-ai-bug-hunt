# Formal models and scripts: parallel-reader-advertises-index-sort-of-shadowed-sort-field

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ParallelLeafReader and ParallelCompositeReader advertise a sub-reader's index sort even when an earlier, unsorted sub-reader serves the sort field, so sorted searches and index-sort range shortcuts return wrong hits and IndexWriter.addIndexes writes a segment that breaks its declared index sort (bug LFV-981; [repro](../../repro/LFV-981/), [bug page](../../bugs/LFV-981.html))

## Files

- [parallelleafreader-advertises-sort-of-shadowed-sort-field/model/compare_fix.py](parallelleafreader-advertises-sort-of-shadowed-sort-field/model/compare_fix.py) (used for LFV-981)
- [parallelleafreader-advertises-sort-of-shadowed-sort-field/model/drop_breakdown.py](parallelleafreader-advertises-sort-of-shadowed-sort-field/model/drop_breakdown.py) (used for LFV-981)
- [parallelleafreader-advertises-sort-of-shadowed-sort-field/model/violation_smt.py](parallelleafreader-advertises-sort-of-shadowed-sort-field/model/violation_smt.py) (used for LFV-981)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

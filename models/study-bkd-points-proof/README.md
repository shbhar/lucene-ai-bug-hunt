# Formal models and scripts: study-bkd-points-proof

Tools: Dafny, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- PointInSetQuery silently misses most matches on selectively indexed points (fewer index dimensions than data dimensions) (bug LFV-006; [repro](../../repro/LFV-006/), [bug page](../../bugs/LFV-006.html))
- Sandbox HistogramCollector counts deleted documents for a PointRangeQuery (bug LFV-078; [repro](../../repro/LFV-078/))
- Flexible StandardQueryParser: an exclusive int/long point bound at MAX/MIN wraps around, so the query matches every document with the field instead of none (bug LFV-050; [repro](../../repro/LFV-050/))
- Flexible StandardQueryParser misses -0.0 documents for exclusive float/double bounds (bug LFV-059; [repro](../../repro/LFV-059/))
- Sandbox histogram collector drops counts for values near Long.MAX_VALUE (bug LFV-051; [repro](../../repro/LFV-051/))

## Files

- [dafny/Bounds.dfy](dafny/Bounds.dfy)
- [dafny/BoundsFailing.dfy](dafny/BoundsFailing.dfy)
- [dafny/Bytes.dfy](dafny/Bytes.dfy)
- [dafny/MergePointVisitor.dfy](dafny/MergePointVisitor.dfy)
- [dafny/PointRange.dfy](dafny/PointRange.dfy)
- [dafny/RangeField.dfy](dafny/RangeField.dfy)
- [dafny/mutants/Bytes.dfy](dafny/mutants/Bytes.dfy)
- [dafny/mutants/MergePointVisitor_m1.dfy](dafny/mutants/MergePointVisitor_m1.dfy)
- [dafny/mutants/MergePointVisitor_m2.dfy](dafny/mutants/MergePointVisitor_m2.dfy)
- [dafny/mutants/PointRange_m1.dfy](dafny/mutants/PointRange_m1.dfy)
- [dafny/mutants/PointRange_m2.dfy](dafny/mutants/PointRange_m2.dfy)
- [dafny/mutants/RangeField_m1.dfy](dafny/mutants/RangeField_m1.dfy)
- [dafny/mutants/RangeField_m2.dfy](dafny/mutants/RangeField_m2.dfy)
- [z3/comparators.py](z3/comparators.py)
- [z3/fp_nextup.py](z3/fp_nextup.py)

## How to run

- Dafny: `dafny verify <file>.dfy` (a failed obligation marks where the code's contract breaks).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

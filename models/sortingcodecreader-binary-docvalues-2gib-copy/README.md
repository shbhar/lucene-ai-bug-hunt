# Formal models and scripts: sortingcodecreader-binary-docvalues-2gib-copy

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A merge that renumbers documents through SortingCodecReader (BPReorderingMergePolicy, or the re-sort recipe via addIndexes) throws ArithmeticException once one BINARY doc-values field of the merged segment holds 2 GiB, whatever the heap, because the sorted view copies the field into one int-addressed buffer; under the merge policy the failed merge closes IndexWriter (finding LFV-1486; [repro](../../repro/LFV-1486/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1486.html))

## Files

- [binarydvs-sorted-view-int-addressed-copy-over-2gib/model/binarydvs_append_bound.py](binarydvs-sorted-view-int-addressed-copy-over-2gib/model/binarydvs_append_bound.py) (used for LFV-1486)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

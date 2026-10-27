# Formal models and scripts: indexeddisi-dv-bulk

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexedDISI returns its jump-table entry count as a signed short, so a sparse doc-values, norms or vector field whose last doc id is &gt;= 2,147,352,576 stores a negative count and every reader ignores the jump table that was written (advance walks block by block; results stay correct) (bug LFV-169; [repro](../../repro/LFV-169/))
- With an index sort, the flush-time iterator that IndexWriter hands to a doc-values or norms consumer for a numeric field that every document has ends intoBitSet(upTo &gt;= maxDoc) on docID == maxDoc instead of NO_MORE_DOCS, so a custom consumer that bulk-loads it and loops until NO_MORE_DOCS never finishes (latent: Lucene's own consumers skip the bulk load for such fields) (bug LFV-165; [repro](../../repro/LFV-165/))

## Files

- [indexeddisi-jump-table-count-short-overflow/model/indexeddisi_bv.py](indexeddisi-jump-table-count-short-overflow/model/indexeddisi_bv.py) (used for LFV-169)
- [indexeddisi-jump-table-count-short-overflow/model/jumptable_bmc.py](indexeddisi-jump-table-count-short-overflow/model/jumptable_bmc.py) (used for LFV-169)
- [sorting-numeric-dv-intobitset-dense-end-position/model/indexeddisi_bv.py](sorting-numeric-dv-intobitset-dense-end-position/model/indexeddisi_bv.py) (used for LFV-165)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: iw-from-reader-commit-leaf-sorted-reader-pairs-wrong-segments

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- An IndexWriter opened from a leaf-sorted reader's IndexCommit pairs each segment with the segment reader at the same leaf position, so deleteDocuments(term) deletes another segment's documents and the commit persists that silently (finding LFV-1062; [repro](../../repro/LFV-1062/), [finding page](../../bugs/LFV-1062.html))

## Files

- [iw-from-reader-commit-pairs-leaf-sorted-leaves-by-position/model/pairing_guard_z3.py](iw-from-reader-commit-pairs-leaf-sorted-leaves-by-position/model/pairing_guard_z3.py) (used for LFV-1062)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

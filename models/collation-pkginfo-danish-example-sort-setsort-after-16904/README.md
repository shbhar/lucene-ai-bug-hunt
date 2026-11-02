# Formal models and scripts: collation-pkginfo-danish-example-sort-setsort-after-16904

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- The collation package overview's Danish Sorting example calls Sort.setSort, which was removed in 9.0.0, so the example does not compile; on main it is the one error left on the page after #16904 updated its examples (documentation defect) (finding LFV-1085; [repro](../../repro/LFV-1085/))

## Files

- [model/verbatim_check.py](model/verbatim_check.py) (used for LFV-1085)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: twin-family-static-diff

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MutableValueFloat and MutableValueDouble equals compare with ==, so a NaN value is not equal to itself and grouping by a float or double function puts NaN documents into empty groups (released versions) or throws IllegalArgumentException (main) (bug LFV-427; [repro](../../repro/LFV-427/))

## Files

- [mutablevalue-float-double-equals-uses-primitive-eq/model/mutablevalue_fp_equality.py](mutablevalue-float-double-equals-uses-primitive-eq/model/mutablevalue_fp_equality.py) (used for LFV-427)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

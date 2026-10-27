# Formal models and scripts: wand-blockmax-bounds

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ScorerUtil.minRequiredScore returns NaN when the minimum competitive score is +Infinity, so block-max conjunctions and MaxScore disjunctions drop documents that score +Infinity (bug LFV-104; [repro](../../repro/LFV-104/))
- WANDScorer mishandles a minimum competitive score of +Infinity: an assertion fails with -ea, and with -da it prunes documents that score +Infinity (bug LFV-113; [repro](../../repro/LFV-113/))

## Files

- [model/wand_lemmas.py](model/wand_lemmas.py) (used for LFV-104, LFV-113)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

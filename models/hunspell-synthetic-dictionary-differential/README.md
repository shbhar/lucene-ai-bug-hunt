# Formal models and scripts: hunspell-synthetic-dictionary-differential

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Hunspell spell skips the per-entry checks on COMPOUNDRULE compound parts, so it accepts compounds that C Hunspell rejects: a KEEPCASE first part in capitalized or upper case, a FORCEUCASE last part in lower case, and a NEEDAFFIX or FORBIDDENWORD entry as a first or middle part (bug LFV-731; [repro](../../repro/LFV-731/), [bug page](../../bugs/LFV-731.html))

## Files

- [harness/compare.py](harness/compare.py) (used for LFV-731)
- [harness/gen_cpd.py](harness/gen_cpd.py) (used for LFV-731)
- [harness/minimize.py](harness/minimize.py) (used for LFV-731)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

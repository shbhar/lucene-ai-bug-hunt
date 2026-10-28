# Formal models and scripts: legacy-int7-int4-scalar-quantized-scoring-fp-smt

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- On main, a 9.x/10.x int7/int4 scalar-quantized segment whose vectors are all identical (stored quantiles equal) is decoded with the min == max widening that #14049 added, not the quantizer that wrote it, so its vectors read 127 (int7) or 15 (int4) units lower in every dimension and exact matches score about 0 and rank last (unreleased, 11.0 only) (bug LFV-841; [repro](../../repro/LFV-841/))

## Files

- [legacy-sq-equal-quantiles-rewidened-on-read/model/sq_legacy_smt.py](legacy-sq-equal-quantiles-rewidened-on-read/model/sq_legacy_smt.py) (used for LFV-841)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

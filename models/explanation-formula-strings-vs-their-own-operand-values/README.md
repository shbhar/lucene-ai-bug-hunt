# Formal models and scripts: explanation-formula-strings-vs-their-own-operand-values

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- AfterEffectB.explain shows the raw totalTermFreq and docFreq under children labelled 'F, ... + 1' and 'n, ... + 1' (and lists tfn twice), so its own formula (F + 1) / (n * (tfn + 1)) on the children it lists does not give the value it shows; the score itself is correct (finding LFV-922; [repro](../../repro/LFV-922/), [finding page](../../bugs/LFV-922.html))
- Normalization.NoNormalization.explain reports 1 instead of tf, so IBSimilarity explains its Distribution at tfn = 1 and that child disagrees with the score it explains (finding LFV-921; [repro](../../repro/LFV-921/), [finding page](../../bugs/LFV-921.html))
- AxiomaticF1EXP, F1LOG, F3EXP and F3LOG explain tf as 'computed as 1 + log(1 + log(freq))' of the raw freq, but the value they show is 1 + log(1 + log(freq + 1)); the score itself is correct (finding LFV-920; [repro](../../repro/LFV-920/), [finding page](../../bugs/LFV-920.html))

## Files

- [harness/eval_sweep.py](harness/eval_sweep.py) (used for LFV-920, LFV-921, LFV-922)
- [harness/mutate_check.py](harness/mutate_check.py) (used for LFV-920, LFV-921, LFV-922)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

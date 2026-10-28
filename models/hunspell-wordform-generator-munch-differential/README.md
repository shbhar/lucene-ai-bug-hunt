# Formal models and scripts: hunspell-wordform-generator-munch-differential

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Hunspell rejects, cannot stem and does not generate twofold-suffix words (a second suffix allowed by the first suffix's continuation class) when either suffix class has cross product N, although cross product only governs prefix+suffix combinations: with 'SFX S 0 st/E', 'SFX E N 2' (en, e) and raich/SE, spell("raichsten") is false; the real Luxembourgish dictionary rejects räichsten (bug LFV-641; [repro](../../repro/LFV-641/), [bug page](../../bugs/LFV-641.html))
- Hunspell getAllWordForms, generateAllSimpleWords and compress never generate a suffix that only a prefix's continuation class allows: with ampere/U and 'PFX U 0 kilo/S', spell accepts amperes and kiloamperes, but getAllWordForms("ampere") returns only [ampere, kiloampere] (bug LFV-634; [repro](../../repro/LFV-634/), [bug page](../../bugs/LFV-634.html))

## Files

- [harness/compare.py](harness/compare.py) (used for LFV-634)
- [harness/gen_micro.py](harness/gen_micro.py) (used for LFV-634)
- [model/survey/aff_survey.py](model/survey/aff_survey.py) (used for LFV-641)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: hunspell

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Hunspell spell-checker: CHECKCOMPOUNDTRIPLE misses an x|xx triple letter (one letter before the boundary, two after) at the second and later boundaries of a compound, so words such as operabareeel (opera + bare|eel) are accepted (absolute break position compared with the tail's relative length) (bug LFV-373; [repro](../../repro/LFV-373/))
- Hunspell spell-checker: a CHECKCOMPOUNDPATTERN simplified compound form (the optional replacement field) after the first compound part is checked from the start of the whole word, so valid compounds such as dampfschiffahrt (dampf + schiff|fahrt with 'ff f ff') are rejected and some words (schiffschiffahrt, or auu with 'u u u') make spell/suggest throw StackOverflowError (expandReplacement returns offset 0 over an array that still holds the earlier parts) (bug LFV-377; [repro](../../repro/LFV-377/))
- Hunspell: a bracket or dot affix condition with fewer character patterns than the strip is checked against the wrong end of the strip (a suffix rule at the strip's start, a prefix rule at its end), so the rule never applies when it should, or always applies when it should not (bug LFV-374; [repro](../../repro/LFV-374/))

## Files

- [affixcondition-short-condition-wrong-strip-end/model/affixcond_z3.py](affixcondition-short-condition-wrong-strip-end/model/affixcond_z3.py) (used for LFV-374)
- [compound-pattern-replacement-loses-offset/model/cmp.py](compound-pattern-replacement-loses-offset/model/cmp.py) (used for LFV-377)
- [compound-triple-check-absolute-offset/model/cmp.py](compound-triple-check-absolute-offset/model/cmp.py) (used for LFV-373)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

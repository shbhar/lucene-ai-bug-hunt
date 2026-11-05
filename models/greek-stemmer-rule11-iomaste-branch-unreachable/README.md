# Formal models and scripts: greek-stemmer-rule11-iomaste-branch-unreachable

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- GreekStemmer rule11 tests the suffix 'ομαστε' (omaste) before 'ιομαστε' (iomaste), so its 'ιομαστε' branch can never run and -ιόμαστε (-iómaste) verb forms keep a trailing ι (i): GreekAnalyzer stems 'αγαπιόμαστε' (agapiómaste) to 'αγαπι' (agapi) instead of 'αγαπ' (agap), so it no longer matches the other forms of the same verb (finding LFV-1385; [repro](../../repro/LFV-1385/), [finding page](../../bugs/LFV-1385.html))

## Files

- [model/rule11_shadow.py](model/rule11_shadow.py) (used for LFV-1385)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

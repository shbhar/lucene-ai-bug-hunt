# Formal models and scripts: timeout-partial-results

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- IndexSearcher#count on a two-term disjunction over-counts, even above maxDoc, when the searcher timeout fires while the intersection is counted (finding LFV-465; [repro](../../repro/LFV-465/), [finding page](../../bugs/LFV-465.html))

## Files

- [count-inclusion-exclusion-partial-intersection/model/count_ie_timeout_z3.py](count-inclusion-exclusion-partial-intersection/model/count_ie_timeout_z3.py) (used for LFV-465)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

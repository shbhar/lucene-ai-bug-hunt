# Formal models and scripts: spatial-extras

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- BBoxStrategy's overlap-ratio relevance (BBoxOverlapRatioValueSource) undercounts the overlap of two dateline-crossing boxes that intersect in two longitude pieces, so the score is too low (bug LFV-369; [repro](../../repro/LFV-369/), [bug page](../../bugs/LFV-369.html))

## Files

- [bbox-overlap-ratio-two-piece-dateline-intersection/model/smt/overlap_ratio_fp.py](bbox-overlap-ratio-two-piece-dateline-intersection/model/smt/overlap_ratio_fp.py) (used for LFV-369)
- [bbox-overlap-ratio-two-piece-dateline-intersection/model/smt/overlap_ratio_fp_ge1.py](bbox-overlap-ratio-two-piece-dateline-intersection/model/smt/overlap_ratio_fp_ge1.py) (used for LFV-369)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

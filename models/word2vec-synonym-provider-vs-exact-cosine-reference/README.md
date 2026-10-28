# Formal models and scripts: word2vec-synonym-provider-vs-exact-cosine-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Word2VecSynonymFilter documents minAcceptedSimilarity as a cosine similarity, but Word2VecSynonymProvider compares it with the normalised score (1 + cosine) / 2, so the effective cosine cutoff is 2m - 1 (the default 0.8 accepts cosine 0.6, 0.5 accepts orthogonal terms, values below 0.5 accept negatively correlated terms); documentation defect, the javadoc fix is the default proposal (bug LFV-870; [repro](../../repro/LFV-870/))

## Files

- [word2vec-min-accepted-similarity-compared-as-score-not-cosine/model/threshold_closed_form.py](word2vec-min-accepted-similarity-compared-as-score-not-cosine/model/threshold_closed_form.py) (used for LFV-870)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

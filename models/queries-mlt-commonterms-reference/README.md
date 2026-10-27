# Formal models and scripts: queries-mlt-commonterms-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MoreLikeThis keeps the lowest-scoring selected terms when the query reaches the boolean clause limit, so the truncated query silently drops the most interesting terms (regression since 4.10.0) (bug LFV-571; [repro](../../repro/LFV-571/))
- MoreLikeThis with setBoost(true) divides every term's score by the lowest selected score, so a selected term that scores 0 (a TFIDFSimilarity whose idf is 0, such as the textbook log(N/df) for a term in every document) makes like throw 'boost must be a positive float, got NaN' (8.0 and later) or build NaN and Infinity boosts (4.10 to 7.x) (bug LFV-559; [repro](../../repro/LFV-559/))
- MoreLikeThis with maxQueryTerms = 0 throws NullPointerException from like(...), retrieveInterestingTerms(...) and the rewrite of MoreLikeThisQuery or the XML parser's LikeThisQuery, instead of returning a query with no terms (regression since 4.10.0) (bug LFV-553; [repro](../../repro/LFV-553/))

## Files

- [model/mlt_selection_model.py](model/mlt_selection_model.py) (used for LFV-553, LFV-559, LFV-571)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

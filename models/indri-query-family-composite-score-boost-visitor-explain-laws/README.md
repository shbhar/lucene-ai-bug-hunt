# Formal models and scripts: indri-query-family-composite-score-boost-visitor-explain-laws

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndriAndQuery returns documents that match none of its clauses when a clause uses two-phase iteration (PhraseQuery, doc-values range queries), because IndriDisjunctionScorer iterates the clauses' approximations and never calls TwoPhaseIterator.matches (bug LFV-874; [repro](../../repro/LFV-874/))
- IndriQuery.visit reports the whole query as a leaf and never visits its clauses, so QueryVisitor.termCollector, UnifiedHighlighter term extraction, IndexSearcher's cumulative clause limit and RamUsageEstimator.sizeOf all ignore an IndriAndQuery's clauses (bug LFV-872; [repro](../../repro/LFV-872/), [bug page](../../bugs/LFV-872.html))
- IndriAndQuery accepts MUST, FILTER and MUST_NOT clauses without complaint but ignores them when matching (it always matches the union of all clauses), while its explain does read MUST/FILTER, so it explains some of its own hits as non-matches (bug LFV-871; [repro](../../repro/LFV-871/))
- IndriAndWeight.explain does not check that its scorer landed on the requested document, so a document that matches no clause is explained as a match with another document's score, or explain throws (EOFException or IndexOutOfBoundsException from a norms read past the clause scorer's last match; AssertionError with -ea) (bug LFV-875; [repro](../../repro/LFV-875/))

## Files

- [model/indri_and_model.py](model/indri_and_model.py) (used for LFV-871, LFV-872, LFV-874, LFV-875)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

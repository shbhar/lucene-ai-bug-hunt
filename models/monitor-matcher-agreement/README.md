# Formal models and scripts: monitor-matcher-agreement

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Lucene Monitor's ScoringMatch and ExplainingMatch score a registered DisjunctionMaxQuery with a tie-breaker below 1 as the sum of its matching disjuncts instead of max + tieBreaker * others (bug LFV-852; [repro](../../repro/LFV-852/), [bug page](../../bugs/LFV-852.html))
- Monitor's QueryDecomposer turns a BooleanQuery whose only required clause is a single FILTER clause into that clause as a scoring query, so ScoringMatch and ExplainingMatch report a positive score for a query (or query part) that scores 0, and twice the true score when such a query is nested in a disjunction (bug LFV-853; [repro](../../repro/LFV-853/), [bug page](../../bugs/LFV-853.html))
- Lucene Monitor's QueryDecomposer collects a query's parts in a Set, so a sub-query that occurs more than once in a registered query (a repeated SHOULD or MUST clause, or a term shared by two nested disjunctions) is scored once by ScoringMatch and ExplainingMatch instead of once per occurrence (bug LFV-856; [repro](../../repro/LFV-856/), [bug page](../../bugs/LFV-856.html))

## Files

- [monitor-decomposer-set-collapses-repeated-subqueries/model/score_algebra.py](monitor-decomposer-set-collapses-repeated-subqueries/model/score_algebra.py) (used for LFV-856)
- [monitor-dismax-decomposition-sums-disjunct-scores/model/score_algebra.py](monitor-dismax-decomposition-sums-disjunct-scores/model/score_algebra.py) (used for LFV-852)
- [monitor-filter-clause-decomposed-as-scoring-query/model/score_algebra.py](monitor-filter-clause-decomposed-as-scoring-query/model/score_algebra.py) (used for LFV-853)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

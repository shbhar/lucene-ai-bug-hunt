# Formal models and scripts: scoring-term-join-unknown-term-count

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A scoring JoinUtil term join (ScoreMode Max, Min, Avg or Total, run where scores are needed) throws IllegalArgumentException 'cost must be &gt;= 0' on a segment whose Terms#size() for the join's to-field returns -1, the value its javadoc documents for a term count that is not stored: TermsIncludingScoreQuery multiplies maxDoc by it. Run with an application's reader view that reports -1; on 10.6.0, branch_11x and main the stock postings formats and six stock reader views that were run report a real count (the join throws on 6.1.0 to 10.6.0, branch_11x and main) (finding LFV-1539; [repro](../../repro/LFV-1539/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1539.html))

## Files

- [model/cost-sign/cost_sign.py](model/cost-sign/cost_sign.py) (used for LFV-1539)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

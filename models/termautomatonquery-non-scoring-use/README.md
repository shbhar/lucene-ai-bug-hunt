# Formal models and scripts: termautomatonquery-non-scoring-use

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Sandbox TermAutomatonQuery throws IllegalStateException ('Cannot call docFreq() when needsStats=false') whenever it runs without scores (IndexSearcher.count, a FILTER or MUST_NOT clause, ConstantScoreQuery, a search sorted by index order), for every automaton that rewrite() keeps, while the scoring search of the same query returns its hits; every release from 8.0.0 to 10.6.0, branch_11x and main; known outside Lucene (finding LFV-1514; [repro](../../repro/LFV-1514/))

## Files

- [model/scan/scan_termstates.py](model/scan/scan_termstates.py) (used for LFV-1514)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

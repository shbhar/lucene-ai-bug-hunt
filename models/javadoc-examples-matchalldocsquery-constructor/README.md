# Formal models and scripts: javadoc-examples-matchalldocsquery-constructor

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- The 'Example usage' blocks in the class javadoc of AllGroupHeadsCollectorManager, DocValuesStatsCollectorManager and LargeNumHitsTopDocsCollectorManager call new MatchAllDocsQuery(), whose constructor #16962 made private on branch_11x and main: with the elided searcher supplied the examples compile and run on 10.6.0 and are rejected by javac on branch_11x 3221dd4cf3a1 and main 7b8418faa2ff (documentation defect; the private constructor is deliberate) (finding LFV-1576; [repro](../../repro/LFV-1576/), [finding page](../../bugs/LFV-1576.html))

## Files

- [three-class-javadoc-usage-examples-still-call-matchalldocsquery-constructor-made-private-by-16962/model/scan_ctor_calls.py](three-class-javadoc-usage-examples-still-call-matchalldocsquery-constructor-made-private-by-16962/model/scan_ctor_calls.py) (used for LFV-1576)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

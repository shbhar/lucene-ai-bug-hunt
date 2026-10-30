# Formal models and scripts: analysis-benchmark-backward-codecs-javadoc-examples-compile-on-main

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- The org.apache.lucene.analysis.compound package overview's three code examples do not compile on any release since 4.0.0: they call HyphenationCompoundWordTokenFilter.getHyphenationTree(Reader), WhitespaceTokenizer(Reader) and the String dictionary constructors of both decompounding filters, all removed; their loop never calls reset, and the page's output table shows per-part offsets that the filters stopped emitting in 4.4.0 (documentation defect) (bug LFV-1041; [repro](../../repro/LFV-1041/))
- The benchmark quality package overview's TREC sample calls qrun.execute, a 4-argument QualityBenchmark.execute that no release has ever had (maxResults moved to setMaxResults(int) in LUCENE-1116, before 2.3.0, the package's first release), so the sample has not compiled in any release (documentation defect) (bug LFV-1040; [repro](../../repro/LFV-1040/))

## Files

- [compound-package-overview-examples-use-removed-apis/model/verbatim_check.py](compound-package-overview-examples-use-removed-apis/model/verbatim_check.py) (used for LFV-1041)
- [quality-package-trec-sample-calls-nonexistent-execute-overload/model/verbatim_check.py](quality-package-trec-sample-calls-nonexistent-execute-overload/model/verbatim_check.py) (used for LFV-1040)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

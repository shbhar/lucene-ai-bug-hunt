# Formal models and scripts: addbatch-row-column-arrays-grown-apart

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- IndexWriter.addBatch (and updateDocuments with a ColumnBatch) can throw ArrayIndexOutOfBoundsException for a batch with 39 or more stored or indexed columns when Lucene takes an object reference to be 8 bytes (run: -XX:-UseCompressedOops, -XX:+UseZGC, a runtime without the jdk.management module, a 34 GB heap): IndexingChain.processRowColumns grows three parallel arrays to different lengths (finding LFV-1579; [repro](../../repro/LFV-1579/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1579.html))

## Files

- [processrowcolumns-growth-test-reads-reference-array-length-only-int-arrays-grown-apart/model/growth_invariant.py](processrowcolumns-growth-test-reads-reference-array-length-only-int-arrays-grown-apart/model/growth_invariant.py) (used for LFV-1579)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

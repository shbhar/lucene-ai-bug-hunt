# Formal models and scripts: upstream-delta-columnar-dense-vector-batch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- When IndexWriter#addBatch rejects a vector part-way through a DENSE vector column, the core HNSW and scalar-quantized vectors writers fall out of step with a flat field writer that keeps the default addDenseValues (new in #16734): the next commit closes the IndexWriter, or with assertions off commits a corrupt HNSW graph or misaligned COSINE quantization state (bug LFV-579; [repro](../../repro/LFV-579/), [bug page](../../bugs/LFV-579.html))

## Files

- [dense-vector-wrappers-assume-atomic-delegate/model/dense_vector_commit.py](dense-vector-wrappers-assume-atomic-delegate/model/dense_vector_commit.py) (used for LFV-579)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

# Formal models and scripts: columnar-addbatch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- When a dense LongColumn's values cursor throws during addBatch, the NUMERIC/SORTED_NUMERIC doc values it already delivered stay buffered with no document, so later documents of that field in the same segment are stored with another document's value (bug LFV-097; [repro](../../repro/LFV-097/), [bug page](../../bugs/LFV-097.html))
- When IndexWriter#addBatch fails part-way through a dense points column, the point bytes it already copied stay in the points buffer, so documents added later to the same in-memory segment are silently indexed with the rejected batch's point values (bug LFV-101; [repro](../../repro/LFV-101/), [bug page](../../bugs/LFV-101.html))
- A SORTED_SET DictionaryColumn tuple that addBatch rejects (out-of-range ordinal, or ordValue throws) leaves an empty document in the doc-values buffer, so the next flush fails and closes the IndexWriter, or with -da commits a segment whose field cannot be read (bug LFV-094; [repro](../../repro/LFV-094/), [bug page](../../bugs/LFV-094.html))

## Files

- [model/dense_commit.py](model/dense_commit.py) (used for LFV-094, LFV-097, LFV-101)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

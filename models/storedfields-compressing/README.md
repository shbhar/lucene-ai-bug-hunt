# Formal models and scripts: storedfields-compressing

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexWriter accepts a stored string of 2^30 - 1 or more UTF-8 bytes of non-Latin-1 text (from about half of its own MAX_STORED_STRING_LENGTH up), but DataInput.readString can never decode it, so StoredFields#document, visitor-path merges and index-sorted flushes throw OutOfMemoryError (bug LFV-172; [repro](../../repro/LFV-172/))
- Stored fields writer: the int slice loop in flush wraps once a chunk is larger than 2,147,450,880 bytes, so a ~2 GiB document within the documented per-document limit closes IndexWriter when smaller documents are still buffered in its chunk (bug LFV-186; [repro](../../repro/LFV-186/))
- Lucene90StoredFieldsFormat (the default codec's stored fields) documents a per-document limit of 2^31 - 2^14 bytes, but the format it delegates to documents and enforces 2^31 - chunkSize (chunks of 81,920 / 491,520 bytes), so a document just below the stated limit can fail with ArithmeticException and close IndexWriter (documentation defect: the javadoc value has been stale since the chunks grew past 16 KiB) (bug LFV-163; [repro](../../repro/LFV-163/))

## Files

- [stored-string-over-1gib-utf8-unreadable/model/chunk_arith.py](stored-string-over-1gib-utf8-unreadable/model/chunk_arith.py) (used for LFV-172)
- [storedfields-slice-loop-int-overflow/model/chunk_arith.py](storedfields-slice-loop-int-overflow/model/chunk_arith.py) (used for LFV-186)
- [storedfieldsformat-documented-size-limit-stale/model/chunk_arith.py](storedfieldsformat-documented-size-limit-stale/model/chunk_arith.py) (used for LFV-163)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

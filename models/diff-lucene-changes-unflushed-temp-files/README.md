# Formal models and scripts: diff-lucene-changes-unflushed-temp-files

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Release tooling only (a hand-run developer script of the source tree, not a library class): dev-tools/scripts/diff_lucene_changes.py starts diff on two temporary files it has not flushed, so in the runs where both CHANGES.txt sections still sat in the files' write buffers (Linux, Python 3.9 to 3.15) it printed no diff lines although the sections differ (finding LFV-1557; [repro](../../repro/LFV-1557/))

## Files

- [diff-lucene-changes-temp-files-not-flushed-before-diff/model/first-model/model_buffered_write.first.py](diff-lucene-changes-temp-files-not-flushed-before-diff/model/first-model/model_buffered_write.first.py) (used for LFV-1557)
- [diff-lucene-changes-temp-files-not-flushed-before-diff/model/model_buffered_write.py](diff-lucene-changes-temp-files-not-flushed-before-diff/model/model_buffered_write.py) (used for LFV-1557)
- [diff-lucene-changes-temp-files-not-flushed-before-diff/model/real_sections.py](diff-lucene-changes-temp-files-not-flushed-before-diff/model/real_sections.py) (used for LFV-1557)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

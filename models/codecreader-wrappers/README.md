# Formal models and scripts: codecreader-wrappers

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SlowCompositeCodecReaderWrapper's merged vector iterator numbers ordinals by position and ignores each input's own index, so addIndexes of two or more SortingCodecReader inputs through a reordering merge policy writes vectors onto the wrong documents (bug LFV-278; [repro](../../repro/LFV-278/), [bug page](../../bugs/LFV-278.html))
- A reordered addIndexes(CodecReader...) silently drops the stored fields and term vectors of the first document after an empty (maxDoc 0) input reader: SlowCompositeCodecReaderWrapper.docIdToReaderId keeps an Arrays.binarySearch hit on a repeated doc start and reads that document from the empty reader (bug LFV-318; [repro](../../repro/LFV-318/), [bug page](../../bugs/LFV-318.html))
- addIndexes(CodecReader...) with the default config rejects inputs whose maxDoc sum, deleted documents included, exceeds MAX_DOCS although only their live documents are added, because it always builds a merged view of the inputs for OneMerge#reorder (regression since 9.9.0) (bug LFV-285; [repro](../../repro/LFV-285/))

## Files

- [addindexes-merged-view-total-maxdoc-limit/model/leaf_lookup_smt.py](addindexes-merged-view-total-maxdoc-limit/model/leaf_lookup_smt.py) (used for LFV-285)
- [merged-vector-iterator-ignores-sub-ord/model/leaf_lookup_smt.py](merged-vector-iterator-ignores-sub-ord/model/leaf_lookup_smt.py) (used for LFV-278)
- [slowcomposite-docid-lookup-empty-leaf/model/leaf_lookup_smt.py](slowcomposite-docid-lookup-empty-leaf/model/leaf_lookup_smt.py) (used for LFV-318)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

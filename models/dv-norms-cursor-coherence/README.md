# Formal models and scripts: dv-norms-cursor-coherence

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LFV-415 (bug LFV-415; [repro](../../repro/LFV-415/))
- SortedDocValues#ordinalRangeIntoBitSet's default implementation, called after advanceExact(d) returned false, reads ordValue for doc d, which has no value, so it marks d using a neighbouring document's ordinal (or throws EOFException) (bug LFV-419; [repro](../../repro/LFV-419/))
- The flush-time doc-values iterators that IndexWriter hands to a DocValuesConsumer move only their doc-ID cursor in intoBitSet, so values read afterwards belong to earlier documents (NUMERIC, BINARY, SORTED, SORTED_NUMERIC, SORTED_SET; latent: Lucene's own consumers never read values after intoBitSet on the same iterator) (bug LFV-418; [repro](../../repro/LFV-418/))

## Files

- [flush-buffered-dv-intobitset-stale-value-cursor/model/triage.py](flush-buffered-dv-intobitset-stale-value-cursor/model/triage.py) (used for LFV-418)
- [sorted-ordinalrange-default-after-advanceexact-false/model/triage.py](sorted-ordinalrange-default-after-advanceexact-false/model/triage.py) (used for LFV-419)
- [sparse-sortednumeric-range-after-advanceexact-false/model/triage.py](sparse-sortednumeric-range-after-advanceexact-false/model/triage.py) (used for LFV-415)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

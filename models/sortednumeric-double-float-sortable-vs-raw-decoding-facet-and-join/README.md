# Formal models and scripts: sortednumeric-double-float-sortable-vs-raw-decoding-facet-and-join

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- JoinUtil's numeric join reads a DoubleField or FloatField from field as raw IEEE bits, so a join on a negative value silently misses its matches (finding LFV-1111; [repro](../../repro/LFV-1111/), [finding page](../../bugs/LFV-1111.html))

## Files

- [joinutil-numeric-join-decodes-sortable-doublefield-as-raw-bits/model/decode_mismatch.py](joinutil-numeric-join-decodes-sortable-doublefield-as-raw-bits/model/decode_mismatch.py) (used for LFV-1111)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

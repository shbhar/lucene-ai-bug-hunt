# Formal models and scripts: hunspell-set-encoding-names-vs-c-hunspell-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Hunspell Dictionary fails to load with 'Too many collisions' when its entry count is a multiple of 961, because WordStorage takes the raw String.hashCode modulo the table size (for example the en_US dictionary plus a 300-word custom list) (bug LFV-933; [repro](../../repro/LFV-933/))
- Hunspell Dictionary cannot load SET ISO8859-10, or ISO8859-14 spelled any way except exactly "ISO8859-14" (UnsupportedCharsetException at load) (bug LFV-928; [repro](../../repro/LFV-928/))

## Files

- [hunspell-set-encoding-name-table-incomplete/model/expected_all_bytes.py](hunspell-set-encoding-name-table-incomplete/model/expected_all_bytes.py) (used for LFV-928)
- [hunspell-wordstorage-hash-modulo-multiple-of-31/model/slot_counts.py](hunspell-wordstorage-hash-modulo-multiple-of-31/model/slot_counts.py) (used for LFV-933)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

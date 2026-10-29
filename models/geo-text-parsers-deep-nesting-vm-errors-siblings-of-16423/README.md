# Formal models and scripts: geo-text-parsers-deep-nesting-vm-errors-siblings-of-16423

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Polygon.fromGeoJSON keeps a full key-path String alive at every nesting level, so its heap use grows with nesting depth times key length: a valid 750 KB GeoJSON Feature (900 nested arrays under one long property key), or a 1 MB one (1,000 nested objects with 1,000-char keys), runs out of a 512 MiB heap (every release since 6.2.0) (bug LFV-952; [repro](../../repro/LFV-952/))

## Files

- [geojson-parser-per-level-path-strings-memory/model/pathmem_z3.py](geojson-parser-per-level-path-strings-memory/model/pathmem_z3.py) (used for LFV-952)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

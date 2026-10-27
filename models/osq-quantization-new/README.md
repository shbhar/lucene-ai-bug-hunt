# Formal models and scripts: osq-quantization-new

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FLOAT16 vectors read back from a quantized index whose raw vectors were dropped can contain +-Infinity, so full-precision rescoring ranks an exact match last (bug LFV-084; [repro](../../repro/LFV-084/))

## Files

- [fp16-dequantized-view-overflows-to-infinity/model/fp16_chain.py](fp16-dequantized-view-overflows-to-infinity/model/fp16_chain.py) (used for LFV-084)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

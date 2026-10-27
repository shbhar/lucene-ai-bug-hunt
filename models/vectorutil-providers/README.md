# Formal models and scripts: vectorutil-providers

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Byte-vector similarity kernels wrap their int accumulators from 33,026 dimensions (squareDistance and the uint8 kernels) and from 131,072 dimensions (dotProduct, cosine), below the 2^18 / 2^16 no-overflow bounds their code comments state (bug LFV-138; [repro](../../repro/LFV-138/))

## Files

- [byte-kernel-int-accumulator-dim-bound/model/byte_kernel_overflow_bounds.py](byte-kernel-int-accumulator-dim-bound/model/byte_kernel_overflow_bounds.py) (used for LFV-138)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

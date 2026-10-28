# Formal models and scripts: lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LMDirichletSimilarity accepts mu = 0, but then every document scores 0: log(1 + tf/(mu*P)) + log(mu/(dl+mu)) becomes +Infinity + -Infinity = NaN, and the 'score &gt; 0' clamp turns NaN into 0 (bug LFV-902; [repro](../../repro/LFV-902/))

## Files

- [model/lmdirichlet_mu0.py](model/lmdirichlet_mu0.py) (used for LFV-902)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

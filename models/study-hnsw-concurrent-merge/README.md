# Formal models and scripts: study-hnsw-concurrent-merge

Tools: TLA+/TLC, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HNSW merge-time graph reuse can leave the entry node with no neighbours, dropping kNN recall to zero (bug LFV-005; [repro](../../repro/LFV-005/), [bug page](../../bugs/LFV-005.html))
- Concurrent HNSW build retries a lost entry-node promotion with the wrong query vector (bug LFV-061; [repro](../../repro/LFV-061/))
- HNSW addConnections descends from the worst upper-level candidate instead of the best (bug LFV-068; [repro](../../repro/LFV-068/))

## Files

- [model/C1.cfg](model/C1.cfg)
- [model/C1b.cfg](model/C1b.cfg)
- [model/C1fix.cfg](model/C1fix.cfg)
- [model/C2bug.cfg](model/C2bug.cfg)
- [model/C2fix.cfg](model/C2fix.cfg)
- [model/C2reach.cfg](model/C2reach.cfg)
- [model/C2reachSeq.cfg](model/C2reachSeq.cfg)
- [model/C2split3.cfg](model/C2split3.cfg)
- [model/C3fix.cfg](model/C3fix.cfg)
- [model/C3split.cfg](model/C3split.cfg)
- [model/C3w3bug.cfg](model/C3w3bug.cfg)
- [model/C3w3fix.cfg](model/C3w3fix.cfg)
- [model/C4bug.cfg](model/C4bug.cfg)
- [model/C4fix.cfg](model/C4fix.cfg)
- [model/C6bug.cfg](model/C6bug.cfg)
- [model/C6fix.cfg](model/C6fix.cfg)
- [model/C6w3fix.cfg](model/C6w3fix.cfg)
- [model/HnswConc.tla](model/HnswConc.tla)
- [model/MC.tla](model/MC.tla)
- [model/MC_S1.tla](model/MC_S1.tla)
- [model/MC_S2.tla](model/MC_S2.tla)
- [model/MC_S3.tla](model/MC_S3.tla)
- [model/R1.cfg](model/R1.cfg)
- [model/R2.cfg](model/R2.cfg)
- [model/R3.cfg](model/R3.cfg)
- [model/R5.cfg](model/R5.cfg)
- [model/R5bug.cfg](model/R5bug.cfg)
- [model/R5split.cfg](model/R5split.cfg)
- [model/R6w3.cfg](model/R6w3.cfg)
- [model/S1_conc.cfg](model/S1_conc.cfg)
- [model/S2_conc.cfg](model/S2_conc.cfg)
- [model/S3_conc.cfg](model/S3_conc.cfg)
- [scripts/mkcfg.py](scripts/mkcfg.py)
- [scripts/serial.py](scripts/serial.py)
- [scripts/trace.py](scripts/trace.py)
- [smt/hnswlock_index.py](smt/hnswlock_index.py)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

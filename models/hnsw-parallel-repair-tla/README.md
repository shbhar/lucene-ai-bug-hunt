# Formal models and scripts: hnsw-parallel-repair-tla

Tools: TLA+/TLC, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HNSW merge with graph reuse throws BufferAllocationException when the reused graph has more than 805,306,368 vectors (bug LFV-458; [repro](../../repro/LFV-458/))
- Concurrent HNSW merge throws once the merged segment has more than 2^31 - 2048 x numMergeWorkers vectors: the int batch-claim counter wraps past Integer.MAX_VALUE and workers are handed negative ordinals (bug LFV-466; [repro](../../repro/LFV-466/))

## Files

- [hnsw-concurrent-merge-claim-counter-wrap/model/MC_W1_repair_wrap.cfg](hnsw-concurrent-merge-claim-counter-wrap/model/MC_W1_repair_wrap.cfg) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/MC_W1_repair_wrap.tla](hnsw-concurrent-merge-claim-counter-wrap/model/MC_W1_repair_wrap.tla) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/MC_W2_insert_wrap.cfg](hnsw-concurrent-merge-claim-counter-wrap/model/MC_W2_insert_wrap.cfg) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/MC_W2_insert_wrap.tla](hnsw-concurrent-merge-claim-counter-wrap/model/MC_W2_insert_wrap.tla) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/MC_W6_insert_edge9.cfg](hnsw-concurrent-merge-claim-counter-wrap/model/MC_W6_insert_edge9.cfg) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/RepairTasks.tla](hnsw-concurrent-merge-claim-counter-wrap/model/RepairTasks.tla) (used for LFV-466)
- [hnsw-concurrent-merge-claim-counter-wrap/model/q_claim_wrap.py](hnsw-concurrent-merge-claim-counter-wrap/model/q_claim_wrap.py) (used for LFV-466)
- [hnsw-merge-ordmap-hashmap-capacity/model/capacity_bound.py](hnsw-merge-ordmap-hashmap-capacity/model/capacity_bound.py) (used for LFV-458)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

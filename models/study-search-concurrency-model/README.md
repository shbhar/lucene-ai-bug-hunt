# Formal models and scripts: study-search-concurrency-model

Tools: TLA+/TLC, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexSearcher#count hangs forever when Weight#count throws, for example on a query timeout, on a segment split into intra-segment partitions (bug LFV-045; [repro](../../repro/LFV-045/), [bug page](../../bugs/LFV-045.html))
- Reusing a TotalHitCountCollectorManager after a failed search silently returns count 0 or hangs (bug LFV-053; [repro](../../repro/LFV-053/))
- LRUQueryCache's 16 hard partitions give a 0% hit rate with the class-javadoc configuration (main only) (bug LFV-054; [repro](../../repro/LFV-054/))
- A timed-out search reports its partial hit count as TotalHits.Relation.EQUAL_TO (bug LFV-077; [repro](../../repro/LFV-077/))
- TaskExecutor#invokeAll returns while a task is still running if the caller is interrupted (bug LFV-056; [repro](../../repro/LFV-056/))

## Files

- [models/CountBarrier.tla](models/CountBarrier.tla)
- [models/LRUCache.tla](models/LRUCache.tla)
- [models/LRUCacheM1.tla](models/LRUCacheM1.tla)
- [models/MC_CountBarrier.tla](models/MC_CountBarrier.tla)
- [models/MC_LRUCache.tla](models/MC_LRUCache.tla)
- [models/MC_LRUCacheM1.tla](models/MC_LRUCacheM1.tla)
- [models/MC_TaskExec.tla](models/MC_TaskExec.tla)
- [models/MC_TopKAcc.tla](models/MC_TopKAcc.tla)
- [models/MC_TopKAccM1.tla](models/MC_TopKAccM1.tla)
- [models/MC_TopKAccM2.tla](models/MC_TopKAccM2.tla)
- [models/MC_TopKAccM3.tla](models/MC_TopKAccM3.tla)
- [models/TaskExec.tla](models/TaskExec.tla)
- [models/TopKAcc.tla](models/TopKAcc.tla)
- [models/TopKAccM1.tla](models/TopKAccM1.tla)
- [models/TopKAccM2.tla](models/TopKAccM2.tla)
- [upstream/grep.py](upstream/grep.py)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

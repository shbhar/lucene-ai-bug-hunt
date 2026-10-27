# Formal models and scripts: executor-pluggable-behaviour-sweep

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Monitor's ParallelMatcher starts blocking worker tasks in its constructor that only finish releases: a Monitor#match that fails before finish (an exception such as TooManyClauses from the presearcher, or a rejected submit) leaves its workers parked on the executor forever, and an executor that runs a worker on the calling thread (CallerRunsPolicy on a saturated pool) makes Monitor#match hang (bug LFV-637; [repro](../../repro/LFV-637/))

## Files

- [parallelmatcher-workers-released-only-by-finish/model/MC_abort.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_abort.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_abort_q1_w3.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_abort_q1_w3.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_callerruns.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_callerruns.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_control_capacity.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_control_capacity.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_control_capacity_cr.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_control_capacity_cr.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_failaftercreate.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_failaftercreate.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_fix_abort.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_fix_abort.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_fix_callerruns.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_fix_callerruns.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/MC_fix_failaftercreate.cfg](parallelmatcher-workers-released-only-by-finish/model/MC_fix_failaftercreate.cfg) (used for LFV-637)
- [parallelmatcher-workers-released-only-by-finish/model/ParallelMatcherLifecycle.tla](parallelmatcher-workers-released-only-by-finish/model/ParallelMatcherLifecycle.tla) (used for LFV-637)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

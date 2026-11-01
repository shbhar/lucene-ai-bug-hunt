# Formal models and scripts: iw-hasuncommittedchanges-read-order-race

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- IndexWriter.hasUncommittedChanges() can return false for an acknowledged, uncommitted delete while another thread flushes or refreshes an NRT reader, because it reads changeCount before the in-RAM state (every release since 4.5.1) (finding LFV-1060; [repro](../../repro/LFV-1060/), [finding page](../../bugs/LFV-1060.html))

## Files

- [hasuncommittedchanges-reads-changecount-before-ram-state/model/HasUncommitted.tla](hasuncommittedchanges-reads-changecount-before-ram-state/model/HasUncommitted.tla) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_NeverFinishAfterApply.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_NeverFinishAfterApply.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_NoFalseNegative.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_NoFalseNegative.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_ReaderNeverFinishes.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_fixed_ReaderNeverFinishes.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_recheck_NoFalseNegative.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_recheck_NoFalseNegative.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_NeverFinishAfterApply.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_NeverFinishAfterApply.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_NoFalseNegative.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_NoFalseNegative.cfg) (used for LFV-1060)
- [hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_ReaderNeverFinishes.cfg](hasuncommittedchanges-reads-changecount-before-ram-state/model/MC_stock_ReaderNeverFinishes.cfg) (used for LFV-1060)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

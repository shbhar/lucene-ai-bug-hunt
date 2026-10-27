# Formal models and scripts: monitor-lifecycle-persistence-mbt

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A Monitor.register call that IndexWriter rejects is not rolled back: the earlier versions of the queries in the call are deleted at the next commit or close, and until then getQuery and match disagree (bug LFV-499; [repro](../../repro/LFV-499/), [bug page](../../bugs/LFV-499.html))
- Monitor.deleteById and Monitor.clear issue their IndexWriter delete outside the commit lock, so a delete or clear that runs while register is indexing a decomposed query leaves that query partly indexed: it stays registered but silently stops matching what its deleted parts match, also after a reopen (bug LFV-501; [repro](../../repro/LFV-501/), [bug page](../../bugs/LFV-501.html))

## Files

- [delete-and-clear-bypass-commit-lock/model/MC_conc_clear_nofail__AtomicQuery.cfg](delete-and-clear-bypass-commit-lock/model/MC_conc_clear_nofail__AtomicQuery.cfg) (used for LFV-501)
- [delete-and-clear-bypass-commit-lock/model/MC_conc_nofail__AtomicQuery.cfg](delete-and-clear-bypass-commit-lock/model/MC_conc_nofail__AtomicQuery.cfg) (used for LFV-501)
- [delete-and-clear-bypass-commit-lock/model/MC_fix_all.cfg](delete-and-clear-bypass-commit-lock/model/MC_fix_all.cfg) (used for LFV-501)
- [delete-and-clear-bypass-commit-lock/model/MonitorWrite.tla](delete-and-clear-bypass-commit-lock/model/MonitorWrite.tla) (used for LFV-501)
- [failed-register-not-rolled-back/model/MC_fix_all.cfg](failed-register-not-rolled-back/model/MC_fix_all.cfg) (used for LFV-499)
- [failed-register-not-rolled-back/model/MC_seq_fail__AtomicQuery.cfg](failed-register-not-rolled-back/model/MC_seq_fail__AtomicQuery.cfg) (used for LFV-499)
- [failed-register-not-rolled-back/model/MC_seq_fail__FailureNoOp.cfg](failed-register-not-rolled-back/model/MC_seq_fail__FailureNoOp.cfg) (used for LFV-499)
- [failed-register-not-rolled-back/model/MonitorWrite.tla](failed-register-not-rolled-back/model/MonitorWrite.tla) (used for LFV-499)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

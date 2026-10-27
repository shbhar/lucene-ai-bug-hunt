# Formal models and scripts: frozen-updates-apply-merge-carryover-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexWriter.forceApply marks a re-resolving update packet finished outside the IndexWriter lock when its re-iteration matches no segment, so a merge that commits in that gap skips the packet's already-resolved doc-values updates and nobody applies them again: an acknowledged updateNumericDocValue/updateDocValues is lost and softUpdateDocument leaves two live versions of the document, also after commit (since 7.0.0) (bug LFV-596; [repro](../../repro/LFV-596/))

## Files

- [empty-reresolve-finish-outside-iw-lock/model/FrozenApplyMerge.tla](empty-reresolve-finish-outside-iw-lock/model/FrozenApplyMerge.tla) (used for LFV-596)
- [empty-reresolve-finish-outside-iw-lock/model/MC.tla](empty-reresolve-finish-outside-iw-lock/model/MC.tla) (used for LFV-596)
- [empty-reresolve-finish-outside-iw-lock/model/MC_emptyS3.cfg](empty-reresolve-finish-outside-iw-lock/model/MC_emptyS3.cfg) (used for LFV-596)
- [empty-reresolve-finish-outside-iw-lock/model/MC_emptyS3atomic.cfg](empty-reresolve-finish-outside-iw-lock/model/MC_emptyS3atomic.cfg) (used for LFV-596)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

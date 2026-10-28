# Formal models and scripts: snapshot-deletion-policies-commit-retention-model-based

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A PersistentSnapshotDeletionPolicy re-opened over its save file counts the persisted snapshots, but getSnapshots is empty and getIndexCommit(gen) returns null until an IndexWriter has opened with it, and no javadoc states this precondition (documentation gap; the behaviour is by design) (bug LFV-890; [repro](../../repro/LFV-890/))

## Files

- [psdp-restarted-policy-counts-snapshots-it-cannot-list/model/PsdpRetention.tla](psdp-restarted-policy-counts-snapshots-it-cannot-list/model/PsdpRetention.tla) (used for LFV-890)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

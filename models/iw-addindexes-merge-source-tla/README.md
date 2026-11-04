# Formal models and scripts: iw-addindexes-merge-source-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A deleteAll() that overlaps addIndexes clears the incoming fields from the writer's field schema, but addIndexes still publishes its segment, so a later document can give such a field another doc-values type and the committed index can no longer be opened by an IndexWriter (finding LFV-1074; [repro](../../repro/LFV-1074/), [finding page](../../bugs/LFV-1074.html))
- With SerialMergeScheduler, a valid addIndexes(CodecReader...) call can run a concurrent call's queued merge on its own thread and fail with that merge's exception, discarding its own completed merge (finding LFV-629; [repro](../../repro/LFV-629/), [finding page](../../bugs/LFV-629.html))
- addIndexes(CodecReader...) throws AlreadyClosedException 'this IndexWriter is closed' on an open writer when it overlaps a deleteAll() that is waiting for a running merge to abort, although deleteAll's javadoc says such callers may receive MergeAbortedException (finding LFV-632; [repro](../../repro/LFV-632/), [finding page](../../bugs/LFV-632.html))

## Files

- [addindexes-codecreader-alreadyclosed-during-deleteall/model/AddIndexesMergeSource.tla](addindexes-codecreader-alreadyclosed-during-deleteall/model/AddIndexesMergeSource.tla) (used for LFV-632)
- [addindexes-codecreader-alreadyclosed-during-deleteall/model/MC.tla](addindexes-codecreader-alreadyclosed-during-deleteall/model/MC.tla) (used for LFV-632)
- [addindexes-codecreader-alreadyclosed-during-deleteall/model/da1_ace.cfg](addindexes-codecreader-alreadyclosed-during-deleteall/model/da1_ace.cfg) (used for LFV-632)
- [addindexes-codecreader-alreadyclosed-during-deleteall/model/da1_fixed.cfg](addindexes-codecreader-alreadyclosed-during-deleteall/model/da1_fixed.cfg) (used for LFV-632)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/AddIndexesMergeSource.tla](deleteall-clears-field-schema-of-inflight-addindexes/model/AddIndexesMergeSource.tla) (used for LFV-1074)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/MC.tla](deleteall-clears-field-schema-of-inflight-addindexes/model/MC.tla) (used for LFV-1074)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/da1_fixed.cfg](deleteall-clears-field-schema-of-inflight-addindexes/model/da1_fixed.cfg) (used for LFV-1074)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/da1_schema.cfg](deleteall-clears-field-schema-of-inflight-addindexes/model/da1_schema.cfg) (used for LFV-1074)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/da1_t2.cfg](deleteall-clears-field-schema-of-inflight-addindexes/model/da1_t2.cfg) (used for LFV-1074)
- [deleteall-clears-field-schema-of-inflight-addindexes/model/xda_schema.cfg](deleteall-clears-field-schema-of-inflight-addindexes/model/xda_schema.cfg) (used for LFV-1074)
- [serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/AddIndexesMergeSource.tla](serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/AddIndexesMergeSource.tla) (used for LFV-629)
- [serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/MC.tla](serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/MC.tla) (used for LFV-629)
- [serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/sms2_foreign.cfg](serial-scheduler-addindexes-fails-with-other-callers-merge-exception/model/sms2_foreign.cfg) (used for LFV-629)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

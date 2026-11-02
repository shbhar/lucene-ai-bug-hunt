# Formal models and scripts: deleteall-during-addindexes-keeps-imported-field-schema

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A deleteAll() that overlaps addIndexes clears the incoming fields from the writer's field schema, but addIndexes still publishes its segment, so a later document can give such a field another doc-values type and the committed index can no longer be opened by an IndexWriter (finding LFV-1074; [repro](../../repro/LFV-1074/))

## Files

- [model/IWSchema.tla](model/IWSchema.tla) (used for LFV-1074)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

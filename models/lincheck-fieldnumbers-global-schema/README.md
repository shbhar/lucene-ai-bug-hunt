# Formal models and scripts: lincheck-fieldnumbers-global-schema

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A deleteAll() that overlaps addIndexes clears the incoming fields from the writer's field schema, but addIndexes still publishes its segment, so a later document can give such a field another doc-values type and the committed index can no longer be opened by an IndexWriter (finding LFV-1074; [repro](../../repro/LFV-1074/), [finding page](../../bugs/LFV-1074.html))
- A doc-values update racing IndexWriter.deleteAll() can be queued after deleteAll has cleared its field from the writer's field schema; applying it then makes every commit, NRT reopen and close() fail (a tragic close with assertions on), or, if a document added in between gives the field another doc-values type, commits a segment whose field cannot be read (finding LFV-635; [repro](../../repro/LFV-635/), [finding page](../../bugs/LFV-635.html))

## Files

- [addindexes-publishes-schema-cleared-by-concurrent-deleteall/model/IWSchema.tla](addindexes-publishes-schema-cleared-by-concurrent-deleteall/model/IWSchema.tla) (used for LFV-1074)
- [updatedocvalues-field-registration-cleared-by-concurrent-deleteall/model/IWSchema.tla](updatedocvalues-field-registration-cleared-by-concurrent-deleteall/model/IWSchema.tla) (used for LFV-635)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

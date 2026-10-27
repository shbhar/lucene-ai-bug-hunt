# Formal models and scripts: indexingchain-schema

Tools: Alloy, Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- The frozen-FieldType validation cache (#15886) keys a field on its first value's type, so for a field built from several field types a later document skips the per-field schema check (or is checked against stale state after a rejected addBatch), depending on value order, flushes and earlier operations (bug LFV-118; [repro](../../repro/LFV-118/), [bug page](../../bugs/LFV-118.html))
- A rejected document leaves its doc-values skip index type in the field's per-document schema state, so later valid documents with that field are rejected until the in-memory segment is flushed (bug LFV-116; [repro](../../repro/LFV-116/))
- IndexWriter#addBatch marks the frozen FieldType of an earlier rejected document as validated, so later documents with that type skip the per-field schema check (a wrong-dimension vector is stored truncated) (bug LFV-127; [repro](../../repro/LFV-127/))
- A failed addIndexes(Directory...) leaves the incoming fields registered in the writer's field schema, so getFieldNames lists them and later valid documents that use those names differently are rejected until the writer is reopened (bug LFV-124; [repro](../../repro/LFV-124/))

## Files

- [addindexes-directory-registers-schema-before-failing/model/FieldNumbersTxn.als](addindexes-directory-registers-schema-before-failing/model/FieldNumbersTxn.als) (used for LFV-124)
- [fieldschema-reset-keeps-skip-index/model/FieldSchemaCache.als](fieldschema-reset-keeps-skip-index/model/FieldSchemaCache.als) (used for LFV-116)
- [fieldschema-reset-keeps-skip-index/model/FieldSchemaCacheSmall.als](fieldschema-reset-keeps-skip-index/model/FieldSchemaCacheSmall.als) (used for LFV-116)
- [fieldschema-reset-keeps-skip-index/model/decode_m1.py](fieldschema-reset-keeps-skip-index/model/decode_m1.py) (used for LFV-116)
- [frozen-type-cache-promotes-rejected-type-via-addbatch/model/FieldSchemaCache.als](frozen-type-cache-promotes-rejected-type-via-addbatch/model/FieldSchemaCache.als) (used for LFV-127)
- [frozen-type-cache-promotes-rejected-type-via-addbatch/model/FieldSchemaCacheSmall.als](frozen-type-cache-promotes-rejected-type-via-addbatch/model/FieldSchemaCacheSmall.als) (used for LFV-127)
- [frozen-type-cache-promotes-rejected-type-via-addbatch/model/decode_m1.py](frozen-type-cache-promotes-rejected-type-via-addbatch/model/decode_m1.py) (used for LFV-127)
- [frozen-type-cache-skips-check-for-multi-type-fields/model/FieldSchemaCache.als](frozen-type-cache-skips-check-for-multi-type-fields/model/FieldSchemaCache.als) (used for LFV-118)
- [frozen-type-cache-skips-check-for-multi-type-fields/model/FieldSchemaCacheSmall.als](frozen-type-cache-skips-check-for-multi-type-fields/model/FieldSchemaCacheSmall.als) (used for LFV-118)
- [frozen-type-cache-skips-check-for-multi-type-fields/model/decode_m1.py](frozen-type-cache-skips-check-for-multi-type-fields/model/decode_m1.py) (used for LFV-118)

## How to run

- Alloy 6: open the `.als` file and run its commands (an instance found for a bug assertion is the bug).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.

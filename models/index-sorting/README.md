# Formal models and scripts: index-sorting

Tools: Alloy.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- With an index sort and a parent field, a document that IndexWriter rejects (a documented, non-aborting IllegalArgumentException) as the last document of an in-memory segment that holds a document block makes the next flush throw ArrayIndexOutOfBoundsException 'Index 2147483647' and close IndexWriter, losing every uncommitted document (since 9.10.0) (finding LFV-1050; [repro](../../repro/LFV-1050/), [finding page](../../bugs/LFV-1050.html))
- IndexWriter accepts doc-values updates of the reserved parent field; with index sorting and document blocks this leaves a segment that fails CheckIndex, or an index whose merges fail (finding LFV-095; [repro](../../repro/LFV-095/), [finding page](../../bugs/LFV-095.html))

## Files

- [model/SortBlocks.als](model/SortBlocks.als) (used for LFV-095, LFV-1050)

## How to run

- Alloy 6: open the `.als` file and run its commands (a counterexample found for one of its assertions is the finding).

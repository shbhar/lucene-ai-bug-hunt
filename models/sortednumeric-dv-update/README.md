# Formal models and scripts: sortednumeric-dv-update

Tools: Alloy.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- A rejected IndexWriter#updateDocValues call (an index-sort field, SORTED or SORTED_SET doc values, or a bad later field in the same call) first registers a never-seen field as doc-values-only, so later legal documents that index that field are rejected until the writer is reopened (finding LFV-134; [repro](../../repro/LFV-134/), [finding page](../../bugs/LFV-134.html))

## Files

- [updatedocvalues-registers-field-before-rejecting/model/sn_api_guard.als](updatedocvalues-registers-field-before-rejecting/model/sn_api_guard.als) (used for LFV-134)

## How to run

- Alloy 6: open the `.als` file and run its commands (a counterexample found for one of its assertions is the finding).

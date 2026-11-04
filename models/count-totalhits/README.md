# Formal models and scripts: count-totalhits

Tools: Alloy.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- FieldExistsQuery#count undercounts documents whose norms-free text field analyzes to no tokens but still has a doc value, so IndexSearcher#count is too low (finding LFV-086; [repro](../../repro/LFV-086/), [finding page](../../bugs/LFV-086.html))

## Files

- [fieldexists-count-terms-docfreq-proxy/model/FieldExistsCount.als](fieldexists-count-terms-docfreq-proxy/model/FieldExistsCount.als) (used for LFV-086)

## How to run

- Alloy 6: open the `.als` file and run its commands (a counterexample found for one of its assertions is the finding).

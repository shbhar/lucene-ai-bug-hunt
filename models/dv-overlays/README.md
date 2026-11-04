# Formal models and scripts: dv-overlays

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- An IOException late in a doc-values update write leaves IndexWriter open but inconsistent: published update files are never checkpointed and get deleted while still referenced, and a failed pooled-reader re-open silently drops the update (finding LFV-110; [repro](../../repro/LFV-110/), [finding page](../../bugs/LFV-110.html))

## Files

- [writefieldupdates-reader-reopen-failure-after-publish/model/tla/DvOverlayFaults.tla](writefieldupdates-reader-reopen-failure-after-publish/model/tla/DvOverlayFaults.tla) (used for LFV-110)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

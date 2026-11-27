# Formal models and scripts: dv-overlays

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- An IOException late in a doc-values update write leaves IndexWriter open but inconsistent: the failed call does not checkpoint the update files it published, so they can be deleted while still referenced, and after a failed pooled-reader re-open the acknowledged update can be missing from later successful commits and from the index after close(), with no further exception (finding LFV-110; [repro](../../repro/LFV-110/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-110.html))

## Files

- [writefieldupdates-reader-reopen-failure-after-publish/model/tla/DvOverlayFaults.tla](writefieldupdates-reader-reopen-failure-after-publish/model/tla/DvOverlayFaults.tla) (used for LFV-110)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).

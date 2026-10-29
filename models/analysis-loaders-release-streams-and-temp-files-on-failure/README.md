# Formal models and scripts: analysis-loaders-release-streams-and-temp-files-on-failure

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A hunspell Dictionary load that fails with the offline sorting of the Dictionary(Directory,...) constructors leaves its temp IndexOutput open (regression since 9.9.0, #12834): through HunspellStemFilterFactory every failed inform keeps a file descriptor on the deleted temp file until GC, and with a caller-supplied tempDir the temp file also stays behind (bug LFV-1006; [repro](../../repro/LFV-1006/))
- ICUTokenizerFactory leaves a custom per-script rule file (rulefiles) open when reading or decoding it fails: parseRules closes its reader only after a successful read loop, so a rule file that is not valid UTF-8, or an I/O error while reading it, leaves the stream the ResourceLoader opened unclosed until garbage collection (every release since 4.1.0) (bug LFV-1010; [repro](../../repro/LFV-1010/))

## Files

- [hunspell-offline-sort-temp-output-not-released-on-failure/model/MC_buggy.cfg](hunspell-offline-sort-temp-output-not-released-on-failure/model/MC_buggy.cfg) (used for LFV-1006)
- [hunspell-offline-sort-temp-output-not-released-on-failure/model/MC_fixed.cfg](hunspell-offline-sort-temp-output-not-released-on-failure/model/MC_fixed.cfg) (used for LFV-1006)
- [hunspell-offline-sort-temp-output-not-released-on-failure/model/OfflineDictLoad.tla](hunspell-offline-sort-temp-output-not-released-on-failure/model/OfflineDictLoad.tla) (used for LFV-1006)
- [icutokenizerfactory-parserules-stream-not-closed-on-failure/model/MC_buggy.cfg](icutokenizerfactory-parserules-stream-not-closed-on-failure/model/MC_buggy.cfg) (used for LFV-1010)
- [icutokenizerfactory-parserules-stream-not-closed-on-failure/model/MC_fixed.cfg](icutokenizerfactory-parserules-stream-not-closed-on-failure/model/MC_fixed.cfg) (used for LFV-1010)
- [icutokenizerfactory-parserules-stream-not-closed-on-failure/model/OfflineDictLoad.tla](icutokenizerfactory-parserules-stream-not-closed-on-failure/model/OfflineDictLoad.tla) (used for LFV-1010)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

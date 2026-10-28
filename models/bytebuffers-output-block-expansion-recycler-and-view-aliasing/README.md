# Formal models and scripts: bytebuffers-output-block-expansion-recycler-and-view-aliasing

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ByteBuffersDirectory.rename ends with a leftover unconditional files.remove(source), so a file that another thread creates (createOutput) or renames onto the source name while the rename runs is silently deleted, although that thread's call returned normally (bug LFV-836; [repro](../../repro/LFV-836/))

## Files

- [bbdirectory-rename-drops-concurrent-source-file/model/BBDir.tla](bbdirectory-rename-drops-concurrent-source-file/model/BBDir.tla) (used for LFV-836)
- [bbdirectory-rename-drops-concurrent-source-file/model/MC_bug.cfg](bbdirectory-rename-drops-concurrent-source-file/model/MC_bug.cfg) (used for LFV-836)
- [bbdirectory-rename-drops-concurrent-source-file/model/MC_fix.cfg](bbdirectory-rename-drops-concurrent-source-file/model/MC_fix.cfg) (used for LFV-836)
- [bbdirectory-rename-drops-concurrent-source-file/model/MC_probe.cfg](bbdirectory-rename-drops-concurrent-source-file/model/MC_probe.cfg) (used for LFV-836)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

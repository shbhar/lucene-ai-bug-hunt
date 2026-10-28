# Formal models and scripts: hardlink-copy-unwrap-bypasses-intermediate-directory-wrappers

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HardlinkCopyDirectoryWrapper.copyFrom unwraps both directory chains to the innermost FSDirectory and hard-links there, so every FilterDirectory in between is skipped for the copied file: an inner TrackingDirectoryWrapper does not record it, an inner LockValidatingDirectoryWrapper does not check its lock, and a one-sided byte-transforming or namespace-owning layer is bypassed (bug LFV-912; [repro](../../repro/LFV-912/))

## Files

- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/HardlinkCopy.tla](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/HardlinkCopy.tla) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/HardlinkCopyShift4.tla](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/HardlinkCopyShift4.tla) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_F1.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_F1.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_F3.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_F3.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_none.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_none.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_none_L2.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_none_L2.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_probe.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_probe.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_small.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_small.cfg) (used for LFV-912)
- [hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_small_dump.cfg](hardlink-copyfrom-unwrap-bypasses-inner-wrappers/model/MC_small_dump.cfg) (used for LFV-912)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

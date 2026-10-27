# Formal models and scripts: locks-pending-deletes

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- NativeFSLockFactory tracks the write locks this JVM holds by real-path string instead of by file, so obtaining write.lock through a second path to the same file (a hard link, or a bind mount of the index directory) opens and closes a second channel on it, which silently drops the holder's OS lock while its ensureValid keeps passing and lets an IndexWriter in another process open and commit to the same index (bug LFV-343; [repro](../../repro/LFV-343/))

## Files

- [nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_bindmount.cfg](nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_bindmount.cfg) (used for LFV-343)
- [nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_hardlink.cfg](nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_hardlink.cfg) (used for LFV-343)
- [nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_hardlink.tla](nativefs-obtain-closes-channel-on-jvm-held-file/model/MC_hardlink.tla) (used for LFV-343)
- [nativefs-obtain-closes-channel-on-jvm-held-file/model/NativeFSLock.tla](nativefs-obtain-closes-channel-on-jvm-held-file/model/NativeFSLock.tla) (used for LFV-343)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).

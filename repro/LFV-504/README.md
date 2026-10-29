# DirectIOIndexInput.fill moves filePos to the next block, and clears the buffer, before its EOF check and before the channel read succeeds, so after a read that threw, a legal seek or the next read on the same input returns bytes of another file position or a spurious EOFException (since 9.0.0)

Bug LFV-504 · Area: misc / store: DirectIODirectory (DirectIOIndexInput.fill and seek) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-504.html](../../bugs/LFV-504.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read a file through the misc DirectIODirectory, one read on an input throws (an EOFException because it asked for more bytes than are left, or an IOException from a transient storage error), and you then seek back to a legal position, or simply read again, on the same input. You expect the bytes at the file pointer, or another exception (after the same read-past-EOF sequences MMapDirectory returns the right bytes). Instead fill had already moved its window position (filePos) to the next block, and on the read path cleared the buffer, before it threw, so the next reads silently return bytes of a different position of the same file (or throw EOFException at a legal position) while getFilePointer reports the requested one.

The test shows that after a readBytes past EOF on a DirectIODirectory input, a legal seek lands in a stale buffer (fill() moved filePos before its EOF check threw), so the next read silently returns a byte of another file position.

## Expected vs actual

Expected: `readByte()` after `seek(block + 1)` returns the byte at position block + 1 (81 for 4096-byte blocks). Actual: `expected:<81> but was:<1>`, the byte at position 1.

## Files

- [src/org/apache/lucene/misc/store/TestDirectIOSeekAfterReadPastEOF.java](src/org/apache/lucene/misc/store/TestDirectIOSeekAfterReadPastEOF.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestDirectIOSeekAfterReadPastEOF
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

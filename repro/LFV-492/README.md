# BufferedIndexInput.refill marks its buffer as filled before readInternal succeeds, so after a failed read the next reads of the same input return up to one buffer of stale bytes instead of re-reading or throwing (regression of LUCENE-1262 since 8.6.0)

Bug LFV-492 · Area: store / BufferedIndexInput (NIOFSDirectory, misc RAFDirectory, BufferedIndexInput.wrap; surfaces through CodecUtil.checkFooter and stored-fields reads) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read an index through NIOFSDirectory (or any BufferedIndexInput), one read fails with an IOException, for example a transient storage error, and the same input is read again, by your retry or by Lucene's own CodecUtil.checkFooter(in, priorException). You expect the next read to re-read the failed region or to throw again. Instead, since 8.6.0 refill has already marked the buffer as holding the new region before readInternal failed, so the next reads silently return up to one buffer of bytes from the previous region (or zeros), and one transient failure while opening a healthy index makes DirectoryReader.open report it as corrupt.

## Expected vs actual

- Expected: You expect the next read to re-read the failed region or to throw again.
- Actual: Since 8.6.0 refill has already marked the buffer as holding the new region before readInternal failed, so the next reads silently return up to one buffer of bytes from the previous region (or zeros), and one transient failure while opening a healthy index makes DirectoryReader.open report it as corrupt.

## Files

- [src/org/apache/lucene/store/TestBufferedIndexInputReadAfterFailedRefill.java](src/org/apache/lucene/store/TestBufferedIndexInputReadAfterFailedRefill.java) (JUnit test)
- [src/org/apache/lucene/index/TestStoredFieldsRetryAfterReadFailure.java](src/org/apache/lucene/index/TestStoredFieldsRetryAfterReadFailure.java) (JUnit test)
- [src/NIOFSReadAfterFailedRefillMain.java](src/NIOFSReadAfterFailedRefillMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestBufferedIndexInputReadAfterFailedRefill

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestStoredFieldsRetryAfterReadFailure
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" NIOFSReadAfterFailedRefillMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/store-cross-impl-differential/](../../models/store-cross-impl-differential/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# IndexWriter buffers the first doc-values update of each field twice in every buffered-updates packet (BufferedUpdates adds again the update that FieldUpdatesBuffer's constructor already recorded), so that update is RAM-accounted twice and, except on the all-same-value numeric path, applied twice to every matching document (since 7.7.0, LUCENE-8590)

Bug LFV-837 · Area: index / buffered doc-values updates (BufferedUpdates, FieldUpdatesBuffer, FrozenBufferedUpdates) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field through IndexWriter (for example updateBinaryDocValue with a 1 MiB value) and expect each update to be buffered once, as every later update of the same field is. Instead BufferedUpdates creates the field's FieldUpdatesBuffer with the update, whose constructor already records it as entry 0, and then adds the same update again, so the first 1 MiB update adds 2,162,956 bytes to ramBytesUsed while each later one adds 1,048,576, and IndexWriter's InfoStream reports '1 field updates; 20 new updates' for one binary update matching 10 documents. Final values stay correct because the duplicate is identical and adjacent; Lucene 7.6.0 buffered the update once, and the double insert arrived with LUCENE-8590 in 7.7.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: BufferedUpdates creates the field's FieldUpdatesBuffer with the update, whose constructor already records it as entry 0, and then adds the same update again, so the first 1 MiB update adds 2,162,956 bytes to ramBytesUsed while each later one adds 1,048,576, and IndexWriter's InfoStream reports '1 field updates; 20 new updates' for one binary update matching 10 documents.

## Files

- [src/org/apache/lucene/index/TestFirstDocValuesUpdateBufferedTwice.java](src/org/apache/lucene/index/TestFirstDocValuesUpdateBufferedTwice.java) (JUnit test)
- [src/FirstDocValuesUpdateBufferedTwiceMain.java](src/FirstDocValuesUpdateBufferedTwiceMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF -Dtests.method=testFirstBinaryUpdateIsAppliedOnce \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFirstDocValuesUpdateBufferedTwice

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" FirstDocValuesUpdateBufferedTwiceMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# FrozenBufferedUpdates narrows its packet RAM estimate to int and BufferedUpdatesStream subtracts it with an int negation, so a deletes/updates packet whose estimate is exactly 2^31 bytes (mod 2^32) leaves the stream's byte count at -2^32: for the rest of the writer's life NRT readers are never current while openIfChanged returns null, and hasUncommittedChanges() and hasDeletions() stay true

Finding LFV-1287 · Area: index / IndexWriter buffered deletes and doc-values updates (BufferedUpdatesStream byte accounting) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1287.html](../../bugs/LFV-1287.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You buffer numeric doc-values updates in one IndexWriter (RAM-based flushing disabled, NoMergePolicy) so that the frozen packet's RAM estimate is exactly 2^31 bytes; the test gets there with 19 updates keyed by a term whose field name is 56,511,839 chars (about 60 MB of real heap), and the 19th update crosses main's 1945 MB delete-buffer limit, so the packet is frozen and applied. After commit() you expect a newly opened NRT reader to be current and hasUncommittedChanges() and hasDeletions() to be false. Instead isCurrent() is false while openIfChanged returns null, and both writer methods return true, for the writer's lifetime, because the packet is added to the stream's long counter as Integer.MIN_VALUE and subtracted as -Integer.MIN_VALUE, which in int arithmetic is Integer.MIN_VALUE again (main -ea and -da, 10.6.0, 10.5.2, 9.12.3).

## Expected vs actual

- Expected: `hasUncommittedChanges()` is false after `commit()`: every update was applied and committed.
- Actual: `hasUncommittedChanges()` is true (`AssertionError` at `TestTwoGiBUpdatePacket.java:54`), with assertions on and off, and it stays true for the writer's life.

## Files

- [src/org/apache/lucene/index/TestTwoGiBUpdatePacket.java](src/org/apache/lucene/index/TestTwoGiBUpdatePacket.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTwoGiBUpdatePacket
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTwoGiBUpdatePacket.java](TestTwoGiBUpdatePacket.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

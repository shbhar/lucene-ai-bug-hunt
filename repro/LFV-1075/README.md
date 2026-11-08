# IndexWriter.tryUpdateDocValue never checks the RAM buffer for the doc-values updates it adds, so they pile up on the heap without bound until the next refresh, flush() or commit

Finding LFV-1075 · Area: index / IndexWriter tryUpdateDocValue (reader-pool doc-values update RAM) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1075.html](../../bugs/LFV-1075.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an NRT reader from an IndexWriter and call tryUpdateDocValue on its documents many times, for example 10,000 counter updates or one soft-delete per document, and expect the writer to keep these pending updates within its RAM buffer and write them out once they use too much RAM, as it does for the same updates made through updateNumericDocValue. Instead every call adds one more pending-update object to the writer's per-segment state and nothing ever compares their size with the buffer: with a 1 MB buffer, 10,000 calls leave 3,340,000 bytes pending, and without a refresh, flush() or commit the heap keeps growing until OutOfMemoryError. IndexWriter.ramBytesUsed() reports 0 for all of it.

## Expected vs actual

- Expected: At most about half the 1 MB buffer (524,288 bytes) of updates pending; the test allows the whole 1 MB.
- Actual: `AssertionError: pending doc-values updates use 3340000 bytes` on main, assertions on and off. An earlier variant of the test (its message adds ", more than the 1 MB RAM buffer") fails with 3,340,000 bytes on 10.6.0, 10.5.2, 10.0.0, 9.12.3 and 9.0.0 and with 3,020,000 bytes on 8.11.4, 8.0.0, 7.7.3 and 7.4.0.

## Files

- [src/org/apache/lucene/index/TestTryUpdateDocValueRamBuffer.java](src/org/apache/lucene/index/TestTryUpdateDocValueRamBuffer.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTryUpdateDocValueRamBuffer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTryUpdateDocValueRamBuffer.java](TestTryUpdateDocValueRamBuffer.java)

Formal models and scripts this finding's part of the hunt used: [../../models/trydvupdate-bypasses-ram-buffer-unbounded-heap/](../../models/trydvupdate-bypasses-ram-buffer-unbounded-heap/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

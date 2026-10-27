# MemoryAccountingBitsetCollector sizes each collector's bitset to the doc-id span of its slice, so under concurrent search a slice whose segments are not adjacent in doc-id order allocates, and charges to the memory tracker, bits for the segments that other slices search

Bug LFV-348 · Area: misc / memory-accounting bitset collector (MemoryAccountingBitsetCollector, MemoryAccountingBitsetCollectorManager, CollectorMemoryTracker) under IndexSearcher slicing · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with MemoryAccountingBitsetCollectorManager and a CollectorMemoryTracker on an IndexSearcher built with an executor, and expect each slice's collector to allocate a bitset only for the segments it searches, as the manager's javadoc says ('each collector only allocates a bitset for the slices it processes'). Instead each collector sizes its bitset to the doc-id range from the first to the last segment of its slice, and the default slicing groups segments by size, not by doc-id order, so in an index whose segments are 10, 300,000, 10, 300,000 and 10 documents the slice of the three small segments allocates a bitset over 600,030 doc ids for its 30 documents. The search tracks 243,960 bytes against 159,480 for the same search without an executor, and a tracker limit of 161,528 bytes that the sequential search fits throws IllegalStateException 'Memory limit exceeded' with the executor; the matched documents are correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Each collector sizes its bitset to the doc-id range from the first to the last segment of its slice, and the default slicing groups segments by size, not by doc-id order, so in an index whose segments are 10, 300,000, 10, 300,000 and 10 documents the slice of the three small segments allocates a bitset over 600,030 doc ids for its 30 documents.

## Files

- [src/org/apache/lucene/misc/search/TestMemoryAccountingBitsetSliceSpan.java](src/org/apache/lucene/misc/search/TestMemoryAccountingBitsetSliceSpan.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.misc.search.TestMemoryAccountingBitsetSliceSpan
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/memory-bitset-collector-manager/](../../models/memory-bitset-collector-manager/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

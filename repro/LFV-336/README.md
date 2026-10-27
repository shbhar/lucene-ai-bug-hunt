# LateInteractionFloatValuesSource#isCacheable always returns true although it reads the multi-vectors from binary doc values, so after an in-place update of a LateInteractionField the query cache keeps returning the old matches of a FunctionMatchQuery or DoubleRange query over it

Bug LFV-336 · Area: core / search: LateInteractionFloatValuesSource (late-interaction multi-vectors) and the query cache · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score documents against a query multi-vector with LateInteractionFloatValuesSource, filter on that score through a query cache (for example FunctionMatchQuery(source, s -&gt; s &gt; 0.75), or DoubleRange#getQuery in the facet module), then replace one document's LateInteractionField multi-vector in place with IndexWriter#updateBinaryDocValue and reopen the reader. You expect the reopened searcher to stop matching that document, because a source that reads doc values must report itself not cacheable once those doc values have been updated (SegmentCacheable). Instead isCacheable always returns true, the segment keeps its core cache key, and the cache keeps serving the pre-update result (5 hits instead of 4 in the repro) until the entry is evicted or the segment is merged away.

## Expected vs actual

- Expected: You expect the reopened searcher to stop matching that document, because a source that reads doc values must report itself not cacheable once those doc values have been updated (SegmentCacheable).
- Actual: isCacheable always returns true, the segment keeps its core cache key, and the cache keeps serving the pre-update result (5 hits instead of 4 in the repro) until the entry is evicted or the segment is merged away.

## Files

- [src/org/apache/lucene/queries/function/TestLateInteractionValuesSourceCaching.java](src/org/apache/lucene/queries/function/TestLateInteractionValuesSourceCaching.java) (JUnit test)
- [src/zeroconfig/ZeroConfigLateInteraction.java](src/zeroconfig/ZeroConfigLateInteraction.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestLateInteractionValuesSourceCaching

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" zeroconfig.ZeroConfigLateInteraction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/query-cache-correctness/](../../models/query-cache-correctness/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

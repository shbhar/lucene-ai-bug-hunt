# DoubleValuesSourceRescorer and LateInteractionRescorer throw NoSuchElementException instead of returning no hits when the searcher's reader has no segments: rescore takes leaves.getFirst before it looks at the hits

Bug LFV-329 · Area: search / rescoring · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search an index that has no segments yet (for example a freshly created or emptied index, or an empty shard), get the expected 0 hits, and pass them to a DoubleValuesSourceRescorer or LateInteractionRescorer. You expect 0 rescored hits, which is what QueryRescorer and SortRescorer return for the same input. Instead rescore throws java.util.NoSuchElementException, because it takes the reader's first segment with leaves.getFirst before it looks at the hits, and there is no first segment.

## Expected vs actual

- Expected: You expect 0 rescored hits, which is what QueryRescorer and SortRescorer return for the same input.
- Actual: rescore throws java.util.NoSuchElementException, because it takes the reader's first segment with leaves.getFirst before it looks at the hits, and there is no first segment.

## Files

- [src/org/apache/lucene/search/TestDoubleValuesSourceRescorerEmptyIndex.java](src/org/apache/lucene/search/TestDoubleValuesSourceRescorerEmptyIndex.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDoubleValuesSourceRescorerEmptyIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

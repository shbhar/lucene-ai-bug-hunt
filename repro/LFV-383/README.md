# Sequential DrillSideways never returns on a segment of more than 2^31 - 2048 documents: DrillSidewaysScorer's int chunk start wraps to Integer.MIN_VALUE, so its union and drill-down-advance loops never exit (released since 4.2.0)

Bug LFV-383 · Area: facet / DrillSideways (sequential DrillSidewaysScorer, union and drill-down-advance strategies) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a faceted DrillSideways search in the default sequential mode on an index whose single segment holds more than 2,147,481,600 documents, which IndexWriter accepts (MAX_DOCS = 2,147,483,519), and expect it to return its hits as it does on a segment 2,048 documents smaller. Instead DrillSidewaysScorer's union and drill-down-advance loops walk the segment in 2048-document chunks with an int nextChunkStart that wraps from 2^31 - 2048 to Integer.MIN_VALUE before the exit test 'nextChunkStart &gt;= maxDoc' can fire, so the search thread spins forever and the hits of the last chunk are never collected. Through the public API on a real segment of MAX_DOCS documents the stock search did not return within 300 s, while the fixed build returned its 3 hits in 0.033 s.

The test shows that `DrillSidewaysScorer`'s union loop on a segment of `IndexWriter.MAX_DOCS` documents (simulated: a `FilterLeafReader` reports that `maxDoc()`) never ends, because the int chunk start wraps from 2^31 - 2048 to `Integer.MIN_VALUE`; the test's base iterator stops it after twice the 2^20 chunks.

## Expected vs actual

Expected `score()` to return and collect the last 3 docs; actual `AssertionError:... still looping after 2097153 docID() calls, collected []`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/TestDrillSidewaysScorerLastChunk.java](src/org/apache/lucene/facet/TestDrillSidewaysScorerLastChunk.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestDrillSidewaysScorerLastChunk
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

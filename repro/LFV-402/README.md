# PointInSetIncludingScoreQuery.ramBytesUsed leaves out its per-value score list, so scoring numeric JoinUtil joins (ScoreMode Max, Min, Avg, Total) under-report their heap (1.5x-2.4x on 9.11.0 and later, 6.2x or more in 8.2.0-9.10.0) and LRUQueryCache under-charges them (since 8.2.0)

Bug LFV-402 · Area: join / JoinUtil numeric query-time join RAM accounting (PointInSetIncludingScoreQuery, Accountable#ramBytesUsed; core LRUQueryCache RAM budget) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a numeric query-time join with JoinUtil.createJoinQuery(fromField, multipleValuesPerDocument, toField, numberType, fromQuery, fromSearcher, ScoreMode.Max) (or Min, Avg, Total) and expect its ramBytesUsed to estimate the heap the query holds, which is what Accountable promises, what LRUQueryCache charges against maxRamBytesUsed, and what the string-field scoring join (TermsIncludingScoreQuery) and the ScoreMode.None numeric join (PointInSetQuery) do. Instead the PointInSetIncludingScoreQuery it returns adds up only its base object, field, from-query and packed join values, and leaves out aggregatedJoinScores, the FloatArrayList with one float per join value that its constructor fills: with 100,000 join values it reports 304,248 bytes while it retains 710,632 (2.3x; 1.5x-2.4x depending on how compactly the keys prefix-code), against 303,272 vs 304,544 for ScoreMode.None. A query cache holding such a join as a filter charges it the same low number (304,408 bytes charged, 710,632 retained), so the cache can hold up to about 2.5 times its budget in these keys; the matching documents are correct.

The test shows that the scoring numeric join query from `JoinUtil.createJoinQuery(..., Integer.class,..., ScoreMode.Max)` (PointInSetIncludingScoreQuery) leaves its per-value score list out of `ramBytesUsed()`.

## Expected vs actual

Expected `ramBytesUsed()` within 5% of the heap RamUsageTester measures (37,224 bytes for 5,000 join values); actual 16,504 (`expected:<37224.0> but was:<16504.0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/join/TestNumericScoreJoinRamBytesUsed.java](src/org/apache/lucene/search/join/TestNumericScoreJoinRamBytesUsed.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestNumericScoreJoinRamBytesUsed
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

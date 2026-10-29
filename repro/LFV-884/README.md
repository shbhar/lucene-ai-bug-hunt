# Grouping collectors never ask for scores on behalf of their group selector, so DoubleRangeGroupSelector or LongRangeGroupSelector over a score-dependent source (e.g. DoubleValuesSource.SCORES) groups every hit by the placeholder score of a non-scoring search (0 in every repro) when no sort or reducer needs scores, or when AllGroups/AllGroupHeads runs alone (since 8.6.0)

Bug LFV-884 · Area: grouping / GroupSelector score needs (FirstPass, SecondPass/TopGroups, AllGroups, AllGroupHeads collector score modes) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-884.html](../../bugs/LFV-884.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group hits into score ranges, for example with new DoubleRangeGroupSelector(DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10)), and sort the groups by a field, expecting each hit in the range of its own score, which is what the same search returns with the default relevance group sort (three ranges, [1,2) = {0, 3}, [2,3) = {1}, [3,4) = {2, 4}, in the repro). Instead GroupingSearch returns one empty group [0,1) (LongRangeGroupSelector: LongRange(0, 1)), and AllGroupsCollectorManager on its own reports one range instead of three, because the grouping collectors take their ScoreMode only from their sorts or reducer, so the query runs without scores and the selector, which reads the collector's Scorable through DoubleValuesSource.fromScorer, sees the placeholder score 0 for every hit. GroupSelector has no way to say that it needs scores; a GroupSelector#needsScores hook (true for the range selectors when their source needs scores), ORed into the four collectors' score modes, fixes it.

The test shows that grouping by `DoubleRangeGroupSelector(DoubleValuesSource.SCORES,...)` with a field group sort runs the first pass without scores, so every hit falls in the range of score 0.

## Expected vs actual

Expected groups `[DoubleRange(1.0, 2.0), DoubleRange(2.0, 3.0)]` for two hits that score 1 and 2; actual `[DoubleRange(0.0, 1.0)]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/grouping/TestScoreRangeGroupingFieldGroupSort.java](src/org/apache/lucene/search/grouping/TestScoreRangeGroupingFieldGroupSort.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestScoreRangeGroupingFieldGroupSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

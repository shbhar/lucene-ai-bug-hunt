# Grouping starts its running maxScore at Float.MIN_VALUE (the smallest positive float), so TopGroups.maxScore and GroupDocs.maxScore report 1.4E-45 instead of 0 when every hit scores 0, and since 10.5.0 (#15873) TopGroupsCollector.getTopGroups reports 1.4E-45 instead of NaN when no scores were computed

Bug LFV-511 · Area: grouping / max-score reporting (TopGroupsCollector, BlockGroupingCollector) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a grouped search whose hits all score 0, for example a BooleanQuery with only FILTER clauses, and sort the documents inside each group by a field; you expect TopGroups.maxScore and each GroupDocs.maxScore to be 0, the highest score of any hit. Instead they are 1.4E-45 (Float.MIN_VALUE), because the running maximum starts at Float.MIN_VALUE, the smallest positive float, instead of below every score; and since 10.5.0 a second-pass TopGroupsCollector built with getMaxScores=false reports the same 1.4E-45 instead of the documented NaN for 'scores were not computed'. The hits, the group order and the counts are correct; only the reported max score is off.

## Expected vs actual

- Expected: You expect TopGroups.maxScore and each GroupDocs.maxScore to be 0, the highest score of any hit.
- Actual: They are 1.4E-45 (Float.MIN_VALUE), because the running maximum starts at Float.MIN_VALUE, the smallest positive float, instead of below every score; and since 10.5.0 a second-pass TopGroupsCollector built with getMaxScores=false reports the same 1.4E-45 instead of the documented NaN for 'scores were not computed'.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingMaxScoreSentinel.java](src/org/apache/lucene/search/grouping/TestGroupingMaxScoreSentinel.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.grouping.TestGroupingMaxScoreSentinel
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

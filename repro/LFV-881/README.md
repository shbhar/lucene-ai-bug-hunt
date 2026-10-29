# GroupingSearch ignores setIncludeMaxScore for doc-block grouping: with field-only group and within-group sorts, TopGroups.maxScore and every GroupDocs.maxScore are NaN, while two-pass grouping with the same settings reports the real max scores (since 8.0.0)

Bug LFV-881 · Area: grouping / GroupingSearch doc-block path (BlockGroupingCollectorManager score mode) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group documents indexed as blocks with new GroupingSearch(groupEndDocs), sort the groups and the documents inside each group by fields, and leave setIncludeMaxScore at its default true (or set it), expecting TopGroups.maxScore and each GroupDocs.maxScore to hold the best score, as the setIncludeMaxScore javadoc ('Whether to include the score of the most relevant document per group') says and as two-pass grouping with the same settings returns (0.22094907 in the repro). Instead both are NaN, because GroupingSearch.groupByDocBlock asks BlockGroupingCollectorManager to compute scores only when one of the two sorts needs them and never reads includeMaxScore. Adding includeMaxScore to that needsScores argument, the rule the two-pass path already follows, fixes it.

doc-block `GroupingSearch` ignores `setIncludeMaxScore(true)` when the group sort and the within-group sort are both field sorts, so `TopGroups.maxScore` is NaN.

## Expected vs actual

Expected: the matching document's score 1.0 (`MatchAllDocsQuery`, one one-document block); actual: NaN (`expected:<1.0> but was:<NaN>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/grouping/TestBlockGroupingIncludeMaxScore.java](src/org/apache/lucene/search/grouping/TestBlockGroupingIncludeMaxScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestBlockGroupingIncludeMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# With an executor, block grouping's TopGroups.maxScore includes groups that are not returned, so it changes with the index slicing (and with groupOffset &gt; 0 it counts the skipped groups); regression in 10.5.0

Bug LFV-239 · Area: grouping / block grouping (TopGroups.mergeBlockGroups and BlockGroupingCollectorManager.reduce; GroupingSearch(Query groupEndDocs) on a searcher with an executor) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index each group as a document block and run block grouping (GroupingSearch(Query groupEndDocs)) with a field group sort on an IndexSearcher that has an executor. You expect TopGroups.maxScore to be the same number whether the search runs sequentially or concurrently: sequential block grouping and two-pass term grouping report the best score of the returned groups, and the javadoc says 'Highest score across all hits'. Since 10.5.0 the concurrent value is the maximum over every group that any slice kept, including groups that are not returned, so it changes with the slicing (sequential 1.0, concurrent 5.0 from an unreturned group, best hit 9.0 in the repro), while the returned groups and documents are correct.

## Expected vs actual

- Expected: You expect TopGroups.maxScore to be the same number whether the search runs sequentially or concurrently: sequential block grouping and two-pass term grouping report the best score of the returned groups, and the javadoc says 'Highest score across all hits'.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/grouping/TestBlockGroupingTopGroupsMaxScoreSlicing.java](src/org/apache/lucene/search/grouping/TestBlockGroupingTopGroupsMaxScoreSlicing.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestBlockGroupingTopGroupsMaxScoreSlicing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

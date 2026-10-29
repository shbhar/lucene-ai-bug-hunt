# Block grouping reports the wrong group maxScore when paging inside groups (withinGroupOffset &gt; 0, relevance order): the score of the first document on the page, or -Infinity for an empty page; regression in 10.5.0

Bug LFV-230 · Area: grouping / block grouping (BlockGroupingCollector.getTopGroups group maxScore; GroupingSearch(Query) and BlockGroupingCollectorManager within-group paging) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-230.html](../../bugs/LFV-230.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index each group as a document block and search with GroupingSearch(Query groupEndDocs) and the default relevance sorts, asking for the second page of documents inside each group with setGroupDocsOffset(1). You expect each group's maxScore to be the best score in the group (GroupDocs documents it as 'Max score in this group'), as two-pass term grouping reports and as block grouping did up to 10.4.0. Since 10.5.0 it is instead the score of the first document on the requested page, or -Infinity when the page is past the end of the group, and TopGroups.maxScore inherits the wrong value; the documents returned are correct.

The test shows that block grouping (`GroupingSearch(Query groupEndDocs)`, default relevance sorts) reports the wrong `GroupDocs.maxScore` when paging inside groups with `setGroupDocsOffset(1)`.

## Expected vs actual

Expected the best score in the group, `2.0`; actual: `1.0`, the score of the first document on the requested page (`group maxScore expected:<2.0> but was:<1.0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/grouping/TestBlockGroupingMaxScoreOffset.java](src/org/apache/lucene/search/grouping/TestBlockGroupingMaxScoreOffset.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestBlockGroupingMaxScoreOffset
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

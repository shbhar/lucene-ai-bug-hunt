# The grouping package documentation says groupOffset 3 with topNGroups 10 returns 7 groups, but FirstPassGroupingCollectorManager and BlockGroupingCollectorManager, which the page names and shows since #16385 (10.6.0), return 10: for them topNGroups is the page size after the offset (documentation defect; the inputs list has also disagreed with the page's own example code since 3.2)

Bug LFV-961 · Area: grouping / package documentation (package-info.java inputs list vs the collector managers it names) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the grouping package overview, which says topNGroups is 'how many top groups to keep' and that groupOffset 3 'means you'll get 7 groups back (assuming topNGroups is 10)', and which names FirstPassGroupingCollectorManager as the first pass and builds BlockGroupingCollectorManager(groupSort, groupOffset, topNGroups,...), so you expect groupOffset 3 with topNGroups 10 to return 7 groups. Instead both managers, like GroupingSearch, treat topNGroups as the number of groups to return after the offset and return 10 groups (ranks 4 to 13). The '7 groups' sentence is right only for the plain FirstPassGroupingCollector and BlockGroupingCollector, which the page named until #16385 rewrote it for the managers, so paging code written from the sentence gets oversized, overlapping pages.

## Expected vs actual

- Expected: You expect groupOffset 3 with topNGroups 10 to return 7 groups.
- Actual: Both managers, like GroupingSearch, treat topNGroups as the number of groups to return after the offset and return 10 groups (ranks 4 to 13).

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingPackageDocOffsetExample.java](src/org/apache/lucene/search/grouping/TestGroupingPackageDocOffsetExample.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupingPackageDocOffsetExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

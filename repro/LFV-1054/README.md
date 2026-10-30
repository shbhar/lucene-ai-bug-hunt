# GroupingSearch with setAllGroups(true) returns totalGroupCount null ("not computed") for a query without hits or a page past the last group, although it computed the group count

Bug LFV-1054 · Area: grouping / GroupingSearch two-pass grouping (term, function and GroupSelector grouping), TopGroups.totalGroupCount · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group with new GroupingSearch("group") and setAllGroups(true) so that TopGroups.totalGroupCount tells you how many groups match, and for a query that matches nothing you expect 0 (TopGroups documents null as 'this value is not computed'). Instead search returns early with an empty TopGroups whose totalGroupCount is null, skipping the step that adds the group count it has already computed, so getAllMatchingGroups.size is 0 while totalGroupCount is null; the same happens for a page at or past the last group (4 hits in 3 groups, offset 3: null instead of 3). The package documentation's own example, 'int totalGroupCount = result.totalGroupCount;', then throws NullPointerException.

## Expected vs actual

- Expected: You expect 0 (TopGroups documents null as 'this value is not computed').
- Actual: search returns early with an empty TopGroups whose totalGroupCount is null, skipping the step that adds the group count it has already computed, so getAllMatchingGroups.size is 0 while totalGroupCount is null; the same happens for a page at or past the last group (4 hits in 3 groups, offset 3: null instead of 3).

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchNoHitsTotalGroupCount.java](src/org/apache/lucene/search/grouping/TestGroupingSearchNoHitsTotalGroupCount.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchNoHitsTotalGroupCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

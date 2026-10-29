# SearchGroup.merge (the shard-merge helper of grouped search) with topN = Integer.MAX_VALUE ('all remaining groups') throws OutOfMemoryError because it pre-sizes its result list by topN (regression in 6.2.0, LUCENE-7356), and with offset &gt;= 1 throws NullPointerException because offset + topN wraps to a negative queue bound (since 3.3.0)

Bug LFV-958 · Area: grouping / SearchGroup.merge (merging first-pass top groups across shards or slices; reduce step of FirstPassGroupingCollectorManager) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge the first-pass top groups of two shards (three groups in all) with SearchGroup.merge(shardGroups, 0, Integer.MAX_VALUE, groupSort) to get every group, and expect the three groups, the same as topN = 10 returns, as 6.1.0 and earlier returned for the same call, and as core TopDocs.merge(0, Integer.MAX_VALUE,...) does for hits. Instead merge throws OutOfMemoryError 'Requested array size exceeds VM limit', because since 6.2.0 (LUCENE-7356) it pre-sizes its result list with new ArrayList&lt;&gt;(topN). With offset 1 it throws NullPointerException, because maxQueueSize = offset + topN wraps to a negative int and the pruning loop polls an empty queue (NoSuchElementException on 3.x).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Merge throws OutOfMemoryError 'Requested array size exceeds VM limit', because since 6.2.0 (LUCENE-7356) it pre-sizes its result list with new ArrayList&lt;&gt;(topN).

## Files

- [src/org/apache/lucene/search/grouping/TestSearchGroupMergeNoLimit.java](src/org/apache/lucene/search/grouping/TestSearchGroupMergeNoLimit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestSearchGroupMergeNoLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/grouping-offset-plus-limit-overflow/](../../models/grouping-offset-plus-limit-overflow/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

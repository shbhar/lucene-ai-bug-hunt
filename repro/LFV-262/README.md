# TopDocs.merge(start, topN,...) returns a page of null ScoreDocs, with no error, when start + topN overflows int (for example start = 1 and topN = Integer.MAX_VALUE meaning 'no limit')

Bug LFV-262 · Area: core / search: TopDocs.merge paging window (mergeAux; score, Sort and tie-breaker overloads) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge two shards' results (5 hits in total) with TopDocs.merge(1, Integer.MAX_VALUE, shards) to get every hit after the first, and expect the 4 remaining hits in score order, the same as TopDocs.merge(1, 4, shards) returns. Instead you get an array of length 4 in which every entry is null, and no exception: merge adds start + topN in int, the sum wraps to a negative number, and the loop that copies hits never runs. The Sort and tie-breaker overloads behave the same way; start + topN = 2^31 - 1 still works and 2^31 fails.

The test shows that `TopDocs.merge(start, topN, shardHits)` returns null hits when `start + topN` overflows int, here `merge(1, Integer.MAX_VALUE,...)` over one TopDocs with 2 hits.

## Expected vs actual

Expected: the page `[second hit]`. Actual: `[null]` (`expected:<doc=1 score=1.0 shardIndex=-1> but was:<null>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestTopDocsMergeWindowOverflow.java](src/org/apache/lucene/search/TestTopDocsMergeWindowOverflow.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTopDocsMergeWindowOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/priorityqueue-topdocs-merge/](../../models/priorityqueue-topdocs-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

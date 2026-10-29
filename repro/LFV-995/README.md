# RescoreTopNQuery and LatLonPoint.nearest size their priority queue from the caller's n, not from what the index can return, so n = Integer.MAX_VALUE ('all') throws IllegalArgumentException and a large n throws OutOfMemoryError, even on an index of a few documents

Bug LFV-995 · Area: search / top-n rescoring and geo nearest search (queue sizing: RescoreTopNQuery.rewrite; LatLonPoint.nearest -> NearestNeighbor) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with new RescoreTopNQuery(query, source, Integer.MAX_VALUE), or call LatLonPoint.nearest(searcher, field, lat, lon, Integer.MAX_VALUE), to get every candidate, and expect them all back, as n = 1000 (or 100) gives on a 9-document index and as nearest's documented equivalent, a MatchAllDocsQuery search sorted by distance, gives for Integer.MAX_VALUE. Instead both build a priority queue of n + 1 slots before looking at the index: Integer.MAX_VALUE throws IllegalArgumentException('maxSize must be &gt;= 0 and &lt; 2147483631') from inside the search, and a large n below that throws OutOfMemoryError (n = 3*10^8 under a 1 GB heap). IndexSearcher.search has capped the same request at maxDoc since LUCENE-2119, as Michael McCandless proposed there; the analogous fix caps RescoreTopNQuery's queue at maxDoc and nearest's at the number of indexed points (not maxDoc, because nearest returns a multi-valued document once per point).

The test shows that `new RescoreTopNQuery(q, src, Integer.MAX_VALUE)` and `LatLonPoint.nearest(searcher, field, lat, lon, Integer.MAX_VALUE)` size their priority queue from n, not from the index, so asking for 'all' fails even on a 1-document index.

## Expected vs actual

Expected: 1 hit from each API (as with any n &gt;= 1). Actual (-ea and -da): IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647' from PriorityQueue, reported as an AssertionError.

## Files

- [src/org/apache/lucene/search/TestTopNQueuePresizedByN.java](src/org/apache/lucene/search/TestTopNQueuePresizedByN.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
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
     execute --select-class org.apache.lucene.search.TestTopNQueuePresizedByN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn/](../../models/rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

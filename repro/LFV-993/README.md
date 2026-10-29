# On main, a field-sorted TopFieldCollectorManager with numHits &gt;= ArrayUtil.MAX_ARRAY_LENGTH (e.g. Integer.MAX_VALUE) throws OutOfMemoryError instead of the IllegalArgumentException that 10.x throws, because since #14873 FieldValueHitQueue.create allocates the numHits-sized sort comparators before the PriorityQueue size check runs (unreleased regression of the SOLR-5661 guard)

Bug LFV-993 · Area: core / search: field-sorted top-N collection (FieldValueHitQueue, TopFieldCollectorManager) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call new TopFieldCollectorManager(sort, Integer.MAX_VALUE, null, 1).newCollector with any sort (a long field, a string field, Sort.RELEVANCE), directly or through SortRescorer.rescore(searcher, topDocs, Integer.MAX_VALUE), and expect the IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631' that Lucene 4.7.0 to 10.5.2 and the 10.6 RC throw, and that main's score-ordered TopScoreDocCollectorManager still throws. On main you get OutOfMemoryError 'Requested array size exceeds VM limit' instead ('Java heap space' at 2147483631 with a 1 GB heap). Since #14873, FieldValueHitQueue.create builds the sort comparators, each of which pre-allocates a numHits-sized array, before it calls the PriorityQueue constructor, whose size check exists to prevent exactly this confusing OutOfMemoryError (SOLR-5661).

The test shows that on main `new TopFieldCollectorManager(sort, Integer.MAX_VALUE, null, 1).newCollector()` with a LONG sort allocates the numHits-sized comparator in `FieldValueHitQueue.create` before PriorityQueue's size check runs (regression from #14873).

## Expected vs actual

Expected: PriorityQueue's `IllegalArgumentException` ('maxSize must be &gt;= 0 and &lt; 2147483631'), as in 4.7.0-10.5.2. Actual: `OutOfMemoryError: Requested array size exceeds VM limit`.

## Files

- [src/org/apache/lucene/search/TestTopFieldCollectorManagerMaxNumHits.java](src/org/apache/lucene/search/TestTopFieldCollectorManagerMaxNumHits.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestTopFieldCollectorManagerMaxNumHits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

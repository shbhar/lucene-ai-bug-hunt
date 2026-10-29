# On main, LRUQueryCache.clear empties its partitions one at a time and then calls onClear with no lock held, so an entry cached during clear survives while a subclass that resets its statistics in onClear drops it (it reports 0 entries for a cache holding 1, and -1 once that entry is evicted); regression from #15558, main only

Bug LFV-991 · Area: search / query cache (LRUQueryCache.clear and the onClear statistics callback) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass LRUQueryCache to keep per-index statistics the way its class javadoc suggests (count in onCacheEntryInserted/onCacheEntryEvicted, reset in onClear) and call clear while searches are running. You expect the subclass's numbers to match getCacheSize and ramBytesUsed afterwards, as on 10.x, where clear and onClear run under the same lock every insertion takes; on main, clear empties the partitions one at a time, each under its own lock, and then calls onClear with no lock, so an entry that a search caches into an already emptied partition (or just before onClear) stays in the cache while onClear wipes its count. The subclass then reports 0 entries and 0 bytes for a cache that holds 1 entry of 336 bytes, -1 and -336 once that entry is evicted, and the error lasts until the next clear.

The test shows that on main, `LRUQueryCache.clear()` (1 partition) releases the partition lock before it calls `onClear()`, so an entry a search caches in that window stays cached while a subclass that counts entries in `onCacheEntryInserted` and resets in `onClear()` drops it.

## Expected vs actual

Expected: callback count 1 = `getCacheSize()`. Actual: 0 for a cache holding 1 entry, `expected:<1> but was:<0>`, with -ea and -da.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheClearOnClear.java](src/org/apache/lucene/search/TestLRUQueryCacheClearOnClear.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheClearOnClear
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/lruquerycache-partitioned-clear-and-callback-serialization/](../../models/lruquerycache-partitioned-clear-and-callback-serialization/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

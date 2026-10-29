# On main, LRUQueryCache's onCacheEntryInserted/onCacheEntryEvicted javadoc still calls ramBytesUsed 'the total RAM cost of this entry', but since #16657 a query's shared RAM is reported only with its first inserted entry and its last removed entry, so one entry is inserted and evicted with different amounts and per-segment or per-index RAM kept through these callbacks drifts (one index stays positive, another goes negative) (documentation defect, main only)

Bug LFV-1008 · Area: search / query cache (LRUQueryCache unified callback javadoc and MIGRATE.md) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass LRUQueryCache to keep per-index RAM statistics in onCacheEntryInserted/onCacheEntryEvicted, whose javadoc says ramBytesUsed is 'the total RAM cost of this entry' (and of the evicted entry), so you expect each entry to give back at eviction what it added at insertion and an index's total to return to 0 once its entries are gone. Since #16657 the cache deliberately counts a query's own RAM once per distinct query and reports it only with the query's first inserted entry and with the entry that drops its last reference, usually entries of different segments or indices: in a two-segment index one entry is inserted with 344 bytes and evicted with 160, and with two indices sharing one cache index A keeps +184 bytes after it is closed while index B ends at -184. The dedup and the cache's own accounting are correct

The test shows that, since #16657, `LRUQueryCache.onCacheEntryInserted` and `onCacheEntryEvicted` report different `ramBytesUsed` for the same entry (the query's shared bytes go with its first inserted and last removed entry), although both javadocs call it "the total RAM cost of this entry" (a documentation defect).

## Expected vs actual

Expected segment 0's entry to be evicted with the bytes it was inserted with; actual: `expected:<344> but was:<160>` in a two-segment index after `clearCoreCacheKey(segment 0)` and `cleanUp()`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheEntryCallbackBytes.java](src/org/apache/lucene/search/TestLRUQueryCacheEntryCallbackBytes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheEntryCallbackBytes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

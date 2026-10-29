# On main, LRUQueryCache.cleanUp is synchronized on the cache object and takes partition locks while it holds that monitor, so a subclass whose statistics callbacks synchronize on the cache (synchronized methods or synchronized (this)) deadlocks the cleanup thread against a search, and every later search whose callback needs the monitor hangs too; regression from #15558, main only

Bug LFV-1001 · Area: search / query cache (LRUQueryCache.cleanUp locking vs the statistics callbacks) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass LRUQueryCache to keep your own statistics in the callbacks its javadoc offers (onHit, onMiss, onCacheEntryInserted, onCacheEntryEvicted) and make them thread-safe the plain Java way, with synchronized methods or synchronized (this), and you let the cache clean up stale entries as documented. You expect searches and cleanUp, which its javadoc calls 'safe to call concurrently', to keep running, as the same subclass does on 10.x, where Lucene never locks the cache object itself; on main, cleanUp is a synchronized method, so it holds the cache object's monitor while it waits for a partition lock, while a search holds that partition lock and waits for the same monitor inside your callback. Both threads wait for each other forever (the JVM's deadlock detector names the cycle for the insertion callback; for onHit it cannot, because a read-lock holder has no owner), and every later search whose callback needs the monitor, every clearQuery on that partition and every later cleanUp block behind them.

The test shows that on main, LRUQueryCache.cleanUp() holds the cache monitor while it waits for a partition lock, so a subclass whose onCacheEntryInserted uses synchronized (this) deadlocks a search against cleanUp().

## Expected vs actual

Expected: the search finishes; actual on main 645d4fe: after 10 s search=BLOCKED (cache monitor), cleanUp()=WAITING (partition lock), with -ea and -da.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheCleanUpDeadlock.java](src/org/apache/lucene/search/TestLRUQueryCacheCleanUpDeadlock.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheCleanUpDeadlock
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/lruquerycache-partitioned-clear-and-callback-serialization/](../../models/lruquerycache-partitioned-clear-and-callback-serialization/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

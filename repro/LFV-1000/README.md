# On main, LRUQueryCache's class javadoc still says its statistics callbacks are 'called synchronously and under a lock', but since #15558 the callbacks of different partitions run at the same time and onClear runs under no lock, and neither the javadoc nor MIGRATE.md tells subclasses that their statistics must now be thread-safe (documentation defect, main only)

Bug LFV-1000 · Area: search / query cache (LRUQueryCache callback javadoc and MIGRATE.md) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass LRUQueryCache to keep per-index statistics in its callbacks, as the class javadoc suggests, and trust its sentence that the callbacks 'are called synchronously and under a lock', which on 10.x meant one cache-wide write lock around every insertion, eviction and clear callback, so plain fields were safe. On main, #15558 gave each partition (16 by default) its own lock, so insertion and eviction callbacks of different partitions run at the same time (2 at once in a deterministic JUnit, up to 3 in a stress where a plain counter loses updates), and clear calls onClear with no lock held. The partitioning itself is intended

The test shows that on main two `LRUQueryCache.onCacheEntryInserted` callbacks for queries in different partitions run at the same time, although the class javadoc still says the callbacks "are called synchronously and under a lock" (true for one cache-wide lock on 10.x; a documentation defect, the concurrency itself is intended).

## Expected vs actual

Expected (javadoc / 10.x): one callback at a time. Actual: `insertion callbacks running at the same time expected:<1> but was:<2>` (two threads, bounded latch waits, -ea and -da).

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheCallbacksOverlap.java](src/org/apache/lucene/search/TestLRUQueryCacheCallbacksOverlap.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheCallbacksOverlap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/lruquerycache-partitioned-clear-and-callback-serialization/](../../models/lruquerycache-partitioned-clear-and-callback-serialization/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

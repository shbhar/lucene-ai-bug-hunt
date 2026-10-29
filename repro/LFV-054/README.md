# LRUQueryCache's 16 hard partitions give a 0% hit rate with the class-javadoc configuration (main only)

Bug LFV-054 · Area: search / query cache · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-054.html](../../bugs/LFV-054.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On main, you build an LRUQueryCache with the configuration from its class javadoc (maxSize 256, 50 MB) and run one filter over more than 16 segments. You expect the filter to be cached. Instead maxSize and RAM are hard-split across 16 partitions routed by query, so it gets a 0% hit rate forever; maxSize &lt; 16 caches nothing, and partition counts that are 0, negative or not a power of two crash or waste capacity.

The test shows that on main an `LRUQueryCache(256, 64 MB, ctx -> true, +inf)` never hits for one filter over 17 one-document segments: 16 partitions of 16 entries, and all of one query's entries go to the same partition.

## Expected vs actual

Expected 17 cache hits when the same TermQuery is searched a second time (17 entries fit in maxSize 256). Actual: 0 hits; each insert evicts the entry that the next segment's lookup needs.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheManySegments.java](src/org/apache/lucene/search/TestLRUQueryCacheManySegments.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheManySegments
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-search-concurrency-model/](../../models/study-search-concurrency-model/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

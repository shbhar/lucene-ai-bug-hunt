# TopSuggestDocs.merge, documented as analogous to TopDocs.merge, sizes its priority queue by topN before it reads any shard, so merging with topN = Integer.MAX_VALUE ('all') throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631' even for 3 hits or no shards (NegativeArraySizeException before 7.5.0), while TopDocs.merge returns the hits; a large legal topN allocates topN + 1 queue slots for a few hits

Bug LFV-1002 · Area: suggest / document suggester: TopSuggestDocs.merge (merging per-shard suggestion results) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You collect suggestions from two index shards (for example SuggestIndexSearcher.suggest(query, 5, false) on each, giving 3 and 2 hits) and combine them with TopSuggestDocs.merge(Integer.MAX_VALUE, shards) to keep them all; its javadoc calls it 'Analogous to TopDocs#merge(int, TopDocs)' and sets no bound on topN, so you expect the 5 hits that merge(10,...) returns and that TopDocs.merge(Integer.MAX_VALUE,...) returns for ordinary hits. Instead merge throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647', even for a single empty shard, because it creates its PriorityQueue with topN slots before it looks at any shard (TopSuggestDocs.java:116-117), while TopDocs.merge sizes its queue by the number of shards and its result by the hits it received. Below that limit, a large topN such as 10^8 makes merge allocate topN + 1 slots for a handful of hits.

The test shows that TopSuggestDocs.merge of two one-hit shards with topN = Integer.MAX_VALUE ("all hits") throws, because it sizes its PriorityQueue by topN before it reads any shard (TopSuggestDocs.java:117).

## Expected vs actual

Expected: the 2 merged hits, as with topN = 10 and as TopDocs.merge, its documented analogue, returns. Actual (-ea and -da): IllegalArgumentException "maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647".

## Files

- [src/org/apache/lucene/search/suggest/document/TestTopSuggestDocsMergeAllHits.java](src/org/apache/lucene/search/suggest/document/TestTopSuggestDocsMergeAllHits.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestTopSuggestDocsMergeAllHits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

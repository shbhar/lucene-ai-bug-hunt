# Sandbox PhraseWildcardQuery's Weight reports isCacheable true although its one expansion budget is spent across all of the reader's segments (smallest first), so after a reopen changes the segment set a query cache returns hits that differ from an uncached search on the same reader (since 8.4.0)

Finding LFV-1471 · Area: sandbox / search: PhraseWildcardQuery (PhraseWeight#isCacheable) and the query cache · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new PhraseWildcardQuery.Builder("f", 1).addMultiTerm(new PrefixQuery(new Term("f", "a"))).addTerm(new Term("f", "x")).build(), count it through an LRUQueryCache on a one-segment reader whose segment holds 'aa x' (count 1), then add a smaller segment holding 'ab x' and reopen. You expect the cached count on the reopened reader to equal the uncached count on that reader (1: the one allowed expansion is spent on the smaller new segment first, as documented), because a weight whose per-segment result depends on the other segments must not report itself cacheable (SegmentCacheable). Instead the query's Weight inherits PhraseWeight#isCacheable == true, the cache reuses the old segment's entry computed when it was the only segment, and the cached count is 2 (in the other direction a segment can also lose its hits).

## Expected vs actual

- Expected: On the reopened reader the cached count equals the uncached count: 1.
- Actual: The cached count is 2: `AssertionError: expected:<1> but was:<2>` at line 86, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestPhraseWildcardQueryCacheable.java](src/org/apache/lucene/sandbox/search/TestPhraseWildcardQueryCacheable.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestPhraseWildcardQueryCacheable
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

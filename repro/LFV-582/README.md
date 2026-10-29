# SynonymQuery, MultiPhraseQuery, CombinedFieldQuery and SpanOrQuery ask IndexSearcher#termStats only about terms that occur in the local reader, so an override that supplies distributed (whole-collection) statistics is never asked about a query term that occurs only on other shards, and documents on those shards score differently than in the whole collection (since 8.3.0, LUCENE-8921)

Bug LFV-582 · Area: search / IndexSearcher#termStats distributed-statistics hook (SynonymQuery, MultiPhraseQuery, CombinedFieldQuery, SpanWeight) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-582.html](../../bugs/LFV-582.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search one shard of a collection with an IndexSearcher whose termStats and fieldStats overrides return the statistics of the whole collection, as their javadoc suggests, and run SynonymQuery(quick, fast) where 'fast' occurs only on another shard. You expect each document to get the score it gets when you search the whole collection, as TermQuery and PhraseQuery do. Instead the query asks the override only about 'quick', because it calls termStats only for terms whose local docFreq is above 0, so it scores the document 0.573 instead of 0.170 and ranks it above an equally relevant document on the other shard; MultiPhraseQuery, CombinedFieldQuery and SpanOrQuery are off the same way.

The test shows that `SynonymQuery` never asks an `IndexSearcher#termStats` override about a synonym that is absent from the local shard, so a shard searcher that supplies whole-collection statistics scores `SynonymQuery(quick, fast)` differently from the collection (shard 0 = {quick}, shard 1 = {fast, fast}).

## Expected vs actual

Expected doc 0's score on shard 0 to equal its score in the whole collection, `0.21363801`; actual `0.44583148` (the statistics of `fast` are left out), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestSynonymQueryDistributedStats.java](src/org/apache/lucene/search/TestSynonymQueryDistributedStats.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestSynonymQueryDistributedStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

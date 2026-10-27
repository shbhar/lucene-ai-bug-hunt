# SynonymQuery, MultiPhraseQuery, CombinedFieldQuery and SpanOrQuery ask IndexSearcher#termStats only about terms that occur in the local reader, so an override that supplies distributed (whole-collection) statistics is never asked about a query term that occurs only on other shards, and documents on those shards score differently than in the whole collection (since 8.3.0, LUCENE-8921)

Bug LFV-582 · Area: search / IndexSearcher#termStats distributed-statistics hook (SynonymQuery, MultiPhraseQuery, CombinedFieldQuery, SpanWeight) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-582.html](../../bugs/LFV-582.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search one shard of a collection with an IndexSearcher whose termStats and fieldStats overrides return the statistics of the whole collection, as their javadoc suggests, and run SynonymQuery(quick, fast) where 'fast' occurs only on another shard. You expect each document to get the score it gets when you search the whole collection, as TermQuery and PhraseQuery do. Instead the query asks the override only about 'quick', because it calls termStats only for terms whose local docFreq is above 0, so it scores the document 0.573 instead of 0.170 and ranks it above an equally relevant document on the other shard; MultiPhraseQuery, CombinedFieldQuery and SpanOrQuery are off the same way.

## Expected vs actual

- Expected: Doc A scores the same on the shard as in the whole collection: 0.170 for `SynonymQuery(quick, fast)`, 0.325 for `CombinedFieldQuery`, 1.316 for `MultiPhraseQuery`.
- Actual: 0.573, 0.565 and 1.147 on the shard (span-or 0.573 instead of 0.743): 4 of 7 core and 2 of 3 span tests fail, the controls pass, assertions on and off.

## Files

- [src/org/apache/lucene/search/TestDistributedStatsLocallyAbsentTerm.java](src/org/apache/lucene/search/TestDistributedStatsLocallyAbsentTerm.java) (JUnit test)
- [src/repro/DistributedStatsReleaseCheck.java](src/repro/DistributedStatsReleaseCheck.java) (main() class)
- [src2/repro/DistributedStatsReleaseCheck.java](src2/repro/DistributedStatsReleaseCheck.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDistributedStatsLocallyAbsentTerm

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" repro.DistributedStatsReleaseCheck
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" repro.DistributedStatsReleaseCheck
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDistributedStatsLocallyAbsentTerm.java](TestDistributedStatsLocallyAbsentTerm.java)
- [TestSpanDistributedStatsLocallyAbsentTerm.java](TestSpanDistributedStatsLocallyAbsentTerm.java)
- [optionA-fix-with-tests.diff](optionA-fix-with-tests.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

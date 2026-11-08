# BlendedTermQuery blends shard-local statistics in rewrite() and passes the blended values to IndexSearcher#termStats, so an override that returns per-term distributed statistics undoes the blend and the blended terms no longer score alike (known: elastic/elasticsearch#42957 and #9884, partial overlap) (released)

Finding LFV-1103 · Area: search / BlendedTermQuery with IndexSearcher statistics overrides · Priority: medium · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search a shard with an IndexSearcher whose termStats/fieldStats overrides add another shard's statistics, as the in-tree ShardSearchingTestBase does, and expect a BlendedTermQuery over 'apple' and 'pear' to score the two one-word documents identically, as the class javadoc promises. Instead BlendedTermQuery.rewrite blends the shard's own statistics (df = max, ttf = sum) and TermQuery passes those blended values as the docFreq/totalTermFreq arguments of termStats, which the override turns back into per-term values: 'apple' scores 0.630 and 'pear' 0.131.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: BlendedTermQuery.rewrite blends the shard's own statistics (df = max, ttf = sum) and TermQuery passes those blended values as the docFreq/totalTermFreq arguments of termStats, which the override turns back into per-term values: 'apple' scores 0.630 and 'pear' 0.131.

## Files

- [src/org/apache/lucene/search/TestBlendedTermQueryDistributedStats.java](src/org/apache/lucene/search/TestBlendedTermQueryDistributedStats.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestBlendedTermQueryDistributedStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

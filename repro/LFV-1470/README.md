# Sandbox PhraseWildcardQuery.equals/hashCode ignore maxMultiTermExpansions and segmentOptimizationEnabled, so two phrase queries that match different documents compare equal: BooleanQuery rewrites FILTER(limit 100) + MUST_NOT(limit 1) to a no-match query, a query cache serves one query's hits for the other, and IndexWriter keeps only one of two such delete-by-query calls (since 8.4.0)

Finding LFV-1470 · Area: sandbox / search: PhraseWildcardQuery equality (expansion budget, segment optimization); consequences in search / BooleanQuery rewrite, the query cache and index / IndexWriter delete-by-query buffering · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a bx' and 'a by' and build the phrase "a b*" twice with PhraseWildcardQuery.Builder, once with maxMultiTermExpansions 100 (it matches both documents) and once with 1 (it matches one), and you expect FILTER(limit 100) + MUST_NOT(limit 1) to match one document. Instead equals and hashCode compare only slop and the phrase terms, so the two queries are equal and BooleanQuery rewrites the pair to MatchNoDocsQuery("FILTER clause also in MUST_NOT"): count 0. The same equality merges the two queries in a query cache (one limit's hits are returned for the other) and in IndexWriter's delete-by-query buffer (one of two deletes is lost), and segmentOptimizationEnabled is ignored the same way although it also changes the matches once the budget binds.

## Expected vs actual

- Expected: `FILTER(limit 100) + MUST_NOT(limit 1)` counts 1 document, `a by`.
- Actual: It counts 0: `AssertionError: expected:<1> but was:<0>` at line 61, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestPhraseWildcardQueryEquality.java](src/org/apache/lucene/sandbox/search/TestPhraseWildcardQueryEquality.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestPhraseWildcardQueryEquality
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

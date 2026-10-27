# QueryBuilder hands a token boost (BoostAttribute) above 1 or equal to 0 on a word that shares its position with a synonym straight to SynonymQuery, which throws IllegalArgumentException, so every query containing that word fails, while the same weight on a word without a synonym works

Bug LFV-588 · Area: core / util QueryBuilder token boosts on stacked tokens (newSynonymQuery; BoostAttribute from DelimitedBoostTokenFilter and weighted synonyms; reached through the classic and simple query parsers) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure weighted synonyms ('leopard, panthera|1.5') or let users type boosts ('leopard|2'), and expect QueryBuilder to apply the weight, as it does for a word without a synonym: 'lion =&gt; simba|1.5' gives (body:simba)^1.5 and 'tiger|2' gives (body:tiger)^2.0. But when the boosted token shares its position with another token, QueryBuilder.newSynonymQuery passes the raw boost to SynonymQuery.Builder.addTerm, which accepts only (0, 1], so building the query throws IllegalArgumentException 'boost must be a positive float between 0 (exclusive) and 1 (inclusive)' and QueryParser.parse('spotted leopard') fails with it instead of returning a query or a ParseException. Weights in (0, 1] work on both paths; whether 1.5 or 0 works depends only on whether the analyzer stacked the token.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/util/TestQueryBuilderStackedBoostRange.java](src/org/apache/lucene/util/TestQueryBuilderStackedBoostRange.java) (JUnit test)
- [src2/org/apache/lucene/util/TestQueryBuilderStackedBoostRange.java](src2/org/apache/lucene/util/TestQueryBuilderStackedBoostRange.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestQueryBuilderStackedBoostRange
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestQueryBuilderStackedBoostRange
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

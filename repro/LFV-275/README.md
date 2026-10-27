# TermInSetQuery.newIndexOrDocValuesQuery neither documents nor checks that its index-side rewrite method must be constant-score and keep every term, so a scoring or top-terms rewrite builds an IndexOrDocValuesQuery whose two sides disagree and whose hits, scores and explain then depend on the execution path (documentation / API-validation defect)

Bug LFV-275 · Area: core / search: TermInSetQuery.newIndexOrDocValuesQuery and IndexOrDocValuesQuery (MultiTermQuery rewrite methods) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a set query with the expert factory TermInSetQuery.newIndexOrDocValuesQuery and, since its @param states no restriction, pass a scoring rewrite (SCORING_BOOLEAN_REWRITE, which TermInSetQuery's javadoc offers 'if scores are required') or a top-terms rewrite for the index side, expecting the query to behave the same however Lucene executes it. Instead the factory pairs it with a constant-score, all-terms DOC_VALUES_REWRITE side, which breaks IndexOrDocValuesQuery's rule that both sides 'must match the same documents and give the same scores': with TopTermsScoringBooleanQueryRewrite(1) and terms {x, y} the query matches 60 documents at top level but 90 when another clause leads, and with SCORING_BOOLEAN_REWRITE a document scores 0.544 at top level while explain reports 1.0. IndexOrDocValuesQuery behaves as specified given that precondition; the defect is that the factory neither states nor checks it (the same mismatched pair can be built by hand with the public constructors since 9.6.0).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The factory pairs it with a constant-score, all-terms DOC_VALUES_REWRITE side, which breaks IndexOrDocValuesQuery's rule that both sides 'must match the same documents and give the same scores': with TopTermsScoringBooleanQueryRewrite(1) and terms {x, y} the query matches 60 documents at top level but 90 when another clause leads, and with SCORING_BOOLEAN_REWRITE a document scores 0.544 at top level while explain reports 1.0.

## Files

- [src/org/apache/lucene/search/TestTermInSetIndexOrDocValuesFactory.java](src/org/apache/lucene/search/TestTermInSetIndexOrDocValuesFactory.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.TestTermInSetIndexOrDocValuesFactory
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

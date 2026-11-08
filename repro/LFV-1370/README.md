# Classic Highlighter: QueryScorer weights a term that several clauses name by clause order (the last term clause wins, the first span or phrase clause wins) instead of using the highest weight, so two equal() queries whose clauses differ only in order pick different best fragments (the classic-Highlighter sibling of #15434)

Finding LFV-1370 · Area: highlighter / classic Highlighter term weights (WeightedSpanTermExtractor.extractWeightedTerms and extractWeightedSpanTerms, used by QueryScorer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1370.html](../../bugs/LFV-1370.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a text with the classic Highlighter and QueryScorer using two BooleanQuery objects that are equal() and differ only in clause order: (f:alpha)^5 (f:beta)^3 f:alpha and f:alpha (f:beta)^3 (f:alpha)^5. You expect the same best fragment for both, the one with alpha, because alpha's highest weight 5 beats beta's 3 (the rule that QueryScorer's own WeightedSpanTerm[] constructor, QueryTermScorer and, since #15434, the FastVectorHighlighter follow). Instead WeightedSpanTermExtractor keeps the weight of the term clause it visits last (and of the first span or phrase clause), so alpha gets weight 1 for the first query and its best fragment is the one with beta (' &lt;B&gt;beta&lt;/B&gt;' for the repro's text 'alpha beta'), with no error.

## Expected vs actual

- Expected: Both queries give the best fragment `<B>alpha</B>`.
- Actual: `q1` gives ` <B>beta</B>`: `ComparisonFailure: expected:<[<B>alph]a</B>> but was:<[ <B>bet]a</B>>`, with no other error.

## Files

- [src/org/apache/lucene/search/highlight/TestQueryScorerRepeatedTermWeight.java](src/org/apache/lucene/search/highlight/TestQueryScorerRepeatedTermWeight.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestQueryScorerRepeatedTermWeight
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryScorerRepeatedTermWeight.java](TestQueryScorerRepeatedTermWeight.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

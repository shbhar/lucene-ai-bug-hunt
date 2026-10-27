# SynonymQuery with a repeated term adds that term's totalTermFreq once per copy, so the pseudo-term statistics it gives the Similarity can exceed the field's sumTotalTermFreq (AssertionError in SimilarityBase with -ea, scores from impossible statistics with -da)

Bug LFV-628 · Area: search / SynonymQuery pseudo-term statistics (SynonymWeight; reached through QueryBuilder with stock synonym parsers) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-628.html](../../bugs/LFV-628.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a SynonymQuery that contains the same term more than once, built with SynonymQuery.Builder or produced by QueryBuilder when a synonym rule's entries coincide after analysis (the rule 'résumé, resume, cv' with ASCII folding gives Synonym(f:cv f:resume f:resume) for the query 'resume'), and expect the pseudo-term statistics it hands to the Similarity to stay within the field's totals, which TermStats lists as 'always true' (totalTermFreq &lt;= sumTotalTermFreq of the field). Instead SynonymWeight takes the largest docFreq of the terms but adds the repeated term's totalTermFreq once per copy: on a field with the documents 'a' and 'a b' (sumTotalTermFreq 4), SynonymQuery(a, a) passes totalTermFreq 6. With assertions enabled every SimilarityBase similarity throws AssertionError from fillBasicStats when the Weight is built; with assertions disabled they score from impossible statistics, for example LM Dirichlet uses a collection probability P(t|C) of 1.17 for SynonymQuery(x, x, x) where TermQuery(x) uses 0.5.

## Expected vs actual

- Expected: The synonym's pseudo-term stays within the field: totalTermFreq at most 4 for `SynonymQuery(a, a)` (`TermQuery(a)`: 3), at most 3 for the `résumé` rule, and LM Dirichlet's P(t|C) at most 1.
- Actual: totalTermFreq 6 of 4 and 5 of 3 tokens; with `-ea` an `AssertionError` at `SimilarityBase.java:80`, with `-da` P(t|C) = 1.4.

## Files

- [src/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java](src/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java) (JUnit test)
- [src2/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java](src2/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSynonymQueryRepeatedTermStats
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSynonymQueryRepeatedTermStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSynonymQueryRepeatedTermStats.java](TestSynonymQueryRepeatedTermStats.java)
- [TestSynonymQueryFromFoldedRule.java](TestSynonymQueryFromFoldedRule.java)
- [count-repeated-term-once.diff](count-repeated-term-once.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

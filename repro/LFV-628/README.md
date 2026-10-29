# SynonymQuery with a repeated term adds that term's totalTermFreq once per copy, so the pseudo-term statistics it gives the Similarity can exceed the field's sumTotalTermFreq (AssertionError in SimilarityBase with -ea, scores from impossible statistics with -da)

Bug LFV-628 · Area: search / SynonymQuery pseudo-term statistics (SynonymWeight; reached through QueryBuilder with stock synonym parsers) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-628.html](../../bugs/LFV-628.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a SynonymQuery that contains the same term more than once, built with SynonymQuery.Builder or produced by QueryBuilder when a synonym rule's entries coincide after analysis (the rule 'résumé, resume, cv' with ASCII folding gives Synonym(f:cv f:resume f:resume) for the query 'resume'), and expect the pseudo-term statistics it hands to the Similarity to stay within the field's totals, which TermStats lists as 'always true' (totalTermFreq &lt;= sumTotalTermFreq of the field). Instead SynonymWeight takes the largest docFreq of the terms but adds the repeated term's totalTermFreq once per copy: on a field with the documents 'a' and 'a b' (sumTotalTermFreq 4), SynonymQuery(a, a) passes totalTermFreq 6. With assertions enabled every SimilarityBase similarity throws AssertionError from fillBasicStats when the Weight is built; with assertions disabled they score from impossible statistics, for example LM Dirichlet uses a collection probability P(t|C) of 1.17 for SynonymQuery(x, x, x) where TermQuery(x) uses 0.5.

The test shows that `SynonymQuery(f:a, f:a)` counts the repeated term's totalTermFreq once per copy, so the pseudo-term statistics handed to the Similarity exceed the field's sumTotalTermFreq (documents "a" and "a b").

## Expected vs actual

Expected, per the `TermStats` javadoc ("always true"): totalTermFreq &lt;= sumTotalTermFreq = 4. Actual: totalTermFreq 6, with -ea and -da.

## Files

- [src/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java](src/org/apache/lucene/search/TestSynonymQueryRepeatedTermStats.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestSynonymQueryRepeatedTermStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

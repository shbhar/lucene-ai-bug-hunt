# Exact PhraseQuery/MultiPhraseQuery: when the first query term occurs twice at one document position, the phrase freq exceeds the maxFreq/impacts bound, so Scorer#getMaxScore is below score and TOP_SCORES search drops the best hit

Bug LFV-195 · Area: search / phrase queries (ExactPhraseMatcher maxFreq and merged impacts, PhraseScorer TOP_SCORES pruning) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index text with an analyzer that can put the first word of a phrase twice at one position (for example KeywordRepeatFilter plus a stemmer, or index-time synonyms queried through QueryBuilder) and run an exact phrase search with IndexSearcher#search(q, n); you expect the same top hits as scoring every match. ExactPhraseMatcher counts such a phrase occurrence once per copy of the first word, so the document's phrase freq is 2, but the bound it reports for skipping work (maxFreq and the block impacts behind getMaxScore) is the smallest term freq, 1. Once more than 1000 hits are counted the searcher skips documents whose real score is competitive: in the repro search(q, 1) returns doc 0 although exhaustive scoring ranks doc 1100 first, with no error.

## Expected vs actual

- Expected: You expect the same top hits as scoring every match.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestExactPhraseDuplicatePositions.java](src/org/apache/lucene/search/TestExactPhraseDuplicatePositions.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestExactPhraseDuplicatePositions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

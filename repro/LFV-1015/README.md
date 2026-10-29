# RescoreTopNQuery passes negative DoubleValuesSource values through as negative scores, so a top-k BooleanQuery that ORs it with another clause silently returns no hits

Bug LFV-1015 · Area: search / rescoring (RescoreTopNQuery scores, consumed by MaxScoreBulkScorer dynamic pruning) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 10 documents with a numeric doc-values field v = -2..-11 and run BooleanQuery(SHOULD RescoreTopNQuery(MatchAllDocsQuery, DoubleValuesSource.fromLongField("v"), 1), SHOULD MatchAllDocsQuery) with searcher.search(q, 10), expecting all 10 documents, since each one matches the MatchAllDocsQuery clause and count(q) is 10. Instead it returns 0 hits and totalHits 0. RescoreTopNQuery copies the raw value -2 into the score, although Lucene requires non-negative scores (LUCENE-7996) and FunctionScoreQuery maps negative values to 0, so the clause reports a max score of -2, the two clauses' max scores sum to -1, below the initial minimum competitive score of 0, and MaxScoreBulkScorer skips the whole window as if no document could compete.

The test shows that `BooleanQuery(SHOULD RescoreTopNQuery(MatchAllDocsQuery, DoubleValuesSource.fromLongField("v"), 1), SHOULD MatchAllDocsQuery)` returns no hits from `search(q, 10)` on a one-document index with `v = -2`: the negative value becomes a negative score and max score (-2), the clauses' max scores sum to -1, and MaxScoreBulkScorer skips every document.

## Expected vs actual

Expected: 1 hit (the document matches the MatchAllDocsQuery clause); actual: `expected:<1> but was:<0>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestRescoreTopNQueryNegativeValues.java](src/org/apache/lucene/search/TestRescoreTopNQueryNegativeValues.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestRescoreTopNQueryNegativeValues
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/rescoretopn-negative-rescore-values-maxscore-hang/](../../models/rescoretopn-negative-rescore-values-maxscore-hang/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

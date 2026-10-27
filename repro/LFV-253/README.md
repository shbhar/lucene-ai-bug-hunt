# CombinedFieldQuery computes its max score as score(+Infinity, 1), which is NaN under AxiomaticF2LOG/F2EXP, so top-k pruning silently drops the best hits

Bug LFV-253 · Area: search / CombinedFieldQuery (BM25F) max-score bound under dynamic pruning (CombinedFieldScorer.getMaxScore as used by MaxScoreBulkScorer) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-253.html](../../bugs/LFV-253.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score a query with CombinedFieldQuery under the core AxiomaticF2LOG (or AxiomaticF2EXP) similarity, which the class javadoc counts as compatible because it keeps the default computeNorm, put it in a BooleanQuery next to another scoring clause such as a TermQuery, and expect IndexSearcher#search(q, 10) to return the same top hits as an exhaustive search. CombinedFieldScorer reports score(Float.POSITIVE_INFINITY, 1) as its max score, although SimScorer#score requires a finite freq and documents score(Float.MAX_VALUE, 1) as the bound; for the F2 similarities that is Inf/Inf = NaN, and MaxScoreBulkScorer, for which NaN is never below the minimum competitive score, then makes the other clause required. Documents that match only the CombinedFieldQuery are skipped: in a 3000-document index the default search returns doc 0 (score 2.1975672) as the top hit instead of doc 2000 (6.383921), and the upstream oracle CheckHits.checkTopScores fails.

## Expected vs actual

- Expected: `getMaxScore` is a finite upper bound, and `search(q, 10)` returns doc 2000 (6.383921) first, like the exhaustive search.
- Actual: `getMaxScore` is NaN, and `search(q, 10)` returns doc 0 (2.1975672) first; doc 2000 is not in the top 10.

## Files

- [src/org/apache/lucene/search/TestCombinedFieldMaxScoreInfiniteFreq.java](src/org/apache/lucene/search/TestCombinedFieldMaxScoreInfiniteFreq.java) (JUnit test)
- [src/org/apache/lucene/search/TestSandboxCombinedFieldMaxScoreInfiniteFreq.java](src/org/apache/lucene/search/TestSandboxCombinedFieldMaxScoreInfiniteFreq.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCombinedFieldMaxScoreInfiniteFreq

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSandboxCombinedFieldMaxScoreInfiniteFreq
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCombinedFieldQueryMaxScore.java](TestCombinedFieldQueryMaxScore.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestSandboxCombinedFieldNaNMaxScore.java](TestSandboxCombinedFieldNaNMaxScore.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

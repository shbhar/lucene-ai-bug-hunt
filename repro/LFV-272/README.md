# SynonymQuery adds up boosted term frequencies in float in heap order, so identical documents can score differently and explain can differ from the hit score; with more than 2^24 occurrences in one field a score can exceed getMaxScore

Bug LFV-272 · Area: search / SynonymQuery scoring (SynonymScorer.freq float sum) and its merged-impacts max-score bound · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a SynonymQuery with three or more boosted terms, for example Synonym(a^0.2, b^0.2, c^0.3) over four identical documents 'a b c' with the default BM25, and expect identical documents to score the same and explain to report the hit's score, because the query scores 'the sum of all term frequencies for the document'. SynonymScorer.freq adds boost * freq in float, in the order of the disjunction's topList, and that order depends on how the iterator reached the document, so the rounding differs: docs 0 and 3 score 0.038817033, docs 1 and 2 score 0.038817026, and explain(doc 1) reports 0.038817033. The same float sum can also round above the exact integer sum that the merged impacts use as the max-score bound once a field holds more than 2^24 occurrences of the terms: under ClassicSimilarity a document scores 1.032795 while getMaxScore is 1.0327948, and a pruned top-1 search returns a different document than an exhaustive one.

The test shows that `SynonymQuery` Synonym(a^0.2, b^0.2, c^0.3) over four identical documents "a b c" (default BM25) sums boosted freqs in float in iteration order, so `explain()` disagrees with the hit score.

## Expected vs actual

Expected: explain(doc) equals the hit's score. Actual: `doc 1 hit score vs explain() expected:<0.038817026> but was:<0.038817033>` (-ea and -da).

## Files

- [src/org/apache/lucene/search/TestSynonymFreqSumOrder.java](src/org/apache/lucene/search/TestSynonymFreqSumOrder.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestSynonymFreqSumOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/dismax-synonym-combined/](../../models/dismax-synonym-combined/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

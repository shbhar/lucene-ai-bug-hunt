# Document suggester: which equal-score copies of one suggestion are returned depends on n (top-2 [2, 1] is not a prefix of top-3 [2, 0, 1]), because NRTSuggesterBuilder numbers the copies in java.util.PriorityQueue heap order instead of doc-id order

Bug LFV-831 · Area: suggest / document (NRT) suggester (NRTSuggesterBuilder.finishTerm dedup bytes; NRTSuggester top-n search through Util.TopNSearcher; TopSuggestDocsCollector tie order) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents with the same suggestion 'c' and weights 1, 1 and 2 in one segment and call SuggestIndexSearcher.suggest(PrefixCompletionQuery 'c', n, skipDuplicates=false). For n=3 you get docs [2, 0, 1] (score, then the smaller doc id, as the collector's comparator orders them), so n=2 should return the first two, [2, 0]; it returns [2, 1]. NRTSuggesterBuilder.finishTerm numbers the copies of one analyzed form (its dedup bytes) by iterating a java.util.PriorityQueue, whose iterator returns heap-array order ([doc 2, doc 1, doc 0] here), and the per-segment TopNSearcher breaks score ties by those bytes and stops after n results, so for n=2 doc 0 never reaches the collector.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/suggest/document/TestSuggestEqualWeightTopN.java](src/org/apache/lucene/search/suggest/document/TestSuggestEqualWeightTopN.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestSuggestEqualWeightTopN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

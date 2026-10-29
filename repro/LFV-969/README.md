# CompletionScorer silently ignores the BulkScorer [min, max) window instead of rejecting it, so a CompletionQuery run through IndexSearcher#search with a timeout (even one that never fires) or with intra-segment partitions returns repeated suggestions and drops others

Bug LFV-969 · Area: suggest / document (NRT) suggester: CompletionScorer under IndexSearcher's windowed execution (setTimeout, intra-segment partitions) · Priority: unranked · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-969.html](../../bugs/LFV-969.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a CompletionQuery through IndexSearcher#search with a TopSuggestDocsCollector (for example suggestSearcher.search(query, collector)) on a searcher that has a timeout set, even one that never fires, or that splits a segment into intra-segment partitions. You expect the same top suggestions as SuggestIndexSearcher#suggest, or a loud error if this path is unsupported, because BulkScorer#score must score only the documents in [min, max) and a timeout that never fires must not change results. Instead CompletionScorer runs its whole-segment top-N lookup again on every window or partition, so the top suggestions come back several times and lower ones are dropped (500 suggestions, n=5: [499, 499, 498, 498, 497] instead of [499, 498, 497, 496, 495]; one suggestion returned 5 times; partitions: [499 x5]), while the total says EQUAL_TO and timedOut is false.

## Expected vs actual

- Expected: The same five suggestions as `suggest`, docs [499, 498, 497, 496, 495], under the timeout and with partitions; one suggestion returned once; no hit outside `[0, 100)`.
- Actual: [499, 499, 498, 498, 497] under the timeout (`timedOut` false), [499 x5] with partitions, one suggestion 5 times, 400 hits outside `[0, 100)`.

## Files

- [src/org/apache/lucene/search/suggest/document/TestCompletionScorerWindows.java](src/org/apache/lucene/search/suggest/document/TestCompletionScorerWindows.java) (JUnit test)
- [src/org/apache/lucene/search/suggest/document/TestCompletionScorerWindows9x.java](src/org/apache/lucene/search/suggest/document/TestCompletionScorerWindows9x.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionScorerWindows

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestCompletionScorerWindows9x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCompletionScorerWindows.java](TestCompletionScorerWindows.java)
- [2-TestCompletionScorerWindows.java](2-TestCompletionScorerWindows.java)
- [TestCompletionWindows.java](TestCompletionWindows.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

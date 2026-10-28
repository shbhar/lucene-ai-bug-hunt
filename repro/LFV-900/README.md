# PositiveScoresOnlyCollector inherits its wrapped collector's scoreMode and setWeight, so a wrapped sort-by-field TopFieldCollector or TotalHitCountCollector silently gets the wrong hits or count instead of exactly the hits that score &gt; 0 (score-mode leg since 5.1.0, count leg since 9.3.0)

Bug LFV-900 · Area: core / search collectors (PositiveScoresOnlyCollector; FilterCollector scoreMode and setWeight forwarding; TotalHitCountCollector Weight#count shortcut) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-900.html](../../bugs/LFV-900.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a collector in PositiveScoresOnlyCollector, whose javadoc says it 'makes sure only documents with scores &gt; 0 are collected' and which Collector's overview says 'wraps any other Collector', and expect the wrapped collector to see exactly the matches that score &gt; 0: in the repro 6 hits for the TermQuery g:y with a TopFieldCollector sorted by a field, and counts 0 for (f:x)^0 and 6 for (g:y)^2 OR (f:x)^0 with a TotalHitCountCollector. Instead the sort-by-field collector receives 0 hits and the counter reports 12 for both queries, because PositiveScoresOnlyCollector, which reads scorer.score itself, inherits two FilterCollector hooks: scoreMode reports the wrapped collector's no-score mode, so the search builds scorers without scores and the &gt; 0 test reads placeholder values (0 for TermQuery, so real hits are dropped; a constant positive value for FunctionScoreQuery over MatchAllDocsQuery, so zero-score hits get through), and setWeight hands the wrapped TotalHitCountCollector the Weight, so it counts whole segments with Weight#count and never goes through the filter. Overriding scoreMode to return COMPLETE when the wrapped collector needs no scores, and passing on a Weight whose count returns -1 (or not forwarding setWeight at all), fixes both.

## Expected vs actual

- Expected: 6 hits for `g:y` sorted by field; counts 0 for `(f:x)^0` and 6 for `(g:y)^2 OR (f:x)^0` (the scoring controls get 6, 0 and 6).
- Actual: 0 hits for `g:y` sorted by field; counts 12 and 12; no exception, with assertions on or off.

## Files

- [src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegates.java](src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegates.java) (JUnit test)
- [src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegatesLegacy.java](src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegatesLegacy.java) (JUnit test)
- [src2/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegatesLegacy.java](src2/org/apache/lucene/search/TestPositiveScoresOnlyCollectorDelegatesLegacy.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF0353
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF0353 \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPositiveScoresOnlyCollectorDelegates

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPositiveScoresOnlyCollectorDelegatesLegacy
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPositiveScoresOnlyCollectorDelegatesLegacy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPositiveScoresOnlyCollectorNoScoreDelegates.java](TestPositiveScoresOnlyCollectorNoScoreDelegates.java)
- [TestPositiveScoresOnlyCollectorFunctionScore.java](TestPositiveScoresOnlyCollectorFunctionScore.java)
- [TestPositiveScoresOnlyCollectorDelegates.java](TestPositiveScoresOnlyCollectorDelegates.java)
- [TestPositiveScoresOnlyCollector.java](TestPositiveScoresOnlyCollector.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

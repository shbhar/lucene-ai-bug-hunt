# PositiveScoresOnlyCollector inherits its wrapped collector's scoreMode and setWeight, so a wrapped sort-by-field TopFieldCollector or TotalHitCountCollector silently gets the wrong hits or count instead of exactly the hits that score &gt; 0 (score-mode leg since 5.1.0, count leg since 9.3.0)

Bug LFV-900 · Area: core / search collectors (PositiveScoresOnlyCollector; FilterCollector scoreMode and setWeight forwarding; TotalHitCountCollector Weight#count shortcut) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-900.html](../../bugs/LFV-900.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a collector in PositiveScoresOnlyCollector, whose javadoc says it 'makes sure only documents with scores &gt; 0 are collected' and which Collector's overview says 'wraps any other Collector', and expect the wrapped collector to see exactly the matches that score &gt; 0: in the repro 6 hits for the TermQuery g:y with a TopFieldCollector sorted by a field, and counts 0 for (f:x)^0 and 6 for (g:y)^2 OR (f:x)^0 with a TotalHitCountCollector. Instead the sort-by-field collector receives 0 hits and the counter reports 12 for both queries, because PositiveScoresOnlyCollector, which reads scorer.score itself, inherits two FilterCollector hooks: scoreMode reports the wrapped collector's no-score mode, so the search builds scorers without scores and the &gt; 0 test reads placeholder values (0 for TermQuery, so real hits are dropped; a constant positive value for FunctionScoreQuery over MatchAllDocsQuery, so zero-score hits get through), and setWeight hands the wrapped TotalHitCountCollector the Weight, so it counts whole segments with Weight#count and never goes through the filter. Overriding scoreMode to return COMPLETE when the wrapped collector needs no scores, and passing on a Weight whose count returns -1 (or not forwarding setWeight at all), fixes both.

The test shows that PositiveScoresOnlyCollector inherits scoreMode() and setWeight() from FilterCollector, so on a one-document index a wrapped sort-by-field TopFieldCollector loses the positive-score hit and a wrapped TotalHitCountCollector counts the zero-score hit (TestPositiveScoresOnlyCollectorWrapsNoScoreCollector, one test per leg).

## Expected vs actual

Expected: 1 hit for TermQuery f:x sorted by field, count 0 for (f:x)^0; actual: 0 hits (`expected:<1> but was:<0>`) and count 1 (`expected:<0> but was:<1>`), with -ea and -da.

## Files

- [src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorWrapsNoScoreCollector.java](src/org/apache/lucene/search/TestPositiveScoresOnlyCollectorWrapsNoScoreCollector.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
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
     execute --select-class org.apache.lucene.search.TestPositiveScoresOnlyCollectorWrapsNoScoreCollector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

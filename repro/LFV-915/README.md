# Monitor HighlightsMatch drops a hit when another hit has the same positions but different offsets (for example shingles or multi-word synonyms), because HighlightsMatch.Hit.compareTo ignores offsets

Bug LFV-915 · Area: monitor / HighlightsMatch hit set (Hit.compareTo used as TreeSet identity) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-915.html](../../bugs/LFV-915.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a query such as wi OR "wi fi" (or the prefix query wi*, or guinea OR cavy with a 'guinea pig =&gt; cavy' synonym rule) in a Monitor whose analyzer stacks tokens at one position, match a document with HighlightsMatch.MATCHER, and expect both hits that Weight#matches reports: for 'wi fi' with ShingleFilter(2,2) and unigrams these are 0(0)-&gt;0(2) for 'wi' and 0(0)-&gt;0(5) for 'wi fi'. Instead HighlightsMatch keeps only one of them and getHitCount is 1, because Hit.compareTo compares start and end positions only and the hits are kept in TreeSets, which treat compareTo == 0 as a duplicate, although Hit's own equals (a record over all four fields) says the two hits differ. Which hit survives depends on clause order, and the same root cause makes getHits(field).contains(...) ignore offsets and lets HighlightsMatch.equals return true for two objects whose hashCodes differ.

The test shows that Monitor's `HighlightsMatch` drops one of two hits at the same positions with different offsets, because `HighlightsMatch.Hit.compareTo` ignores offsets and the hits are kept in a `TreeSet`.

## Expected vs actual

Expected 2 hits for `f:wi OR f:"wi fi"` (two TermQuery clauses, the second on the shingle term) on "wi fi" with `ShingleFilter(2,2)` (unigram 0(0)-&gt;0(2) and shingle 0(0)-&gt;0(5)); actual: `getHitCount()` is 1 (`expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/monitor/TestHighlightsMatchStackedHits.java](src/org/apache/lucene/monitor/TestHighlightsMatchStackedHits.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestHighlightsMatchStackedHits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

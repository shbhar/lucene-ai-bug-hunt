# Intervals.ordered(a, a) and Intervals.unordered(a, a) report minExtent 1, so the default disjunction rewrite under phrase/maxgaps leaves or(ordered(a, a), a) unsplit and phrase(x, or(ordered(a, a), a), c) misses 'x a c'

Bug LFV-805 · Area: queries / intervals (RepeatingIntervalsSource.minExtent for ordered/unordered of equal sub-sources; Disjunctions rewrite under phrase/maxgaps) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-805.html](../../bugs/LFV-805.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with Intervals.phrase(x, Intervals.or(Intervals.ordered(a, a), a), c), meaning 'x, then either a or a, then c', on the text 'x a c' and expect a hit, because phrase(x, ordered(a, a), c) alone matches it and Intervals.or is documented to rewrite itself under gap-sensitive parents so that alternatives of different lengths are not lost. Instead it finds nothing: ordered(a, a) and unordered(a, a) are built as a RepeatingIntervalsSource whose minExtent returns the minExtent of a single 'a' (1) instead of 2, the rewrite assumes that only single-position sources report 1 and so keeps or(ordered(a, a), a) as one OR, and inside that OR the interval of 'a a' contains the interval of a single 'a' and is minimized away. The same query with two different words, phrase(x, or(ordered(a, b), a), c) on 'x a b c', matches as expected.

## Expected vs actual

- Expected: `phrase(x, or(ordered(a, a), a), c)` finds `x a c` (1 hit), as `phrase(x, ordered(a, a), c)` does, and `ordered(a, a).minExtent` is 2.
- Actual: 0 hits (`BLOCK(x,or(ORDERED(a,a),a),c) expected:<1> but was:<0>`), and `ordered(a, a).minExtent` is 1; the same for `unordered`, `maxgaps` and `or(ordered(a, a), b)`.

## Files

- [src/org/apache/lucene/queries/intervals/TestRepeatedSourceMinExtent.java](src/org/apache/lucene/queries/intervals/TestRepeatedSourceMinExtent.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestRepeatedSourceMinExtent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRepeatedSourceRewrite.java](TestRepeatedSourceRewrite.java)
- [regression-test-TestDisjunctionRewrites.diff](regression-test-TestDisjunctionRewrites.diff)
- [TestRepeatedSourceMinExtent.java](TestRepeatedSourceMinExtent.java)
- [TestRepeatMinExtent.java](TestRepeatMinExtent.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

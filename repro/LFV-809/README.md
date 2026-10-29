# Intervals.unordered/ordered given the same disjunction twice merge it into one repeated source that the gap-sensitive disjunction rewrite does not look into (RepeatingIntervalsSource.pullUpDisjunctions returns itself), so maxgaps(0, unordered(X, X)) with X = or(b, phrase(c, b)) misses 'c b c b', and maxgaps or phrase over ordered(X, X) miss the same way

Bug LFV-809 · Area: queries / intervals (gap-sensitive disjunction rewrite of LUCENE-8477 vs repeated equal sub-sources: RepeatingIntervalsSource.pullUpDisjunctions, Ordered/UnorderedIntervalsSource.deduplicate) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-809.html](../../bugs/LFV-809.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an interval query that uses the same OR twice, for example maxgaps(0, unordered(X, X)) with X = or(b, phrase(c, b)), and expect what the Intervals.or javadoc promises ('Automatically rewrites if wrapped by an interval source that is sensitive to internal gaps'), so that 'c b c b' matches through b at position 1 followed by the phrase 'c b' at positions 2-3, which do not overlap and leave no gap. Instead, because the two arguments are equal, Lucene merges them into one RepeatingIntervalsSource whose pullUpDisjunctions returns only itself, so the rewrite never runs, the longer 'c b' alternative is minimized away inside X, and the query returns no hits. maxgaps(0, ordered(X, X)) and phrase(a, ordered(X, X)) miss in the same way, and so does Intervals.analyzedText for the query text 'york york' with a synonym york -&gt; 'new york' (unordered, maxGaps 0) on the document 'new york new york'.

The test shows that `maxgaps(0, unordered(X, X))` with `X = or(b, phrase(c, b))` misses `c b c b`, because the repeated `X` becomes a `RepeatingIntervalsSource` whose `pullUpDisjunctions()` hides the `or()` from the gap-sensitive rewrite.

## Expected vs actual

Expected: 1 hit (`b` at [1,1] and `c b` at [2,3] are disjoint with no gap). Actual: 0 hits (`expected:<1> but was:<0>`).

## Files

- [src/org/apache/lucene/queries/intervals/TestUnorderedRepeatedOrMaxGaps.java](src/org/apache/lucene/queries/intervals/TestUnorderedRepeatedOrMaxGaps.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestUnorderedRepeatedOrMaxGaps
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

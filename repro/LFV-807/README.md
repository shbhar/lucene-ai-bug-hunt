# Intervals.ordered(X, X) with two equal sub-sources that span several positions lets the two copies of X overlap, so ordered(phrase(a, a), phrase(a, a)) matches 'a a' and ordered(unordered(a, b), unordered(a, b)) matches 'a b a' (one b), while the same query with an equivalent but unequal second source finds nothing

Bug LFV-807 · Area: queries / intervals (Intervals.ordered and unorderedNoOverlaps with equal consecutive sub-sources: OrderedIntervalsSource.deduplicate, RepeatingIntervalsSource) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-807.html](../../bugs/LFV-807.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask for two occurrences of X in order with Intervals.ordered(X, X), for example X = unordered(a, b) ('a near b', twice) or X = phrase(a, a), and expect the usual ordered rule that the sub-intervals do not overlap (the fn:ordered documentation says 'Source intervals must not overlap'). Instead, because the two arguments are equal, OrderedIntervalsSource.build merges them into a RepeatingIntervalsSource, which joins consecutive intervals of a single sub-iterator without checking that they are disjoint, so ordered(unordered(a, b), unordered(a, b)) matches 'a b a', which has only one b, and ordered(phrase(a, a), phrase(a, a)) matches 'a a'. The same query with an equivalent but unequal second source, ordered(X, or(X, a term that occurs nowhere)), correctly finds nothing, and so did Lucene 8.4.1.

## Expected vs actual

- Expected: 0 hits for every repeated-copy query on the texts that are too short (`a a`, `a b a`), and the single interval [0,3] on `a a a`, as the unequal-copy controls give.
- Actual: 1 hit for each (also both `fn:` strings), and the intervals [0,2] and [1,3] on `a a a`: all 7 tests fail at their last assertion, with assertions on and off.

## Files

- [src/org/apache/lucene/queries/intervals/TestOrderedRepeatOverlap.java](src/org/apache/lucene/queries/intervals/TestOrderedRepeatOverlap.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.intervals.TestOrderedRepeatOverlap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOrderedRepeatOverlap.java](TestOrderedRepeatOverlap.java)
- [TestFnOrderedRepeatOverlap.java](TestFnOrderedRepeatOverlap.java)
- [3-TestOrderedRepeatOverlap.java](3-TestOrderedRepeatOverlap.java)
- [TestOrderedEqualSourcesMustNotOverlap.java](TestOrderedEqualSourcesMustNotOverlap.java)
- [StackedTermRepeatProbe.java](StackedTermRepeatProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

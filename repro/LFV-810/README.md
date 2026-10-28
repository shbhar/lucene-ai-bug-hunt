# A nested Intervals.ordered(a, a) or unordered(a, a) passes its inner gap up to its parent, so maxgaps(0, ordered(ordered(a, a), a)) misses 'a x a a' while maxgaps(0, ordered(ordered(a, b), a)) matches 'a x b a'

Bug LFV-810 · Area: queries / intervals (a whole ordered/unordered of equal sub-sources nested in another operator: RepeatingIntervalsSource width, Ordered/UnorderedIntervalsSource.deduplicate) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You nest a repeated pair inside another interval operator, for example ordered(ordered(a, a), a), and filter it with maxgaps(0,...): the IntervalIterator#gaps javadoc counts only the gaps between the parent's immediate sub-intervals, so 'a x a a' (sub-intervals [0,2] and [3,3]) should match, just as maxgaps(0, ordered(ordered(a, b), a)) matches 'a x b a'. Instead, because the two arguments of the inner ordered(a, a) are equal, Lucene builds it as a RepeatingIntervalsSource whose width is the sum of its two copies (2) rather than its span (3), so the parent counts the inner 'x' as its own gap (gaps 1) and maxgaps(0) silently drops the document. The same happens for ordered(unordered(a, a), b), unordered(ordered(a, a), b) and ordered(x, ordered(a, a)), while wrapping the inner pair in a no-op filter such as maxwidth(100,...) brings the hit back.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Because the two arguments of the inner ordered(a, a) are equal, Lucene builds it as a RepeatingIntervalsSource whose width is the sum of its two copies (2) rather than its span (3), so the parent counts the inner 'x' as its own gap (gaps 1) and maxgaps(0) silently drops the document.

## Files

- [src/org/apache/lucene/queries/intervals/TestRepeatedNestedSourceGaps.java](src/org/apache/lucene/queries/intervals/TestRepeatedNestedSourceGaps.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.intervals.TestRepeatedNestedSourceGaps
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

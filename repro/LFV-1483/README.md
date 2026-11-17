# Intervals.range and Intervals.prefix throw ArrayIndexOutOfBoundsException while the source is built when a bound is not valid UTF-8 and ends in a cut-off multi-byte sequence (a bound that TermRangeQuery and PrefixQuery accept), because the factories decode the bound with utf8ToString() only to build the source's description string (prefix since 8.2.0, range since 9.12.0)

Finding LFV-1483 · Area: queries / intervals (the multi-term source factories Intervals.range and Intervals.prefix; the description string of MultiTermIntervalsSource) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1483.html](../../bugs/LFV-1483.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an interval source over a range or a prefix of terms with Intervals.range or Intervals.prefix and pass a bound that is not valid UTF-8, for example the two-byte binary term {01 F4}, a text prefix cut in the middle of a multi-byte character, or UnicodeUtil.BIG_TERM. You expect the source to be built, as TermRangeQuery and PrefixQuery are with the same bound (they accept it and find a document that holds the term). Instead the factory throws ArrayIndexOutOfBoundsException before any search runs, because it decodes the bound as UTF-8 text only to build the description string that the source shows in toString and in error messages; bounds that are ordinary text are never affected.

## Expected vs actual

- Expected: The factory returns a source, as `TermRangeQuery` builds for the same bound.
- Actual: `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2` from `Intervals.range` (`Intervals.java:291`), at line 27 of the test.

## Files

- [src/org/apache/lucene/queries/intervals/TestIntervalsRangeBinaryBound.java](src/org/apache/lucene/queries/intervals/TestIntervalsRangeBinaryBound.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (2026-11-16): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: it calls Intervals.range with the two-byte bound {01 F4} and asserts that a source comes back; it indexes nothing and runs no search, and it fails with the ArrayIndexOutOfBoundsException thrown by that call. Intervals.prefix, the other bounds and the comparison with TermRangeQuery named above are not part of this test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.queries.intervals.TestIntervalsRangeBinaryBound
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestIntervalsRangeBinaryBound.java](TestIntervalsRangeBinaryBound.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

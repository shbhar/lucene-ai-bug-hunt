# Intervals.nonOverlapping and notWithin hide a disjunction in their source from the gap-sensitive rewrite that the default Intervals.or promises, so a phrase or maxgaps over them misses hits: phrase(a, nonOverlapping(or("b c", b), z), d) does not match 'a b c d' although z occurs in no document and phrase(a, or("b c", b), d) matches

Finding LFV-1489 · Area: queries / intervals (the gap-sensitive disjunction rewrite of LUCENE-8477, IntervalsSource.pullUpDisjunctions, through the filter source NonOverlappingIntervalsSource behind Intervals.nonOverlapping and Intervals.notWithin) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1489.html](../../bugs/LFV-1489.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a b c d' and search for the phrase a, then either "b c" or b, then d, with the middle step wrapped in Intervals.nonOverlapping(..., z), where z occurs in no document, so the filter removes nothing. You expect 1 hit, as the same phrase without the filter gives, because the default Intervals.or is documented to rewrite itself when it is wrapped by a source that is sensitive to internal gaps. Instead you get 0 hits: when the phrase asks the filter for its alternatives, nonOverlapping (and notWithin, which is built on it) answers with itself, so the or() inside is read as one minimized source that keeps only 'b', and the phrase looks for 'a b d'.

## Expected vs actual

- Expected: 1 hit, as the same phrase without the filter gives: `z` is in no document, so the filter removes nothing.
- Actual: 0 hits: `f:BLOCK(a,NON_OVERLAPPING(or(BLOCK(b,c),b),z),d) expected:<1> but was:<0>` at line 47 of the test.

## Files

- [src/org/apache/lucene/queries/intervals/TestPhraseOverNonOverlappingDisjunction.java](src/org/apache/lucene/queries/intervals/TestPhraseOverNonOverlappingDisjunction.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it indexes one document with the text 'a b c d' (default IndexWriterConfig) and counts the hits of an IntervalQuery for Intervals.phrase(a, Intervals.nonOverlapping(Intervals.or(phrase(b, c), b), z), d). It asserts 1 hit and gets 0; the failure message starts with the built query, f:BLOCK(a,NON_OVERLAPPING(or(BLOCK(b,c),b),z),d). The same phrase without the filter, Intervals.notWithin and a maxgaps parent, which the title names, are not part of this test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestPhraseOverNonOverlappingDisjunction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestPhraseOverNonOverlappingDisjunction.java](TestPhraseOverNonOverlappingDisjunction.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

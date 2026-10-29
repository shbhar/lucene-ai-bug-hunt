# Intervals.overlapping, nonOverlapping and notWithin read a disjunctive reference through the minimized or, while containedBy and within test every alternative, so for the reference or(york, "new york") the same interval is reported both within and not within n positions of it, and containedBy(new, ref) matches 'new york' where overlapping(new, ref) does not

Bug LFV-828 · Area: queries / intervals (overlap filters Intervals.overlapping / nonOverlapping / notWithin vs the LUCENE-8477 disjunction rewrite that containedBy and within apply to their reference) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-828.html](../../bugs/LFV-828.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'visit new york' and run within(visit, 1, R) and notWithin(visit, 1, R) with R = or(york, "new york"), and you expect exactly one of them to return 'visit', because the javadoc defines notWithin as the intervals that do not appear within n positions of the reference. Instead both return the same interval [0,0]: within is built on containedBy, which pulls the or apart (LUCENE-8477) and sees 'new york' right next to 'visit', while notWithin, overlapping and nonOverlapping read the default or as one minimized source, which drops 'new york' because that interval contains 'york'. In the same way containedBy(new, R) matches 'new york' but overlapping(new, R) does not, and maxgaps(0, overlapping(new, R)) does match, because the nested form does pull the reference apart.

The test shows that `Intervals.within(visit, 1, ref)` and `Intervals.notWithin(visit, 1, ref)` both match 'visit new york' when `ref = Intervals.or(term(york), phrase(new, york))`: within tests every alternative of the or(), notWithin reads the minimized or() that drops 'new york'.

## Expected vs actual

Expected the one 'visit' interval to be within 1 position of ref or not (1 hit in total); actual: both queries match (`within=1, notWithin=1 expected:<1> but was:<2>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/intervals/TestWithinNotWithinDisjunction.java](src/org/apache/lucene/queries/intervals/TestWithinNotWithinDisjunction.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.intervals.TestWithinNotWithinDisjunction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

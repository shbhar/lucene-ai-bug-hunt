# Intervals.overlapping, nonOverlapping and notWithin read a disjunctive reference through the minimized or, while containedBy and within test every alternative, so for the reference or(york, "new york") the same interval is reported both within and not within n positions of it, and containedBy(new, ref) matches 'new york' where overlapping(new, ref) does not

Bug LFV-828 · Area: queries / intervals (overlap filters Intervals.overlapping / nonOverlapping / notWithin vs the LUCENE-8477 disjunction rewrite that containedBy and within apply to their reference) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-828.html](../../bugs/LFV-828.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'visit new york' and run within(visit, 1, R) and notWithin(visit, 1, R) with R = or(york, "new york"), and you expect exactly one of them to return 'visit', because the javadoc defines notWithin as the intervals that do not appear within n positions of the reference. Instead both return the same interval [0,0]: within is built on containedBy, which pulls the or apart (LUCENE-8477) and sees 'new york' right next to 'visit', while notWithin, overlapping and nonOverlapping read the default or as one minimized source, which drops 'new york' because that interval contains 'york'. In the same way containedBy(new, R) matches 'new york' but overlapping(new, R) does not, and maxgaps(0, overlapping(new, R)) does match, because the nested form does pull the reference apart.

## Expected vs actual

- Expected: `notWithin(visit, 1, R)` returns nothing where `within(visit, 1, R)` returns visit [0,0], and `overlapping(new, R)` matches "new york" as `containedBy(new, R)` does.
- Actual: `within` and `notWithin` both return [0,0]; `overlapping(new, R)` finds 0 hits while `containedBy(new, R)` finds 1, and `nonOverlapping` keeps that `new`: 4 of 4 tests fail.

## Files

- [src/org/apache/lucene/queries/intervals/TestOverlapFiltersDisjunctiveReference.java](src/org/apache/lucene/queries/intervals/TestOverlapFiltersDisjunctiveReference.java) (JUnit test)
- [src/org/apache/lucene/queryparser/flexible/standard/TestFnNotWithinDisjunctiveReference.java](src/org/apache/lucene/queryparser/flexible/standard/TestFnNotWithinDisjunctiveReference.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestOverlapFiltersDisjunctiveReference

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestFnNotWithinDisjunctiveReference
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOverlapFiltersDisjunctiveReference.java](TestOverlapFiltersDisjunctiveReference.java)
- [TestFnNotWithinDisjunctiveReference.java](TestFnNotWithinDisjunctiveReference.java)
- [BriefProbe.java](BriefProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

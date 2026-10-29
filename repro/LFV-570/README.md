# SimpleWKTShapeParser rejects a MULTIPOINT whose points each sit in their own parentheses, e.g. MULTIPOINT ((1 2), (3 4)), with ParseException "expected ) but found: ','", although it accepts MULTIPOINT ((1 2)) and MULTIPOINT (1 2, 3 4) (every release since 7.6.0)

Bug LFV-570 · Area: core / geo WKT parsing (SimpleWKTShapeParser) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SimpleWKTShapeParser.parse("MULTIPOINT ((1 2), (3 4))"), the Simple Features WKT form in which each point of a multipoint sits in its own parentheses, and expect double {{1, 2}, {3, 4}}, because the class says it parses WKT in compliance with ISO/IEC 13249-3:2016 and it already accepts MULTIPOINT ((1 2)) and the bare legacy form MULTIPOINT (1 2, 3 4). Instead it throws java.text.ParseException "expected ) but found: ','", also inside a GEOMETRYCOLLECTION and with 3D points. The cause is in parseCoordinates: when the first point starts with '(' the code before the loop does not read that point's ')', so the loop takes that ')' as the end of the whole list and the check after the loop then finds the ',' where it wants the missing ')'; whether a parenthesized point is accepted therefore depends on where it sits (MULTIPOINT (1 2, (3 4), 5 6) parses, while MULTIPOINT (1 2, (3 4)) fails with 'expected word but found: END-OF-STREAM').

The test shows that SimpleWKTShapeParser rejects a MULTIPOINT whose points each have their own parentheses, `MULTIPOINT ((1 2), (3 4))`.

## Expected vs actual

Expected `{{1, 2}, {3, 4}}`; actual `java.text.ParseException: expected ) but found: ','`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/geo/TestWKTMultiPointParenthesizedPoints.java](src/org/apache/lucene/geo/TestWKTMultiPointParenthesizedPoints.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestWKTMultiPointParenthesizedPoints
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

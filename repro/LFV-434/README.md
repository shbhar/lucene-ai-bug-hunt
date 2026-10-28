# XYShape.newLineQuery / newGeometryQuery accept QueryRelation.WITHIN with an XYLine, which LatLonShape (and XYShape itself up to 8.4.1) rejects with IllegalArgumentException; the accepted query silently returns only indexed points lying on the line and never an indexed line, not even one identical to the query line (the check was lost in LUCENE-9194, 8.5.0)

Bug LFV-434 · Area: core / document: XYShape queries (XYShape.newLineQuery and newGeometryQuery, XYShapeQuery, Line2D) · Priority: low · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-434.html](../../bugs/LFV-434.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build XYShape.newLineQuery(field, QueryRelation.WITHIN, line) with a valid XYLine and expect the IllegalArgumentException that LatLonShape.newLineQuery throws for the same relation ('does not support WITHIN queries with line geometries'), and that XYShape itself threw up to 8.4.1 ('XYShapeLineQuery does not support WITHIN queries'). Instead the query is accepted, because the XYShapeQuery constructor has validated nothing since LUCENE-9194 (8.5.0) folded the old XY line query into it. The search then returns indexed points that lie on the line but no indexed line, not even one identical to the query line, because Line2D.containsLine and containsTriangle always return false.

## Expected vs actual

- Expected: `XYShape.newLineQuery("shape", WITHIN, line)` throws `IllegalArgumentException`, as `LatLonShape.newLineQuery` does.
- Actual: No exception: the query matches 1 of the 3 shapes, `[point (5,0)]`, and `newGeometryQuery(WITHIN, polygon, line)` is accepted too.

## Files

- [src/org/apache/lucene/document/TestXYShapeWithinLineQuery.java](src/org/apache/lucene/document/TestXYShapeWithinLineQuery.java) (JUnit test)
- [src/org/apache/lucene/document/TestXYShapeWithinLineQuery8x.java](src/org/apache/lucene/document/TestXYShapeWithinLineQuery8x.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.document.TestXYShapeWithinLineQuery

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYShapeWithinLineQuery8x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestXYShapeWithinLine.java](TestXYShapeWithinLine.java)
- [TestXYShapeWithinLineRejected.java](TestXYShapeWithinLineRejected.java)
- [TestXYShapeWithinLineQuery.java](TestXYShapeWithinLineQuery.java)
- [TestVerifyXYWithinLine.java](TestVerifyXYWithinLine.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

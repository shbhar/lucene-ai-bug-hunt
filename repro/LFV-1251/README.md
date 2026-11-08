# SimpleWKTShapeParser.parseExpectedType(wkt, ShapeType.GEOMETRYCOLLECTION) does no type check, because GEOMETRYCOLLECTION is also the parser's internal 'skip the check' marker for the first member of a collection: POINT (1 2) is returned as a double[] instead of the ParseException 'Expected geometry type: [GEOMETRYCOLLECTION], but found: [POINT]' that every other expected type gets (every release since 7.6.0)

Finding LFV-1251 · Area: core / geo WKT parsing (SimpleWKTShapeParser) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1251.html](../../bugs/LFV-1251.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SimpleWKTShapeParser.parseExpectedType("POINT (1 2)", ShapeType.GEOMETRYCOLLECTION) and expect ParseException 'Expected geometry type: [GEOMETRYCOLLECTION], but found: [POINT]', the error the method gives for every other expected type (POINT where POLYGON is expected, for example). Instead it returns double[]{1.0, 2.0}, and LINESTRING, POLYGON or MULTIPOINT text likewise comes back as its own type, never as the Object[] a collection parses to. The cause is that GEOMETRYCOLLECTION doubles as an internal marker: parseGeometryCollection parses a collection's first member with ShapeType.GEOMETRYCOLLECTION to mean 'no check' (later members get null for the same purpose), so parseGeometry checks the type only when shapeType != null &amp;&amp; shapeType != GEOMETRYCOLLECTION, and a public caller who asks for a collection hits the marker.

## Expected vs actual

- Expected: `ParseException: Expected geometry type: [GEOMETRYCOLLECTION], but found: [POINT]`, as for any other mismatched type.
- Actual: `AssertionFailedError: Expected exception ParseException but no exception was thrown`: the point comes back as `double[]{1.0, 2.0}`.

## Files

- [src/org/apache/lucene/geo/TestWKTExpectedCollectionType.java](src/org/apache/lucene/geo/TestWKTExpectedCollectionType.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestWKTExpectedCollectionType
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestWKTExpectedCollectionType.java](TestWKTExpectedCollectionType.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

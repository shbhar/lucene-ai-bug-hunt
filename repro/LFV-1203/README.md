# LatLonPoint.newGeometryQuery with QueryRelation.CONTAINS returns a query that matches nothing instead of the documented IllegalArgumentException when the geometry array is empty or holds a null (LatLonDocValuesField.newSlowGeometryQuery does the same for a null element), because the CONTAINS shortcut runs before any argument check (since 8.8.0, LUCENE-9641)

Finding LFV-1203 · Area: core / document: LatLon point geometry query factories (LatLonPoint.newGeometryQuery CONTAINS shortcut, LatLonDocValuesField.newSlowGeometryQuery) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1203.html](../../bugs/LFV-1203.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call LatLonPoint.newGeometryQuery("f", QueryRelation.CONTAINS) with no geometries (an empty array), or with an array that holds a null, and expect the IllegalArgumentException its javadoc promises ('if latLonGeometries is null, empty or contain a null'), which INTERSECTS and WITHIN and the doc-values twin LatLonDocValuesField.newSlowGeometryQuery("f", CONTAINS) throw for the empty array ('geometries must not be empty'). Instead you get a query that matches no document: a ConstantScoreQuery over an empty BooleanQuery for the empty array, and MatchNoDocsQuery('Contains LatLonPoint.newGeometryQuery with non-point geometries') for a null element, which newSlowGeometryQuery also returns for a null element. The CONTAINS shortcut runs before any argument check: its loop never runs for an empty array, and a null element fails 'instanceof Point' and lands in the branch meant for valid non-point geometries.

## Expected vs actual

- Expected: `newGeometryQuery("f", CONTAINS)` with no geometries throws `IllegalArgumentException`.
- Actual: It returns `ConstantScore()`, which matches nothing: "Expected exception IllegalArgumentException but no exception was thrown", assertions on and off.

## Files

- [src/org/apache/lucene/document/TestLatLonPointContainsNoGeometries.java](src/org/apache/lucene/document/TestLatLonPointContainsNoGeometries.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.document.TestLatLonPointContainsNoGeometries
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonPointContainsNoGeometries.java](TestLatLonPointContainsNoGeometries.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

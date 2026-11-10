# LatLonShape.newSlowDocValuesBoxQuery rejects CONTAINS for every box except a dateline-crossing one, where it returns a points-index query that silently matches nothing on a doc-values-only field (since 9.4.0)

Finding LFV-1474 · Area: core / document: LatLonShape doc-values box query (newSlowDocValuesBoxQuery, CONTAINS dateline split) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1474.html](../../bugs/LFV-1474.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index shapes only as doc values (LatLonShape.createDocValueField) and build LatLonShape.newSlowDocValuesBoxQuery(field, CONTAINS, -1, 1, 175, -175), a box that crosses the dateline. You expect the IllegalArgumentException 'ShapeDocValuesBoundingBoxQuery does not yet support CONTAINS queries' that the same factory throws for a box that does not cross the dateline, and that XYShape's factory throws for every CONTAINS box, because doc-values shape queries do not support CONTAINS. Instead the factory returns two points-index box queries copied from newBoxQuery, which never read doc values, so the search succeeds and silently matches nothing.

## Expected vs actual

- Expected: `IllegalArgumentException: ShapeDocValuesBoundingBoxQuery does not yet support CONTAINS queries`, as for a box that does not cross the dateline.
- Actual: `Expected exception IllegalArgumentException but no exception was thrown`: the factory returns two points-index clauses instead.

## Files

- [src/org/apache/lucene/document/TestLatLonShapeSlowDocValuesBoxQueryContains.java](src/org/apache/lucene/document/TestLatLonShapeSlowDocValuesBoxQueryContains.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestLatLonShapeSlowDocValuesBoxQueryContains
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonShapeSlowDocValuesBoxQueryContains.java](TestLatLonShapeSlowDocValuesBoxQueryContains.java)

Formal models and scripts this finding's part of the hunt used: [../../models/latlonshape-slow-docvalues-box-query-crossing-contains-runs-points-index-clauses-silent-zero-instead-of-rejection-low-plus-index-vs-docvalues-parity-leg-gpt-records-line/](../../models/latlonshape-slow-docvalues-box-query-crossing-contains-runs-points-index-clauses-silent-zero-instead-of-rejection-low-plus-index-vs-docvalues-parity-leg-gpt-records-line/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

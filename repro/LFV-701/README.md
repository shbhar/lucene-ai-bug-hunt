# LatLonDocValuesField.newSlowGeometryQuery with CONTAINS and two or more Point geometries matches documents that hold only some of the query points, while its documented IndexOrDocValuesQuery partner LatLonPoint.newGeometryQuery requires all of them

Bug LFV-701 · Area: core / document: LatLon point geometry queries (LatLonDocValuesQuery CONTAINS on the doc-values path vs LatLonPoint.newGeometryQuery, and their IndexOrDocValuesQuery pairing) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with LatLonPoint and LatLonDocValuesField and ask for the documents that contain both P1 and P2 (CONTAINS with two Point geometries); a document that holds only P1 must not match, and LatLonPoint.newGeometryQuery, which builds one MUST clause per query point, indeed matches none. LatLonDocValuesField.newSlowGeometryQuery, which its javadoc says to pair with that query in an IndexOrDocValuesQuery (whose two queries 'must match the same documents'), accepts a document as soon as any one of its points equals any one query point, so it computes an OR instead of an AND. In the repro (1000 one-point documents, half at P1 and half at P2) the points query returns 0 hits, the doc-values query returns all 1000, and 'id:7 AND IndexOrDocValuesQuery(points, docValues)' returns document 7, with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestLatLonDocValuesContainsMultiPoint.java](src/org/apache/lucene/document/TestLatLonDocValuesContainsMultiPoint.java) (JUnit test)
- [src/LatLonDocValuesContainsMultiPointMain.java](src/LatLonDocValuesContainsMultiPointMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.document.TestLatLonDocValuesContainsMultiPoint

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" LatLonDocValuesContainsMultiPointMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

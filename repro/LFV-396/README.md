# ShapeDocValues.relate rewinds to position 0 of the backing array instead of to the BytesRef's offset, so a LatLonShape or XYShape doc value built from a BytesRef slice (offset != 0) silently relates wrongly to queries

Bug LFV-396 · Area: core / document: shape doc values (ShapeDocValues reader behind LatLonShapeDocValues / XYShapeDocValues relate and the slow doc-values shape queries; MemoryIndex shape doc-values fields) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-396.html](../../bugs/LFV-396.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a LatLonShape or XYShape doc value from a BytesRef that points into the middle of a larger byte array (offset != 0), for example with LatLonShape.createLatLonShapeDocValues(BytesRef) or as a MemoryIndex field, and expect relate and the doc-values shape queries to answer exactly as for the same bytes at offset 0. Instead, ShapeDocValues reads its header at the offset but then rewinds its ByteArrayDataInput to position 0 of the backing array, so relate decodes whatever bytes sit at the start of that array: the relation is silently wrong in either direction (CROSSES or INSIDE becomes OUTSIDE, OUTSIDE becomes CROSSES), and on a MemoryIndex an overlapping shape is missed by an INTERSECTS box query and matched by a DISJOINT one. getBoundingBox and getCentroid stay correct, which hides the problem.

The test shows that a `LatLonShapeDocValues` built with `LatLonShape.createLatLonShapeDocValues` from a `BytesRef` slice (offset != 0) relates to a query as the bytes at the start of the backing array, because `ShapeDocValues.Reader.rewind()` goes to position 0, not to the slice's offset.

## Expected vs actual

Expected: square a, stored after square b in one array, relates to the box 5..15 x 5..15 like its offset-0 copy (`CELL_CROSSES_QUERY`). Actual: it relates like b (`expected:<CELL_CROSSES_QUERY> but was:<CELL_OUTSIDE_QUERY>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestShapeDocValuesBytesRefOffset.java](src/org/apache/lucene/document/TestShapeDocValuesBytesRefOffset.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestShapeDocValuesBytesRefOffset
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

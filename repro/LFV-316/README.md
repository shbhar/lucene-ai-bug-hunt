# QuadPrefixTree/PackedQuadPrefixTree with non-power-of-two world bounds put a point that lies on a grid line into a cell whose shape excludes it, so Intersects misses the document (deep trees throw InvalidShapeException)

Bug LFV-316 · Area: spatial-extras / prefix trees (QuadPrefixTree, PackedQuadPrefixTree under RecursivePrefixTreeStrategy) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a point with RecursivePrefixTreeStrategy over a QuadPrefixTree (or PackedQuadPrefixTree) in a non-geo SpatialContext whose world bounds are not powers of two, such as Web Mercator's +/-pi*6378137, and the point lies exactly on a grid line (for example longitude 45 degrees projected, at y = 0) or on the world's east edge. You expect Intersects with that same point, or with a box whose edge passes through it, to find the document, because getCell is documented to return 'the cell containing point p' and the query error may only inflate a shape, never shrink it. Instead getCell chooses the cell by comparing the point with mid-points accumulated one way while QuadCell.makeShape rebuilds that cell's edges another way, the two floating-point results differ by one ulp, so the indexed cell's shape ends just short of the point, the query treats it as disjoint and returns 0 hits (and with the 50-level factory default a query for a point on the world's top edge throws InvalidShapeException).

## Expected vs actual

- Expected: You expect Intersects with that same point, or with a box whose edge passes through it, to find the document, because getCell is documented to return 'the cell containing point p' and the query error may only inflate a shape, never shrink it.
- Actual: getCell chooses the cell by comparing the point with mid-points accumulated one way while QuadCell.makeShape rebuilds that cell's edges another way, the two floating-point results differ by one ulp, so the indexed cell's shape ends just short of the point, the query treats it as disjoint and returns 0 hits (and with the 50-level factory default a query for a point on the world's top edge throws InvalidShapeException).

## Files

- [src/org/apache/lucene/spatial/prefix/TestQuadTreeCellEdgeFalseNegative.java](src/org/apache/lucene/spatial/prefix/TestQuadTreeCellEdgeFalseNegative.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.TestQuadTreeCellEdgeFalseNegative
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

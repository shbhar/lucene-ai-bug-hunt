# Tessellator: after a hole that touches the outer ring at one vertex is merged, the leftover copies of that vertex make a later hole's bridge cross the first hole, so LatLonShape and XYShape index a valid two-hole polygon with triangles that cover a hole, or reject it (regression from #15211, in 10.4.0 and 9.12.3)

Bug LFV-757 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with two holes, where hole A touches the outer ring at one corner (the Tessellator javadoc allows 'Holes may only touch at one vertex') and hole B touches nothing, for example the outer ring (0,0) (2,5) (0,10) (10,10) (10,0) with hole A (2,5) (5,6) (4,8) and hole B (6,5) (8,4) (8,6), and you expect a point inside hole A not to match. Since #15211 the Tessellator leaves several copies of the shared corner (2,5) next to each other while it joins the holes to the outer ring; when it then chooses the bridge for hole B, whose left-most corner (6,5) is level with (2,5), it rejects every copy and bridges to (0,10) instead, a line that cuts through hole A. The triangles then cover hole A, and an INTERSECTS query for a point in it matches the document with no error, while with checkSelfIntersections=true the same valid polygon is rejected with 'Polygon self-intersection'.

## Expected vs actual

- Expected: You expect a point inside hole A not to match.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestTessellatorSharedVertexLaterHoleBridge.java](src/org/apache/lucene/document/TestTessellatorSharedVertexLaterHoleBridge.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestTessellatorSharedVertexLaterHoleBridge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

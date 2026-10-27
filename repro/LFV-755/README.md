# Tessellator rejects a valid polygon whose holes touch the outer ring and each other at single vertices with 'Unable to Tessellate shape', because #15739 made the reflex-vertex test in isLocallyInside inclusive ('&lt;= 0'): a regression in 10.5.0 (10.4.0 accepts the polygon)

Bug LFV-755 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with holes, for example an L-shaped outer ring (0,0) (0,12) (4,12) (4,4) (12,4) (12,0) with three small triangular holes that touch the ring and each other only at single shared corners, which the Tessellator javadoc allows ('Holes may only touch at one vertex'), and you expect XYShape.createIndexableFields or LatLonShape.createIndexableFields to return its triangles, as 10.4.0 does. Instead, since #15739 (first released in 10.5.0), both calls throw IllegalArgumentException 'Unable to Tessellate shape. Possible malformed shape detected.', so the document cannot be indexed, although every proper subset of the holes tessellates. The cause is that #15739 changed the reflex-vertex branch of Tessellator.isLocallyInside from '&lt; 0' to '&lt;= 0', which accepts a candidate exactly collinear with a neighbouring edge; reverting that comparison fixes this polygon but breaks the #15731 polygon #15739 was written for, so a correct fix has to be more selective.

## Expected vs actual

- Expected: You expect XYShape.createIndexableFields or LatLonShape.createIndexableFields to return its triangles, as 10.4.0 does.
- Actual: Since #15739 (first released in 10.5.0), both calls throw IllegalArgumentException 'Unable to Tessellate shape.

## Files

- [src/org/apache/lucene/document/TestTessellatorChainedTouchingHoles.java](src/org/apache/lucene/document/TestTessellatorChainedTouchingHoles.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorChainedTouchingHoles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

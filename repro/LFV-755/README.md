# Tessellator rejects a valid polygon whose holes touch the outer ring and each other at single vertices with 'Unable to Tessellate shape', because #15739 made the reflex-vertex test in isLocallyInside inclusive ('&lt;= 0'): a regression in 10.5.0 (10.4.0 accepts the polygon)

Bug LFV-755 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-755.html](../../bugs/LFV-755.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with holes, for example an L-shaped outer ring (0,0) (0,12) (4,12) (4,4) (12,4) (12,0) with three small triangular holes that touch the ring and each other only at single shared corners, which the Tessellator javadoc allows ('Holes may only touch at one vertex'), and you expect XYShape.createIndexableFields or LatLonShape.createIndexableFields to return its triangles, as 10.4.0 does. Instead, since #15739 (first released in 10.5.0), both calls throw IllegalArgumentException 'Unable to Tessellate shape. Possible malformed shape detected.', so the document cannot be indexed, although every proper subset of the holes tessellates. The cause is that #15739 changed the reflex-vertex branch of Tessellator.isLocallyInside from '&lt; 0' to '&lt;= 0', which accepts a candidate exactly collinear with a neighbouring edge; reverting that comparison fixes this polygon but breaks the #15731 polygon #15739 was written for, so a correct fix has to be more selective.

The test shows that `XYShape.createIndexableFields` rejects a valid polygon whose three holes touch the outer ring and each other only at single shared vertices (a regression from #15739; 10.4.0 accepts it).

## Expected vs actual

Expected the polygon to be tessellated; actual on main: `IllegalArgumentException: Unable to Tessellate shape. Possible malformed shape detected.` (from `Tessellator.earcutLinkedList` via `splitEarcut`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestTessellatorChainedTouchingHoles.java](src/org/apache/lucene/document/TestTessellatorChainedTouchingHoles.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorChainedTouchingHoles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

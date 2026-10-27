# Tessellator bridges a hole whose left-most vertex touches an outer-ring edge to a vertex off that edge, so LatLonShape and XYShape index the hole (in some shapes also area outside the polygon) as part of the shape, or reject a valid polygon

Bug LFV-210 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon whose hole touches the outer boundary at one corner, which the Tessellator javadoc allows ('Holes may only touch at one vertex'), for example the outer ring (2,0) (1,1) (0,2) (3,3) with the hole (1,1) (1,2) (2,2), and you expect a point inside the hole not to match. To cut the polygon into triangles Lucene first joins the hole to the outer ring with a bridge; when the hole's left-most corner lies on an outer edge, Tessellator.fetchHoleBridge links it to the vertex before that edge instead of the edge's own endpoint, so the bridge runs across the hole. The triangles then cover the hole and an INTERSECTS query for a point in it matches the document with no error, or, for other shapes such as a hole corner touching the middle of an outer edge, the valid polygon is rejected with 'Unable to Tessellate shape'.

## Expected vs actual

- Expected: You expect a point inside the hole not to match.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/geo/TestTessellatorHoleTouchingShell.java](src/org/apache/lucene/geo/TestTessellatorHoleTouchingShell.java) (JUnit test)
- [src/HoleTouchingShellMain.java](src/HoleTouchingShellMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.geo.TestTessellatorHoleTouchingShell

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" HoleTouchingShellMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

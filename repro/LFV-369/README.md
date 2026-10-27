# BBoxStrategy's overlap-ratio relevance (BBoxOverlapRatioValueSource) undercounts the overlap of two dateline-crossing boxes that intersect in two longitude pieces, so the score is too low

Bug LFV-369 · Area: spatial-extras / BBoxStrategy overlap-ratio relevance (BBoxOverlapRatioValueSource.score: intersection width when both boxes cross the dateline) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a dateline-crossing rectangle with BBoxStrategy in the default geo context and score it with makeOverlapRatioValueSource and a query rectangle that also crosses the dateline, and you expect the documented score intersectionArea/queryArea*p + intersectionArea/targetArea*(1-p), where intersectionArea is 'the area of the intersection between the query and target envelopes'. When the two boxes wrap far enough round the globe that they overlap twice, once across the dateline and once on the far side (query rect(10,-170,-10,10) and document rect(170,20,-10,10) share 170..-170 across the dateline, 20 degrees, and 10..20, 10 degrees), the score counts only the piece across the dateline: 0.10317460317460317 instead of 0.15476190476190477 at p = 0.5. The both-cross branch computes the width as right - left + 360, which is only that piece, while the branch for one crossing box already adds up both pieces.

## Expected vs actual

- Expected: You expect the documented score intersectionArea/queryArea*p + intersectionArea/targetArea*(1-p), where intersectionArea is 'the area of the intersection between the query and target envelopes'.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/spatial/bbox/TestOverlapRatioTwoPieceIntersection.java](src/org/apache/lucene/spatial/bbox/TestOverlapRatioTwoPieceIntersection.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial.bbox.TestOverlapRatioTwoPieceIntersection
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/spatial-extras/](../../models/spatial-extras/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

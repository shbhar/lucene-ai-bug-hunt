# BBoxStrategy's overlap-ratio explanation (BBoxOverlapRatioValueSource) shows the target ratio under 'queryFactor' instead of the query ratio, so explain misreports how the score was computed; the score itself is correct

Bug LFV-370 · Area: spatial-extras / BBoxStrategy overlap-ratio relevance (BBoxOverlapRatioValueSource.score: explanation tree) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score documents with BBoxStrategy.makeOverlapRatioValueSource and call explain, and you expect the tree to show the terms of the documented formula, queryFactor = queryRatio * queryTargetProportion and targetFactor = targetRatio * (1 - queryTargetProportion). Instead the 'ratio' child of the 'queryFactor' node, printed next to 'area of &lt;query box&gt;', is targetRatio, the same number as under 'targetFactor': for the query box x[0,20] y[0,10] and the document box x[10,40] y[5,15] at proportion 0.3 it shows 0.16666667 (50/300) where queryRatio is 0.25 (50/200). The score (0.19166666) and the queryFactor value (0.075) are right; one token on line 255 is wrong, a slip from the 2015 LUCENE-6446 Explanation API rewrite, and 5.1.0 still showed queryRatio.

The test shows that `BBoxStrategy.makeOverlapRatioValueSource(...).explain(...)` lists the target ratio as the "ratio" under "queryFactor" (one 20x10 document box inside a 40x40 query box, proportion 0.5).

## Expected vs actual

Expected: queryRatio 200/1600 = 0.125; actual (-ea and -da): targetRatio 1.0 ("queryFactor ratio expected:&lt;0.125&gt; but was:&lt;1.0&gt;"); the score itself is correct.

## Files

- [src/org/apache/lucene/spatial/bbox/TestBBoxOverlapRatioExplainQueryRatio.java](src/org/apache/lucene/spatial/bbox/TestBBoxOverlapRatioExplainQueryRatio.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.bbox.TestBBoxOverlapRatioExplainQueryRatio
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# Multi-dimensional PointInSetQuery skips segments that contain matching points, because its segment early exit uses the lexicographically first and last set points as per-dimension bounds

Bug LFV-475 · Area: search / points queries (PointInSetQuery segment early exit) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-475.html](../../bugs/LFV-475.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass PointInSetQuery for a 2-D point field, as Lucene's own tests do, and search for the set {(0,1), (1,0)} in an index whose segment holds one document at (1,0). You expect that document to match, as it does for the one-point set {(1,0)}. Instead, since 10.3.0 the query keeps the first and last set points in sorted order, (0,1) and (1,0), as if they were the set's per-dimension minimum and maximum, concludes from dimension 1 (1 is above the segment's maximum 0) that the segment cannot match, and skips it, so count is 0 with no error; adding a point to a set can thus remove hits.

## Expected vs actual

- Expected: `count` is 1 for {(0,1), (1,0)}, as for {(1,0)} alone and as on 10.2.2.
- Actual: `count` is 0 (`p:{0,1 1,0} expected:<1> but was:<0>`), with assertions on and off, on 10.3.0 to 10.5.2 and main.

## Files

- [src/org/apache/lucene/search/TestPointInSetQueryMultiDimEarlyExit.java](src/org/apache/lucene/search/TestPointInSetQueryMultiDimEarlyExit.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPointInSetQueryMultiDimEarlyExit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPointInSetQueryMultiDimEarlyExit.java](TestPointInSetQueryMultiDimEarlyExit.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestMultiDimPointSetSegmentSkip.java](TestMultiDimPointSetSegmentSkip.java)
- [TestMultiDimSetQueryDenyRule.java](TestMultiDimSetQueryDenyRule.java)

Formal models and scripts this bug's part of the hunt used: [../../models/bulk-overload-precondition-matrix/](../../models/bulk-overload-precondition-matrix/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# SimpleWKTShapeParser.parse throws StackOverflowError instead of a result or its declared ParseException when GEOMETRYCOLLECTIONs nest a few thousand levels deep, valid or unterminated (10,000 levels, about 210 KB of text, on the default thread stack of the test machine; every release since 7.6.0)

Bug LFV-954 · Area: core / geo WKT parsing (SimpleWKTShapeParser) · Priority: unranked · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SimpleWKTShapeParser.parse on a GEOMETRYCOLLECTION that holds a GEOMETRYCOLLECTION, and so on 10,000 levels deep (about 210 KB of valid WKT), or on the same text left unterminated, and expect the nested Object result, or the method's declared ParseException for bad or too-deep input. Instead java.lang.StackOverflowError escapes, because parseGeometry and parseGeometryCollection call each other once per nesting level with no depth limit and nothing turns the Error into a ParseException; the same unterminated text still gives ParseException at 5,000 levels. The caller has no limit it can set, and a plain length cap does not help, because about 42 KB already fails on a 512 KiB stack while real polygons are often longer than 200 KB.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: java.lang.StackOverflowError escapes, because parseGeometry and parseGeometryCollection call each other once per nesting level with no depth limit and nothing turns the Error into a ParseException; the same unterminated text still gives ParseException at 5,000 levels.

## Files

- [src/org/apache/lucene/geo/TestWKTDeepGeometryCollection.java](src/org/apache/lucene/geo/TestWKTDeepGeometryCollection.java) (JUnit test)

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

# run 1: assertions off (production-like), seed 383DEE9
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=383DEE9 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestWKTDeepGeometryCollection
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

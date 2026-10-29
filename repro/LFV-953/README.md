# Polygon.fromGeoJSON throws NullPointerException instead of its own ParseException 'coordinates should be an array, but got:...' when "coordinates" (or an element of a MultiPolygon's coordinates) is JSON null or an object, because the error message calls o.getClass on the null the parser uses for those values (every release since 6.2.0)

Bug LFV-953 · Area: core / geo GeoJSON parsing (Polygon.fromGeoJSON, SimpleGeoJSONPolygonParser) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Polygon.fromGeoJSON on a GeoJSON Polygon whose "coordinates" is null or an object, such as {"type":"Polygon","coordinates":null}, or on a MultiPolygon with an object element, {"type":"MultiPolygon","coordinates":[{}]}, and expect the ParseException that the parser has an explicit check for ('coordinates should be an array, but got:...', 'elements of coordinates array should be an array, but got:...'), as you get when coordinates is a string, a number or a boolean. Instead java.lang.NullPointerException ('Cannot invoke "Object.getClass" because "o" is null') escapes, because SimpleGeoJSONPolygonParser stores JSON null and nested objects as Java null and both checks build their message with + o.getClass. Every other type check in the class builds its message with + o, which is null-safe.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: java.lang.NullPointerException ('Cannot invoke "Object.getClass" because "o" is null') escapes, because SimpleGeoJSONPolygonParser stores JSON null and nested objects as Java null and both checks build their message with + o.getClass.

## Files

- [src/org/apache/lucene/geo/TestGeoJSONNullCoordinates.java](src/org/apache/lucene/geo/TestGeoJSONNullCoordinates.java) (JUnit test)

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
     execute --select-class org.apache.lucene.geo.TestGeoJSONNullCoordinates
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

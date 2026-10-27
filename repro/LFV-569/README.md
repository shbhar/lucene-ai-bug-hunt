# Polygon.fromGeoJSON garbles every \uXXXX string escape (it appends the code point as decimal digits and then reads the four hex digits again), so valid GeoJSON with an escaped letter in a key or type value is rejected with ParseException, and a malformed escape throws NumberFormatException (every release since 6.2.0)

Bug LFV-569 · Area: core / geo GeoJSON parsing (Polygon.fromGeoJSON, SimpleGeoJSONPolygonParser) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Polygon.fromGeoJSON on valid GeoJSON in which one character of a key or of the type value is written as a JSON \uXXXX escape, for example {"type": "\u0050olygon", "coordinates":...}, and expect the same polygon as for {"type": "Polygon",...}, because JSON (RFC 8259 section 7) lets any string character be escaped and the parser has a branch commented '4 hex digit unicode BMP escape'. Instead that branch appends the code point with StringBuilder.append(int), that is as decimal digits, and does not skip the four hex digits, so "\u0050olygon" becomes "800050olygon" and the call throws ParseException 'can only handle type... but got 800050olygon' (an escaped key such as "\u0074ype" gives 'did not see type: Polygon or MultiPolygon'). A malformed escape such as \uZZZZ throws an unchecked NumberFormatException instead of ParseException, and because a garbled key no longer matches, a non-CRS84 crs whose 'name' key is escaped, or a second feature whose 'coordinates' key is escaped, is silently accepted instead of rejected.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: That branch appends the code point with StringBuilder.append(int), that is as decimal digits, and does not skip the four hex digits, so "\u0050olygon" becomes "800050olygon" and the call throws ParseException 'can only handle type... but got 800050olygon' (an escaped key such as "\u0074ype" gives 'did not see type: Polygon or MultiPolygon').

## Files

- [src/org/apache/lucene/geo/TestGeoJSONUnicodeEscape.java](src/org/apache/lucene/geo/TestGeoJSONUnicodeEscape.java) (JUnit test)
- [src/org/apache/lucene/geo/TestGeoJSONUnicodeEscapeOld.java](src/org/apache/lucene/geo/TestGeoJSONUnicodeEscapeOld.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.geo.TestGeoJSONUnicodeEscape

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestGeoJSONUnicodeEscapeOld
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

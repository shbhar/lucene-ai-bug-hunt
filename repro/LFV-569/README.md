# Polygon.fromGeoJSON garbles every \uXXXX string escape (it appends the code point as decimal digits and then reads the four hex digits again), so valid GeoJSON with an escaped letter in a key or type value is rejected with ParseException, and a malformed escape throws NumberFormatException (every release since 6.2.0)

Finding LFV-569 · Area: core / geo GeoJSON parsing (Polygon.fromGeoJSON, SimpleGeoJSONPolygonParser) · Priority: low · Impact: wrong error · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Polygon.fromGeoJSON on valid GeoJSON in which one character of a key or of the type value is written as a JSON \uXXXX escape, for example {"type": "\u0050olygon", "coordinates": ...}, and expect the same polygon as for {"type": "Polygon", ...}, because JSON (RFC 8259 section 7) lets any string character be escaped and the parser has a branch commented '4 hex digit unicode BMP escape'. Instead that branch appends the code point with StringBuilder.append(int), that is as decimal digits, and does not skip the four hex digits, so "\u0050olygon" becomes "800050olygon" and the call throws ParseException 'can only handle type ... but got 800050olygon' (an escaped key such as "\u0074ype" gives 'did not see type: Polygon or MultiPolygon'). A malformed escape such as \uZZZZ throws an unchecked NumberFormatException instead of ParseException, and because a garbled key no longer matches, a non-CRS84 crs whose 'name' key is escaped, or a second feature whose 'coordinates' key is escaped, is silently accepted instead of rejected.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: That branch appends the code point with StringBuilder.append(int), that is as decimal digits, and does not skip the four hex digits, so "\u0050olygon" becomes "800050olygon" and the call throws ParseException 'can only handle type ... but got 800050olygon' (an escaped key such as "\u0074ype" gives 'did not see type: Polygon or MultiPolygon').

## Files

- [src/org/apache/lucene/geo/TestGeoJSONUnicodeEscape.java](src/org/apache/lucene/geo/TestGeoJSONUnicodeEscape.java) (JUnit test)
- [src/org/apache/lucene/geo/TestGeoJSONUnicodeEscapeOld.java](src/org/apache/lucene/geo/TestGeoJSONUnicodeEscapeOld.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- The javac line below compiles the two files under src/ together, and they are the same test for two ranges of Lucene versions: they differ in the package from which they import LuceneTestCase and in the class name. As printed, the line stops on a build of Lucene main or of branch_11x and with the jars of the 10.6.0 release (TestGeoJSONUnicodeEscapeOld.java does not compile there), and both runs then find no test. Name one file in the javac line, in place of $(find src -name '*.java'), and leave out the run of the other class. On a build of main or of branch_11x (there V=11.0.0-SNAPSHOT) and with the 10.6.0 jars (Maven Central, org.apache.lucene, in place of the three $LUCENE jars; V=10.6.0) that file is src/org/apache/lucene/geo/TestGeoJSONUnicodeEscape.java, the class of run 1: run on 2026-11-22 on a build of main b5a4546a5964, on a build of branch_11x 262e019500ec and with the 10.6.0 jars, with assertions on and off, run 1 reports 3 tests found and 2 failed, each with java.text.ParseException (testEscapedLetterInTypeValue: '... but got 800050olygon at character offset 9'; testEscapedLetterInKey: 'did not see type: Polygon or MultiPolygon at character offset 113'), and testControlNoEscape passes; with the jars of 9.12.3 and of 9.1.0 the same file gives the same result (run the same day with assertions on). TestGeoJSONUnicodeEscapeOld.java, the class of run 2, imports LuceneTestCase from org.apache.lucene.util, its package in the releases up to 9.0.0 (from 9.1.0 on it is org.apache.lucene.tests.util): named alone in the javac line, with the jars of 9.0.0 and of 8.11.4 (V=9.0.0, V=8.11.4), run 2 reports the same two failures of three tests (run the same day with assertions on).
- How to read the outcome: a failing test is the finding reproducing: with the javac line as the note above says, run 1 reports 3 tests found and 2 tests failed, each of the 2 with a java.text.ParseException, and testControlNoEscape passes.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

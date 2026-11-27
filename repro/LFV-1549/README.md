# Polygon.fromGeoJSON refuses a valid GeoJSON Feature when an array in its properties holds true, false or null as an element (ParseException 'expected another array or number while parsing array, not ...'), while the same parser accepts these literals as member values and, since 8.3.0, strings in such an array (run on the 90 releases from 6.2.0 to 10.6.0, on branch_11x and on main; known outside Lucene)

Finding LFV-1549 · Area: core / geo GeoJSON parsing (Polygon.fromGeoJSON, SimpleGeoJSONPolygonParser) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Polygon.fromGeoJSON on a GeoJSON Feature whose geometry is a well-formed Polygon and whose properties hold an array with a boolean or a null in it, for example {"type":"Feature","properties":{"flags":[true,false,null]},"geometry":{"type":"Polygon","coordinates":[...]}}, and expect the one polygon back: the method's javadoc accepts a Polygon 'optionally embedded under a "type: Feature"' and links the GeoJSON specification, in which the value of a Feature's properties is 'any JSON object or a JSON null value'. Instead it throws java.text.ParseException 'expected another array or number while parsing array, not 't'' ('f' or 'n' when false or null is the first such element), because the parser reads through the properties with its one array reader, parseArray, whose element dispatch has branches for an array, an object, a number and (since 8.3.0) a string and throws for anything else, while the member-value dispatch of the same class reads true, false and null: the same Feature parses with the literals as member values ({"a":true,"b":false,"c":null}) and, from 8.3.0 on, with an array of strings. An array in a foreign member (run: beside a bare Polygon's members, beside a Feature's geometry, inside the geometry object) is refused the same way; there the contract is weaker, because RFC 7946 section 6.1 lets support for foreign members vary. Not new in 11: 10.6.0 fails the same test, and so does 6.2.0, the first release with the method. The parser also refuses other valid JSON (string escapes other than \\ and \u, an exponent with a plus sign); those are not part of this row.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It throws java.text.ParseException 'expected another array or number while parsing array, not 't'' ('f' or 'n' when false or null is the first such element), because the parser reads through the properties with its one array reader, parseArray, whose element dispatch has branches for an array, an object, a number and (since 8.3.0) a string and throws for anything else, while the member-value dispatch of the same class reads true, false and null: the same Feature parses with the literals as member values ({"a":true,"b":false,"c":null}) and, from 8.3.0 on, with an array of strings.

## Files

- [src/org/apache/lucene/geo/TestGeoJSONLiteralsInPropertyArray.java](src/org/apache/lucene/geo/TestGeoJSONLiteralsInPropertyArray.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene branch_11x b98f116b3179 (a commit of 2026-11-18): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestGeoJSONLiteralsInPropertyArray
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

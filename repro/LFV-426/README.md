# XYDocValuesField.newDistanceSort accepts a NaN or infinite origin although its javadoc promises IllegalArgumentException for invalid coordinates (the LatLon twin and every other XY entry point reject such values); with a NaN origin, documents without a location sort first instead of last

Bug LFV-426 · Area: core / document: XY distance sort (XYDocValuesField.newDistanceSort, XYPointSortField, XYPointDistanceComparator) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a distance sort with XYDocValuesField.newDistanceSort("p", Float.NaN, 0f), or with an infinite x or y, and expect the IllegalArgumentException that its javadoc promises when the 'location has invalid coordinates', as LatLonDocValuesField.newDistanceSort throws for NaN and as the XYDocValuesField constructor, XYPointField.newDistanceQuery and XYCircle throw for the same values. Instead the sort is built and the search runs, because the XYPointSortField constructor checks only that the field name is not null. With a NaN origin every distance is NaN, which Double.compare orders after +Infinity, so a document without a location (distance +Infinity, documented as 'missing values sort last') is returned before the documents that have one; with an infinite origin all distances tie at +Infinity.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The sort is built and the search runs, because the XYPointSortField constructor checks only that the field name is not null.

## Files

- [src/org/apache/lucene/document/TestXYDistanceSortOriginValidation.java](src/org/apache/lucene/document/TestXYDistanceSortOriginValidation.java) (JUnit test)
- [src/org/apache/lucene/document/TestXYDistanceSortOriginValidation9x.java](src/org/apache/lucene/document/TestXYDistanceSortOriginValidation9x.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestXYDistanceSortOriginValidation

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYDistanceSortOriginValidation9x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

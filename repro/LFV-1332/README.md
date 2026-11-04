# LatLonDocValuesField.newSlowGeometryQuery keeps the caller's geometry array, so two queries built from one refilled array compare equal while matching different regions, and BooleanQuery merges them (an OR of two regions finds only the first region's points; since 8.8.0)

Finding LFV-1332 · Area: document / lat-lon doc-values geometry query (LatLonDocValuesQuery, LatLonDocValuesField.newSlowGeometryQuery) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1332.html](../../bugs/LFV-1332.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build several lat/lon doc-values geometry queries in a loop by writing each region into one scratch LatLonGeometry[] and calling LatLonDocValuesField.newSlowGeometryQuery(field, relation, scratch), then OR them in a BooleanQuery. You expect each query to stay the region it was built from, as LatLonPoint.newGeometryQuery and XYDocValuesField.newSlowGeometryQuery do, since both copy the array. Instead LatLonDocValuesQuery keeps a reference to your array: it matches on a shape it captured when it was built, but equals, hashCode and toString read the array's current contents, so both queries compare equal, BooleanQuery de-duplicates them, and an OR of WITHIN(square around doc 0) and WITHIN(square around doc 1) counts 1 document instead of 2.

## Expected vs actual

- Expected: The OR finds 2 documents, one per square.
- Actual: `AssertionError: expected:<2> but was:<1>`: `q0.equals(q1)`, so `BooleanQuery` kept one clause.

## Files

- [src/TestLatLonDocValuesQueryReusedArray.java](src/TestLatLonDocValuesQueryReusedArray.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestLatLonDocValuesQueryReusedArray
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonDocValuesQueryReusedArray.java](TestLatLonDocValuesQueryReusedArray.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

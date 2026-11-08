# Long/Int/Float/DoubleRangeDocValuesField getMin/getMax (and LatLon/XYShapeDocValuesField getCentroid, getBoundingBox, numberOfTerms, getHighestDimensionType) keep returning the constructor's value after Field.setBytesValue gives the field a new value, while the index gets the new value

Finding LFV-1146 · Area: core / document: range and shape doc-values fields (Long/Int/Float/DoubleRangeDocValuesField getMin/getMax over BinaryRangeDocValuesField; LatLon/XYShapeDocValuesField getCentroid, getBoundingBox, numberOfTerms, getHighestDimensionType over ShapeDocValuesField; the inherited Field.setBytesValue) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1146.html](../../bugs/LFV-1146.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reuse a LongRangeDocValuesField the way Field's javadoc suggests: you build it for the range [1, 2] and later call setBytesValue with the encoding of [10, 20], so the field now indexes [10, 20] and you expect getMin(0) to return 10 and getMax(0) to return 20. Instead they still return 1 and 2, because the class answers from the arrays it kept from its constructor and does not override setBytesValue. Int/Float/DoubleRangeDocValuesField behave the same, and LatLon/XYShapeDocValuesField likewise keep the old shape's centroid, bounding box, term count and dimension type.

## Expected vs actual

- Expected: `getMin(0)` returns 10 and `getMax(0)` returns 20, the range the field now indexes.
- Actual: `getMin(0)` returns 1, the constructor's value: `java.lang.AssertionError: expected:<10> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestRangeDocValuesFieldGettersAfterSetBytesValue.java](src/org/apache/lucene/document/TestRangeDocValuesFieldGettersAfterSetBytesValue.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestRangeDocValuesFieldGettersAfterSetBytesValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRangeDocValuesFieldGettersAfterSetBytesValue.java](TestRangeDocValuesFieldGettersAfterSetBytesValue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

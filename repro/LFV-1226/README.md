# BinaryPoint.newRangeQuery(String, byte[][], byte[][]) does not throw the IllegalArgumentException its javadoc promises when lowerValue.length != upperValue.length if both bounds pack to the same number of bytes (for example 2 dimensions x 2 bytes against 1 x 4), and instead builds a query that reads the upper bound's bytes in the lower bound's dimension layout, because only the packed byte lengths are compared (since 6.0.0, LUCENE-7117/LUCENE-7118)

Finding LFV-1226 · Area: core / document: BinaryPoint range query factory (n-dimensional BinaryPoint.newRangeQuery, PointRangeQuery constructor checks) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1226.html](../../bugs/LFV-1226.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call BinaryPoint.newRangeQuery("f", lower, upper) with a lower bound of two 2-byte dimensions and an upper bound of one 4-byte dimension, and expect the IllegalArgumentException its javadoc promises 'if lowerValue.length != upperValue.length'. Instead you get a query, printed as f:[binary(0 0) TO binary(1 1)],[binary(0 0) TO binary(1 1)]: the 4-byte upper bound was silently re-split into two 2-byte dimensions. newRangeQuery packs each bound on its own and passes only lowerValue.length as the dimension count, and the PointRangeQuery constructor compares only the packed byte lengths, so a dimension-count mismatch gets through whenever the byte totals agree.

## Expected vs actual

- Expected: `IllegalArgumentException`, because the lower bound has 2 dimensions and the upper bound 1.
- Actual: No exception: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestBinaryPointRangeDimensionCount.java](src/org/apache/lucene/document/TestBinaryPointRangeDimensionCount.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestBinaryPointRangeDimensionCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBinaryPointRangeDimensionCount.java](TestBinaryPointRangeDimensionCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

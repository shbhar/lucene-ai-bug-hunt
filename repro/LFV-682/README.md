# DatasetSplitter puts at most about half of each class into the test index, whatever testRatio is

Bug LFV-682 · Area: classification / utils: DatasetSplitter (split, test/cv/training assignment loop) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-682.html](../../bugs/LFV-682.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index with new DatasetSplitter(0.8, 0).split(...) to put 80% of the documents into the test index. You expect about 8 of every 10 documents of each class in the test index, as crossValidationRatio 0.8 gives 8 of 10 in the cross-validation index. Instead the test index gets only 5 of 10 and training gets the other 5, because the loop lets a document into the test index only when its running counter b is even (DatasetSplitter.java:153, 'if (b % 2 == 0 &amp;&amp; tc &lt; testSize)'), so at most every other document can be a test document, with no error.

## Expected vs actual

- Expected: testRatio 0.8, 1.0 and 0.6 with cross-validation 0.2 give test/cv/training 8/0/2, 10/0/0 and 6/2/2.
- Actual: All three get 5 test documents (5/0/5, 5/0/5, 5/2/3): `5 tests completed, 3 failed`.

## Files

- [src/org/apache/lucene/classification/utils/TestDatasetSplitterTestRatio.java](src/org/apache/lucene/classification/utils/TestDatasetSplitterTestRatio.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/classification/build/libs/lucene-classification-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.utils.TestDatasetSplitterTestRatio
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDatasetSplitterRatios.java](TestDatasetSplitterRatios.java)
- [TestDatasetSplitterTestRatio.java](TestDatasetSplitterTestRatio.java)
- [TestSplitterTestRatioV1.java](TestSplitterTestRatioV1.java)
- [TestSplitterRatioSweepV2.java](TestSplitterRatioSweepV2.java)
- [TestDatasetSplitterUpstreamOracleV3.java](TestDatasetSplitterUpstreamOracleV3.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# BooleanPerceptronClassifier with batchSize &gt; 1 can silently lose the weight updates of misclassified training documents: the weight FST is rebuilt only when a misclassified document sits at a multiple of batchSize and never after the last batch, and updates inside a batch are computed from the last FST build and overwrite each other, so the trained model is wrong (released since 5.0.0)

Bug LFV-680 · Area: classification / BooleanPerceptronClassifier (batched training) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-680.html](../../bugs/LFV-680.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two training documents labelled false, 'bb' and 'aa aa', train BooleanPerceptronClassifier with bias 1.5 and batchSize 2, and expect assignClass('aa aa') = false, which is what batchSize 1 gives on the same data and what both an online and a mini-batch perceptron give. Instead it returns true (score 0.81): the update for the misclassified 'aa aa' (batch position 1) is written only to the pending weight map, because the FST that holds the weights is rebuilt only when a misclassified document sits at a position that is a multiple of batchSize, and the constructor ends by clearing that map without a final rebuild. Updates inside a batch are also computed from the last FST build and overwrite each other, so with batchSize 10 and three training documents 'aa' labelled false only the first update is applied and the model still answers true.

## Expected vs actual

- Expected: With batchSize 2 and 3, `assignClass` returns false for "aa aa" and "aa", as with batchSize 1.
- Actual: All three return true (scores 0.81, 0.81 and 0.28): `3 tests completed, 3 failed`.

## Files

- [src/org/apache/lucene/classification/TestBooleanPerceptronBatchUpdates.java](src/org/apache/lucene/classification/TestBooleanPerceptronBatchUpdates.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.TestBooleanPerceptronBatchUpdates
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBooleanPerceptronBatchSize.java](TestBooleanPerceptronBatchSize.java)
- [TestBooleanPerceptronBatchUpdates.java](TestBooleanPerceptronBatchUpdates.java)
- [TestPerceptronBatchV1.java](TestPerceptronBatchV1.java)
- [TestPerceptronBatchSemantics.java](TestPerceptronBatchSemantics.java)
- [Audit4PerceptronProbe.java](Audit4PerceptronProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

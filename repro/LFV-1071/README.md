# DatasetSplitter.split in its all-fields mode (fieldNames omitted) throws at the first document with a stored binary field, because it copies the bytes into an indexed, tokenized TextField-type field that Field rejects

Finding LFV-1071 · Area: classification / utils: DatasetSplitter (split, createNewDoc all-fields copy of a stored binary value) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1071.html](../../bugs/LFV-1071.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index into training, test and cross-validation indexes with DatasetSplitter.split and leave fieldNames out, which the javadoc documents as 'all [fields] should be used', and a document of the source index has a stored binary field such as new StoredField("payload", new byte[] {1, 2, 3}). You expect the split to succeed and keep the bytes; createNewDoc even has a branch written for binary values. Instead split() throws an IOException wrapping IllegalArgumentException 'cannot set a BytesRef value on a tokenized field', because every copied field gets the indexed, tokenized type TextField.TYPE_STORED and the core Field constructor refuses a BytesRef value on such a type, so no split of such an index is possible in that mode.

## Expected vs actual

- Expected: The split succeeds, and the test index holds the document with `payload` = {1, 2, 3}.
- Actual: `IOException: IllegalArgumentException: cannot set a BytesRef value on a tokenized field` from `DatasetSplitter.createNewDoc` (`DatasetSplitter.java:199`); in this one-document example no document reaches any output.

## Files

- [src/org/apache/lucene/classification/utils/TestDatasetSplitterStoredBinaryField.java](src/org/apache/lucene/classification/utils/TestDatasetSplitterStoredBinaryField.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.classification.utils.TestDatasetSplitterStoredBinaryField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDatasetSplitterStoredBinaryField.java](TestDatasetSplitterStoredBinaryField.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

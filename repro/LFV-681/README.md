# DatasetSplitter.split with explicit fieldNames copies only the first value of each multi-valued field into the split indexes

Bug LFV-681 · Area: classification / utils: DatasetSplitter (split, createNewDoc fieldNames branch) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-681.html](../../bugs/LFV-681.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index into training, test and cross-validation indexes with DatasetSplitter.split and name the fields to copy, for example {id, text, cls}, where text has two stored values in each document. You expect each new document to carry both values of text, as it does when fieldNames is null ('all should be used'). Instead only the first value is copied, because the named-fields branch reads document.getField(name), which returns only the first value, so the second and later values are missing from the stored fields and from the postings of every output index, with no error.

## Expected vs actual

- Expected: In each of the test, cross-validation and training indexes the copied document has `text` = [first value, second value] and `docFreq(text:second)` = 1, as with no field names.
- Actual: `text` = [first value] and `docFreq(text:second)` = 0 in all three indexes; no exception.

## Files

- [src/org/apache/lucene/classification/utils/TestDatasetSplitterMultiValuedField.java](src/org/apache/lucene/classification/utils/TestDatasetSplitterMultiValuedField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.classification.utils.TestDatasetSplitterMultiValuedField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDatasetSplitterNamedFieldsMultiValued.java](TestDatasetSplitterNamedFieldsMultiValued.java)
- [TestDatasetSplitterMultiValuedField.java](TestDatasetSplitterMultiValuedField.java)
- [TestDatasetSplitterNamedFieldsV3.java](TestDatasetSplitterNamedFieldsV3.java)
- [DatasetSplitterHistoryMain.java](DatasetSplitterHistoryMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

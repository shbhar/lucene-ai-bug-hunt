# KNearestNeighborDocumentClassifier leaves a Reader-valued text field of the document it classifies out of its query without an error, so the document can be assigned another class than the same text given as a String

Finding LFV-1603 · Area: classification / document classifiers (KNearestNeighborDocumentClassifier.knnSearch, reached through assignClass(Document) and both getClasses overloads) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You classify a Document with KNearestNeighborDocumentClassifier, and one of the text fields that you configured as its inputs carries its text as a Reader (new TextField(name, reader)) and not as a String. You expect that field's text to take part in the classification, or the call to say that it does not. Instead the classifier builds its like-this clauses from Document.getValues, which leaves out a value without a String form, so the Reader-valued field adds no clause, no error is raised and a class is still returned: in the test (two training documents, k = 1, that field as the only text field) the text 'blue sky' given as a fresh Reader is assigned 'food', the class of the training document that was added to the index first, where the same text given as a String is assigned 'nature' (a control beside the test); in the runs with a Reader that counts its calls the Reader was not read, and the answer was the answer for a Document without fields.

## Expected vs actual

- Expected: You expect that field's text to take part in the classification, or the call to say that it does not.
- Actual: The classifier builds its like-this clauses from Document.getValues, which leaves out a value without a String form, so the Reader-valued field adds no clause, no error is raised and a class is still returned: in the test (two training documents, k = 1, that field as the only text field) the text 'blue sky' given as a fresh Reader is assigned 'food', the class of the training document that was added to the index first, where the same text given as a String is assigned 'nature' (a control beside the test); in the runs with a Reader that counts its calls the Reader was not read, and the answer was the answer for a Document without fields.

## Files

- [src/TestKNearestNeighborDocumentClassifierReaderValuedField.java](src/TestKNearestNeighborDocumentClassifierReaderValuedField.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced, with assertions on and off.
- Tested on Lucene main 1640b5c394de (a commit of 2026-11-24): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (4e8fd3dc3bc4) and on a build of Lucene main (77d56fefb355), with and without assertions. What the test does: it indexes two training documents with StandardAnalyzer ('red apple' with the class food, 'blue sky' with the class nature), builds a KNearestNeighborDocumentClassifier with k = 1 over the field 'text', and classifies a document whose 'text' field carries the text 'blue sky' as a Reader (new TextField("text", new StringReader("blue sky"))). It expects the class nature and gets food, the class of the training document that was added first: org.junit.ComparisonFailure: expected:&lt;[nature]&gt; but was:&lt;[food]&gt;. Lucene throws nothing. The same text given as a String is assigned nature (a main() copy of the test printed both answers in each of its runs). The test is plain JUnit 4 with no randomness; the seed in the command is not read. On the older releases that main() copy ran (the same documents, input text and k): it printed 'String-valued nature, Reader-valued food' on each of the 93 releases from 6.0.0, the first with the class, to 10.6.0, without assertions. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there; for the release, set V=10.6.0 and use the seven 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common, lucene-classification, lucene-grouping, lucene-queries) in place of the seven $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/classification: with the test copied to lucene/classification/src/test/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 4e8fd3dc3bc4 and on main 77d56fefb355, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with org.junit.ComparisonFailure: expected:&lt;[nature]&gt; but was:&lt;[food]&gt;; Lucene throws no exception.
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

# run 1: assertions on, seed 5EED0993
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=5EED0993 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestKNearestNeighborDocumentClassifierReaderValuedField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

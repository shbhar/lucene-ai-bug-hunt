# Copying a ParallelLeafReader into an index (IndexWriter.addIndexes over SlowCodecReaderWrapper, or a merge whose OneMerge.wrapForMerge returns one) silently stores a later sub-reader's stored fields under another field's name: ParallelLeafReader.storedFields lets each sub-reader visit with its own FieldInfo numbers while getFieldInfos renumbers the fields, and StoredFieldsWriter.MergeVisitor skips its remap-by-name step when the numbers look aligned

Bug LFV-652 · Area: core / index (ParallelLeafReader stored fields x StoredFieldsWriter.MergeVisitor, on addIndexes and wrapForMerge copies) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-652.html](../../bugs/LFV-652.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two indexes in lockstep, as ParallelLeafReader requires (index A stores title='Lucene in Action', index B stores price='42'), wrap them in a ParallelLeafReader, which itself returns both fields correctly, and copy it into a new index with IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(reader)), expecting the copy to hold title and price. Instead the copy holds title='Lucene in Action' and title='42' and no price field, with no error and a clean CheckIndex: storedFields lets B report price with B's own field number 0, the parallel reader's getFieldInfos numbers price 1, and because the target's merged numbers equal the parallel reader's, the stored-fields merge skips its remap-by-name step and writes the value under number 0, which is title. A merge whose OneMerge.wrapForMerge adds a parallel index with stored fields behind each segment goes wrong the same way (a 'summary' value stored as a second 'id').

## Expected vs actual

- Expected: The copied document holds `title="Lucene in Action"` and `price="42"` (after the merge: `id`, `body` and `summary`).
- Actual: It holds `title="Lucene in Action"` and `title="42"` and no `price` (after the merge `summary0` is a second `id`); 2 of 2 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestParallelReaderCopyKeepsStoredFieldNames.java](src/org/apache/lucene/index/TestParallelReaderCopyKeepsStoredFieldNames.java) (JUnit test)
- [src/org/apache/lucene/index/TestParallelLeafReaderStoredCopyIntentReview.java](src/org/apache/lucene/index/TestParallelLeafReaderStoredCopyIntentReview.java) (JUnit test)
- [src/org/apache/lucene/index/TestParallelReaderExpertStoredFieldsReaders.java](src/org/apache/lucene/index/TestParallelReaderExpertStoredFieldsReaders.java) (JUnit test)
- [src/repro/ParallelCopyStoredNamesMain.java](src/repro/ParallelCopyStoredNamesMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.index.TestParallelReaderCopyKeepsStoredFieldNames

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestParallelLeafReaderStoredCopyIntentReview \
       --select-class org.apache.lucene.index.TestParallelReaderExpertStoredFieldsReaders
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.ParallelCopyStoredNamesMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestParallelReaderCopyKeepsStoredFieldNames.java](TestParallelReaderCopyKeepsStoredFieldNames.java)
- [TestParallelLeafReaderCopyStoredFieldNames.java](TestParallelLeafReaderCopyStoredFieldNames.java)
- [TestParallelReaderExpertStoredFieldsReaders.java](TestParallelReaderExpertStoredFieldsReaders.java)
- [ParallelCopyStoredNamesMain.java](ParallelCopyStoredNamesMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# ByteWritesTrackingDirectoryWrapper(dir, true) gives every temp output it tracks the name 'ByteTrackingIndexOutput{&lt;file&gt;}' from IndexOutput#getName() instead of the file name, so the default stored-fields writer cannot reopen or delete its temp files and every merge (and the first flush of an index-sorted writer) fails with NoSuchFileException (since 9.5.0)

Finding LFV-1361 · Area: misc / store: ByteWritesTrackingDirectoryWrapper and ByteTrackingIndexOutput (temp outputs and IndexOutput#getName; consumer: core stored-fields FieldsIndexWriter) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1361.html](../../bugs/LFV-1361.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a directory in ByteWritesTrackingDirectoryWrapper(dir, true) to count flushed and merged bytes including temporary files, commit two one-document segments with the default codec and call forceMerge(1); you expect one merged segment with two documents, as with trackTempOutput=false, because Directory#createTempOutput says the temp file name is 'accessible via IndexOutput#getName()'. Instead the wrapper's temp outputs report getName() = 'ByteTrackingIndexOutput{_2_Lucene90FieldsIndex-doc_ids_4.tmp}' (ByteTrackingIndexOutput.java:33), and the stored-fields index writer, which reopens and deletes its temp files by getName(), fails the merge with NoSuchFileException at FieldsIndexWriter.finish. Every merge fails this way, and with an index sort the first flush fails too and closes the writer.

## Expected vs actual

- Expected: `forceMerge(1)` leaves one segment and the test passes, as it does with `trackTempOutput=false` or with the fix.
- Actual: `NoSuchFileException: ByteTrackingIndexOutput{_2_Lucene90FieldsIndex-doc_ids_4.tmp}` from `FieldsIndexWriter.finish` (`FieldsIndexWriter.java:120`), with assertions on and off.

## Files

- [src/org/apache/lucene/misc/store/TestTrackTempOutputForceMerge.java](src/org/apache/lucene/misc/store/TestTrackTempOutputForceMerge.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 3AD8575B7411EAEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=3AD8575B7411EAEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestTrackTempOutputForceMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTrackTempOutputForceMerge.java](TestTrackTempOutputForceMerge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

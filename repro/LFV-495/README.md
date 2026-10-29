# When DirectoryReader.openIfChanged on a FilterDirectoryReader (for example SoftDeletesDirectoryReaderWrapper) fails while wrapping the reopened reader, the newly opened inner reader is never closed, so the segment readers it shares with the old reader, and its files, leak

Bug LFV-495 · Area: core / index reader reopen (FilterDirectoryReader.wrapDirectoryReader on every doOpenIfChanged path; SoftDeletesDirectoryReaderWrapper as the stock trigger) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reopen a FilterDirectoryReader, for example a SoftDeletesDirectoryReaderWrapper, with DirectoryReader.openIfChanged (directly or through SearcherManager or ReaderManager), and the call fails after the inner reader was reopened, while the wrapper wraps it: for example an I/O error while the soft-deletes wrapper reads the new segment's soft-deletes doc values. You expect the failed call to release what it opened, so that closing your old reader, the only one you hold ('The provided reader is not closed (you are responsible for doing so); if a new reader is returned you also must eventually close it'), frees every segment reader and file. Instead FilterDirectoryReader.wrapDirectoryReader neither returns nor closes the inner reader it was just given, so that reader keeps an extra reference on every shared segment (refCount 2 after the failure, still 1 after you close the old reader) and the new segments' files stay open.

The test shows that when `DirectoryReader.openIfChanged` on a `FilterDirectoryReader` throws while wrapping the reopened reader, the reopened inner reader is never closed, so it keeps its reference on the segment it shares with the old reader.

## Expected vs actual

Expected the shared segment reader's refCount to be back to 1 (only the old reader holds it); actual: 2 (`expected:<1> but was:<2>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestFilterDirectoryReaderReopenWrapFailure.java](src/org/apache/lucene/index/TestFilterDirectoryReaderReopenWrapFailure.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.index.TestFilterDirectoryReaderReopenWrapFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

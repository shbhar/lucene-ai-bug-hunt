# A failed NRT reader open (DirectoryReader.open(IndexWriter), openIfChanged over the writer, or a SearcherManager/ReaderManager refresh) whose IndexWriterConfig leaf sorter throws never releases the IndexFileDeleter reference it took, so the still-open writer keeps every segment file of that point in time on disk for the rest of its life

Finding LFV-1067 · Area: core / NRT reader open from IndexWriter (StandardDirectoryReader.open(IndexWriter, ...) failure path, with an IndexWriterConfig leaf sorter, LUCENE-9507) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1067.html](../../bugs/LFV-1067.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set a leaf sorter on IndexWriterConfig and open an NRT reader from the writer; if the sorter throws while the new reader sorts its leaves, you expect only that exception and a writer left as it was, because the open's own catch releases the segment readers it took and IndexWriter.getReader keeps the writer usable after a non-Error exception. Instead the open has already called writer.incRefDeleter(segmentInfos), which only the returned reader's close undoes, and no reader is returned, so the writer keeps every segment file of that point in time referenced for the rest of its life: after forceMerge(1) and commit, the files of the merged-away segments _0 and _1 are still in the directory.

## Expected vs actual

- Expected: After the failed open, `forceMerge(1)` and `commit()`, no file of `_0` or `_1` is left; only `_2`'s files and `segments_3` remain.
- Actual: `AssertionError: file of a merged-away segment was not deleted: _0.cfe`; all 6 files of `_0` and `_1` remain, assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/index/TestNrtOpenLeafSorterFailureReleasesFiles.java](src/org/apache/lucene/index/TestNrtOpenLeafSorterFailureReleasesFiles.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNrtOpenLeafSorterFailureReleasesFiles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNrtOpenLeafSorterFailureReleasesFiles.java](TestNrtOpenLeafSorterFailureReleasesFiles.java)

Formal models and scripts this finding's part of the hunt used: [../../models/reader-open-leaf-sorter-failure-leaks/](../../models/reader-open-leaf-sorter-failure-leaks/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

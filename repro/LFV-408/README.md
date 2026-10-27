# IndexSplitter.split writes each copied segment into the destination commit without its doc-values update metadata, so on main the copy silently reads wrong doc values, and in every release since 4.6.0 the first IndexWriter opened on the copy deletes the copied update files and the copy can no longer be opened

Bug LFV-408 · Area: misc / index tools (IndexSplitter.split) over core / index: SegmentCommitInfo per-commit doc-values update state · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-408.html](../../bugs/LFV-408.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc value in a segment (updateNumericDocValue, updateBinaryDocValue, or a soft delete), commit, and copy that segment to a new index with IndexSplitter.split, expecting the copy to hold the same documents and values and to stay a normal index. Instead split copies all of the segment's files but writes a destination commit that records only the segment's generation numbers, not its per-commit field-infos files, doc-values update files or (on main) doc-values overlays. So on main the copy reads an updated field from its newest sparse update alone (price 10,999,12,13 reads as none,999,none,none), and in every release since 4.6.0 the first IndexWriter opened on the copy deletes the copied update files as unreferenced, after which the copy cannot be opened (NoSuchFileException: _0_1.fnm).

## Expected vs actual

- Expected: The copy reads 10,999,12,13, its commit lists the same 8 files as the source, and it still opens after an IndexWriter was opened on it.
- Actual: 3 of 4 fail on main: the copy reads none,999,none,none; its commit lists only _0.cfe, _0.cfs, _0.si; after a writer, NoSuchFileException: _0_1.fnm. The control passes.

## Files

- [src/org/apache/lucene/misc/index/TestIndexSplitterDocValuesUpdates.java](src/org/apache/lucene/misc/index/TestIndexSplitterDocValuesUpdates.java) (JUnit test)
- [src/IndexSplitterUpdatesMain.java](src/IndexSplitterUpdatesMain.java) (main() class)
- [src2/IndexSplitterUpdatesMain.java](src2/IndexSplitterUpdatesMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexSplitterDocValuesUpdates

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" IndexSplitterUpdatesMain
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" IndexSplitterUpdatesMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestIndexSplitterDocValuesUpdates.java](TestIndexSplitterDocValuesUpdates.java)
- [optionA-clone-with-test.diff](optionA-clone-with-test.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.

# Index-sorted IndexWriter.addIndexes(CodecReader...) with an empty (maxDoc 0) input reader silently writes a corrupt segment: MultiSorter gives the empty reader's nonexistent doc 0 a merged doc id, so the postings of later documents point one document too far

Finding LFV-1133 · Area: index / index sorting at merge: MultiSorter doc maps, reached through IndexWriter.addIndexes(CodecReader...) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1133.html](../../bugs/LFV-1133.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort an IndexWriter by a long field k and call addIndexes(CodecReader...) with the single segment of a sorted 2-document index {a k=1, c k=2} followed by an empty reader that advertises the same sort (SortingCodecReader.wrap(SlowCodecReaderWrapper.wrap(new ParallelLeafReader()), sort)), and you expect a 2-document segment in which a TermQuery on id:a returns the document that stores a, as it does without an index sort or with the empty reader listed first. Instead MultiSorter.sort also queues a cursor at doc 0 for the empty reader, whose nonexistent doc 0 (missing sort value 0) is popped first and takes merged doc id 0, so a and c are mapped to 1 and 2 while the new segment has maxDoc 2. With assertions disabled addIndexes and commit succeed, but id:a returns the document that stores c and the posting of id:c points at doc 2 (&gt;= maxDoc 2), which only CheckIndex at level 2 or higher reports.

## Expected vs actual

- Expected: `id:a` returns the document that stores `a`.
- Actual: `expected:<[a]> but was:<[c]>` with assertions off (the test framework's close-time `CheckIndex`, level 2 or higher: `doc 2 >= maxDoc 2`); with assertions on, an `AssertionError` in the stored-fields merge inside `addIndexes`.

## Files

- [src/org/apache/lucene/index/TestIndexSortAddIndexesEmptyReader.java](src/org/apache/lucene/index/TestIndexSortAddIndexesEmptyReader.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIndexSortAddIndexesEmptyReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIndexSortAddIndexesEmptyReader.java](TestIndexSortAddIndexesEmptyReader.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
